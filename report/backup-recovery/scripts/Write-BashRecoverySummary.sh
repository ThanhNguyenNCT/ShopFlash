#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 3 ]; then
  echo "Usage: $0 <results-dir> <backup-id> <run-id>" >&2
  exit 64
fi

results_dir=$1
backup_id=$2
run_id=$3
work_dir="${results_dir}/.work"

for required in \
  "${work_dir}/before-loss.oracle.txt" \
  "${work_dir}/after-oracle-recovery.oracle.txt" \
  "${work_dir}/before-loss.redis.txt" \
  "${work_dir}/after-redis-live-recovery.redis.txt" \
  "${work_dir}/oracle-restore-duration.txt" \
  "${work_dir}/redis-restore-duration.txt"; do
  test -f "$required"
done

cmp -s "${work_dir}/before-loss.oracle.txt" "${work_dir}/after-oracle-recovery.oracle.txt"
cmp -s "${work_dir}/before-loss.redis.txt" "${work_dir}/after-redis-live-recovery.redis.txt"

oracle_seconds=$(sed -n 's/^ORACLE_TECHNICAL_RESTORE_SECONDS=//p' "${work_dir}/oracle-restore-duration.txt")
redis_seconds=$(sed -n 's/^REDIS_TECHNICAL_RESTORE_SECONDS=//p' "${work_dir}/redis-restore-duration.txt")
case "$oracle_seconds" in ''|*[!0-9]*) echo 'Invalid Oracle duration.' >&2; exit 1 ;; esac
case "$redis_seconds" in ''|*[!0-9]*) echo 'Invalid Redis duration.' >&2; exit 1 ;; esac

total_seconds=$((oracle_seconds + redis_seconds))

{
  printf '# Run %s - Recovery conclusion\n\n' "$run_id"
  printf -- '- Backup ID: `%s`\n' "$backup_id"
  printf -- '- Oracle Data Pump: **Recovery succeeded**\n'
  printf -- '- Redis RDB: **Recovery succeeded**\n'
  printf -- '- Oracle technical restore duration: **%s s**\n' "$oracle_seconds"
  printf -- '- Redis technical restore duration: **%s s**\n' "$redis_seconds"
  printf -- '- Combined technical restore duration: **%s s**\n' "$total_seconds"
  printf '\n'
  printf 'Durations cover the commands in their restore sections. They exclude manual delay and are not RTO. Raw checkpoints and the Data Pump output are retained in `.work/`.\n'
} > "${results_dir}/summary.md"

printf 'Summary was written to %s/summary.md\n' "$results_dir"
