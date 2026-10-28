#!/usr/bin/env bash
# C2 <invalid|reference|negative> <run_id> <product_1_id>
set -euo pipefail
mode=${1:?Cần mode}
run_id=${2:?Cần run ID}
p1=${3:?Cần Product ID}
[[ "$mode" == invalid || "$mode" == reference || "$mode" == negative ]] || exit 2
[[ "$run_id" =~ ^[a-z0-9]{1,12}$ && "$p1" =~ ^[0-9]+$ ]] || exit 2
prefix="txresearch:${run_id}:"
printf 'MODE=%s RUN_ID=%s\n' "$mode" "$run_id"
case "$mode" in
  invalid)
    docker compose exec -T redis redis-cli --raw HSET "${prefix}order" product_id 999999 status PENDING
    docker compose exec -T redis redis-cli --raw HSET "${prefix}inventory:${p1}" on_hand -5
    ;;
  reference)
    docker compose exec -T redis redis-cli --raw EVAL \
      "$(cat report/transaction/research/redis/lua/C2-validate.lua)" 2 \
      "${prefix}order" "${prefix}inventory:999999"
    ;;
  negative)
    docker compose exec -T redis redis-cli --raw EVAL \
      "$(cat report/transaction/research/redis/lua/C2-validate.lua)" 2 \
      "${prefix}order" "${prefix}inventory:${p1}"
    ;;
esac
