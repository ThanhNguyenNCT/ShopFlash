#!/usr/bin/env bash
# P0: chỉ đọc Redis Stack local. Chạy từ root repository; không in password.
set -euo pipefail

docker compose exec -T redis redis-cli INFO server | rg '^(redis_version|redis_mode):'
docker compose exec -T redis redis-cli INFO persistence | rg '^(aof_enabled|aof_last_write_status|rdb_last_bgsave_status):'
docker compose exec -T redis redis-cli CONFIG GET appendfsync
docker compose exec -T redis redis-cli MODULE LIST
docker compose exec -T redis redis-cli FT._LIST
docker compose exec -T redis redis-cli INFO keyspace
for prefix in 'product:*' 'flash:*' 'order:analytics:*' 'order:line:*' 'txlab:*' 'txresearch:*'; do
  count=$(docker compose exec -T redis redis-cli --scan --pattern "$prefix" | wc -l)
  printf '%s %s\n' "$prefix" "$count"
done
docker compose exec -T redis redis-cli CLIENT LIST | wc -l
