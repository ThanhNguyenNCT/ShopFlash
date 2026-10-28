#!/usr/bin/env bash
# Chỉ DEL từng key đã liệt kê trong manifest. Không xóa theo wildcard/prefix.
set -euo pipefail
run_id=${1:?Cần run ID}
key_file=${2:?Cần danh sách key manifest}
[[ "$run_id" =~ ^[a-z0-9]{1,12}$ && -f "$key_file" ]] || exit 2
prefix="txresearch:${run_id}:"
mapfile -t keys < "$key_file"
(( ${#keys[@]} > 0 )) || { echo 'Manifest key rỗng' >&2; exit 2; }
declare -A seen=()
for key in "${keys[@]}"; do
  [[ "$key" =~ ^txresearch:${run_id}:[A-Za-z0-9:_-]+$ ]] || {
    echo "Key ngoài run/không hợp lệ: $key" >&2; exit 2;
  }
  [[ -z "${seen[$key]+x}" ]] || { echo "Key trùng: $key" >&2; exit 2; }
  seen[$key]=1
done

for key in "${keys[@]}"; do
  docker compose exec -T redis redis-cli DEL "$key" </dev/null
done
left=$(docker compose exec -T redis redis-cli --scan --pattern "${prefix}*")
if [[ -n "$left" ]]; then
  echo 'Còn key cùng run không nằm trong manifest; giữ nguyên để kiểm tra:' >&2
  printf '%s\n' "$left" >&2
  exit 1
fi
printf 'CLEANUP_DONE RUN_ID=%s KEYS_LISTED=%s\n' "$run_id" "${#keys[@]}"
