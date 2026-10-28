#!/usr/bin/env bash
# Chạy một SQL file trong MỘT Oracle session. Dùng cho audit/setup/reset/case một session.
# B1–B3 phải dùng hai cửa sổ SQL tương tác riêng, không gọi wrapper cho từng bước.
set -euo pipefail

run_id=${1:?Cần run ID}
sql_file=${2:?Cần đường dẫn SQL file}
[[ "$run_id" =~ ^[a-z0-9]{1,12}$ ]] || { echo 'run ID không hợp lệ' >&2; exit 2; }
[[ -f "$sql_file" ]] || { echo 'SQL file không tồn tại' >&2; exit 2; }

binds=''
if (( $# > 2 )); then
  (( $# == 8 || $# == 9 )) || { echo 'Cần đúng 6 ID manifest và tùy chọn JSON order ID' >&2; exit 2; }
  names=(fixture_user_id fixture_category_id fixture_cart_id p1_id p2_id p3_id)
  args=("$@")
  for n in {0..5}; do
    value=${args[$((n+2))]}
    [[ "$value" =~ ^[0-9]+$ ]] || { echo "ID manifest không hợp lệ: ${names[n]}" >&2; exit 2; }
    binds+="variable ${names[n]} number"$'\n'
    binds+="exec :${names[n]} := ${value};"$'\n'
  done
  order_ids=${9:-[]}
  [[ "$order_ids" =~ ^\[([0-9]+(,[0-9]+)*)?\]$ ]] || { echo 'JSON order ID không hợp lệ' >&2; exit 2; }
  binds+="variable order_ids varchar2(4000)"$'\n'
  binds+="exec :order_ids := '${order_ids}';"$'\n'
fi

{
  printf "variable run_id varchar2(12)\nexec :run_id := '%s';\n" "$run_id"
  printf '%s' "$binds"
  cat "$sql_file"
} | docker compose exec -T oracle bash -lc '{
  printf "whenever sqlerror exit failure rollback\nconnect shop/%s@//localhost:1521/FREEPDB1\n" "$SHOP_PASSWORD"
  cat
  printf "\nexit\n"
} | sqlplus -s -L /nolog'
