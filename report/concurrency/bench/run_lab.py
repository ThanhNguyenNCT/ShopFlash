"""Chạy thí nghiệm E1, E2 của mục 3.5 qua API Concurrency Lab, ghi CSV và vẽ biểu đồ.

    python report/concurrency/bench/run_lab.py e1
    python report/concurrency/bench/run_lab.py e2
    python report/concurrency/bench/run_lab.py plot      # vẽ lại từ CSV đã có, không đo

Cần backend chạy profile dev (có tài khoản admin của seed). Mật khẩu lấy từ biến môi trường
LAB_ADMIN_PASSWORD, không có thì dùng mật khẩu của seed dev. Kết quả ghi vào ../results và ../images.

E1: 10 chiến lược, N = 1000, M = 100, C = 64.
E2: 5 chiến lược đúng, N = M = 2000, C lần lượt 1, 8, 32, 64, 128.
Mỗi cấu hình: --warmup lần khởi động không tính (mặc định 1), --runs lần đo (mặc định 5).
"""

import argparse
import csv
import json
import os
import sys
from pathlib import Path

import requests

HERE = Path(__file__).resolve().parent
RESULTS = HERE.parent / "results"
IMAGES = HERE.parent / "images"

E1 = {"buyers": 1000, "quota": 100, "concurrency": 64}
E2 = {"buyers": 2000, "quota": 2000}
E2_CONCURRENCY = [1, 8, 32, 64, 128]
E2_STRATEGIES = ["ORACLE_PESSIMISTIC", "ORACLE_OPTIMISTIC", "ORACLE_ATOMIC", "REDIS_WATCH", "REDIS_LUA"]

RUN_FIELDS = ["experiment", "strategy", "store", "buyers", "quota", "concurrency", "oraclePoolSize",
              "redisPoolSize", "run", "successes", "soldOut", "errors", "errorCodes", "retries", "finalSold",
              "oversold", "lostUpdates", "elapsedMs", "throughput", "p50Ms", "p95Ms", "p99Ms"]
SUMMARY_METRICS = ["successes", "oversold", "finalSold", "lostUpdates", "retries", "errors", "elapsedMs",
                   "throughput", "p50Ms", "p95Ms", "p99Ms"]
SUMMARY_FIELDS = (["experiment", "strategy", "store", "buyers", "quota", "concurrency", "runs"]
                  + [f"{m}_{s}" for m in SUMMARY_METRICS for s in ("mean", "sd")] + ["errorCodes"])


def login(base_url, email, password):
    response = requests.post(f"{base_url}/api/auth/login", json={"email": email, "password": password}, timeout=30)
    response.raise_for_status()
    return response.json()["accessToken"]


def run_lab(base_url, token, body):
    # Chạy đồng bộ, có thể vài phút: không đặt timeout đọc.
    response = requests.post(f"{base_url}/api/admin/concurrency-lab/runs", json=body,
                             headers={"Authorization": f"Bearer {token}"}, timeout=(10, None))
    if response.status_code != 200:
        sys.exit(f"Lab trả {response.status_code}: {response.text}")
    return response.json()


def rows_of(experiment, payload):
    config = payload["config"]
    runs, summaries = [], []
    for result in payload["results"]:
        base = {"experiment": experiment, "strategy": result["strategy"], "store": result["store"],
                "buyers": config["buyers"], "quota": config["quota"], "concurrency": config["concurrency"]}
        codes = {}
        for run in result["runs"]:
            for code, count in run["errorCodes"].items():
                codes[code] = codes.get(code, 0) + count
            runs.append({**base, "oraclePoolSize": config["oraclePoolSize"], "redisPoolSize": config["redisPoolSize"],
                         **{k: run[k] for k in RUN_FIELDS if k in run and k != "errorCodes"},
                         "errorCodes": json.dumps(run["errorCodes"], sort_keys=True)})
        summary = {**base, "runs": len(result["runs"]), "errorCodes": json.dumps(codes, sort_keys=True)}
        for metric in SUMMARY_METRICS:
            summary[f"{metric}_mean"] = result["summary"][metric]["mean"]
            summary[f"{metric}_sd"] = result["summary"][metric]["sd"]
        summaries.append(summary)
    return runs, summaries


def write_csv(path, fields, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)
    print(f"Đã ghi {path} ({len(rows)} dòng)")


def read_csv(path):
    with path.open(encoding="utf-8") as f:
        return list(csv.DictReader(f))


def measure(args, experiment):
    token = login(args.base_url, args.email, args.password)
    runs, summaries = [], []
    if experiment == "e1":
        bodies = [{**E1, "runs": args.runs, "warmup": args.warmup}]
    else:
        bodies = [{**E2, "strategies": E2_STRATEGIES, "concurrency": c, "runs": args.runs, "warmup": args.warmup}
                  for c in E2_CONCURRENCY]
    for body in bodies:
        print(f"{experiment.upper()}: N={body['buyers']} M={body['quota']} C={body['concurrency']} ...", flush=True)
        payload = run_lab(args.base_url, token, body)
        r, s = rows_of(experiment, payload)
        runs += r
        summaries += s
        for row in s:
            print(f"  {row['strategy']:<20} thành công {row['successes_mean']:>7} bán vượt {row['oversold_mean']:>6}"
                  f" mất cập nhật {row['lostUpdates_mean']:>6} lỗi {row['errors_mean']:>6}"
                  f" {row['throughput_mean']:>9} lượt/s p95 {row['p95Ms_mean']} ms", flush=True)
    write_csv(RESULTS / f"{experiment}-runs.csv", RUN_FIELDS, runs)
    write_csv(RESULTS / f"{experiment}-summary.csv", SUMMARY_FIELDS, summaries)


def plot():
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    IMAGES.mkdir(parents=True, exist_ok=True)
    plt.rcParams.update({"font.size": 10, "figure.dpi": 150})
    blue, orange, red = "#1f77b4", "#ff7f0e", "#d62728"

    e1 = RESULTS / "e1-summary.csv"
    if e1.exists():
        rows = read_csv(e1)
        names = [r["strategy"] for r in rows]
        quota = float(rows[0]["quota"])
        fig, ax = plt.subplots(figsize=(8, 4.2))
        y = range(len(rows))
        ax.barh([i - 0.2 for i in y], [float(r["successes_mean"]) for r in rows], height=0.4,
                xerr=[float(r["successes_sd"]) for r in rows], color=blue, label="Lượt được báo mua thành công")
        ax.barh([i + 0.2 for i in y], [float(r["finalSold_mean"]) for r in rows], height=0.4,
                xerr=[float(r["finalSold_sd"]) for r in rows], color=orange, label="Số bán trong bộ đếm cuối cùng")
        ax.axvline(quota, color=red, linestyle="--", linewidth=1, label=f"Số suất M = {quota:.0f}")
        ax.set_yticks(list(y), names)
        ax.invert_yaxis()
        ax.set_xscale("log")
        ax.set_xlabel("Số lượt (thang log), trung bình ± độ lệch chuẩn")
        ax.set_title(f"E1: N = {rows[0]['buyers']}, M = {rows[0]['quota']}, C = {rows[0]['concurrency']}")
        ax.legend(fontsize=8, loc="lower right")
        fig.tight_layout()
        fig.savefig(IMAGES / "3.5-01-e1-ban-vuot.png")
        plt.close(fig)
        print(f"Đã vẽ {IMAGES / '3.5-01-e1-ban-vuot.png'}")

    e2 = RESULTS / "e2-summary.csv"
    if e2.exists():
        rows = read_csv(e2)
        strategies = list(dict.fromkeys(r["strategy"] for r in rows))
        for metric, label, name in (("throughput", "Throughput (lượt/giây)", "3.5-02-e2-throughput.png"),
                                    ("p95Ms", "Độ trễ p95 (ms)", "3.5-03-e2-p95.png")):
            fig, ax = plt.subplots(figsize=(7, 4.2))
            for strategy in strategies:
                mine = sorted((r for r in rows if r["strategy"] == strategy), key=lambda r: int(r["concurrency"]))
                ax.errorbar([int(r["concurrency"]) for r in mine], [float(r[f"{metric}_mean"]) for r in mine],
                            yerr=[float(r[f"{metric}_sd"]) for r in mine], marker="o", capsize=3,
                            linestyle="--" if strategy.startswith("REDIS") else "-", label=strategy)
            ax.set_xscale("log", base=2)
            ax.set_yscale("log")
            ax.set_xticks(E2_CONCURRENCY, [str(c) for c in E2_CONCURRENCY])
            ax.set_xlabel("Số lượt đồng thời C")
            ax.set_ylabel(label + ", thang log")
            ax.set_title(f"E2: N = M = {rows[0]['buyers']}, trung bình ± độ lệch chuẩn")
            ax.grid(True, which="major", alpha=0.3)
            ax.legend(fontsize=8)
            fig.tight_layout()
            fig.savefig(IMAGES / name)
            plt.close(fig)
            print(f"Đã vẽ {IMAGES / name}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("experiment", choices=["e1", "e2", "plot"])
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--email", default="admin@shopflash.local")
    parser.add_argument("--password", default=os.environ.get("LAB_ADMIN_PASSWORD", "ShopFlash123!"))
    parser.add_argument("--runs", type=int, default=5)
    parser.add_argument("--warmup", type=int, default=1)
    args = parser.parse_args()
    if args.experiment != "plot":
        measure(args, args.experiment)
    plot()


if __name__ == "__main__":
    main()
