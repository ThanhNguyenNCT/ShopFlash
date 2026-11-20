"""Đo số liệu cho mục 3.3 (Query processing) qua API của module query.

Điều kiện: stack Docker đang chạy, backend ở :8080, đã nạp dữ liệu (backend/src/main/resources/db/history-seed) và đã
rebuild read model Redis. Chạy từ thư mục gốc repo:

    python report/query/bench/run_benchmarks.py [--runs 5] [--warmup 1]

Ghi vào report/query/results/:
    environment.md              môi trường đo (máy, Docker, image digest, số dòng)
    benchmark-<report>.csv      mẫu thô từng lần đo (report,source,variant,run,elapsedMs,roundTrips)
    benchmark-summary.md        bảng trung bình ± độ lệch chuẩn, p50, p95, round-trip
    plans/<report>-oracle-<hint>.txt, plans/<report>-redis-<mode>.txt   kế hoạch thực thi
    plans/stats-{before-delete,after-delete,after-gather}-top-products.txt  demo thống kê (D3)
    cache-summary.md, benchmark-cache.csv     cache-aside: Oracle, cache MISS (trống), cache HIT (đã có)

Chỉ đo cache (nhanh, khoảng 1 phút): python report/query/bench/run_benchmarks.py --only-cache

API /api/admin/** cần JWT của tài khoản ADMIN: script tự đăng nhập bằng admin@shopflash.local
(tài khoản mẫu của profile dev), đổi bằng biến môi trường SHOPFLASH_ADMIN_EMAIL / SHOPFLASH_ADMIN_PASSWORD.

Không đo khi đang chạy việc khác trên máy (trình duyệt, build) để số liệu ổn định.
"""
import argparse
import json
import os
import platform
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone, timedelta
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

BASE = "http://localhost:8080/api/admin/reports"
LOGIN_URL = "http://localhost:8080/api/auth/login"
ADMIN_EMAIL = os.environ.get("SHOPFLASH_ADMIN_EMAIL", "admin@shopflash.local")
ADMIN_PASSWORD = os.environ.get("SHOPFLASH_ADMIN_PASSWORD", "ShopFlash123!")
OUT = Path(__file__).resolve().parent.parent / "results"
PLANS = OUT / "plans"
REPORTS = ["revenue-by-month", "revenue-by-category", "top-products", "vip-customers", "unsold-products"]
JOIN_REPORTS = ["revenue-by-category", "top-products", "vip-customers"]
ICT = timezone(timedelta(hours=7))
# Khoảng ngày cố định, khớp bộ sinh dữ liệu 100k đơn (00_params.sql: start_date, end_date). Không để API tự lấy
# "12 tháng gần nhất": khi đó khoảng ngày trôi theo ngày chạy và hai lần đo không cùng dữ liệu.
RANGE = {"from": "2025-11-01", "to": "2026-10-18"}


_token = {"value": None, "at": 0.0}


def auth_header():
    """Token sống 30 phút (shopflash.auth.jwt-ttl) nên lấy lại sau 20 phút."""
    if _token["value"] is None or time.monotonic() - _token["at"] > 20 * 60:
        body = json.dumps({"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD}).encode()
        req = urllib.request.Request(LOGIN_URL, data=body, method="POST", headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                _token["value"] = json.loads(resp.read())["accessToken"]
        except urllib.error.HTTPError as e:
            sys.exit(f"Không đăng nhập được {ADMIN_EMAIL} (HTTP {e.code}). Backend cần profile dev, "
                     "hoặc đặt SHOPFLASH_ADMIN_EMAIL / SHOPFLASH_ADMIN_PASSWORD.")
        _token["at"] = time.monotonic()
    return {"Authorization": f"Bearer {_token['value']}"}


def call(method, path, params=None, body=None, accept="application/json"):
    url = BASE + path + ("?" + "&".join(f"{k}={v}" for k, v in params.items()) if params else "")
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Accept": accept, "Content-Type": "application/json", **auth_header()}
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
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
    # `config --images` không giữ thứ tự service, nên chọn image theo tên thay vì theo dòng.
    images = sh("docker", "compose", "config", "--images").splitlines()
    oracle_image = next((i for i in images if "database/free" in i), "")
    redis_image = next((i for i in images if "redis" in i), "")
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
        f"| Oracle | {oracle_ver.splitlines()[-1] if oracle_ver else ''}; image `{oracle_image}` |",
        f"| Redis | {redis_ver}; image `{redis_image}` |",
        f"| Dữ liệu | Oracle: {status['oracle']['countedOrders']:,} đơn được tính / {status['oracle']['orderItems']:,} dòng hàng "
        f"(CONFIRMED + COMPLETED, trên tổng 100.000 đơn); Redis: {status['redis']['orderDocs']:,} doc đơn, "
        f"{status['redis']['lineDocs']:,} doc dòng hàng |",
        "",
        "Oracle Free bị giới hạn 2 CPU, 2 GB RAM, 12 GB dữ liệu. Thời gian `elapsedMs` đo trong backend quanh lời gọi DB "
        "(gồm mạng tới container và xử lý kết quả, không gồm HTTP tới trình duyệt).",
        "",
        f"Khoảng ngày của mọi báo cáo: {RANGE['from']} đến {RANGE['to']} (cả hai đầu tính trọn ngày, +07:00).",
    ]
    (OUT / "environment.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))


def benchmarks(runs, warmup):
    rows = ["| Báo cáo | Nguồn | Biến thể | Trung bình ± độ lệch (ms) | p50 (ms) | p95 (ms) | Round-trip |", "|---|---|---|---|---|---|---|"]
    for report in REPORTS:
        body = {"runs": runs, "warmup": warmup, **RANGE}
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


def clear_cache():
    """Xóa mọi key report:cache:* trong Redis (mật khẩu lấy từ REDISCLI_AUTH trong container)."""
    subprocess.run(["docker", "compose", "exec", "-T", "redis", "sh", "-c",
                    "redis-cli --scan --pattern 'report:cache:*' | xargs -r redis-cli unlink > /dev/null"],
                   check=True, capture_output=True, timeout=60)


def stat(ms):
    ms = sorted(ms)
    n = len(ms)
    mean = sum(ms) / n
    sd = (sum((x - mean) ** 2 for x in ms) / (n - 1)) ** 0.5 if n > 1 else 0.0
    return mean, sd


def cache_benchmarks(runs, warmup):
    """Cache-aside (source=cache): MISS = cache trống, phải chạy Oracle rồi SET; HIT = chỉ GET từ Redis.
    MISS đo bằng cách xóa cache trước mỗi lần gọi; HIT và Oracle AUTO đo bằng endpoint benchmark (chạy lần lượt)."""
    rows = ["| Báo cáo | Oracle AUTO (ms) | Cache MISS (ms) | Cache HIT (ms) | Round-trip MISS / HIT | Oracle / HIT |",
            "|---|---|---|---|---|---|"]
    csv = ["report,variant,run,elapsedMs,roundTrips"]
    for report in REPORTS:
        print(f"cache {report} ...", flush=True)
        miss, miss_rt = [], None
        for i in range(warmup + runs):
            clear_cache()
            meta = call("GET", f"/{report}", {"source": "cache", **RANGE})["meta"]
            assert meta["variant"] == "MISS", meta
            if i >= warmup:
                miss.append(meta["elapsedMs"])
                miss_rt = meta["roundTrips"]
                csv.append(f"{report},MISS,{i - warmup + 1},{meta['elapsedMs']},{meta['roundTrips']}")
        res = call("POST", f"/{report}/benchmark", body={
            "runs": runs, "warmup": warmup, **RANGE,
            "variants": [{"source": "oracle", "variant": "AUTO"}, {"source": "cache", "variant": "HIT"}]})["results"]
        by = {r["source"]: r for r in res}
        for r in res:
            for i, ms in enumerate(r["samplesMs"], 1):
                csv.append(f"{report},{'ORACLE_AUTO' if r['source'] == 'oracle' else 'HIT'},{i},{ms},{r['roundTrips']}")
        o, h = by["oracle"], by["cache"]
        m_mean, m_sd = stat(miss)
        rows.append(f"| {report} | {o['meanMs']:,.1f} ± {o['stdDevMs']:,.1f} | {m_mean:,.1f} ± {m_sd:,.1f} | "
                    f"{h['meanMs']:,.2f} ± {h['stdDevMs']:,.2f} | {miss_rt} / {h['roundTrips']} | "
                    f"{o['meanMs'] / h['meanMs']:,.0f}× |")
    clear_cache()
    header = [f"# Cache-aside: Oracle tính, Redis giữ kết quả (khởi động {warmup} lần không tính, đo {runs} lần)", "",
              "MISS: cache trống, backend GET (không có) → chạy Oracle → SET kèm TTL. HIT: chỉ GET một key rồi đọc JSON.",
              "Thời gian đo trong backend như các bảng khác. Số liệu thô: `benchmark-cache.csv`.",
              "Cái giá: trong thời gian TTL, kết quả không thấy đơn mới (kiểm ở `tests/contract_test.py`, phần cache).", ""]
    (OUT / "cache-summary.md").write_text("\n".join(header + rows) + "\n", encoding="utf-8")
    (OUT / "benchmark-cache.csv").write_text("\n".join(csv) + "\n", encoding="utf-8")
    print("\n".join(rows))


def save_plan(name, params, report="top-products"):
    plan = call("GET", f"/{report}/plan", {**params, **RANGE})
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
    ap.add_argument("--only-cache", action="store_true", help="chỉ đo cache-aside")
    ap.add_argument("--from", dest="date_from", default=RANGE["from"], help="ngày đầu YYYY-MM-DD (mặc định %(default)s)")
    ap.add_argument("--to", dest="date_to", default=RANGE["to"], help="ngày cuối YYYY-MM-DD (mặc định %(default)s)")
    args = ap.parse_args()
    RANGE.update({"from": args.date_from, "to": args.date_to})
    PLANS.mkdir(parents=True, exist_ok=True)
    if not args.only_cache:
        environment()
        plans()
        stats_demo()
        benchmarks(args.runs, args.warmup)
    cache_benchmarks(args.runs, args.warmup)
