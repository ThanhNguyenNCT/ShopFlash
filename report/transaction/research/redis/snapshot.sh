#!/usr/bin/env bash
# Chỉ đọc đúng key manifest của một run; cần ba Product ID từ Oracle manifest.
set -euo pipefail
run_id=${1:?Cần run ID}
p1=${2:?Cần Product 1 ID}
p2=${3:?Cần Product 2 ID}
p3=${4:?Cần Product 3 ID}
[[ "$run_id" =~ ^[a-z0-9]{1,12}$ ]] || exit 2
for id in "$p1" "$p2" "$p3"; do [[ "$id" =~ ^[0-9]+$ ]] || exit 2; done
prefix="txresearch:${run_id}:"
redis() { docker compose exec -T redis redis-cli --raw "$@" | tr -d '\r'; }
printf 'RUN_ID=%s\n' "$run_id"
for key in "${prefix}cart" "${prefix}inventory:${p1}" "${prefix}inventory:${p2}" \
           "${prefix}inventory:${p3}" "${prefix}quota"; do
  printf 'KEY=%s TYPE=%s\n' "$key" "$(redis TYPE "$key")"
  redis HGETALL "$key"
done
printf 'KEY=%sorder TYPE=%s\n' "$prefix" "$(redis TYPE "${prefix}order")"
redis HGETALL "${prefix}order"
