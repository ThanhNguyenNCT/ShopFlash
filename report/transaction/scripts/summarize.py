#!/usr/bin/env python3
"""Tổng hợp mẫu thật của Java runner và k6; chỉ dùng Python standard library."""

import argparse
import csv
import gzip
import json
import math
import statistics
from collections import defaultdict
from datetime import datetime, timedelta
from pathlib import Path


FIELDS = [
    "run_id", "scenario", "dbms_or_http", "api_or_flow", "cart_size", "concurrency",
    "round", "iteration", "started_at", "duration_ms", "outcome", "error_code",
    "fixture", "invariant_violations", "requests_in_flow",
]
OUTCOMES = ("success", "rejected", "expected_failure", "error", "invalid")


def iso(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def percentile(values, portion):
    if not values:
        return None
    ordered = sorted(values)
    index = (len(ordered) - 1) * portion
    lower = math.floor(index)
    upper = math.ceil(index)
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (index - lower)


def mean_sd(values):
    return (statistics.mean(values), statistics.stdev(values) if len(values) >= 2 else None) if values else (None, None)


def rounded(value):
    return None if value is None else round(value, 4)


def load_db(path):
    with path.open(newline="", encoding="utf-8") as stream:
        samples = list(csv.DictReader(stream))
    runs_path = path.with_name(path.stem + "-runs.jsonl")
    with runs_path.open(encoding="utf-8") as stream:
        runs = [json.loads(line) for line in stream if line.strip()]
    walls = {(r["scenario"], str(r["round"])): float(r["wall_seconds"]) for r in runs}
    for row in samples:
        row["requests_in_flow"] = ""
        row["round"] = str(row["round"])
    return samples, walls


def load_http(directory):
    samples = []
    walls = {}
    sequence = defaultdict(int)
    for path in sorted(directory.glob("cart*-c*-r*.json*")):
        if path.name.endswith("-audit.json"):
            continue
        file_samples = []
        flow_intervals = []
        opener = gzip.open if path.suffix == ".gz" else open
        with opener(path, "rt", encoding="utf-8") as stream:
            for line in stream:
                point = json.loads(line)
                if point.get("type") != "Point" or point.get("metric") not in (
                    "tx_api_duration_ms", "tx_flow_duration_ms"
                ):
                    continue
                data = point["data"]
                tags = data.get("tags", {})
                duration = float(data["value"])
                end = iso(data["time"])
                start = end - timedelta(milliseconds=duration)
                api = tags.get("api", "full_flow") if point["metric"] == "tx_api_duration_ms" else "full_flow"
                key = (tags.get("run_id", directory.name), tags.get("cart_size", ""),
                       tags.get("concurrency", ""), tags.get("round", ""), api)
                sequence[key] += 1
                scenario = f"http-cart{key[1]}-c{key[2]}"
                row = {
                    "run_id": key[0], "scenario": scenario, "dbms_or_http": "http",
                    "api_or_flow": api, "cart_size": key[1], "concurrency": key[2],
                    "round": key[3], "iteration": sequence[key], "started_at": start.isoformat(),
                    "duration_ms": duration, "outcome": tags.get("outcome", "invalid"),
                    "error_code": tags.get("error_code", ""), "fixture": tags.get("fixture", ""),
                    "invariant_violations": "", "requests_in_flow": tags.get("requests", "") if api == "full_flow" else "",
                }
                file_samples.append(row)
                if api == "full_flow":
                    flow_intervals.append((start, end))
        if flow_intervals:
            key = (file_samples[0]["scenario"], file_samples[0]["round"])
            walls[key] = (max(end for _, end in flow_intervals)
                          - min(start for start, _ in flow_intervals)).total_seconds()
        samples.extend(file_samples)
    return samples, walls


def summarize(samples, walls):
    groups = defaultdict(list)
    for row in samples:
        if str(row["round"]) == "0":
            continue  # warm-up
        group = tuple(row[field] for field in (
            "scenario", "dbms_or_http", "api_or_flow", "cart_size", "concurrency"
        ))
        groups[group].append(row)
    result = []
    for group, rows in sorted(groups.items()):
        by_round = defaultdict(list)
        for row in rows:
            by_round[str(row["round"])].append(row)
        success = [float(r["duration_ms"]) for r in rows if r["outcome"] == "success"
                   and math.isfinite(float(r["duration_ms"])) and float(r["duration_ms"]) > 0]
        counts = {outcome: sum(r["outcome"] == outcome for r in rows) for outcome in OUTCOMES}
        completed = sum(counts[name] for name in OUTCOMES if name != "invalid")
        round_stats = []
        total_wall = 0.0
        for round_number, round_rows in sorted(by_round.items(), key=lambda item: int(item[0])):
            wall = walls.get((group[0], round_number))
            if wall is None or wall <= 0:
                raise ValueError(f"Thiếu thời gian thực của lượt {group[0]}/{round_number}")
            total_wall += wall
            valid = [float(r["duration_ms"]) for r in round_rows if r["outcome"] == "success"
                     and math.isfinite(float(r["duration_ms"])) and float(r["duration_ms"]) > 0]
            finished = sum(r["outcome"] in OUTCOMES[:-1] for r in round_rows)
            round_stats.append({
                "round": int(round_number), "wall_seconds": wall,
                "success": len(valid), "p50_ms": percentile(valid, 0.50),
                "p95_ms": percentile(valid, 0.95), "throughput_success_per_second": len(valid) / wall,
                "error_rate_percent": 100 * sum(r["outcome"] == "error" for r in round_rows) / finished if finished else None,
                "rejection_rate_percent": 100 * sum(r["outcome"] == "rejected" for r in round_rows) / finished if finished else None,
            })
        means = {}
        for name in ("p50_ms", "p95_ms", "throughput_success_per_second", "error_rate_percent", "rejection_rate_percent"):
            values = [round_row[name] for round_row in round_stats if round_row[name] is not None]
            means[name + "_round_mean"], means[name + "_round_sd"] = map(rounded, mean_sd(values))
        result.append({
            "scenario": group[0], "dbms_or_http": group[1], "api_or_flow": group[2],
            "cart_size": int(group[3]), "concurrency": int(group[4]), "rounds": len(round_stats),
            "samples": len(rows), **counts, "p50_ms": rounded(percentile(success, 0.50)),
            "p95_ms": rounded(percentile(success, 0.95)),
            "throughput_success_per_second": rounded(len(success) / total_wall) if total_wall else None,
            "error_rate_percent": rounded(100 * counts["error"] / completed) if completed else None,
            "rejection_rate_percent": rounded(100 * counts["rejected"] / completed) if completed else None,
            "invariant_violations": sum(int(r["invariant_violations"] or 0) for r in rows),
            "requests_in_flows": sum(int(r["requests_in_flow"] or 0) for r in rows),
            "wall_seconds": rounded(total_wall), "round_stats": round_stats, **means,
        })
    return result


def save_samples(path, rows):
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    with path.with_suffix(".jsonl").open("w", encoding="utf-8") as stream:
        for row in rows:
            stream.write(json.dumps(row, ensure_ascii=False) + "\n")


def save_summary(path, rows):
    fields = [field for field in rows[0] if field != "round_stats"] if rows else []
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for row in rows:
            writer.writerow({key: row[key] for key in fields})
    path.with_suffix(".json").write_text(json.dumps(rows, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def markdown(db_rows, http_rows):
    lines = ["# Kết quả Transaction đo được", "", "Chỉ tính p50/p95 từ mẫu `success` hợp lệ; thông lượng = số `success` / tổng thời gian thực của các lượt.", ""]
    for title, rows in (("Cơ chế database: Oracle–Redis", db_rows), ("Ứng dụng HTTP: từng API và toàn luồng", http_rows)):
        lines.extend(["## " + title, "", "| Kịch bản | Hệ/API | Giỏ | Đồng thời | p50 ms | p95 ms | Thành công/s | Lỗi % | Từ chối % | Vi phạm |", "|---|---|---:|---:|---:|---:|---:|---:|---:|---:|"])
        for row in rows:
            def shown(key):
                value = row[key]
                return "—" if value is None else str(value)
            lines.append("| " + " | ".join((row["scenario"], row["dbms_or_http"] + "/" + row["api_or_flow"],
                shown("cart_size"), shown("concurrency"), shown("p50_ms"), shown("p95_ms"),
                shown("throughput_success_per_second"), shown("error_rate_percent"),
                shown("rejection_rate_percent"), shown("invariant_violations"))) + " |")
        lines.extend(["", "Độ lệch chuẩn giữa các lượt, số nhóm kết quả và thời gian từng lượt nằm trong JSON/CSV cùng tên.", ""])
    lines.append("Hai bảng có ranh giới đo khác nhau; không đối chiếu trực tiếp trị số database với HTTP.\n")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--db-csv", type=Path)
    parser.add_argument("--http-dir", type=Path)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    if not args.db_csv and not args.http_dir:
        parser.error("Cần --db-csv hoặc --http-dir")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    db_rows = []
    http_rows = []
    if args.db_csv:
        samples, walls = load_db(args.db_csv)
        db_rows = summarize(samples, walls)
        for row in db_rows:
            row["config_file"] = str(args.db_csv.with_name(args.db_csv.stem + "-config.json"))
        save_samples(args.output_dir / "database-samples.csv", samples)
        save_summary(args.output_dir / "database-summary.csv", db_rows)
    if args.http_dir:
        samples, walls = load_http(args.http_dir)
        if not samples:
            raise ValueError("Không tìm thấy metric tx_api_duration_ms/tx_flow_duration_ms của k6")
        http_rows = summarize(samples, walls)
        for row in http_rows:
            row["config_file"] = str(args.http_dir / "config.json")
        save_samples(args.output_dir / "http-samples.csv", samples)
        save_summary(args.output_dir / "http-summary.csv", http_rows)
    (args.output_dir / "provenance.json").write_text(json.dumps({
        "database_raw": str(args.db_csv) if args.db_csv else None,
        "database_runs": str(args.db_csv.with_name(args.db_csv.stem + "-runs.jsonl")) if args.db_csv else None,
        "database_config": str(args.db_csv.with_name(args.db_csv.stem + "-config.json")) if args.db_csv else None,
        "http_raw_dir": str(args.http_dir) if args.http_dir else None,
        "http_config": str(args.http_dir / "config.json") if args.http_dir else None,
        "percentile": "linear interpolation on all valid success samples",
        "throughput": "success count / sum of actual round wall seconds",
        "error_rate_denominator": "success+rejected+expected_failure+error",
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (args.output_dir / "comparison.md").write_text(markdown(db_rows, http_rows), encoding="utf-8")


if __name__ == "__main__":
    main()
