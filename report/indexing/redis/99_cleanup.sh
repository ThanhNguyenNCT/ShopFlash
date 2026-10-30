#!/bin/sh
# Xoa toan bo du lieu va index thi nghiem cua module indexing trong Redis (idxlab:*, idx:idxlab-*).
# Chay: docker compose exec -T redis sh /tmp/indexing-redis/99_cleanup.sh
set -eu
cd "$(dirname "$0")"
. ./lib/common.sh
for idx in $($R FT._LIST | grep '^idx:idxlab-' || true); do
  $R FT.DROPINDEX "$idx" >/dev/null && echo "Da xoa index $idx"
done
echo "Da xoa $($R --eval lua/lab_delete.lua , 'idxlab:*') key idxlab:*"
