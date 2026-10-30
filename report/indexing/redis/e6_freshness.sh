#!/bin/sh
# E6 (Redis): sua du lieu xong thi index (va tim kiem) thay luc nao? Demo hanh vi, khong do toc do.
#   Phan 1: RediSearch vua tao index - index chay nen, trong luc do tim kiem chi thay MOT PHAN du lieu.
#   Phan 2: RediSearch - ghi xong la tim thay ngay (index cap nhat trong chinh lenh ghi), voi moi client.
#   Phan 3: Index tu lam (Sorted Set gia) - ung dung "sap" giua 2 lenh thi index LECH du lieu, khong ai bao loi.
#   Phan 4: MULTI/EXEC - mot lenh loi giua chung, lenh truoc khong duoc hoan tac -> index van lech.
# San pham thu nghiem idxlab:product:900001; san pham 123456 duoc doi gia roi tra lai. Can chay 00_setup.sh truoc.
# Chay: docker compose exec -T redis sh /tmp/indexing-redis/e6_freshness.sh
set -eu
cd "$(dirname "$0")"
. ./lib/common.sh
RAW="redis-cli"   # khong co -e: phan 4 can xem ca loi tra ve trong EXEC

show() { printf '  %-58s %s\n' "$1" "$2"; }

echo "== Moi truong =="
$R INFO server | grep -E '^redis_version' | tr -d '\r'
$R FT.DROPINDEX idx:idxlab-e6 >/dev/null 2>&1 || true
$R UNLINK idxlab:product:900001 >/dev/null
$R --eval lua/lab_delete.lua , 'idxlab:price:*' >/dev/null

echo
echo "================= PHAN 1: REDISEARCH VUA TAO INDEX (INDEX CHAY NEN) ================="
$R FT.CREATE idx:idxlab-e6 ON JSON PREFIX 1 idxlab:product: SCHEMA \
  '$.name' AS name TEXT '$.sku' AS sku TAG '$.price' AS price NUMERIC SORTABLE >/dev/null
i=1
while [ $i -le 4 ]; do
  total=$($R FT.SEARCH idx:idxlab-e6 '*' LIMIT 0 0 TIMEOUT 0 | head -1)
  pct=$($R FT.INFO idx:idxlab-e6 | awk 'p == "percent_indexed" { print; exit } { p = $0 }')
  show "1.$i Ngay sau FT.CREATE - tim '*' (tat ca)" "$total / 200000 document (percent_indexed = $pct)"
  sleep 0.3
  i=$((i + 1))
done
wait_indexed idx:idxlab-e6
show "1.5 Sau khi index xong - tim '*'" "$($R FT.SEARCH idx:idxlab-e6 '*' LIMIT 0 0 TIMEOUT 0 | head -1) / 200000 document"

echo
echo "================= PHAN 2: REDISEARCH - GHI XONG LA TIM THAY ================="
show "2a. Truoc khi them - tim @name:zeta" "$($R FT.SEARCH idx:idxlab-e6 '@name:zeta' LIMIT 0 0 TIMEOUT 0 | head -1) san pham"
$R JSON.SET idxlab:product:900001 '$' '{"id":900001,"sku":"LAB-0900001","name":"Robot hut bui Ecovacs Zeta 1","categoryId":5,"price":7990000,"status":"ACTIVE","inStock":true}' >/dev/null
show "2b. Ngay sau JSON.SET - tim @name:zeta (client khac)" "$($R FT.SEARCH idx:idxlab-e6 '@name:zeta' LIMIT 0 0 TIMEOUT 0 | head -1) san pham"
show "2c. Tim theo gia 7.990.000" "$($R FT.SEARCH idx:idxlab-e6 '@price:[7990000 7990000] @name:zeta' LIMIT 0 0 TIMEOUT 0 | head -1) san pham"
$R JSON.NUMINCRBY idxlab:product:900001 '$.price' 1000000 >/dev/null
show "2d. Sau khi tang gia len 8.990.000 - tim gia 7.990.000" "$($R FT.SEARCH idx:idxlab-e6 '@price:[7990000 7990000] @name:zeta' LIMIT 0 0 TIMEOUT 0 | head -1) san pham"
show "2e. Tim gia 8.990.000" "$($R FT.SEARCH idx:idxlab-e6 '@price:[8990000 8990000] @name:zeta' LIMIT 0 0 TIMEOUT 0 | head -1) san pham"
$R UNLINK idxlab:product:900001 >/dev/null
show "2f. Sau khi xoa key - tim @name:zeta" "$($R FT.SEARCH idx:idxlab-e6 '@name:zeta' LIMIT 0 0 TIMEOUT 0 | head -1) san pham"
$R FT.DROPINDEX idx:idxlab-e6 >/dev/null

echo
echo "================= PHAN 3: INDEX TU LAM - UNG DUNG SAP GIUA CHUNG ================="
b=0
while [ $b -lt 10 ]; do
  s=$((b * 20000 + 1)); e=$((b * 20000 + 20000))
  $R --eval lua/e3_build_manual.lua , "$s" "$e" >/dev/null
  b=$((b + 1))
done
P=idxlab:product:123456
Z=idxlab:price:active
show "3a. Ban dau - gia trong du lieu (JSON)" "$($R JSON.GET $P '$.price')"
show "3a. Ban dau - gia trong index (ZSCORE)" "$($R ZSCORE $Z 123456)"
echo "  ... ung dung doi gia thanh 9.999.000: chay JSON.SET roi SAP truoc khi kip ZADD ..."
$R JSON.SET $P '$.price' 9999000 >/dev/null
show "3b. Gia trong du lieu (JSON)" "$($R JSON.GET $P '$.price')"
show "3b. Gia trong index (ZSCORE)" "$($R ZSCORE $Z 123456)"
in_new=$($R ZRANGE $Z 9990000 10010000 BYSCORE | grep -cx 123456 || true)
in_old=$($R ZRANGE $Z 7100000 7200000 BYSCORE | grep -cx 123456 || true)
show "3c. Khach loc gia 9,99 - 10,01 trieu: co 123456 khong?" "$in_new (dung ra phai la 1)"
show "3d. Khach loc gia 7,1 - 7,2 trieu: co 123456 khong?" "$in_old (dung ra phai la 0, gia that da la 9.999.000)"
echo "  ... sua tay: ZADD lai cho dung ..."
$R ZADD $Z 9999000 123456 >/dev/null
show "3e. Sau khi sua tay - gia trong index" "$($R ZSCORE $Z 123456)"

echo
echo "================= PHAN 4: MULTI/EXEC - LENH LOI GIUA CHUNG, KHONG HOAN TAC ================="
echo "  Gui 1 giao dich: doi gia JSON thanh 5.555.000, roi ZADD voi score sai (khong phai so):"
printf 'MULTI\nJSON.SET %s $.price 5555000\nZADD %s khong_phai_so 123456\nEXEC\n' "$P" "$Z" | $RAW | sed 's/^/    /'
show "4a. Gia trong du lieu (JSON)" "$($R JSON.GET $P '$.price')"
show "4b. Gia trong index (ZSCORE)" "$($R ZSCORE $Z 123456)"
echo "  => Lenh JSON.SET da chay va KHONG duoc hoan tac du lenh sau loi: du lieu va index lai lech."

echo
echo "Don dep: tra gia 123456 ve 7.142.000, xoa Sorted Set va index thu nghiem."
$R JSON.SET $P '$.price' 7142000 >/dev/null
$R --eval lua/lab_delete.lua , 'idxlab:price:*' >/dev/null
show "Gia 123456 sau don dep" "$($R JSON.GET $P '$.price')"
echo "Xong E6 (Redis)."
