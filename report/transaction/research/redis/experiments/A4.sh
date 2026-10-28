#!/usr/bin/env bash
# A4: mỗi nhánh dùng đúng một kết nối redis-cli từ MULTI đến EXEC/DISCARD.
# A4.sh <exec|discard> <run_id> <user_id> <product_1_id>
set -euo pipefail
mode=${1:?Cần exec hoặc discard}
run_id=${2:?Cần run ID}
user_id=${3:?Cần User ID}
p1=${4:?Cần Product ID}
[[ "$mode" == exec || "$mode" == discard ]] || exit 2
[[ "$run_id" =~ ^[a-z0-9]{1,12}$ && "$user_id" =~ ^[0-9]+$ && "$p1" =~ ^[0-9]+$ ]] || exit 2
prefix="txresearch:${run_id}:"

printf 'CONNECTION=one redis-cli; MODE=%s\n' "$mode"
printf 'COMMANDS=MULTI; HSET %sorder; HINCRBY %sinventory:%s on_hand -1; HDEL %scart %s; %s\n' \
  "$prefix" "$prefix" "$p1" "$prefix" "$p1" "${mode^^}"
{
  printf 'MULTI\n'
  printf 'HSET %sorder user_id %s status PENDING product_id %s quantity 1\n' "$prefix" "$user_id" "$p1"
  printf 'HINCRBY %sinventory:%s on_hand -1\n' "$prefix" "$p1"
  printf 'HDEL %scart %s\n' "$prefix" "$p1"
  printf '%s\n' "${mode^^}"
} | docker compose exec -T redis redis-cli --raw
