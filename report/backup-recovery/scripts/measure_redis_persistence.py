#!/usr/bin/env python3
"""Measure Redis SET/GET performance for three AOF persistence modes.

The benchmark never uses the ShopFlash Redis container or its Docker volume.  Each
sample gets a disposable Redis container and named volume, which are removed in a
finally block after the sample completes.  Only CSV and Markdown summaries remain
under report/backup-recovery/results/.
"""

from __future__ import annotations

import argparse
import csv
import re
import statistics
import subprocess
import sys
import time
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Iterable


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
RESULTS_ROOT = REPOSITORY_ROOT / "report" / "backup-recovery" / "results"
MODES = {
    "disabled": {"label": "AOF disabled (`appendonly no`)", "appendonly": "no", "appendfsync": None},
    "everysec": {"label": "AOF `everysec`", "appendonly": "yes", "appendfsync": "everysec"},
    "always": {"label": "AOF `always`", "appendonly": "yes", "appendfsync": "always"},
}
THROUGHPUT_PATTERN = re.compile(r"throughput summary:\s*([0-9.]+)\s+requests per second", re.IGNORECASE)
LATENCY_SUMMARY_PATTERN = re.compile(
    r"latency summary \(msec\):\s*\r?\n"
    r"\s*avg\s+min\s+p50\s+p95\s+p99\s+max\s*\r?\n"
    r"\s*([0-9.]+)\s+([0-9.]+)\s+([0-9.]+)\s+([0-9.]+)\s+([0-9.]+)\s+([0-9.]+)",
    re.IGNORECASE,
)


@dataclass(frozen=True)
class BenchmarkMetrics:
    throughput: float
    p50: float
    p95: float


@dataclass(frozen=True)
class BenchmarkRow:
    mode: str
    run: int
    set_metrics: BenchmarkMetrics
    get_metrics: BenchmarkMetrics


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Benchmark Redis AOF persistence modes in isolated Docker containers.")
    parser.add_argument("--runs", type=positive_integer, default=5, help="Repetitions per persistence mode (default: 5).")
    parser.add_argument("--requests", type=positive_integer, default=100_000, help="SET and GET requests per sample (default: 100000).")
    parser.add_argument("--clients", type=positive_integer, default=50, help="Concurrent clients (default: 50).")
    parser.add_argument("--pipeline", type=positive_integer, default=1, help="Pipeline depth (default: 1).")
    parser.add_argument("--data-size", type=positive_integer, default=256, help="Value size in bytes (default: 256).")
    parser.add_argument("--keyspace", type=positive_integer, default=10_000, help="Random keyspace size (default: 10000).")
    parser.add_argument("--modes", nargs="+", choices=tuple(MODES), default=tuple(MODES), help="Modes to measure (default: all).")
    parser.add_argument("--output-dir", type=Path, help="Optional directory for CSV and Markdown output.")
    return parser.parse_args()


def positive_integer(value: str) -> int:
    parsed = int(value)
    if parsed < 1:
        raise argparse.ArgumentTypeError("must be greater than zero")
    return parsed


def run_command(arguments: list[str], *, check: bool = True) -> subprocess.CompletedProcess[str]:
    try:
        result = subprocess.run(
            arguments,
            cwd=REPOSITORY_ROOT,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            check=False,
        )
    except FileNotFoundError as error:
        raise RuntimeError("Docker was not found on PATH. Start Docker Desktop or install Docker Engine first.") from error
    if check and result.returncode != 0:
        detail = (result.stderr or result.stdout).strip()
        raise RuntimeError(f"Command failed ({' '.join(arguments)}):\n{detail[-2000:]}")
    return result


def resolve_redis_image() -> str:
    images = run_command(["docker", "compose", "config", "--images"]).stdout.splitlines()
    redis_images = [image.strip() for image in images if "redis" in image.lower()]
    if not redis_images:
        raise RuntimeError("Could not resolve the Redis image from docker compose config --images.")
    return redis_images[0]


def wait_for_redis(container_name: str, timeout_seconds: int = 30) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        ping = run_command(["docker", "exec", container_name, "redis-cli", "--raw", "PING"], check=False)
        if ping.returncode == 0 and ping.stdout.strip() == "PONG":
            return
        time.sleep(0.5)
    logs = run_command(["docker", "logs", container_name], check=False)
    raise RuntimeError(f"Temporary Redis did not become ready within {timeout_seconds} seconds.\n{logs.stdout[-2000:]}")


def parse_metrics(operation: str, output: str) -> BenchmarkMetrics:
    throughput_match = THROUGHPUT_PATTERN.search(output)
    latency_summary_match = LATENCY_SUMMARY_PATTERN.search(output)
    if throughput_match is None or latency_summary_match is None:
        raise RuntimeError(
            f"Could not parse {operation} throughput, p50, and p95 from redis-benchmark output:\n{output[-4000:]}"
        )
    return BenchmarkMetrics(
        float(throughput_match.group(1)),
        float(latency_summary_match.group(3)),
        float(latency_summary_match.group(4)),
    )


def benchmark_operation(container_name: str, operation: str, args: argparse.Namespace) -> BenchmarkMetrics:
    result = run_command([
        "docker", "exec", container_name, "redis-benchmark",
        "-n", str(args.requests),
        "-c", str(args.clients),
        "-P", str(args.pipeline),
        "-d", str(args.data_size),
        "-r", str(args.keyspace),
        "-t", operation,
    ])
    return parse_metrics(operation.upper(), result.stdout)


def run_sample(image: str, mode_name: str, run_number: int, args: argparse.Namespace, identifier: str) -> BenchmarkRow:
    mode = MODES[mode_name]
    suffix = f"{identifier}-{mode_name}-{run_number}"
    container_name = f"shopflash-aof-bench-{suffix}"
    volume_name = f"shopflash_aof_bench_{suffix}"
    volume_created = False
    container_started = False
    try:
        run_command(["docker", "volume", "create", volume_name])
        volume_created = True
        command = [
            "docker", "run", "--detach", "--rm", "--name", container_name,
            "--volume", f"{volume_name}:/data",
            "--entrypoint", "redis-server", image,
            "--dir", "/data",
            "--save", "",
            "--appendonly", mode["appendonly"],
        ]
        if mode["appendfsync"] is not None:
            command.extend(["--appendfsync", mode["appendfsync"]])
        run_command(command)
        container_started = True
        wait_for_redis(container_name)
        set_metrics = benchmark_operation(container_name, "set", args)
        get_metrics = benchmark_operation(container_name, "get", args)
        return BenchmarkRow(mode_name, run_number, set_metrics, get_metrics)
    finally:
        if container_started:
            run_command(["docker", "rm", "--force", container_name], check=False)
        if volume_created:
            run_command(["docker", "volume", "rm", volume_name], check=False)


def write_csv(rows: Iterable[BenchmarkRow], output_path: Path) -> None:
    with output_path.open("w", newline="", encoding="utf-8") as file:
        writer = csv.writer(file)
        writer.writerow([
            "mode", "run", "setThroughputOpsPerSecond", "setP50Milliseconds", "setP95Milliseconds",
            "getThroughputOpsPerSecond", "getP50Milliseconds", "getP95Milliseconds",
        ])
        for row in rows:
            writer.writerow([
                row.mode, row.run,
                f"{row.set_metrics.throughput:.2f}", f"{row.set_metrics.p50:.3f}", f"{row.set_metrics.p95:.3f}",
                f"{row.get_metrics.throughput:.2f}", f"{row.get_metrics.p50:.3f}", f"{row.get_metrics.p95:.3f}",
            ])


def metric_mean(rows: list[BenchmarkRow], extractor) -> float:
    return statistics.fmean(extractor(row) for row in rows)


def write_summary(rows: list[BenchmarkRow], output_path: Path, image: str, args: argparse.Namespace) -> None:
    grouped = {mode: [row for row in rows if row.mode == mode] for mode in args.modes}
    lines = [
        "# Redis persistence benchmark",
        "",
        f"- Redis image: `{image}`",
        f"- Repetitions per mode: {args.runs}",
        f"- SET and GET requests per repetition: {args.requests:,}",
        f"- Concurrent clients: {args.clients}",
        f"- Pipeline depth: {args.pipeline}",
        f"- Value size: {args.data_size} B",
        f"- Random keyspace: {args.keyspace:,}",
        "- Each sample used a fresh disposable Docker volume and Redis container; the ShopFlash Redis service and its volume were not used.",
        "",
        "| Mode | Mean SET throughput (ops/s) | Mean SET p50 (ms) | Mean SET p95 (ms) | Mean GET throughput (ops/s) | Mean GET p50 (ms) | Mean GET p95 (ms) |",
        "|---|---:|---:|---:|---:|---:|---:|",
    ]
    for mode_name, mode_rows in grouped.items():
        lines.append(
            "| {label} | {set_throughput:.2f} | {set_p50:.3f} | {set_p95:.3f} | {get_throughput:.2f} | {get_p50:.3f} | {get_p95:.3f} |".format(
                label=MODES[mode_name]["label"],
                set_throughput=metric_mean(mode_rows, lambda row: row.set_metrics.throughput),
                set_p50=metric_mean(mode_rows, lambda row: row.set_metrics.p50),
                set_p95=metric_mean(mode_rows, lambda row: row.set_metrics.p95),
                get_throughput=metric_mean(mode_rows, lambda row: row.get_metrics.throughput),
                get_p50=metric_mean(mode_rows, lambda row: row.get_metrics.p50),
                get_p95=metric_mean(mode_rows, lambda row: row.get_metrics.p95),
            )
        )
    output_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    args = parse_arguments()
    run_command(["docker", "version", "--format", "{{.Server.Version}}"])
    image = resolve_redis_image()
    identifier = datetime.now().astimezone().strftime("%Y%m%dT%H%M%S")
    output_dir = args.output_dir or RESULTS_ROOT / f"redis-persistence-{identifier}"
    output_dir = output_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=False)

    print(f"Redis image: {image}")
    print(f"Results: {output_dir}")
    rows: list[BenchmarkRow] = []
    for mode_name in args.modes:
        print(f"\nMode: {MODES[mode_name]['label']}")
        for run_number in range(1, args.runs + 1):
            print(f"  Run {run_number}/{args.runs} ...", flush=True)
            row = run_sample(image, mode_name, run_number, args, identifier)
            rows.append(row)
            print(
                f"    SET {row.set_metrics.throughput:.2f} ops/s (p50 {row.set_metrics.p50:.3f} ms, p95 {row.set_metrics.p95:.3f} ms); "
                f"GET {row.get_metrics.throughput:.2f} ops/s (p50 {row.get_metrics.p50:.3f} ms, p95 {row.get_metrics.p95:.3f} ms)"
            )

    write_csv(rows, output_dir / "redis-persistence.csv")
    write_summary(rows, output_dir / "summary.md", image, args)
    print("\nCompleted. Use redis-persistence.csv for per-run data and summary.md for the report table.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("\nBenchmark cancelled. The active temporary container and volume are cleaned up before exit.", file=sys.stderr)
        raise SystemExit(130)
    except RuntimeError as error:
        print(f"Benchmark failed: {error}", file=sys.stderr)
        raise SystemExit(1)
