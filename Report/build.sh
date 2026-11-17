#!/usr/bin/env sh
# Chạy từ gốc repo: bash Report/build.sh
set -e
export MSYS_NO_PATHCONV=1
REPORT_DIR="$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))"
IMAGE=shopflash-latex:1
docker image inspect "$IMAGE" >/dev/null 2>&1 || docker build -t "$IMAGE" "$REPORT_DIR/docker"
exec docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp \
  -v "$REPORT_DIR:/work" -w /work "$IMAGE" latexmk -outdir=build "$@" main.tex
