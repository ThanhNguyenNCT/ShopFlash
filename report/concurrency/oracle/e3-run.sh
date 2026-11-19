#!/usr/bin/env sh
# E3: hai phiên SERIALIZABLE cùng "đọc sold rồi ghi sold + 1" trên dòng bộ đếm của Lab (cc_lab_items, id 1).
# Chạy từ thư mục gốc repo (Git Bash, macOS, Linux), cần container oracle đang chạy và file .env:
#   sh report/concurrency/oracle/e3-run.sh
# Kết quả: report/concurrency/results/e3-session-1.txt, e3-session-2.txt.
# Chạy tay: mở hai cửa sổ sqlplus bằng user SHOP, dán e3-session-1.sql vào cửa sổ 1, trong lúc nó
# chờ 6 giây thì dán e3-session-2.sql vào cửa sổ 2.
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/../results"
mkdir -p "$OUT"
# Chỉ lấy hai biến cần dùng trong .env; mật khẩu không in ra màn hình.
SCHEMA="$(grep -E '^ORACLE_SCHEMA=' .env | cut -d= -f2- | tr -d '\r')"
PASS="$(grep -E '^ORACLE_APP_PASSWORD=' .env | cut -d= -f2- | tr -d '\r')"
DB="$(grep -E '^ORACLE_DATABASE=' .env | cut -d= -f2- | tr -d '\r')"
CONNECT="${SCHEMA:-SHOP}/${PASS}@//localhost:1521/${DB:-FREEPDB1}"

run() { docker compose exec -T oracle sqlplus -s -L "$CONNECT" < "$1"; }

# Đặt lại bộ đếm: 10 suất, chưa bán suất nào.
printf '%s\n' \
  "MERGE INTO cc_lab_items t USING (SELECT 1 AS lab_item_id FROM dual) s ON (t.lab_item_id = s.lab_item_id)" \
  "WHEN MATCHED THEN UPDATE SET t.allocated = 10, t.sold = 0, t.version = 0" \
  "WHEN NOT MATCHED THEN INSERT (lab_item_id, allocated, sold, version) VALUES (1, 10, 0, 0);" \
  "COMMIT;" "EXIT" | docker compose exec -T oracle sqlplus -s -L "$CONNECT" > /dev/null

run "$HERE/e3-session-1.sql" > "$OUT/e3-session-1.txt" 2>&1 &
sleep 2
run "$HERE/e3-session-2.sql" > "$OUT/e3-session-2.txt" 2>&1
wait
echo "===== Phiên 1 ====="; cat "$OUT/e3-session-1.txt"
echo "===== Phiên 2 ====="; cat "$OUT/e3-session-2.txt"
