#!/bin/sh
# Nap 200.000 san pham thi nghiem vao Redis (idxlab:product:{n}), giong het bang IDXLAB_PRODUCTS cua Oracle.
# Chay: docker compose exec -T redis sh /tmp/indexing-redis/00_setup.sh
set -eu
cd "$(dirname "$0")"
. ./lib/common.sh

echo "== Moi truong =="
$R INFO server | grep -E '^redis_version' | tr -d '\r'
$R MODULE LIST | awk 'p == "name" { n = $0 } p == "ver" { print "  module " n " " $0 } { p = $0 }'

echo "== Xoa du lieu thi nghiem cu (neu co) =="
$R FT.DROPINDEX idx:idxlab-e1 >/dev/null 2>&1 || true
echo "  idxlab:sku:*     da xoa $($R --eval lua/lab_delete.lua , 'idxlab:sku:*')"
echo "  idxlab:product:* da xoa $($R --eval lua/lab_delete.lua , 'idxlab:product:*')"

echo "== Nap 200.000 san pham (10 dot x 20.000) =="
before=$(used_memory)
t0=$(now_ms)
b=0
while [ $b -lt 10 ]; do
  s=$((b * 20000 + 1)); e=$((b * 20000 + 20000))
  $R --eval lua/lab_load_batch.lua , "$s" "$e" >/dev/null
  b=$((b + 1))
done
t1=$(now_ms)
after=$(used_memory)
echo "  Thoi gian nap: $((t1 - t0)) ms"

echo "== Kiem tra =="
for n in 1 2 123456 199999; do
  echo "  idxlab:product:$n = $($R JSON.GET idxlab:product:$n)"
done
$R --eval lua/lab_memory.lua , 'idxlab:product:*' | awk 'NR == 1 { printf "  So key: %s\n", $0 } NR == 2 { printf "  Tong MEMORY USAGE: %.1f MB (%.0f byte/key)\n", $0 / 1048576, $0 / 200000 }'
echo "  used_memory tang them: $(awk -v a="$after" -v b="$before" 'BEGIN { printf "%.1f", (a - b) / 1048576 }') MB"
