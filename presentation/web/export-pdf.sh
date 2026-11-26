#!/usr/bin/env bash
# Xuất cả deck ra PDF: chụp từng cảnh ở 1920x1080 (chế độ ?export) rồi ghép lại.
# Dùng: ./export-pdf.sh [file-ra.pdf]
set -euo pipefail
cd "$(dirname "$0")"
OUT="${1:-../Oracle-vs-Redis-Nhom11.pdf}"  # mặc định ghi ra presentation/, đã bỏ qua trong .gitignore
TMP="$(mktemp -d)"
N=$(grep -c "Deck.add\|Deck.opener" parts/*.js | awk -F: '{s+=$2} END{print s}')
for i in $(seq 0 $((N-1))); do
  google-chrome --headless=new --disable-gpu --hide-scrollbars --force-prefers-reduced-motion \
    --window-size=1920,1080 --virtual-time-budget=6000 \
    --screenshot="$TMP/$(printf %03d "$i").png" "file://$PWD/index.html?export#$i" 2>/dev/null
done
convert "$TMP"/*.png -quality 92 -compress jpeg -density 144 -units PixelsPerInch "$OUT"
rm -r "$TMP"
echo "Đã xuất $N trang: $OUT"
