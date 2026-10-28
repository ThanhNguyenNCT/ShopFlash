#!/usr/bin/env bash
# D2 <invalid|valid> <run_id> <user_id> <p1_id> <p2_id>
set -euo pipefail
mode=${1:?Cần mode}
run_id=${2:?Cần run ID}
user_id=${3:?Cần User ID}
p1=${4:?Cần Product 1 ID}
p2=${5:?Cần Product 2 ID}
[[ "$mode" == invalid || "$mode" == valid ]] || exit 2
[[ "$run_id" =~ ^[a-z0-9]{1,12}$ && "$user_id" =~ ^[0-9]+$ && "$p1" =~ ^[0-9]+$ && "$p2" =~ ^[0-9]+$ ]] || exit 2
[[ "$p1" != "$p2" ]] || exit 2
prefix="txresearch:${run_id}:"
q2=1
if [[ "$mode" == invalid ]]; then q2=101; fi
printf 'MODE=%s RUN_ID=%s QUANTITY_P1=1 QUANTITY_P2=%s\n' "$mode" "$run_id" "$q2"
docker compose exec -T redis redis-cli --raw EVAL \
  "$(cat report/transaction/research/redis/lua/D2-checkout.lua)" 5 \
  "${prefix}cart" "${prefix}order" "${prefix}inventory:${p1}" \
  "${prefix}inventory:${p2}" "${prefix}quota" \
  "$user_id" "$p1" "$p2" 1 "$q2"
