#!/bin/sh
# E4 (Redis): index lam GHI cham di bao nhieu? Cung mot luong ghi tren 5 cau hinh, tuong ung Oracle C0-C4:
#   R0: khong index phu (chi tra theo ten key, nhu khoa chinh)                 ~ C0
#   R1: index tu lam idxlab:sku:{sku}                                            ~ C1 (ung dung tu cap nhat)
#   R2: R1 + Sorted Set gia theo danh muc + Sorted Set chung (nhu E3)            ~ C2 (ung dung tu cap nhat)
#   R3: RediSearch chi truong sku TAG                                            ~ C1 (Redis tu cap nhat)
#   R4: RediSearch day du nhu idx:products cua nhom (name TEXT, sku TAG,
#       categoryId NUMERIC, price NUMERIC SORTABLE, status TAG, inStock TAG)     ~ C3/C4
# Moi cau hinh: THEM 10.000 document va SUA GIA 10.000 document, moi viec la mot script Lua (nhu mot vong lap PL/SQL).
# In them so byte AOF (nhat ky ghi dia cua Redis) sinh ra o lan do cuoi.
# Can chay 00_setup.sh truoc. Chay: docker compose exec -T redis sh /tmp/indexing-redis/e4_write.sh
set -eu
cd "$(dirname "$0")"
. ./lib/common.sh
ROWS=10000

aof_size() {
  $R INFO persistence | awk -F: '/^aof_current_size:/ { gsub(/\r/, "", $2); print $2 }'
}

# stats <nhan> <so_dong> <aof_byte> <cac gia tri micro giay...>
stats() {
  st_label=$1; st_rows=$2; st_aof=$3; shift 3
  echo "$@" | awk -v label="$st_label" -v k="$st_rows" -v aof="$st_aof" '{
    for (i = 1; i <= NF; i++) { ms[i] = $i / 1000; s += ms[i] }
    m = s / NF
    for (i = 1; i <= NF; i++) ss += (ms[i] - m) ^ 2
    printf "%-22s %d dong = %.2f +/- %.2f ms  =>  %.1f us/dong | AOF %.1f MB\n", label, k, m, sqrt(ss / (NF - 1)), m * 1000 / k, aof / 1048576
  }'
}

# measure_write <nhan cau hinh> <muc index tu lam>
measure_write() {
  label=$1; lvl=$2
  ins=""; upd=""
  r=0
  while [ $r -le 5 ]; do
    a0=$(aof_size); u0=$(eval_usec)
    $R --eval lua/e4_write.lua , THEM "$lvl" "$ROWS" >/dev/null
    u1=$(eval_usec); a1=$(aof_size)
    [ $r -gt 0 ] && ins="$ins $((u1 - u0))"
    aof_ins=$((a1 - a0))
    $R --eval lua/e4_write.lua , HUY_THEM "$lvl" "$ROWS" >/dev/null

    a0=$(aof_size); u0=$(eval_usec)
    $R --eval lua/e4_write.lua , SUA_GIA "$lvl" "$ROWS" >/dev/null
    u1=$(eval_usec); a1=$(aof_size)
    [ $r -gt 0 ] && upd="$upd $((u1 - u0))"
    aof_upd=$((a1 - a0))
    $R --eval lua/e4_write.lua , HUY_SUA_GIA "$lvl" "$ROWS" >/dev/null
    r=$((r + 1))
  done
  stats "$label / THEM" "$ROWS" "$aof_ins" $ins
  stats "$label / SUA GIA" "$ROWS" "$aof_upd" $upd
}

build_manual() {
  b=0
  while [ $b -lt 10 ]; do
    s=$((b * 20000 + 1)); e=$((b * 20000 + 20000))
    $R --eval lua/e1_build_manual.lua , "$s" "$e" >/dev/null
    [ "$1" -ge 2 ] && $R --eval lua/e3_build_manual.lua , "$s" "$e" >/dev/null
    b=$((b + 1))
  done
}

drop_lab_indexes() {
  for idx in $($R FT._LIST | grep '^idx:idxlab-' || true); do $R FT.DROPINDEX "$idx" >/dev/null; done
  $R --eval lua/lab_delete.lua , 'idxlab:sku:*' >/dev/null
  $R --eval lua/lab_delete.lua , 'idxlab:price:*' >/dev/null
  # Xoa document id 200.001-210.000 neu lan chay truoc bi dung giua chung (UNLINK key khong ton tai thi bo qua)
  $R --eval lua/e4_write.lua , HUY_THEM 2 "$ROWS" >/dev/null
}

echo "== Moi truong =="
$R INFO server | grep -E '^redis_version' | tr -d '\r'
$R CONFIG GET appendfsync | paste - - | sed 's/^/  /'
count=$($R --eval lua/lab_memory.lua , 'idxlab:product:*' | head -1)
echo "  So document idxlab:product:*: $count"
if [ "$count" != "200000" ]; then
  echo "Can dung 200.000 document. Chay 00_setup.sh truoc." >&2
  exit 1
fi
drop_lab_indexes

echo
echo "================= R0: KHONG INDEX PHU ================="
measure_write R0_KHONG_INDEX 0

echo
echo "================= R1: INDEX TU LAM idxlab:sku:* ================="
build_manual 1
measure_write R1_TU_LAM_SKU 1
drop_lab_indexes

echo
echo "================= R2: INDEX TU LAM sku + Sorted Set gia ================="
build_manual 2
measure_write R2_TU_LAM_3 2
drop_lab_indexes

echo
echo "================= R3: REDISEARCH sku TAG ================="
$R FT.CREATE idx:idxlab-e4a ON JSON PREFIX 1 idxlab:product: SCHEMA '$.sku' AS sku TAG >/dev/null
wait_indexed idx:idxlab-e4a
measure_write R3_REDISEARCH_SKU 0
drop_lab_indexes

echo
echo "================= R4: REDISEARCH DAY DU (nhu idx:products) ================="
$R FT.CREATE idx:idxlab-e4b ON JSON PREFIX 1 idxlab:product: SCHEMA \
  '$.name' AS name TEXT '$.sku' AS sku TAG '$.categoryId' AS categoryId NUMERIC \
  '$.price' AS price NUMERIC SORTABLE '$.status' AS status TAG '$.inStock' AS inStock TAG >/dev/null
wait_indexed idx:idxlab-e4b
measure_write R4_REDISEARCH_DAY_DU 0
drop_lab_indexes

echo
echo "Xong E4 (Redis). Da xoa index va du lieu them vao; con lai dung 200.000 document idxlab:product:*."
$R --eval lua/lab_memory.lua , 'idxlab:product:*' | head -1 | sed 's/^/  So document: /'
