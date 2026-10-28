#!/usr/bin/env bash
# A5 <runtime|queue|lua> <run_id> <product_1_id>
# Mỗi MULTI tới EXEC dùng một redis-cli. Lỗi dự kiến được ghi nguyên trạng.
set -euo pipefail
mode=${1:?Cần runtime, queue hoặc lua}
run_id=${2:?Cần run ID}
p1=${3:?Cần Product ID}
[[ "$mode" == runtime || "$mode" == queue || "$mode" == lua ]] || exit 2
[[ "$run_id" =~ ^[a-z0-9]{1,12}$ && "$p1" =~ ^[0-9]+$ ]] || exit 2
prefix="txresearch:${run_id}:"
printf 'MODE=%s RUN_ID=%s\n' "$mode" "$run_id"
case "$mode" in
  runtime)
    {
      printf 'MULTI\n'
      printf 'HINCRBY %sinventory:%s on_hand -1\n' "$prefix" "$p1"
      printf 'HSET %sorder status PENDING product_id %s\n' "$prefix" "$p1"
      printf 'LPUSH %sorder impossible\n' "$prefix"
      printf 'HDEL %scart %s\n' "$prefix" "$p1"
      printf 'EXEC\n'
    } | docker compose exec -T redis redis-cli --raw
    ;;
  queue)
    {
      printf 'MULTI\n'
      printf 'HINCRBY %sinventory:%s on_hand -1\n' "$prefix" "$p1"
      printf 'TXRESEARCH_INVALID_COMMAND\n'
      printf 'EXEC\n'
    } | docker compose exec -T redis redis-cli --raw
    ;;
  lua)
    lua_file=report/transaction/research/redis/lua/A5-error-after-write.lua
    docker compose exec -T redis redis-cli --raw EVAL "$(cat "$lua_file")" 2 \
      "${prefix}inventory:${p1}" "${prefix}order"
    ;;
esac
