#!/usr/bin/env bash
# Chỉ đọc key nêu trong manifest và mọi key còn lại của cùng run.
set -euo pipefail
run_id=${1:?Cần run ID}
key_file=${2:?Cần danh sách key manifest}
[[ "$run_id" =~ ^[a-z0-9]{1,12}$ && -f "$key_file" ]] || exit 2
prefix="txresearch:${run_id}:"
while IFS= read -r key || [[ -n "$key" ]]; do
  [[ "$key" == "$prefix"* ]] || { echo "Key ngoài run: $key" >&2; exit 2; }
  printf '%s ' "$key"
  docker compose exec -T redis redis-cli --raw TYPE "$key" </dev/null | tr -d '\r'
done < "$key_file"
printf 'ALL_RUN_KEYS:\n'
docker compose exec -T redis redis-cli --scan --pattern "${prefix}*"
