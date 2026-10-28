#!/usr/bin/env bash
# Dọn theo danh sách key manifest rồi dựng lại trạng thái đầu kỳ.
set -euo pipefail
run_id=${1:?Cần run ID}
key_file=${2:?Cần key manifest}
p1=${3:?Cần Product 1 ID}
p2=${4:?Cần Product 2 ID}
p3=${5:?Cần Product 3 ID}
bash report/transaction/research/redis/cleanup.sh "$run_id" "$key_file"
bash report/transaction/research/redis/setup.sh "$run_id" "$p1" "$p2" "$p3"
