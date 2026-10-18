"""Đo số liệu cho mục 3.3 (Query processing) qua API của module query.

Điều kiện: stack Docker đang chạy, backend ở :8080, đã nạp dữ liệu (report/query/oracle/seed) và đã
rebuild read model Redis. Chạy từ thư mục gốc repo:

    python report/query/bench/run_benchmarks.py [--runs 5] [--warmup 1]

Ghi vào report/query/results/:
    environment.md              môi trường đo (máy, Docker, image digest, số dòng)
    benchmark-<report>.csv      mẫu thô từng lần đo (report,source,variant,run,elapsedMs,roundTrips)
    benchmark-summary.md        bảng trung bình ± độ lệch chuẩn, p50, p95, round-trip
    plans/<report>-oracle-<hint>.txt, plans/<report>-redis-<mode>.txt   kế hoạch thực thi
    plans/stats-{before-delete,after-delete,after-gather}-top-products.txt  demo thống kê (D3)

Không đo khi đang chạy việc khác trên máy (trình duyệt, build) để số liệu ổn định.
"""
import argparse
import json
import os
import platform
import subprocess
import sys
import urllib.request
from datetime import datetime, timezone, timedelta
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

BASE = "http://localhost:8080/api/admin/reports"
OUT = Path(__file__).resolve().parent.parent / "results"
PLANS = OUT / "plans"
REPORTS = ["revenue-by-month", "revenue-by-category", "top-products", "vip-customers", "unsold-products"]
JOIN_REPORTS = ["revenue-by-category", "top-products", "vip-customers"]
ICT = timezone(timedelta(hours=7))


def call(method, path, params=None, body=None, accept="application/json"):
    url = BASE + path + ("?" + "&".join(f"{k}={v}" for k, v in params.items()) if params else "")
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={"Accept": accept, "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=1800) as resp:
        raw = resp.read().decode()
        return raw if accept == "text/csv" else json.loads(raw)


def sh(*cmd):
    try:
        return subprocess.run(cmd, capture_output=True, text=True, timeout=60).stdout.strip()
    except Exception as e:  # noqa: BLE001 - chỉ để ghi môi trường, không dừng phép đo
        return f"(không lấy được: {e})"


def environment():
    cpu = sh("powershell", "-NoProfile", "-Command", "(Get-CimInstance Win32_Processor).Name") if os.name == "nt" else platform.processor()
    ram = sh("powershell", "-NoProfile", "-Command", "[math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory/1GB,1)") if os.name == "nt" else ""
    docker = sh("docker", "version", "--format", "{{.Server.Version}}")
    docker_res = sh("docker", "info", "--format", "{{.NCPU}} CPU, {{.MemTotal}} bytes")
    images = sh("docker", "compose", "config", "--images")
    oracle_ver = sh("docker", "compose", "exec", "-T", "oracle", "bash", "-c", "sqlplus -v")
    redis_ver = sh("docker", "compose", "exec", "-T", "redis", "redis-cli", "INFO", "server")
    redis_ver = next((l for l in redis_ver.splitlines() if l.startswith("redis_version")), redis_ver)
    status = call("GET", "/analytics/status")
    lines = [
        "# Môi trường đo (3.3 Query processing)",
        "",
        f"Đo lúc: {datetime.now(ICT):%Y-%m-%d %H:%M} (+07:00)",
        "",
        "| Thành phần | Giá trị |",
        "|---|---|",
        f"| Máy | {cpu}, {ram} GB RAM |",
        f"| Hệ điều hành, Docker | {platform.platform()}; Docker {docker} ({docker_res}) |",
        f"| Oracle | {oracle_ver.splitlines()[-1] if oracle_ver else ''}; image `{images.splitlines()[0] if images else ''}` |",
        f"| Redis | {redis_ver}; image `{images.splitlines()[1] if len(images.splitlines()) > 1 else ''}` |",
        f"| Dữ liệu | Oracle: {status['oracle']['countedOrders']:,} đơn được tính / {status['oracle']['orderItems']:,} dòng hàng "
        f"(CONFIRMED + COMPLETED, trên tổng 100.000 đơn); Redis: {status['redis']['orderDocs']:,} doc đơn, "
        f"{status['redis']['lineDocs']:,} doc dòng hàng |",
        "",
        "Oracle Free bị giới hạn 2 CPU, 2 GB RAM, 12 GB dữ liệu. Thời gian `elapsedMs` đo trong backend quanh lời gọi DB "
        "(gồm mạng tới container và xử lý kết quả, không gồm HTTP tới trình duyệt).",
    ]
    (OUT / "environment.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))


def benchmarks(runs, warmup):
    rows = ["| Báo cáo | Nguồn | Biến thể | Trung bình ± độ lệch (ms) | p50 (ms) | p95 (ms) | Round-trip |", "|---|---|---|---|---|---|---|"]
    for report in REPORTS:
        body = {"runs": runs, "warmup": warmup}
        print(f"benchmark {report} ...", flush=True)
        csv = call("POST", f"/{report}/benchmark", body=body, accept="text/csv")
        (OUT / f"benchmark-{report}.csv").write_text(csv.replace("\r\n", "\n"), encoding="utf-8")
        # Thống kê tính lại từ đúng các mẫu trong CSV để bảng và file thô khớp nhau.
        samples = {}
        for line in csv.strip().splitlines()[1:]:
            _, source, variant, _, ms, rt = line.split(",")
            samples.setdefault((source, variant), {"ms": [], "rt": rt})["ms"].append(float(ms))
        for (source, variant), d in samples.items():
            ms = sorted(d["ms"])
            n = len(ms)
            mean = sum(ms) / n
            sd = (sum((x - mean) ** 2 for x in ms) / (n - 1)) ** 0.5 if n > 1 else 0.0
            pct = lambda p: ms[max(1, -(-p * n // 100)) - 1]  # nearest-rank
            rows.append(f"| {report} | {source} | {variant} | {mean:,.1f} ± {sd:,.1f} | {pct(50):,.1f} | {pct(95):,.1f} | {d['rt']} |")
    header = [f"# Kết quả đo (khởi động {warmup} lần không tính, đo {runs} lần)", "",
              "Số liệu thô: `benchmark-<report>.csv`. Môi trường: `environment.md`.", ""]
    (OUT / "benchmark-summary.md").write_text("\n".join(header + rows) + "\n", encoding="utf-8")
    print("\n".join(rows))


def save_plan(name, params, report="top-products"):
    plan = call("GET", f"/{report}/plan", params)
    text = f"# {report} {params}\n# statement:\n{plan['statement']}\n\n" + "\n".join(plan["lines"]) + "\n"
    (PLANS / name).write_text(text, encoding="utf-8")
    return plan


def plans():
    for report in JOIN_REPORTS:
        for hint in ["AUTO", "NL", "HASH", "MERGE"]:
            save_plan(f"{report}-oracle-{hint}.txt", {"source": "oracle", "joinHint": hint}, report)
        for mode in ["AGGREGATE", "CLIENT"]:
            save_plan(f"{report}-redis-{mode}.txt", {"source": "redis", "redisMode": mode}, report)
        print(f"plans {report} ok", flush=True)
    for report in ["revenue-by-month", "unsold-products"]:
        save_plan(f"{report}-oracle-AUTO.txt", {"source": "oracle"}, report)


def stats_demo():
    """D3: kế hoạch top-products (AUTO) khi có thống kê, khi xóa thống kê, và sau khi thu thập lại."""
    save_plan("stats-before-delete-top-products.txt", {"source": "oracle"})
    call("POST", "/oracle/stats", body={"action": "DELETE"})
    try:
        save_plan("stats-after-delete-top-products.txt", {"source": "oracle"})
    finally:
        call("POST", "/oracle/stats", body={"action": "GATHER"})
    save_plan("stats-after-gather-top-products.txt", {"source": "oracle"})
    print("stats demo ok (đã GATHER lại)")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--runs", type=int, default=5)
    ap.add_argument("--warmup", type=int, default=1)
    args = ap.parse_args()
    PLANS.mkdir(parents=True, exist_ok=True)
    environment()
    plans()
    stats_demo()
    benchmarks(args.runs, args.warmup)
