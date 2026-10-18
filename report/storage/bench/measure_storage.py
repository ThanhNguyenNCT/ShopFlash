"""Record a Storage Lab snapshot through the application API.

Prerequisites: the Docker stack and backend are running, and the Redis product projection has been rebuilt.

    python report/storage/bench/measure_storage.py
    python report/storage/bench/measure_storage.py --runs 5 --integrity-demo

Creates CSV, JSON, and Markdown files in report/storage/results/. The two footprint GET requests
are read-only. The optional --integrity-demo uses an endpoint with a temporary Redis key that cleans up after itself.
"""

import argparse
import csv
import json
import math
import platform
import statistics
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone, timedelta
from pathlib import Path


if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


ICT = timezone(timedelta(hours=7))
ROOT = Path(__file__).resolve().parent.parent
DEFAULT_RESULTS = ROOT / "results"


def api(base_url: str, method: str, path: str) -> dict:
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}{path}", method=method,
        headers={"Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {path} returned HTTP {error.code}: {detail}") from error
    except urllib.error.URLError as error:
        raise RuntimeError(f"Could not reach backend at {base_url}: {error.reason}") from error


def snapshot(base_url: str) -> dict:
    captured_at = datetime.now(ICT).isoformat(timespec="seconds")
    status = api(base_url, "GET", "/projection/status")
    footprint = api(base_url, "GET", "/storage-footprint")
    return {
        "capturedAt": captured_at,
        "oracleCategories": status["oracleCategories"],
        "oracleProducts": status["oracleProducts"],
        "redisDocuments": status["redisDocuments"],
        "inSync": status["inSync"],
        "oracleAllocatedBytes": footprint["oracleAllocatedBytes"],
        "redisDocumentKeyBytes": footprint["redisDocumentKeyBytes"],
        "footprintRedisDocuments": footprint["redisDocuments"],
        "oracleSegments": footprint["oracleSegments"],
    }


def mean_and_sd(values: list[int]) -> tuple[float, float]:
    return statistics.mean(values), statistics.stdev(values) if len(values) > 1 else 0.0


def format_bytes(value: float) -> str:
    return f"{value:,.0f} B"


def write_csv(samples: list[dict], path: Path) -> None:
    fields = [
        "run", "capturedAt", "oracleCategories", "oracleProducts", "redisDocuments", "inSync",
        "oracleAllocatedBytes", "redisDocumentKeyBytes", "footprintRedisDocuments",
    ]
    with path.open("w", newline="", encoding="utf-8") as output:
        writer = csv.DictWriter(output, fieldnames=fields)
        writer.writeheader()
        for run, sample in enumerate(samples, start=1):
            writer.writerow({field: sample[field] if field != "run" else run for field in fields})


def write_markdown(samples: list[dict], path: Path) -> None:
    latest = samples[-1]
    oracle_values = [sample["oracleAllocatedBytes"] for sample in samples]
    redis_values = [sample["redisDocumentKeyBytes"] for sample in samples]
    oracle_mean, oracle_sd = mean_and_sd(oracle_values)
    redis_mean, redis_sd = mean_and_sd(redis_values)

    lines = [
        "# Storage Lab snapshot",
        "",
        f"Measured at: {latest['capturedAt']}",
        f"Number of runs: {len(samples)}",
        "",
        "| Measurement scope | Latest snapshot value |",
        "|---|---:|",
        f"| Oracle Category | {latest['oracleCategories']:,} |",
        f"| Oracle Product | {latest['oracleProducts']:,} |",
        f"| Redis product document | {latest['redisDocuments']:,} |",
        f"| Projection in sync | {'Yes' if latest['inSync'] else 'No'} |",
        f"| Oracle allocated bytes (table + index catalog) | {format_bytes(latest['oracleAllocatedBytes'])} |",
        f"| Redis product document key bytes | {format_bytes(latest['redisDocumentKeyBytes'])} |",
        "",
    ]
    if len(samples) > 1:
        lines.extend([
            "| Measurement scope | Mean ± standard deviation |",
            "|---|---:|",
            f"| Oracle allocated bytes | {format_bytes(oracle_mean)} ± {format_bytes(oracle_sd)} |",
            f"| Redis product document key bytes | {format_bytes(redis_mean)} ± {format_bytes(redis_sd)} |",
            "",
        ])

    lines.extend([
        "## Oracle segments in the latest snapshot",
        "",
        "| Segment | Type | Bytes |",
        "|---|---|---:|",
    ])
    for segment in latest["oracleSegments"]:
        lines.append(f"| {segment['name']} | {segment['type']} | {segment['bytes']:,} |")
    lines.extend([
        "",
        "Note: Oracle is the source data (Category, Product, Inventory, and indexes); Redis measures only product projection keys. These values are not the total footprint of either DBMS.",
    ])
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description="Measure Storage Lab footprint through the application API.")
    parser.add_argument("--base-url", default="http://localhost:8080/api/admin/catalog")
    parser.add_argument("--runs", type=int, default=1, help="Number of consecutive snapshots; defaults to 1.")
    parser.add_argument("--interval-seconds", type=float, default=0.0, help="Delay between snapshots.")
    parser.add_argument("--integrity-demo", action="store_true", help="Run E1 and save its temporary Redis-key response.")
    parser.add_argument("--results-dir", type=Path, default=DEFAULT_RESULTS)
    args = parser.parse_args()
    if args.runs < 1:
        parser.error("--runs must be at least 1")
    if args.interval_seconds < 0:
        parser.error("--interval-seconds must not be negative")

    args.results_dir.mkdir(parents=True, exist_ok=True)
    samples = []
    for run in range(1, args.runs + 1):
        sample = snapshot(args.base_url)
        samples.append(sample)
        print(
            f"[{run}/{args.runs}] Oracle={sample['oracleAllocatedBytes']:,} B; "
            f"Redis={sample['redisDocumentKeyBytes']:,} B; "
            f"in-sync={'yes' if sample['inSync'] else 'no'}"
        )
        if run < args.runs and args.interval_seconds:
            time.sleep(args.interval_seconds)

    metadata = {
        "capturedAt": datetime.now(ICT).isoformat(timespec="seconds"),
        "baseUrl": args.base_url,
        "runs": args.runs,
        "platform": platform.platform(),
        "python": sys.version.split()[0],
        "samples": samples,
    }
    if args.integrity_demo:
        metadata["integrityDemo"] = api(args.base_url, "POST", "/constraint-demo")
        print("Saved the E1 response; the backend already cleaned up the temporary Redis key.")

    csv_path = args.results_dir / "storage-footprint.csv"
    json_path = args.results_dir / "storage-footprint.json"
    markdown_path = args.results_dir / "storage-footprint.md"
    write_csv(samples, csv_path)
    json_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_markdown(samples, markdown_path)
    print(f"Wrote: {csv_path}")
    print(f"Wrote: {json_path}")
    print(f"Wrote: {markdown_path}")


if __name__ == "__main__":
    main()
