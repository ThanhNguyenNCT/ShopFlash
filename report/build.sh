#!/usr/bin/env sh
# Biên dịch báo cáo bằng Docker (macOS, Linux, Git Bash). PDF nằm ở report/build/main.pdf.
#   report/build.sh        biên dịch
#   report/build.sh -c     xóa file trung gian
# Lần đầu dựng image shopflash-latex:1 (tải khoảng 625 MB, chiếm khoảng 2,3 GB trên đĩa). Xóa image: docker rmi shopflash-latex:1
set -e
export MSYS_NO_PATHCONV=1   # Git Bash: không đổi /work thành đường dẫn Windows
REPORT="$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))"
IMAGE=shopflash-latex:1

docker image inspect "$IMAGE" >/dev/null 2>&1 || docker build -t "$IMAGE" "$REPORT/docker"

exec docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp \
  -v "$REPORT:/work" -w /work "$IMAGE" latexmk -outdir=build "$@" main.tex
