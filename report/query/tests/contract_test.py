"""Kiểm tra API module query theo contract (backend/.../modules/query/AGENTS.md) trên stack đang chạy.

Chạy từ thư mục gốc repo, khi backend đã chạy ở :8080 và Redis đã rebuild:
    python report/query/tests/contract_test.py            # mọi kiểm tra
    python report/query/tests/contract_test.py --no-d6    # bỏ kịch bản chèn đơn vào Oracle

Chỉ dùng thư viện chuẩn. Kịch bản D6 chèn 1 đơn tạm vào Oracle qua `docker compose exec oracle sqlplus`
(mật khẩu lấy từ biến môi trường trong container, không in ra), rồi xóa nó và rebuild lại.
"""
import json
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from decimal import Decimal

# Console Windows mặc định cp1252 không in được tiếng Việt.
sys.stdout.reconfigure(encoding="utf-8")

BASE = "http://localhost:8080/api/admin/reports"
REPORTS = ["revenue-by-month", "revenue-by-category", "top-products", "vip-customers", "unsold-products"]
JOIN_REPORTS = ["revenue-by-category", "top-products", "vip-customers"]
ROW_FIELDS = {
    "revenue-by-month": {"month", "orderCount", "revenue"},
    "revenue-by-category": {"categoryId", "categoryName", "quantity", "revenue"},
    "top-products": {"rank", "productId", "productName", "categoryName", "quantity", "revenue"},
    "vip-customers": {"userId", "fullName", "email", "orderCount", "totalSpent"},
    "unsold-products": {"productId", "productName", "categoryName", "basePrice"},
}
META_FIELDS = {"source", "report", "variant", "supported", "elapsedMs", "roundTrips", "statement", "rowCount", "generatedAt", "note"}
JOIN_OP = {"NL": "NESTED LOOPS", "HASH": "HASH JOIN", "MERGE": "MERGE JOIN"}

passed, failed = 0, []


def check(name, cond, detail=""):
    global passed
    if cond:
        passed += 1
        print(f"  ok   {name}")
    else:
        failed.append(name)
        print(f"  FAIL {name} {detail}")


def call(method, path, params=None, body=None, accept="application/json"):
    url = f"{BASE}{path}"
    if params:
        url += "?" + urllib.parse.urlencode(params)
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={"Accept": accept, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=600) as resp:
            raw = resp.read().decode()
            return resp.status, (raw if accept == "text/csv" else json.loads(raw, parse_float=Decimal))
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except json.JSONDecodeError:
            return e.code, raw


def sql(statement):
    """Chạy SQL bằng user SHOP trong container; mật khẩu đi qua stdin, không lên dòng lệnh."""
    script = (
        'printf "connect shop/%s@//localhost:1521/FREEPDB1\\nset heading off feedback off\\n'
        'whenever sqlerror exit failure\\n%s\\nexit\\n" "$SHOP_PASSWORD" "$SQL_TEXT" | sqlplus -s -L /nolog'
    )
    out = subprocess.run(
        ["docker", "compose", "exec", "-T", "-e", f"SQL_TEXT={statement}", "oracle", "bash", "-c", script],
        capture_output=True, text=True, timeout=120,
    )
    if out.returncode != 0:
        raise RuntimeError(out.stdout + out.stderr)
    return out.stdout.strip()


def test_shapes():
    print("Dạng response và sắp xếp")
    for report in REPORTS:
        for source, variant_key, variants in (("oracle", "joinHint", ["AUTO"]), ("redis", "redisMode", ["AGGREGATE", "CLIENT"])):
            for variant in variants:
                status, body = call("GET", f"/{report}", {"source": source, variant_key: variant})
                tag = f"{report} {source}/{variant}"
                check(f"{tag} 200", status == 200, body)
                if status != 200:
                    continue
                meta = body["meta"]
                check(f"{tag} meta fields", META_FIELDS <= set(meta), sorted(set(meta) ^ META_FIELDS))
                if source == "redis" and report == "unsold-products":
                    check(f"{tag} unsupported", meta["supported"] is False and body["data"] is None and meta["note"])
                    continue
                rows = body["data"]["rows"]
                check(f"{tag} rowCount", meta["rowCount"] == len(rows))
                check(f"{tag} elapsedMs > 0", float(meta["elapsedMs"]) > 0)
                check(f"{tag} roundTrips >= 1", meta["roundTrips"] >= 1)
                if rows:
                    check(f"{tag} row fields", set(rows[0]) == ROW_FIELDS[report], sorted(rows[0]))
                if report == "revenue-by-month":
                    check(f"{tag} month asc", [r["month"] for r in rows] == sorted(r["month"] for r in rows))
                if report == "top-products":
                    check(f"{tag} rank 1..n", [r["rank"] for r in rows] == list(range(1, len(rows) + 1)))
                    check(f"{tag} quantity desc", all(a["quantity"] >= b["quantity"] for a, b in zip(rows, rows[1:])))
                if report in ("vip-customers", "unsold-products"):
                    check(f"{tag} page info", set(body["data"]["page"]) == {"page", "size", "total"})
                if report == "vip-customers":
                    summary = body["data"]["summary"]
                    check(f"{tag} summary", set(summary) == {"averageSpent", "customerCount"})
                    check(f"{tag} all above average", all(r["totalSpent"] > summary["averageSpent"] for r in rows))


def test_compare():
    print("D1 compare Oracle vs Redis")
    for report in REPORTS:
        status, body = call("GET", f"/{report}/compare", {"limit": 20, "size": 50})
        check(f"compare {report} 200", status == 200, body)
        if status != 200:
            continue
        expected = None if report == "unsold-products" else True
        check(f"compare {report} match={expected}", body["match"] is expected, body.get("mismatches"))
        for page in (2, 3):
            if report == "vip-customers":
                status, body = call("GET", f"/{report}/compare", {"page": page, "size": 20})
                check(f"compare {report} page {page} match", status == 200 and body["match"] is True, body.get("mismatches"))


def test_plans():
    print("D2 kế hoạch thực thi")
    for report in JOIN_REPORTS:
        for hint, op in JOIN_OP.items():
            status, body = call("GET", f"/{report}/plan", {"source": "oracle", "joinHint": hint})
            text = "\n".join(body.get("lines", [])) if status == 200 else ""
            check(f"plan {report} {hint} has {op}", op in text, text[:300])
    for mode in ("AGGREGATE", "CLIENT"):
        status, body = call("GET", "/top-products/plan", {"source": "redis", "redisMode": mode})
        check(f"plan redis {mode}", status == 200 and len(body["lines"]) > 0, body)


def test_benchmark():
    print("Benchmark")
    req = {"runs": 2, "warmup": 1, "variants": [{"source": "oracle", "variant": "HASH"}, {"source": "redis", "variant": "AGGREGATE"}]}
    status, body = call("POST", "/top-products/benchmark", body=req)
    check("benchmark 200", status == 200, body)
    if status == 200:
        check("benchmark 2 results", len(body["results"]) == 2)
        r = body["results"][0]
        check("benchmark stats fields", {"meanMs", "stdDevMs", "p50Ms", "p95Ms", "samplesMs", "roundTrips", "runs"} <= set(r))
        check("benchmark samples = runs", len(r["samplesMs"]) == 2)
    status, csv = call("POST", "/top-products/benchmark", body=req, accept="text/csv")
    lines = csv.strip().splitlines() if status == 200 else []
    check("benchmark csv header", lines[:1] == ["report,source,variant,run,elapsedMs,roundTrips"], lines[:2])
    check("benchmark csv rows", len(lines) == 1 + 4, len(lines))


def test_errors():
    print("Lỗi và kiểm tra tham số")
    cases = [
        ("/unknown-report", {}, 400),
        ("/top-products", {"source": "mysql"}, 400),
        ("/top-products", {"from": "2026-10-10", "to": "2026-01-01"}, 400),
        ("/top-products", {"from": "2026-13-40"}, 400),
        ("/top-products", {"limit": 0}, 400),
        ("/vip-customers", {"size": 101}, 400),
        ("/top-products", {"joinHint": "FOO"}, 400),
    ]
    for path, params, code in cases:
        status, body = call("GET", path, params)
        ok = status == code and isinstance(body, dict) and body.get("error", {}).get("code") == "VALIDATION_ERROR"
        check(f"{path} {params} -> {code}", ok, f"{status} {body}")
    status, body = call("POST", "/oracle/stats", body={"action": "NOPE"})
    check("stats bad action -> 400", status == 400, f"{status} {body}")


def cleanup_d6():
    # sqlplus chỉ nhận một câu lệnh trên mỗi dòng.
    sql("delete from order_items where order_id in (select order_id from orders where order_code='CT-D6');\n"
        "delete from orders where order_code='CT-D6';\ncommit;")


def test_consistency():
    print("D6 nhất quán: đơn mới trong Oracle, Redis chỉ thấy sau rebuild")
    cleanup_d6()  # phòng khi lần chạy trước dừng giữa chừng
    status, body = call("POST", "/analytics/rebuild")
    check("rebuild 200", status == 200, body)
    status, st = call("GET", "/analytics/status")
    check("inSync sau rebuild", status == 200 and st["inSync"] is True, st)
    product = sql("select product_id||'|'||base_price from products where sku like 'gen-%' and status='ACTIVE' and rownum=1;")
    pid, price = product.split("|")
    sql(
        "declare v_id number; begin "
        "insert into orders(order_code,user_id,order_type,status,subtotal,discount_amount,total_amount,recipient_name,"
        "recipient_phone,shipping_address,idempotency_key,created_at,updated_at) values('CT-D6',2,'NORMAL','CONFIRMED',"
        f"{price},0,{price},'D6','0900000000','HCMUT','ct-d6',systimestamp,systimestamp) returning order_id into v_id; "
        "insert into order_items(order_id,product_id,product_name,product_sku,unit_price,quantity,line_total) "
        f"select v_id, product_id, name, sku, base_price, 1, base_price from products where product_id={pid}; commit; end;\n/"
    )
    try:
        status, st = call("GET", "/analytics/status")
        check("inSync=false sau khi chèn đơn", st["inSync"] is False, st)
        status, body = call("GET", "/revenue-by-month/compare")
        check("compare lệch trước rebuild", body["match"] is False, body.get("mismatches"))
        call("POST", "/analytics/rebuild")
        status, body = call("GET", "/revenue-by-month/compare")
        check("compare khớp sau rebuild", body["match"] is True, body.get("mismatches"))
    finally:
        cleanup_d6()
        call("POST", "/analytics/rebuild")
    status, st = call("GET", "/analytics/status")
    check("inSync sau khi dọn", st["inSync"] is True, st)


if __name__ == "__main__":
    test_shapes()
    test_compare()
    test_plans()
    test_benchmark()
    test_errors()
    if "--no-d6" not in sys.argv:
        test_consistency()
    print(f"\n{passed} passed, {len(failed)} failed")
    for name in failed:
        print(f"  - {name}")
    sys.exit(1 if failed else 0)
