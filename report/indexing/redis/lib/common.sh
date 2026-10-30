# Ham dung chung cho cac script Redis cua module indexing. Dung bang: . ./lib/common.sh (tu thu muc redis/)
R="redis-cli -e"

# In cac chi so dung luong cua mot index RediSearch (dong ten roi dong gia tri).
ftinfo() {
  $R FT.INFO "$1" | awk 'p ~ /^(num_docs|indexing|percent_indexed|inverted_sz_mb|tag_overhead_sz_mb|text_overhead_sz_mb|doc_table_size_mb|key_table_size_mb|sortable_values_size_mb|offset_vectors_sz_mb|total_index_memory_sz_mb)$/ { printf "  %-26s %s\n", p, $0 } { p = $0 }'
}

used_memory() {
  $R INFO memory | awk -F: '/^used_memory:/ { gsub(/\r/, "", $2); print $2 }'
}

now_ms() {
  echo $(( $(date +%s%N) / 1000000 ))
}

# Cho index RediSearch $1 index xong toan bo document co san.
wait_indexed() {
  while :; do
    state=$($R FT.INFO "$1" | awk 'p == "indexing" { print; exit } { p = $0 }')
    [ "$state" = "0" ] && break
    sleep 0.2
  done
}

# Tong so micro giay Redis da dung de chay lenh EVAL (INFO commandstats, dong cmdstat_eval).
# Khong do bang TIME trong Lua duoc: tu Redis 7, thoi gian bi "dong bang" suot mot lan chay script.
eval_usec() {
  $R INFO commandstats | awk -F'[:,=]' '/^cmdstat_eval:/ { print $5; found = 1 } END { if (!found) print 0 }'
}

# measure <nhan> <so_lan_tim_moi_luot> <script.lua> [tham so...]
# 1 luot khoi dong (bo qua) + 5 luot do. Thoi gian = hieu cmdstat_eval usec truoc/sau luot,
# tuc thoi gian Redis thuc su chay script (do ben trong DB, khong tinh mang va redis-cli).
measure() {
  label=$1; lookups=$2; script=$3; shift 3
  vals=""
  r=0
  while [ $r -le 5 ]; do
    u0=$(eval_usec)
    $R --eval "$script" , "$@" >/dev/null
    u1=$(eval_usec)
    us=$((u1 - u0))
    if [ $r -eq 0 ]; then
      echo "  Lan khoi dong: bo qua"
    else
      echo "  Lan $r: $(awk -v u="$us" 'BEGIN { printf "%.2f", u / 1000 }') ms"
      vals="$vals $us"
    fi
    r=$((r + 1))
  done
  echo "$vals" | awk -v label="$label" -v k="$lookups" '{
    for (i = 1; i <= NF; i++) { ms[i] = $i / 1000; s += ms[i] }
    m = s / NF
    for (i = 1; i <= NF; i++) ss += (ms[i] - m) ^ 2
    sd = sqrt(ss / (NF - 1))
    printf "%s: %d lan tim = %.2f +/- %.2f ms  =>  %.4f ms/lan\n", label, k, m, sd, m / k
  }'
}
