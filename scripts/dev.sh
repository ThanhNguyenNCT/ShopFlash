#!/usr/bin/env bash
# Chạy toàn bộ ShopFlash trên máy dev (macOS / Linux): Oracle + Redis (Docker), backend Spring Boot, frontend Vite.
#
#   scripts/dev.sh              chạy tất cả, lần đầu nạp thêm ~100.000 đơn lịch sử (Ctrl+C để dừng backend và frontend)
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

# Mỗi tiến trình nền chạy trong nhóm tiến trình riêng (set -m) để Ctrl+C dừng cả nhóm:
# mvnw -> JVM của spring-boot:run, npm -> sh -> node (vite). pkill -P chỉ dừng được con trực tiếp.
set -m
PIDS=()
cleanup() { for p in "${PIDS[@]:-}"; do kill -- -"$p" 2>/dev/null || true; done; }
trap cleanup EXIT INT TERM

# Cổng đang có tiến trình khác giữ thì dừng, không tin nhầm backend/Vite cũ là cái vừa chạy.
require_free_port() {
  if command -v lsof >/dev/null 2>&1 && lsof -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; then
    fail "Cổng $1 đang bị chiếm (PID $(lsof -iTCP:"$1" -sTCP:LISTEN -t | head -1)). Tắt $2 cũ rồi chạy lại."
  fi
}

if [ "$NO_BACKEND" = 0 ]; then
  step 'Tìm JDK 21'
  # mvnw dùng JAVA_HOME (nếu có) chứ không dùng java trên PATH, nên kiểm tra đúng JDK đó.
  if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 21+ 2>/dev/null || true)
    export JAVA_HOME
  fi
  java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
  v=$("$java_bin" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')
  [ "${v:-0}" -ge 21 ] 2>/dev/null || fail "Cần JDK 21 trở lên (đang có: ${v:-không có}, lấy từ ${JAVA_HOME:-java trên PATH}). Đặt JAVA_HOME tới JDK 21."
  ok "JDK $v (${JAVA_HOME:-java trên PATH})"
  require_free_port 8080 'backend'
  step 'Chạy backend (profile dev, log ở backend/dev.log)'
  (cd backend && exec ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev > dev.log 2>&1) &
  BACKEND_PID=$!
  PIDS+=("$BACKEND_PID")
  step 'Chờ backend sẵn sàng (GET /api/health)'
  for _ in $(seq 1 90); do
    curl -fs -m 5 http://127.0.0.1:8080/api/health >/dev/null 2>&1 && break
    kill -0 "$BACKEND_PID" 2>/dev/null || fail 'Backend đã dừng khi đang khởi động. Xem backend/dev.log'
    sleep 2
  done
  curl -fs -m 5 http://127.0.0.1:8080/api/health >/dev/null || fail 'Backend chưa lên sau 3 phút. Xem backend/dev.log'
  ok 'Backend UP'

  # Lịch sử 12 tháng (~100.000 đơn) cho trang Báo cáo: chỉ sinh khi Oracle chưa có đơn GEN-*.
  # Mật khẩu SHOP lấy từ biến trong container, không đưa lên dòng lệnh.
  sqlplus_shop() {
    docker compose exec -T oracle bash -c \
      '{ printf "connect shop/%s@//localhost:1521/FREEPDB1\n" "$SHOP_PASSWORD"; cat; } | sqlplus -s -L /nolog'
  }
  gen_orders=$(printf 'set heading off feedback off pagesize 0\nselect count(*) from orders where order_code like '"'"'GEN-%%'"'"';\nexit\n' \
    | sqlplus_shop | tr -dc '0-9')
  if [ "${gen_orders:-0}" = 0 ]; then
    step 'Nạp lịch sử 12 tháng, khoảng 100.000 đơn (lần đầu, khoảng 1,5 phút)'
    cid=$(docker compose ps -q oracle)
    docker exec -u 0 "$cid" rm -rf /tmp/seed
    docker cp backend/src/main/resources/db/history-seed "$cid:/tmp/seed" >/dev/null
    printf 'whenever sqlerror exit failure rollback\n@/tmp/seed/01_generate.sql\nexit\n' | sqlplus_shop \
      > backend/history-seed.log 2>&1 || fail 'Nạp lịch sử lỗi. Xem backend/history-seed.log'
    ok 'Đã nạp lịch sử (log ở backend/history-seed.log)'
  else
    ok "Oracle đã có $gen_orders đơn lịch sử, bỏ qua bước nạp"
  fi

  # API quản trị cần JWT: đăng nhập bằng tài khoản admin mẫu của profile dev.
  token=$(curl -fs -m 30 -X POST -H 'Content-Type: application/json' \
    -d "{\"email\":\"${SHOPFLASH_ADMIN_EMAIL:-admin@shopflash.local}\",\"password\":\"${SHOPFLASH_ADMIN_PASSWORD:-ShopFlash123!}\"}" \
    http://127.0.0.1:8080/api/auth/login | sed -E 's/.*"accessToken":"([^"]+)".*/\1/') \
    || fail 'Không đăng nhập được tài khoản admin mẫu để dựng read model'
  if curl -fs -m 60 -H "Authorization: Bearer $token" http://127.0.0.1:8080/api/admin/reports/analytics/status \
      | grep -q '"inSync":false'; then
    step 'Dựng read model Redis cho báo cáo (khoảng 20-35 s với 100k đơn)'
    curl -fs -m 900 -X POST -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d '{}' \
      http://127.0.0.1:8080/api/admin/reports/analytics/rebuild >/dev/null || fail 'Dựng read model lỗi. Xem backend/dev.log'
    ok 'Read model Redis đã khớp Oracle'
  fi
fi

if [ "$NO_FRONTEND" = 0 ]; then
  require_free_port 5173 'Vite (frontend)'
  step 'Chạy frontend: http://localhost:5173'
  cd frontend
  [ -d node_modules ] || npm install
  npm run dev &
  PIDS+=($!)
fi

step 'Đang chạy. Cửa hàng: http://localhost:5173 | Quản trị: http://localhost:5173/admin | Ctrl+C để dừng'
wait
