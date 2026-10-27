#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)
cd "$repo_root"

: "${TX_BENCH_PASSWORD:?Đặt TX_BENCH_PASSWORD cho tài khoản fixture trước khi chạy}"
k6_image=grafana/k6@sha256:3cd0ae83b47a29b273193eb998740c3b0e219fc34cdf579b90a8fc1698f5c025
docker image inspect "$k6_image" >/dev/null
docker run --rm "$k6_image" version
curl --fail --silent --show-error http://127.0.0.1:5173/api/health >/dev/null

run_id=${TX_HTTP_RUN_ID:-http-$(date -u +%Y%m%dT%H%M%SZ)}
output_dir="$repo_root/report/transaction/results/$run_id"
mkdir -p "$output_dir"
manifest="$repo_root/backend/target/transaction-http-fixtures.json"
if [[ ${TX_HTTP_SMOKE:-0} == 1 ]]; then
  sizes=(1)
  concurrencies=(1)
  warmup=1
  rounds=1
  iterations=2
  override_reason=smoke
else
  sizes=(1 5)
  concurrencies=(1 10)
  warmup=50
  rounds=5
  iterations=200
  override_reason=''
fi
suffix=$(printf '%04x' "$RANDOM")
fixture_ids=""
for index in $(seq -w 1 10); do
  fixture_ids+="${fixture_ids:+,}h$(date -u +%s)$suffix$index"
done

fixture_action() {
  (cd "$repo_root/backend" && ./mvnw -q test -Dtest=TransactionHttpFixtureCommandIT \
    "-Dtx.fixture.action=$1" "-Dtx.fixture.ids=$fixture_ids" \
    "-Dtx.fixture.manifest=$manifest")
}

cleanup() { fixture_action cleanup; }
trap cleanup EXIT
fixture_action setup

cat > "$output_dir/config.json" <<EOF
{"run_id":"$run_id","k6_image":"$k6_image","base_url":"http://127.0.0.1:5173","cart_sizes":[$(IFS=,; echo "${sizes[*]}")],"concurrency":[$(IFS=,; echo "${concurrencies[*]}")],"warmup":$warmup,"rounds":$rounds,"iterations_per_round":$iterations,"fixtures_per_concurrency":10,"fixture_manifest":"backend/target/transaction-http-fixtures.json","login_timing":"excluded from business metrics","reset":"fixture_action reset before warm-up and each round","override_reason":"$override_reason"}
EOF

run_k6() {
  local size=$1 concurrency=$2 round=$3 iterations=$4
  local raw="/work/report/transaction/results/$run_id/cart${size}-c${concurrency}-r${round}.json"
  docker run --rm --network host --user "$(id -u):$(id -g)" \
    --volume "$repo_root/report/transaction:/work/report/transaction" \
    --volume "$manifest:/work/backend/target/transaction-http-fixtures.json:ro" \
    --workdir /work \
    --env TX_BENCH_PASSWORD \
    --env TX_FIXTURE_MANIFEST=/work/backend/target/transaction-http-fixtures.json \
    --env TX_BASE_URL=http://127.0.0.1:5173 \
    --env "TX_CART_SIZE=$size" --env "TX_CONCURRENCY=$concurrency" \
    --env "TX_ITERATIONS=$iterations" --env "TX_ROUND=$round" \
    --env "TX_RUN_ID=$run_id" "$k6_image" run --out "json=$raw" \
    /work/report/transaction/k6/cart-checkout-order.js
  (cd "$repo_root/backend" && ./mvnw -q test -Dtest=TransactionHttpAuditIT \
    "-Dtx.fixture.ids=$fixture_ids" "-Dtx.audit.expected.orders=$iterations" \
    "-Dtx.audit.cart.size=$size" \
    "-Dtx.audit.output=$output_dir/cart${size}-c${concurrency}-r${round}-audit.json")
  gzip -n "$output_dir/cart${size}-c${concurrency}-r${round}.json"
}

for size in "${sizes[@]}"; do
  for concurrency in "${concurrencies[@]}"; do
    fixture_action reset
    run_k6 "$size" "$concurrency" 0 "$warmup"
    for round in $(seq 1 "$rounds"); do
      fixture_action reset
      run_k6 "$size" "$concurrency" "$round" "$iterations"
    done
  done
done

printf 'Kết quả HTTP: %s\n' "$output_dir"
