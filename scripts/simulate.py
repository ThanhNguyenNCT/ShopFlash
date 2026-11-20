#!/usr/bin/env python3
"""
Mô phỏng ShopFlash từ trạng thái trống: xóa sạch đơn, nạp lịch sử, khách mua hàng, tranh flash sale.

Chạy từ thư mục gốc repo, khi Oracle + Redis (docker compose) và backend (profile dev) đang chạy:

    python scripts/simulate.py all                 # reset -> history -> shop -> flash -> confirm -> rebuild
    python scripts/simulate.py reset               # xóa mọi đơn, giỏ, flash sale, dữ liệu sinh; nạp lại tồn kho
    python scripts/simulate.py history             # lớp 1: ~100k đơn của 12 tháng trước (seed SQL của module query)
    python scripts/simulate.py shop --orders 3000  # khách đăng ký, thêm giỏ, đặt hàng qua API thật
    python scripts/simulate.py flash --buyers 2000 # tạo flash sale, chờ tự mở bán, dồn request trong vài giây
    python scripts/simulate.py confirm             # mô phỏng shop xác nhận / giao các đơn PENDING
    python scripts/simulate.py rebuild             # dựng lại read model Redis cho trang Báo cáo

Chỉ dùng thư viện chuẩn của Python. Mọi thao tác mua hàng đi qua REST API của backend (giỏ hàng, checkout, giữ suất
bằng Lua, đặt đơn flash), nên tồn kho, transaction và Redis chạy đúng như khi khách bấm trên web. Chỉ có ba việc đi
thẳng vào Oracle bằng sqlplus trong container: reset, nạp lịch sử và xác nhận đơn (app chưa có màn hình duyệt đơn).
"""
from __future__ import annotations

import argparse
import json
import os
import random
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEED_DIR = ROOT / "backend" / "src" / "main" / "resources" / "db" / "history-seed"
VN = timezone(timedelta(hours=7))

ADMIN_EMAIL = os.environ.get("SHOPFLASH_ADMIN_EMAIL", "admin@shopflash.local")
ADMIN_PASSWORD = os.environ.get("SHOPFLASH_ADMIN_PASSWORD", "ShopFlash123!")
SIM_DOMAIN = "sim.shopflash.local"
SIM_PASSWORD = "SimShop123!"

# ---------------------------------------------------------------- log

if os.name == "nt":
    os.system("")  # bật mã màu ANSI trên Windows Terminal / PowerShell
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8")
    except Exception:
        pass

C = {"dim": "\033[2m", "red": "\033[31m", "green": "\033[32m", "yellow": "\033[33m", "blue": "\033[34m",
     "magenta": "\033[35m", "cyan": "\033[36m", "bold": "\033[1m", "end": "\033[0m"}
_print_lock = threading.Lock()


def log(tag: str, msg: str, color: str = "") -> None:
    now = datetime.now().strftime("%H:%M:%S.%f")[:-3]
    with _print_lock:
        print(f"{C['dim']}{now}{C['end']} {C.get(color, '')}{tag:<7}{C['end']} {msg}", flush=True)


def banner(title: str) -> None:
    line = "═" * 72
    with _print_lock:
        print(f"\n{C['cyan']}{line}\n  {title}\n{line}{C['end']}", flush=True)


def money(v) -> str:
    return f"{int(float(v)):,}đ".replace(",", ".")


# ---------------------------------------------------------------- HTTP

class ApiError(Exception):
    def __init__(self, status: int, code: str, message: str):
        super().__init__(f"{status} {code}: {message}")
        self.status, self.code, self.message = status, code, message


class Api:
    def __init__(self, base: str):
        self.base = base.rstrip("/")

    def call(self, method: str, path: str, body=None, token: str | None = None, headers: dict | None = None,
             timeout: float = 60):
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(self.base + path, data=data, method=method)
        req.add_header("Accept", "application/json")
        if data is not None:
            req.add_header("Content-Type", "application/json")
        if token:
            req.add_header("Authorization", f"Bearer {token}")
        for k, v in (headers or {}).items():
            req.add_header(k, str(v))
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                raw = resp.read()
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as e:
            raw = e.read()
            try:
                err = json.loads(raw).get("error", {})
                raise ApiError(e.code, err.get("code", "HTTP_ERROR"), err.get("message", "")) from None
            except (ValueError, AttributeError):
                raise ApiError(e.code, "HTTP_ERROR", raw[:200].decode(errors="replace")) from None
        except urllib.error.URLError as e:
            raise ApiError(0, "NETWORK", str(e.reason)) from None

    def login(self, email: str, password: str) -> str:
        return self.call("POST", "/api/auth/login", {"email": email, "password": password})["accessToken"]


# ---------------------------------------------------------------- Docker: sqlplus, redis-cli

def compose(args: list[str], stdin: str | None = None, check: bool = True) -> str:
    p = subprocess.run(["docker", "compose", *args], cwd=ROOT, input=stdin, capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    if check and p.returncode != 0:
        raise RuntimeError(f"docker compose {' '.join(args[:3])} lỗi:\n{p.stdout}\n{p.stderr}")
    return p.stdout


SQLPLUS = ('{ printf "connect shop/%s@//localhost:1521/FREEPDB1\\n" "$SHOP_PASSWORD"; cat; } '
           '| sqlplus -s -L /nolog')


def sql(script: str) -> str:
    """Chạy script SQL*Plus trong container Oracle bằng user SHOP; mật khẩu lấy từ biến trong container."""
    header = "whenever sqlerror exit failure rollback\nset feedback off heading off pagesize 0 linesize 4000 trimspool on\n"
    out = compose(["exec", "-T", "oracle", "bash", "-c", SQLPLUS], stdin=header + script + "\nexit\n")
    if "ORA-" in out or "SP2-" in out:
        raise RuntimeError(f"SQL lỗi:\n{out}")
    return out


def sql_rows(query: str, sep: str = "|") -> list[list[str]]:
    out = sql(query.rstrip().rstrip(";") + ";")
    return [line.split(sep) for line in out.splitlines() if line.strip()]


def redis_del(pattern: str) -> int:
    out = compose(["exec", "-T", "redis", "sh", "-c",
                   f"redis-cli --scan --pattern '{pattern}' | xargs -r -n 500 redis-cli del | awk '{{s+=$1}} END {{print s+0}}'"])
    return int((out.strip().splitlines() or ["0"])[-1])


# ---------------------------------------------------------------- dữ liệu giả cho khách

HO = ["Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ", "Võ", "Đặng", "Bùi", "Đỗ", "Hồ", "Ngô", "Dương"]
LOT = ["Văn", "Thị", "Minh", "Ngọc", "Thanh", "Đức", "Hoài", "Gia", "Quốc", "Thu", "Bảo", "Khánh"]
TEN = ["An", "Bình", "Chi", "Dũng", "Hà", "Hải", "Hùng", "Khoa", "Lan", "Linh", "Mai", "Nam", "Phúc", "Quân",
       "Sơn", "Tâm", "Thảo", "Trang", "Tuấn", "Vy", "Yến", "Long", "Nhi", "Duy"]
DUONG = ["Lý Thường Kiệt", "Nguyễn Trãi", "Lê Lợi", "Trần Hưng Đạo", "Võ Văn Tần", "Điện Biên Phủ", "Cách Mạng Tháng 8"]
QUAN = ["Quận 1", "Quận 3", "Quận 5", "Quận 10", "Bình Thạnh", "Thủ Đức", "Gò Vấp", "Tân Bình"]


class Customer:
    def __init__(self, i: int, rnd: random.Random):
        self.i = i
        self.email = f"sim-{i:05d}@{SIM_DOMAIN}"
        self.name = f"{rnd.choice(HO)} {rnd.choice(LOT)} {rnd.choice(TEN)}"
        self.phone = f"09{rnd.randint(10000000, 99999999)}"
        self.address = f"{rnd.randint(1, 499)} {rnd.choice(DUONG)}, {rnd.choice(QUAN)}, TP.HCM"
        self.token: str | None = None


def prepare_customers(api: Api, n: int, workers: int, seed: int) -> list[Customer]:
    """Đăng ký (bỏ qua nếu đã có) rồi đăng nhập n khách sim-xxxxx. Mật khẩu băm bcrypt nên bước này tốn CPU."""
    rnd = random.Random(seed)
    customers = [Customer(i, rnd) for i in range(1, n + 1)]
    done, created, t0 = [0], [0], time.perf_counter()
    lock = threading.Lock()

    def one(c: Customer):
        try:
            api.call("POST", "/api/auth/register",
                     {"fullName": c.name, "email": c.email, "phone": c.phone, "password": SIM_PASSWORD})
            with lock:
                created[0] += 1
        except ApiError as e:
            if e.status not in (400, 409):
                raise
        c.token = api.login(c.email, SIM_PASSWORD)
        with lock:
            done[0] += 1
            if done[0] % 100 == 0 or done[0] == n:
                log("KHÁCH", f"{done[0]:>5}/{n} khách sẵn sàng (mới đăng ký {created[0]}, "
                             f"{time.perf_counter() - t0:.0f} s)", "blue")

    with ThreadPoolExecutor(workers) as pool:
        for f in as_completed([pool.submit(one, c) for c in customers]):
            f.result()
    return customers


# ---------------------------------------------------------------- reset

RESET_SQL = """
prompt Xoa don, gio hang, flash sale...
DELETE FROM order_items;
DELETE FROM orders;
DELETE FROM cart_items;
DELETE FROM carts;
DELETE FROM flash_sale_items;
DELETE FROM flash_sale_configs;
DELETE FROM flash_sales;
UPDATE product_inventory SET reserved_flash_quantity = 0, updated_at = SYSTIMESTAMP WHERE reserved_flash_quantity <> 0;
prompt Xoa du lieu sinh va khach mo phong...
DELETE FROM product_inventory WHERE product_id IN (SELECT product_id FROM products WHERE sku LIKE 'gen-%');
DELETE FROM products   WHERE sku   LIKE 'gen-%';
DELETE FROM categories WHERE slug  LIKE 'gen-%';
DELETE FROM users WHERE email LIKE '%@gen.shopflash.local' OR email LIKE '%@{domain}';
prompt Nap lai ton kho san pham dang ban...
UPDATE product_inventory i SET on_hand_quantity = {stock}, version = version + 1, updated_at = SYSTIMESTAMP
WHERE  i.on_hand_quantity > 0
AND    EXISTS (SELECT 1 FROM products p WHERE p.product_id = i.product_id AND p.status = 'ACTIVE');
COMMIT;
SELECT 'orders=' || (SELECT COUNT(*) FROM orders) || ' products=' || (SELECT COUNT(*) FROM products)
       || ' users=' || (SELECT COUNT(*) FROM users) FROM dual;
"""


def cmd_reset(api: Api, args) -> None:
    banner("RESET · đưa hệ thống về trống: chỉ còn danh mục, sản phẩm, tài khoản mẫu")
    t0 = time.perf_counter()
    log("ORACLE", "Xóa đơn, giỏ hàng, flash sale, dữ liệu sinh và khách mô phỏng (có thể mất ~40 s nếu đã nạp lịch sử)", "magenta")
    out = sql(RESET_SQL.format(domain=SIM_DOMAIN, stock=args.stock))
    log("ORACLE", f"Xong: {out.strip().splitlines()[-1]} · tồn kho mỗi sản phẩm đang bán đặt lại {args.stock}", "green")
    n1 = redis_del("flash:*")
    n2 = redis_del("report:cache:*")
    log("REDIS", f"Xóa {n1} key flash sale, {n2} key cache báo cáo", "green")
    cmd_rebuild(api, args, quiet=True)
    log("XONG", f"Hệ thống trống, {time.perf_counter() - t0:.0f} s", "bold")


# ---------------------------------------------------------------- history (lớp 1)

def cmd_history(api: Api, args) -> None:
    banner("LỚP 1 · nạp lịch sử 12 tháng (~100.000 đơn) bằng seed SQL của module query")
    cid = compose(["ps", "-q", "oracle"]).strip()
    subprocess.run(["docker", "exec", "-u", "0", cid, "rm", "-rf", "/tmp/seed"], check=True)
    subprocess.run(["docker", "cp", str(SEED_DIR), f"{cid}:/tmp/seed"], check=True, capture_output=True)
    log("ORACLE", "Đang sinh dữ liệu (khoảng 1,5 phút)…", "magenta")
    t0 = time.perf_counter()
    proc = subprocess.Popen(["docker", "compose", "exec", "-T", "oracle", "bash", "-c", SQLPLUS], cwd=ROOT,
                            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                            encoding="utf-8", errors="replace")
    proc.stdin.write("@/tmp/seed/01_generate.sql\nexit\n")
    proc.stdin.close()
    for line in proc.stdout:
        if line.strip():
            log("SEED", line.rstrip(), "dim" if not line.startswith(("==", "ORA-")) else "yellow")
    if proc.wait() != 0:
        raise RuntimeError("Seed lịch sử lỗi, xem log phía trên")
    log("XONG", f"Đã nạp lịch sử trong {time.perf_counter() - t0:.0f} s", "bold")
    cmd_rebuild(api, args)


# ---------------------------------------------------------------- shop

def catalog_products() -> list[tuple[int, int, str]]:
    rows = sql_rows("""SELECT p.product_id || '|' || p.base_price || '|' || REPLACE(p.name, '|', '/')
        FROM products p JOIN product_inventory i ON i.product_id = p.product_id
        WHERE p.status = 'ACTIVE' AND p.sku NOT LIKE 'gen-%'
          AND i.on_hand_quantity - i.reserved_flash_quantity >= 50
        ORDER BY p.product_id""")
    return [(int(r[0]), int(float(r[1])), r[2]) for r in rows]


def place_order(api: Api, c: Customer, items: list[tuple[int, int, str]], rnd: random.Random) -> dict:
    cart = api.call("GET", "/api/cart", token=c.token)
    version = cart["version"]
    if cart["items"]:
        version = api.call("DELETE", "/api/cart/items", token=c.token, headers={"X-Cart-Version": version})["version"]
    for pid, _, _ in items:
        cart = api.call("POST", "/api/cart/items", {"productId": pid, "quantity": rnd.choice([1, 1, 1, 2, 2, 3])},
                        token=c.token, headers={"X-Cart-Version": version})
        version = cart["version"]
    preview = api.call("GET", "/api/checkout/preview", token=c.token)
    body = {"cartVersion": preview["cartVersion"], "recipientName": c.name, "recipientPhone": c.phone,
            "shippingAddress": c.address,
            "expectedLines": [{"productId": l["productId"], "quantity": l["quantity"], "unitPrice": l["unitPrice"]}
                              for l in preview["lines"]]}
    order = api.call("POST", "/api/orders", body, token=c.token, headers={"Idempotency-Key": str(uuid.uuid4())})
    order["itemCount"] = sum(l["quantity"] for l in preview["lines"])
    return order


def cmd_shop(api: Api, args) -> None:
    banner(f"LỚP 2 · khách mua hàng: {args.customers} khách, {args.orders} đơn, {args.workers} luồng song song")
    rnd = random.Random(args.seed)
    products = catalog_products()
    if not products:
        raise RuntimeError("Không có sản phẩm còn hàng. Chạy `reset` trước để nạp lại tồn kho.")
    log("SHOP", f"{len(products)} sản phẩm đang bán", "cyan")
    customers = prepare_customers(api, args.customers, args.workers, args.seed)

    # Phân đơn cho khách theo phân phối lệch: ít khách mua rất nhiều (khách VIP), đa số mua 1-2 đơn.
    weights = [1 / (k + 1) ** 0.8 for k in range(len(customers))]
    plan: dict[int, int] = Counter(rnd.choices(range(len(customers)), weights=weights, k=args.orders))
    pop = [1 / (k + 1) ** 0.9 for k in range(len(products))]
    rnd.shuffle(pop)

    stats, lock, t0 = Counter(), threading.Lock(), time.perf_counter()
    revenue = [0]

    def run_customer(idx: int, count: int, seed: int):
        r = random.Random(seed)
        c = customers[idx]
        for _ in range(count):
            items = list({p[0]: p for p in r.choices(products, weights=pop, k=r.choice([1, 1, 2, 2, 3]))}.values())
            started = time.perf_counter()
            try:
                order = place_order(api, c, items, r)
            except ApiError as e:
                with lock:
                    stats["lỗi " + e.code] += 1
                log("LỖI", f"{c.name:<24} {e.code} {e.message[:70]}", "yellow")
                continue
            ms = (time.perf_counter() - started) * 1000
            with lock:
                stats["đặt"] += 1
                revenue[0] += int(float(order["totalAmount"]))
                n = stats["đặt"]
            names = ", ".join(p[2][:22] for p in items[:2]) + (" …" if len(items) > 2 else "")
            log("ĐƠN", f"#{n:<5} {order['orderCode'][:14]:<14} {c.name:<24} {order['itemCount']} món "
                       f"{money(order['totalAmount']):>13}  {C['dim']}{names} · {ms:.0f} ms{C['end']}", "green")
            if r.random() < args.cancel_rate:
                try:
                    api.call("POST", f"/api/orders/{order['orderId']}/cancel", token=c.token)
                    with lock:
                        stats["hủy"] += 1
                    log("HỦY", f"{order['orderCode'][:14]:<14} {c.name} hủy đơn, kho được hoàn lại", "yellow")
                except ApiError as e:
                    log("LỖI", f"hủy {order['orderCode']}: {e.code}", "yellow")

    with ThreadPoolExecutor(args.workers) as pool:
        futures = [pool.submit(run_customer, idx, cnt, rnd.randrange(1 << 30)) for idx, cnt in plan.items()]
        for f in as_completed(futures):
            f.result()

    elapsed = time.perf_counter() - t0
    banner("KẾT QUẢ MUA HÀNG")
    log("TỔNG", f"Đặt thành công {stats['đặt']} đơn trong {elapsed:.1f} s ({stats['đặt'] / elapsed:.0f} đơn/s), "
                f"tổng {money(revenue[0])}", "bold")
    log("TỔNG", f"Hủy {stats['hủy']} đơn (kho hoàn lại). Lỗi: "
                + (", ".join(f"{k.removeprefix('lỗi ')} {v}" for k, v in stats.items() if k.startswith('lỗi')) or "0"),
        "bold")


# ---------------------------------------------------------------- flash

def cmd_flash(api: Api, args) -> None:
    banner(f"FLASH SALE · {args.items} sản phẩm × {args.quota} suất, {args.buyers} người tranh trong vài giây")
    admin = api.login(ADMIN_EMAIL, ADMIN_PASSWORD)
    now = datetime.now(VN).replace(microsecond=0)
    start = now + timedelta(seconds=args.lead)
    sale = api.call("POST", "/api/admin/flash-sales", {
        "name": f"Flash sale {start:%H:%M}", "description": "Chương trình mô phỏng bởi scripts/simulate.py",
        "startAt": start.isoformat(), "endAt": (start + timedelta(minutes=args.duration)).isoformat(),
        "config": {"maxQuantityPerUser": 1, "reservationTimeoutSeconds": 60, "allowCancel": False}}, token=admin)
    sid = sale["flashSaleId"]
    # Chọn sản phẩm thật (có ảnh) giá từ 200.000đ; backend vẫn tự kiểm tra còn hàng, đang bán, không trùng chương trình.
    candidates = [p for p in catalog_products() if p[1] >= 200000]
    picks = random.Random(args.seed).sample(candidates, min(args.items, len(candidates)))
    items = [{"productId": pid, "allocatedQuantity": args.quota,
              "salePrice": max(1000, int(price * 0.5) // 1000 * 1000)} for pid, price, _ in picks]
    sale = api.call("PUT", f"/api/admin/flash-sales/{sid}/items", {"version": sale["version"], "items": items},
                    token=admin)
    sale = api.call("POST", f"/api/admin/flash-sales/{sid}/schedule", token=admin)
    log("ADMIN", f"Tạo chương trình #{sid} \"{sale['name']}\" · trạng thái {sale['status']} · mở bán lúc {start:%H:%M:%S}",
        "magenta")
    for it in sale["items"]:
        log("ADMIN", f"  {it['productName'][:40]:<40} {money(it['basePrice']):>13} → {money(it['salePrice']):>12}"
                     f"  {it['allocated']} suất", "magenta")
    total_quota = sum(it["allocated"] for it in sale["items"])

    log("KHÁCH", f"Trong lúc chờ mở bán: chuẩn bị {args.buyers} người mua (đăng ký, đăng nhập)", "blue")
    # Cùng seed với bước shop: khách sim-00001… đã có thì chỉ đăng nhập, tên in ra khớp tên trong Oracle.
    buyers = prepare_customers(api, args.buyers, args.workers, args.seed)
    item_ids = [it["itemId"] for it in sale["items"]]
    names = {it["itemId"]: it["productName"] for it in sale["items"]}

    # Đợi tới giờ mở bán. Job của backend chạy mỗi 5 s chuyển SCHEDULED -> ACTIVE; giữ suất so giờ theo đồng hồ Redis.
    last = None
    while True:
        left = (start - datetime.now(VN)).total_seconds()
        active = api.call("GET", "/api/flash-sales/active")
        phase = next((s["phase"] for s in active["sales"] if s["flashSaleId"] == sid), None)
        if phase == "LIVE":
            break
        sec = max(0, int(left))
        if sec != last and (sec % 5 == 0 or sec <= 5):
            log("ĐẾM", f"Còn {sec:>2} s mở bán · {total_quota} suất đang chờ trên Redis", "cyan")
            last = sec
        time.sleep(0.2)

    banner(f"MỞ BÁN · {args.buyers} request giữ suất bắn cùng lúc")
    results, lock = Counter(), threading.Lock()
    gate = threading.Event()
    sold_orders: list[str] = []
    rnd = random.Random(args.seed + 2)
    choice = {b.i: rnd.choice(item_ids) for b in buyers}

    def buy(b: Customer):
        gate.wait()
        item = choice[b.i]
        t = time.perf_counter()
        try:
            res = api.call("POST", f"/api/flash-sales/{sid}/items/{item}/reservations", {"quantity": 1}, token=b.token)
        except ApiError as e:
            with lock:
                results[e.code] += 1
            log("TRƯỢT", f"{b.name:<24} {names[item][:28]:<28} {e.code} "
                         f"{C['dim']}{(time.perf_counter() - t) * 1000:.0f} ms{C['end']}", "red" if e.code != "SOLD_OUT" else "dim")
            return
        hold_ms = (time.perf_counter() - t) * 1000
        log("GIỮ", f"{b.name:<24} {names[item][:28]:<28} giữ được suất "
                   f"{C['dim']}{hold_ms:.0f} ms{C['end']}", "green")
        try:
            order = api.call("POST", f"/api/flash-sales/reservations/{res['reservationId']}/checkout",
                             {"recipientName": b.name, "recipientPhone": b.phone, "shippingAddress": b.address},
                             token=b.token)
        except ApiError as e:
            with lock:
                results["giữ được nhưng đặt lỗi " + e.code] += 1
            log("LỖI", f"{b.name} đặt đơn lỗi {e.code}", "yellow")
            return
        with lock:
            results["MUA_ĐƯỢC"] += 1
            sold_orders.append(order["orderCode"])
        log("MUA", f"{b.name:<24} {names[item][:28]:<28} đặt đơn {order['orderCode'][:14]} "
                   f"{money(order['totalAmount'])}", "bold")

    threads = [threading.Thread(target=buy, args=(b,), daemon=True) for b in buyers]
    for t in threads:
        t.start()
    time.sleep(0.5)
    t0 = time.perf_counter()
    gate.set()
    for t in threads:
        t.join()
    burst = time.perf_counter() - t0

    time.sleep(1)
    rt = api.call("GET", f"/api/admin/flash-sales/{sid}/runtime", token=admin)
    banner("KẾT QUẢ FLASH SALE")
    log("TỔNG", f"{args.buyers} người mua, {args.buyers} request giữ suất dồn vào {burst:.2f} s "
                f"(~{args.buyers / burst:.0f} request/s)", "bold")
    for k, v in results.most_common():
        log("TỔNG", f"  {k:<32} {v}", "bold")
    sold = sum(i["sold"] for i in rt["items"])
    for i in rt["items"]:
        log("KIỂM", f"  {i['productName'][:36]:<36} suất {i['allocated']:>3} · Oracle đã bán {i['sold']:>3} · "
                    f"Redis còn {i['remaining']} · đang giữ {i['held']} · khớp {'có' if i['inSync'] else 'KHÔNG'}", "cyan")
    oversell = sold > total_quota or results["MUA_ĐƯỢC"] > total_quota
    ok = not oversell and not rt["invariantViolations"] and sold == results["MUA_ĐƯỢC"]
    log("KIỂM", f"Tổng suất {total_quota}, bán {sold}, đơn đặt được {results['MUA_ĐƯỢC']} → "
                + ("KHÔNG BÁN LỐ, Oracle khớp Redis" if ok else "CÓ SAI LỆCH, xem các dòng trên"),
        "green" if ok else "red")


# ---------------------------------------------------------------- confirm, rebuild

CONFIRM_SQL = """
UPDATE orders SET status = CASE WHEN MOD(ORA_HASH(order_id), 100) < {done} THEN 'COMPLETED' ELSE 'CONFIRMED' END,
       updated_at = SYSTIMESTAMP
WHERE  status = 'PENDING' AND MOD(ORA_HASH(order_id, 999999, 7), 100) >= {keep}
AND    user_id IN (SELECT user_id FROM users WHERE email LIKE '%@{domain}');
COMMIT;
SELECT status || '|' || order_type || '|' || COUNT(*) FROM orders
WHERE  user_id IN (SELECT user_id FROM users WHERE email LIKE '%@{domain}')
GROUP  BY status, order_type ORDER BY 1;
"""


def cmd_confirm(api: Api, args) -> None:
    banner("SHOP XỬ LÝ ĐƠN · chuyển đơn PENDING của khách mô phỏng sang CONFIRMED / COMPLETED")
    log("ORACLE", "App chưa có màn hình duyệt đơn nên bước này cập nhật thẳng trong Oracle; "
                  f"giữ lại ~{args.keep_pending}% đơn PENDING", "magenta")
    out = sql(CONFIRM_SQL.format(done=70, keep=args.keep_pending, domain=SIM_DOMAIN))
    for line in out.splitlines():
        if "|" in line:
            status, typ, n = line.split("|")
            log("ORACLE", f"  {typ:<10} {status:<10} {int(n):>6} đơn", "green")
    log("XONG", "Trang Báo cáo chỉ tính đơn CONFIRMED và COMPLETED", "bold")


def cmd_rebuild(api: Api, args, quiet: bool = False) -> None:
    if not quiet:
        banner("REDIS · dựng lại read model cho trang Báo cáo")
    admin = api.login(ADMIN_EMAIL, ADMIN_PASSWORD)
    log("REDIS", "Dựng lại read model báo cáo từ Oracle…", "magenta")
    r = api.call("POST", "/api/admin/reports/analytics/rebuild", {}, token=admin, timeout=900)
    log("REDIS", f"Read model: {r.get('orders', 0)} đơn, {r.get('lines', 0)} dòng hàng, "
                 f"{r.get('elapsedMs', 0) / 1000:.1f} s", "green")


def cmd_all(api: Api, args) -> None:
    t0 = time.perf_counter()
    cmd_reset(api, args)
    cmd_history(api, args)
    cmd_shop(api, args)
    cmd_flash(api, args)
    cmd_confirm(api, args)
    cmd_rebuild(api, args)
    banner(f"HOÀN TẤT trong {(time.perf_counter() - t0) / 60:.1f} phút")
    log("XEM", "http://localhost:5173/admin/reports · chọn \"Tháng này\" để thấy đơn vừa mô phỏng", "bold")


# ---------------------------------------------------------------- main

def main() -> None:
    p = argparse.ArgumentParser(description="Mô phỏng ShopFlash: reset, nạp lịch sử, mua hàng, flash sale.")
    p.add_argument("command", choices=["all", "reset", "history", "shop", "flash", "confirm", "rebuild"])
    p.add_argument("--base-url", default=os.environ.get("SHOPFLASH_API", "http://127.0.0.1:8080"))
    p.add_argument("--customers", type=int, default=1500, help="số khách mua hàng thường (mặc định 1500)")
    p.add_argument("--orders", type=int, default=4000, help="số đơn thường (mặc định 4000)")
    p.add_argument("--cancel-rate", type=float, default=0.06, help="tỉ lệ khách hủy đơn ngay sau khi đặt")
    p.add_argument("--buyers", type=int, default=2000, help="số người tranh flash sale (mặc định 2000)")
    p.add_argument("--items", type=int, default=4, help="số sản phẩm trong flash sale")
    p.add_argument("--quota", type=int, default=25, help="số suất mỗi sản phẩm")
    p.add_argument("--lead", type=int, default=75, help="mở bán sau bao nhiêu giây (backend yêu cầu > 60)")
    p.add_argument("--duration", type=int, default=30, help="chương trình kéo dài bao nhiêu phút")
    p.add_argument("--workers", type=int, default=24, help="số luồng song song khi mua hàng thường")
    p.add_argument("--stock", type=int, default=5000, help="tồn kho mỗi sản phẩm sau reset")
    p.add_argument("--keep-pending", type=int, default=5, help="phần trăm đơn giữ PENDING ở bước confirm")
    p.add_argument("--seed", type=int, default=2611)
    args = p.parse_args()

    api = Api(args.base_url)
    try:
        api.call("GET", "/api/health", timeout=5)
    except ApiError as e:
        sys.exit(f"Backend chưa chạy ở {args.base_url} ({e}). Chạy scripts\\dev.ps1 trước.")
    try:
        globals()[f"cmd_{args.command}"](api, args)
    except KeyboardInterrupt:
        sys.exit("\nĐã dừng.")
    except (ApiError, RuntimeError) as e:
        sys.exit(f"{C['red']}LỖI: {e}{C['end']}")


if __name__ == "__main__":
    main()
