#!/usr/bin/env bash
# Nạp lịch sử 12 tháng (~100.000 đơn, 10.000 sản phẩm, 20.000 khách sinh tự động) cho phần Query processing,
# rồi dựng lại read model Redis để báo cáo nguồn Redis khớp Oracle.
# Chạy từ terminal khác khi scripts/dev.sh đang chạy (cần Oracle, Redis và backend):
#
#   scripts/seed-history.sh          nạp (chạy lại thì xóa dữ liệu sinh cũ rồi sinh lại, ra cùng dữ liệu)
#   scripts/seed-history.sh --xoa    chỉ xóa dữ liệu sinh rồi dựng lại read model
#
# Dữ liệu mẫu của app (catalog, tài khoản, flash sale) không bị đụng tới. Chi tiết: backend/src/main/resources/db/history-seed/README.md
set -euo pipefail
cd "$(dirname "$0")/.."
ok() { printf '    \033[32m%s\033[0m\n' "$1"; }
step() { printf '\n\033[36m==> %s\033[0m\n' "$1"; }
fail() { printf '\033[31mLỖI: %s\033[0m\n' "$1"; exit 1; }

case "${1:-}" in
  '') script=01_generate.sql; label='Nạp 100.000 đơn lịch sử (khoảng 1,5 phút)' ;;
  --xoa) script=99_cleanup.sql; label='Xóa dữ liệu lịch sử đã sinh' ;;
  *) fail "Tham số lạ: $1" ;;
esac

curl -fs -m 5 http://127.0.0.1:8080/api/health >/dev/null || fail 'Backend chưa chạy. Chạy scripts/dev.sh trước.'
cid=$(docker compose ps -q oracle)
[ -n "$cid" ] || fail 'Container Oracle chưa chạy. Chạy scripts/dev.sh trước.'

# Mật khẩu SHOP lấy từ biến trong container, không đưa lên dòng lệnh.
step "$label"
docker exec -u 0 "$cid" rm -rf /tmp/seed
docker cp backend/src/main/resources/db/history-seed "$cid:/tmp/seed" >/dev/null
docker compose exec -T oracle bash -c \
  '{ printf "connect shop/%s@//localhost:1521/FREEPDB1\n" "$SHOP_PASSWORD"; printf "whenever sqlerror exit failure rollback\n@/tmp/seed/'"$script"'\nexit\n"; } | sqlplus -s -L /nolog' \
  > backend/history-seed.log 2>&1 || fail 'Lỗi khi chạy script SQL. Xem backend/history-seed.log'
ok 'Xong (log ở backend/history-seed.log)'

# API quản trị cần JWT: đăng nhập bằng tài khoản admin mẫu của profile dev.
step 'Dựng lại read model Redis cho báo cáo (khoảng 20-35 s với 100k đơn)'
token=$(curl -fs -m 30 -X POST -H 'Content-Type: application/json' \
  -d "{\"email\":\"${SHOPFLASH_ADMIN_EMAIL:-admin@shopflash.local}\",\"password\":\"${SHOPFLASH_ADMIN_PASSWORD:-ShopFlash123!}\"}" \
  http://127.0.0.1:8080/api/auth/login | sed -E 's/.*"accessToken":"([^"]+)".*/\1/') \
  || fail 'Không đăng nhập được tài khoản admin mẫu'
curl -fs -m 900 -X POST -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d '{}' \
  http://127.0.0.1:8080/api/admin/reports/analytics/rebuild >/dev/null || fail 'Dựng read model lỗi. Xem backend/dev.log'
ok 'Read model Redis đã khớp Oracle. Mở trang Báo cáo: http://localhost:5173/admin'
