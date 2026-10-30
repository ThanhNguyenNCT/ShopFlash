#!/bin/sh
# E1 (Redis): tim san pham theo SKU bang 3 cach: khong index (SCAN), index tu lam (key rieng), RediSearch TAG.
# Can chay 00_setup.sh truoc. Chay lai bao nhieu lan cung duoc.
# Chay: docker compose exec -T redis sh /tmp/indexing-redis/e1_sku.sh
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
echo "  Tong so key trong Redis (SCAN phai duyet qua tat ca): $($R DBSIZE)"

# Bat dau tu trang thai chua co index nao
$R FT.DROPINDEX idx:idxlab-e1 >/dev/null 2>&1 || true
$R --eval lua/lab_delete.lua , 'idxlab:sku:*' >/dev/null

echo
echo "================= CACH 1: KHONG INDEX (SCAN) ================="
echo "Moi lan tim: SCAN moi key idxlab:product:*, doc \$.sku cua tung document va so sanh"
echo "Do thoi gian (1 lan khoi dong + 5 lan do, moi lan tim 5 SKU vi rat cham):"
measure KHONG_INDEX_SCAN 5 lua/e1_lookup_scan.lua 5

echo
echo "================= CACH 2: INDEX TU LAM (bai 6) ================="
echo "Tao key idxlab:sku:{sku} -> id cho 200.000 san pham:"
before=$(used_memory)
t0=$(now_ms)
b=0
while [ $b -lt 10 ]; do
  s=$((b * 20000 + 1)); e=$((b * 20000 + 20000))
  $R --eval lua/e1_build_manual.lua , "$s" "$e" >/dev/null
  b=$((b + 1))
done
t1=$(now_ms)
after=$(used_memory)
echo "  Thoi gian tao: $((t1 - t0)) ms"
$R --eval lua/lab_memory.lua , 'idxlab:sku:*' | awk 'NR == 1 { printf "  So key index: %s\n", $0 } NR == 2 { printf "  Tong MEMORY USAGE: %.1f MB\n", $0 / 1048576 }'
echo "  used_memory tang them: $(awk -v a="$after" -v b="$before" 'BEGIN { printf "%.1f", (a - b) / 1048576 }') MB"
echo "Vi du: GET idxlab:sku:LAB-0123456 -> $($R GET idxlab:sku:LAB-0123456)"
echo "Do thoi gian (1 lan khoi dong + 5 lan do, moi lan tim 200 SKU):"
measure INDEX_TU_LAM 200 lua/e1_lookup_manual.lua 200
echo "Xoa index tu lam: $($R --eval lua/lab_delete.lua , 'idxlab:sku:*') key"

echo
echo "================= CACH 3: REDISEARCH, TRUONG TAG ================="
echo "FT.CREATE idx:idxlab-e1 ON JSON PREFIX 1 idxlab:product: SCHEMA \$.sku AS sku TAG"
before=$(used_memory)
t0=$(now_ms)
$R FT.CREATE idx:idxlab-e1 ON JSON PREFIX 1 idxlab:product: SCHEMA '$.sku' AS sku TAG >/dev/null
wait_indexed idx:idxlab-e1
t1=$(now_ms)
after=$(used_memory)
echo "  Thoi gian tao index (den khi index xong 200.000 document): $((t1 - t0)) ms"
echo "  used_memory tang them: $(awk -v a="$after" -v b="$before" 'BEGIN { printf "%.1f", (a - b) / 1048576 }') MB"
echo "Thong tin index (FT.INFO):"
ftinfo idx:idxlab-e1

echo "Cay thuc thi (FT.EXPLAIN) cho @sku:{LAB\\-0123456}:"
$R FT.EXPLAIN idx:idxlab-e1 '@sku:{LAB\-0123456}'
echo "Ket qua FT.SEARCH:"
$R FT.SEARCH idx:idxlab-e1 '@sku:{LAB\-0123456}' LIMIT 0 1 TIMEOUT 0
echo "FT.PROFILE (thoi gian tung buoc ben trong Redis):"
$R FT.PROFILE idx:idxlab-e1 SEARCH QUERY '@sku:{LAB\-0123456}' LIMIT 0 1 TIMEOUT 0
echo "Do thoi gian (1 lan khoi dong + 5 lan do, moi lan tim 200 SKU):"
measure REDISEARCH_TAG 200 lua/e1_lookup_search.lua 200

$R FT.DROPINDEX idx:idxlab-e1 >/dev/null
echo
echo "Xong E1 (Redis). Da xoa idx:idxlab-e1 va idxlab:sku:*; du lieu idxlab:product:* van giu cho cac thi nghiem sau."
