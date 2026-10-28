#!/usr/bin/env bash
# P1: fixture Redis mô phỏng, chỉ dùng năm key cố định trong namespace txresearch:<runId>:*.
# Chạy từ root repo: setup.sh <run_id> <product_1_id> <product_2_id> <product_3_id>
set -euo pipefail

run_id=${1:?Cần run ID}
p1=${2:?Cần Product 1 ID}
p2=${3:?Cần Product 2 ID}
p3=${4:?Cần Product 3 ID}
[[ "$run_id" =~ ^[a-z0-9]{1,12}$ ]] || { echo 'run ID không hợp lệ' >&2; exit 2; }
for id in "$p1" "$p2" "$p3"; do
  [[ "$id" =~ ^[0-9]+$ ]] || { echo 'Product ID không hợp lệ' >&2; exit 2; }
done
[[ "$p1" != "$p2" && "$p1" != "$p3" && "$p2" != "$p3" ]] || {
  echo 'Product ID phải khác nhau' >&2; exit 2;
}

prefix="txresearch:${run_id}:"
cart="${prefix}cart"
inv1="${prefix}inventory:${p1}"
inv2="${prefix}inventory:${p2}"
inv3="${prefix}inventory:${p3}"
quota="${prefix}quota"
keys=("$cart" "$inv1" "$inv2" "$inv3" "$quota")

redis() { docker compose exec -T redis redis-cli --raw "$@" | tr -d '\r'; }

existing=$(redis EXISTS "${keys[@]}")
if [[ "$existing" == 0 ]]; then
  redis HSET "$cart" "$p1" 1 "$p2" 1 "$p3" 1 >/dev/null
  for key in "$inv1" "$inv2" "$inv3"; do
    redis HSET "$key" on_hand 100 reserved 0 version 0 >/dev/null
  done
  redis HSET "$quota" limit 2 remaining 2 held 0 >/dev/null
  result=CREATED
elif [[ "$existing" == 5 ]]; then
  result=EXISTS
else
  echo "Fixture Redis chưa đầy đủ: $existing/5 key; dừng để đối chiếu manifest" >&2
  exit 1
fi

[[ "$(redis HGET "$cart" "$p1")" == 1 &&
   "$(redis HGET "$cart" "$p2")" == 1 &&
   "$(redis HGET "$cart" "$p3")" == 1 &&
   "$(redis HLEN "$cart")" == 3 ]] || { echo 'Cart không ở trạng thái đầu kỳ' >&2; exit 1; }
for key in "$inv1" "$inv2" "$inv3"; do
  [[ "$(redis HGET "$key" on_hand)" == 100 &&
     "$(redis HGET "$key" reserved)" == 0 &&
     "$(redis HGET "$key" version)" == 0 ]] || { echo "Inventory sai: $key" >&2; exit 1; }
done
[[ "$(redis HGET "$quota" remaining)" == 2 &&
   "$(redis HGET "$quota" held)" == 0 ]] || { echo 'Quota không ở trạng thái đầu kỳ' >&2; exit 1; }

printf 'RESULT=%s\nRUN_ID=%s\n' "$result" "$run_id"
printf 'KEY=%s\n' "${keys[@]}"
