#!/usr/bin/env bash
# C2.sh <run_id> <p1> <p2> <p3>: các case độc lập, chỉ EVAL ghi Checkout.
set -euo pipefail
exec python3 report/transaction/research/redis/experiments/C2.py "$@"
