#!/bin/sh
# E2 (Redis): tim san pham co TU KHOA trong ten (F3).
#   Cach 1: khong index, SCAN + kiem tra chuoi trong Lua (giong LIKE '%tu%')
#   Cach 2: RediSearch truong TEXT (inverted index, co diem lien quan)
# Tu khoa: "615" (hiem, 200 san pham), "sony pro" (2 tu, 1.679), "pro" (pho bien, 20.159).
# Can chay 00_setup.sh truoc. Chay: docker compose exec -T redis sh /tmp/indexing-redis/e2_name.sh
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
$R FT.DROPINDEX idx:idxlab-e2 >/dev/null 2>&1 || true

echo
echo "================= CACH 1: KHONG INDEX (SCAN + tim chuoi) ================="
echo "Kiem tra so ket qua:"
echo "  615      -> $($R --eval lua/e2_search_scan.lua , 1 615 -)"
echo "  sony pro -> $($R --eval lua/e2_search_scan.lua , 1 sony pro)"
echo "  pro      -> $($R --eval lua/e2_search_scan.lua , 1 pro -)"
echo "Do thoi gian (moi lan tim = duyet 200.000 document, lay 24 dau + dem tong; 2 lan tim moi luot vi rat cham):"
measure "SCAN / HIEM    " 2 lua/e2_search_scan.lua 2 615 -
measure "SCAN / HAI_TU  " 2 lua/e2_search_scan.lua 2 sony pro
measure "SCAN / PHO_BIEN" 2 lua/e2_search_scan.lua 2 pro -

echo
echo "================= CACH 2: REDISEARCH TEXT ================="
echo "FT.CREATE idx:idxlab-e2 ON JSON PREFIX 1 idxlab:product: SCHEMA \$.name AS name TEXT"
before=$(used_memory)
t0=$(now_ms)
$R FT.CREATE idx:idxlab-e2 ON JSON PREFIX 1 idxlab:product: SCHEMA '$.name' AS name TEXT >/dev/null
wait_indexed idx:idxlab-e2
t1=$(now_ms)
after=$(used_memory)
echo "  Thoi gian tao index (den khi index xong 200.000 document): $((t1 - t0)) ms"
echo "  used_memory tang them: $(awk -v a="$after" -v b="$before" 'BEGIN { printf "%.1f", (a - b) / 1048576 }') MB"
echo "Thong tin index (FT.INFO):"
ftinfo idx:idxlab-e2

echo "Cay thuc thi (FT.EXPLAIN) cho @name:(sony pro):"
$R FT.EXPLAIN idx:idxlab-e2 '@name:(sony pro)'
echo "Vi du: 5 san pham lien quan nhat cho \"sony pro\" (dong dau la tong so ket qua):"
$R FT.SEARCH idx:idxlab-e2 '@name:(sony pro)' RETURN 1 name LIMIT 0 5 TIMEOUT 0
echo "Kiem tra so ket qua:"
echo "  615      -> $($R --eval lua/e2_search_text.lua , 1 615 -)"
echo "  sony pro -> $($R --eval lua/e2_search_text.lua , 1 sony pro)"
echo "  pro      -> $($R --eval lua/e2_search_text.lua , 1 pro -)"
echo "Do thoi gian (moi lan tim = 1 lenh FT.SEARCH LIMIT 0 24, tra ca tong so; 20 lan tim moi luot):"
measure "TEXT / HIEM    " 20 lua/e2_search_text.lua 20 615 -
measure "TEXT / HAI_TU  " 20 lua/e2_search_text.lua 20 sony pro
measure "TEXT / PHO_BIEN" 20 lua/e2_search_text.lua 20 pro -

$R FT.DROPINDEX idx:idxlab-e2 >/dev/null
echo
echo "Kiem tra: so ket qua phai bang Oracle (615 = 200, sony pro = 1679, pro = 20159)."
echo "Xong E2 (Redis). Da xoa idx:idxlab-e2; du lieu idxlab:product:* van giu."
