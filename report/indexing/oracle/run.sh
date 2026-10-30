#!/bin/bash
# Chay mot file SQL trong thu muc nay bang user SHOP, ben trong container Oracle.
# Dung tu PowerShell (khong can dau ngoac kep, tranh loi cua Windows PowerShell 5.1):
#   docker compose exec -T oracle bash /tmp/indexing/run.sh e1_sku.sql
set -euo pipefail
if [ $# -ne 1 ]; then
  echo "Cach dung: run.sh <ten_file.sql>" >&2
  exit 2
fi
cd "$(dirname "$0")"
printf 'connect shop/%s@//localhost:1521/FREEPDB1\n@%s\nexit\n' "$SHOP_PASSWORD" "$1" | sqlplus -s -L /nolog
