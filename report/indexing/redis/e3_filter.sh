#!/bin/sh
# E3 (Redis): loc nhieu dieu kien + xep theo gia + phan trang (F4), 3 cach:
#   Cach 1: khong index (SCAN + giai ma JSON + loc + sap xep trong Lua)
#   Cach 2: index tu lam bang Sorted Set (bai 6): moi danh muc mot ZSET, score = gia
#   Cach 3: RediSearch: categoryId NUMERIC, price NUMERIC SORTABLE, status TAG
# Ba cau hoi: A (danh muc 5 + gia 1-5 trieu, 321 ket qua), B (chi gia, 15.218), C (chi danh muc, 4.000).
# Moi lan tim = trang dau 24 san pham xep gia tang (chi lay id) + tong so ket qua.
# Can chay 00_setup.sh truoc. Chay: docker compose exec -T redis sh /tmp/indexing-redis/e3_filter.sh
set -eu
cd "$(dirname "$0")"
. ./lib/common.sh

echo "== Moi truong =="
$R INFO server | grep -E '^redis_version' | tr -d '\r'
count=$($R --eval lua/lab_memory.lua , 'idxlab:product:*' | head -1)
echo "  So document idxlab:product:*: $count"
if [ "$count" != "200000" ]; then
  echo "Thieu du lieu thi nghiem. Chay 00_setup.sh truoc." >&2
  exit 1
fi
$R FT.DROPINDEX idx:idxlab-e3 >/dev/null 2>&1 || true
$R --eval lua/lab_delete.lua , 'idxlab:price:*' >/dev/null

echo
echo "================= CACH 1: KHONG INDEX (SCAN) ================="
echo "Kiem tra so ket qua:  A -> $($R --eval lua/e3_filter_scan.lua , 1 A)   B -> $($R --eval lua/e3_filter_scan.lua , 1 B)   C -> $($R --eval lua/e3_filter_scan.lua , 1 C)"
echo "Do thoi gian (2 lan tim moi luot vi rat cham):"
measure "SCAN / A" 2 lua/e3_filter_scan.lua 2 A
measure "SCAN / B" 2 lua/e3_filter_scan.lua 2 B
measure "SCAN / C" 2 lua/e3_filter_scan.lua 2 C

echo
echo "================= CACH 2: INDEX TU LAM (SORTED SET) ================="
before=$(used_memory)
t0=$(now_ms)
b=0
while [ $b -lt 10 ]; do
  s=$((b * 20000 + 1)); e=$((b * 20000 + 20000))
  $R --eval lua/e3_build_manual.lua , "$s" "$e" >/dev/null
  b=$((b + 1))
done
t1=$(now_ms)
after=$(used_memory)
echo "  Thoi gian tao 51 Sorted Set (50 danh muc + 1 chung): $((t1 - t0)) ms"
$R --eval lua/lab_memory.lua , 'idxlab:price:*' | awk 'NR == 1 { printf "  So key index: %s\n", $0 } NR == 2 { printf "  Tong MEMORY USAGE: %.1f MB\n", $0 / 1048576 }'
echo "  used_memory tang them: $(awk -v a="$after" -v b="$before" 'BEGIN { printf "%.1f", (a - b) / 1048576 }') MB"
echo "  ZCARD idxlab:price:cat:5 = $($R ZCARD idxlab:price:cat:5), ZCARD idxlab:price:active = $($R ZCARD idxlab:price:active)"
echo "Vi du A: ZRANGE idxlab:price:cat:5 1000000 5000000 BYSCORE LIMIT 0 5 WITHSCORES"
$R ZRANGE idxlab:price:cat:5 1000000 5000000 BYSCORE LIMIT 0 5 WITHSCORES | paste - - | sed 's/^/  /'
echo "Kiem tra so ket qua:  A -> $($R --eval lua/e3_filter_manual.lua , 1 A)   B -> $($R --eval lua/e3_filter_manual.lua , 1 B)   C -> $($R --eval lua/e3_filter_manual.lua , 1 C)"
echo "Do thoi gian (20 lan tim moi luot):"
measure "TU_LAM / A" 20 lua/e3_filter_manual.lua 20 A
measure "TU_LAM / B" 20 lua/e3_filter_manual.lua 20 B
measure "TU_LAM / C" 20 lua/e3_filter_manual.lua 20 C
echo "Xoa index tu lam: $($R --eval lua/lab_delete.lua , 'idxlab:price:*') key"

echo
echo "================= CACH 3: REDISEARCH ================="
echo "FT.CREATE idx:idxlab-e3 ON JSON PREFIX 1 idxlab:product: SCHEMA \$.categoryId AS categoryId NUMERIC \$.price AS price NUMERIC SORTABLE \$.status AS status TAG"
before=$(used_memory)
t0=$(now_ms)
$R FT.CREATE idx:idxlab-e3 ON JSON PREFIX 1 idxlab:product: SCHEMA \
  '$.categoryId' AS categoryId NUMERIC '$.price' AS price NUMERIC SORTABLE '$.status' AS status TAG >/dev/null
wait_indexed idx:idxlab-e3
t1=$(now_ms)
after=$(used_memory)
echo "  Thoi gian tao index (den khi index xong 200.000 document): $((t1 - t0)) ms"
echo "  used_memory tang them: $(awk -v a="$after" -v b="$before" 'BEGIN { printf "%.1f", (a - b) / 1048576 }') MB"
echo "Thong tin index (FT.INFO):"
ftinfo idx:idxlab-e3
echo "Cay thuc thi (FT.EXPLAIN) cho cau A:"
$R FT.EXPLAIN idx:idxlab-e3 '@categoryId:[5 5] @price:[1000000 5000000] @status:{ACTIVE}'
echo "Kiem tra so ket qua:  A -> $($R --eval lua/e3_filter_search.lua , 1 A)   B -> $($R --eval lua/e3_filter_search.lua , 1 B)   C -> $($R --eval lua/e3_filter_search.lua , 1 C)"
echo "Do thoi gian (20 lan tim moi luot):"
measure "REDISEARCH / A" 20 lua/e3_filter_search.lua 20 A
measure "REDISEARCH / B" 20 lua/e3_filter_search.lua 20 B
measure "REDISEARCH / C" 20 lua/e3_filter_search.lua 20 C

$R FT.DROPINDEX idx:idxlab-e3 >/dev/null
echo
echo "Kiem tra: so ket qua phai bang Oracle (A = 321, B = 15218, C = 4000)."
echo "Xong E3 (Redis). Da xoa idx:idxlab-e3 va idxlab:price:*; du lieu idxlab:product:* van giu."
