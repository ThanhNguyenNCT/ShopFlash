#!/usr/bin/env bash
# Chạy toàn bộ ShopFlash trên máy dev (macOS / Linux): Oracle + Redis (Docker), backend Spring Boot, frontend Vite.
#
#   scripts/dev.sh              chạy tất cả (Ctrl+C để dừng backend và frontend)
#   scripts/dev.sh --no-backend chỉ database + frontend (backend chạy trong IDE)
#   scripts/dev.sh --mock       chỉ frontend với dữ liệu mẫu, không cần Docker
#   scripts/dev.sh --stop       dừng container Oracle và Redis (dữ liệu vẫn giữ)
#
# Windows: dùng scripts\dev.ps1.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
NO_BACKEND=0; NO_FRONTEND=0
step() { printf '\n\033[36m==> %s\033[0m\n' "$1"; }
ok() { printf '    \033[32m%s\033[0m\n' "$1"; }
fail() { printf '\033[31mLỖI: %s\033[0m\n' "$1"; exit 1; }

for arg in "$@"; do
  case "$arg" in
    --no-backend) NO_BACKEND=1 ;;
    --no-frontend) NO_FRONTEND=1 ;;
    --mock) cd frontend; [ -d node_modules ] || npm install; exec npm run dev:mock ;;
    --stop) exec docker compose stop ;;
    *) fail "Tham số lạ: $arg" ;;
  esac
done

step 'Kiểm tra Docker'
docker info >/dev/null 2>&1 || fail 'Docker chưa chạy. Mở Docker Desktop rồi chạy lại.'
for img in $(docker compose config --images); do
  docker image inspect "$img" >/dev/null 2>&1 || fail "Thiếu image $img. Kéo về (một lần): docker pull $img"
done
ok 'Docker và image sẵn sàng'

if [ ! -f .env ]; then
  step 'Tạo .env với mật khẩu ngẫu nhiên (chỉ dùng trên máy này, không commit)'
  gen() { printf 'Sf%s' "$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 18)"; }
  sed -e "s/^ORACLE_ADMIN_PASSWORD=.*/ORACLE_ADMIN_PASSWORD=$(gen)/" \
      -e "s/^ORACLE_APP_PASSWORD=.*/ORACLE_APP_PASSWORD=$(gen)/" \
      -e "s/^REDIS_PASSWORD=.*/REDIS_PASSWORD=$(gen)/" .env.example > .env
  ok '.env đã tạo'
fi

step 'Bật Oracle và Redis (lần đầu Oracle mất khoảng 2 phút)'
docker compose up -d --wait || fail 'docker compose up lỗi. Xem: docker compose logs oracle | grep shopflash'
step 'Tạo index Redis (init.lua)'
docker compose exec -T redis redis-cli -e --eval /opt/shopflash/redis/init.lua \
  || fail 'init.lua lỗi. Nếu báo index cũ: chạy lệnh FT.DROPINDEX trong thông báo rồi chạy lại.'

PIDS=()
# Dừng cả tiến trình con (mvnw -> java, npm -> vite), không chỉ subshell.
cleanup() { for p in "${PIDS[@]:-}"; do pkill -P "$p" 2>/dev/null || true; kill "$p" 2>/dev/null || true; done; }
trap cleanup EXIT INT TERM

if [ "$NO_BACKEND" = 0 ]; then
  step 'Kiểm tra JDK 21'
  v=$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')
  [ "${v:-0}" -ge 21 ] || fail "Cần JDK 21 trở lên (đang có: ${v:-không có}). Đặt JAVA_HOME tới JDK 21."
  step 'Chạy backend (profile dev, log ở backend/dev.log)'
  (cd backend && ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev > dev.log 2>&1) &
  PIDS+=($!)
  step 'Chờ backend sẵn sàng (GET /api/health)'
  for _ in $(seq 1 90); do
    curl -fs http://127.0.0.1:8080/api/health >/dev/null 2>&1 && break
    sleep 2
  done
  curl -fs http://127.0.0.1:8080/api/health >/dev/null || fail 'Backend chưa lên sau 3 phút. Xem backend/dev.log'
  ok 'Backend UP'
  if curl -fs http://127.0.0.1:8080/api/admin/reports/analytics/status | grep -q '"inSync":false'; then
    step 'Dựng read model Redis cho báo cáo (khoảng 20-35 s với 100k đơn)'
    curl -fs -X POST -H 'Content-Type: application/json' -d '{}' \
      http://127.0.0.1:8080/api/admin/reports/analytics/rebuild && echo
  fi
fi

if [ "$NO_FRONTEND" = 0 ]; then
  step 'Chạy frontend: http://localhost:5173'
  cd frontend
  [ -d node_modules ] || npm install
  npm run dev &
  PIDS+=($!)
fi

step 'Đang chạy. Cửa hàng: http://localhost:5173 | Quản trị: http://localhost:5173/admin | Ctrl+C để dừng'
wait
