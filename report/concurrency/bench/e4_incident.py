"""E4 của mục 3.5: sự cố giữa Redis và Oracle. Mỗi bước in trạng thái đối chiếu của chương trình 9101.

    python report/concurrency/bench/e4_incident.py a1            # giữ 1 suất, in mã suất; sau đó TẮT backend
    (bật lại backend)
    python report/concurrency/bench/e4_incident.py a2 <mã suất>  # đợi quá hạn, job dọn phải trả suất
    python report/concurrency/bench/e4_incident.py b [NNN MMM]   # bán 1 suất (khách ccNNN), giữ 1 suất (ccMMM), xóa key Redis, dựng lại

Cần backend chạy profile dev và Redis trong docker compose (bước b gọi redis-cli trong container).
Bước b tạo một đơn flash sale thật trên dữ liệu dev (sản phẩm 9103, mặc định khách cc500). Mỗi khách chỉ mua được
1 suất, nên chạy lại thì đổi khách: e4_incident.py b 496 495.
"""

import subprocess
import sys
import time
from datetime import datetime

import requests

BASE = "http://127.0.0.1:8080"
PASSWORD = "ShopFlash123!"
SALE = 9101


def log(text=""):
    print(text, flush=True)


def login(email):
    r = requests.post(f"{BASE}/api/auth/login", json={"email": email, "password": PASSWORD}, timeout=30)
    r.raise_for_status()
    return {"Authorization": f"Bearer {r.json()['accessToken']}"}


def show(admin, title, item_id=None):
    r = requests.get(f"{BASE}/api/admin/flash-sales/{SALE}/runtime", headers=admin, timeout=30)
    r.raise_for_status()
    data = r.json()
    log(f"--- {title} (giờ server {data['serverTime']}, Oracle {data['status']}, runtimeLoaded={data['runtimeLoaded']})")
    for item in data["items"]:
        if item_id is None or item["itemId"] == item_id:
            log(f"    item {item['itemId']}: allocated={item['allocated']} sold(Oracle)={item['sold']} "
                f"remaining(Redis)={item['remaining']} held={item['held']} "
                f"pending={item['pendingReservations']} inSync={item['inSync']}")
    log(f"    v_inventory_invariant_check: {len(data['invariantViolations'])} dòng lệch")
    return data


def reserve(customer, item_id):
    r = requests.post(f"{BASE}/api/flash-sales/{SALE}/items/{item_id}/reservations", json={"quantity": 1},
                      headers=customer, timeout=30)
    return r.status_code, r.json()


def reservation_status(customer, reservation_id):
    r = requests.get(f"{BASE}/api/flash-sales/reservations/{reservation_id}", headers=customer, timeout=30)
    return r.json().get("status") if r.status_code == 200 else f"HTTP {r.status_code} {r.json()['error']['code']}"


def step_a1():
    admin, customer = login("admin@shopflash.local"), login("cc498@shopflash.local")
    item = 9102
    show(admin, "A0. Trước khi giữ suất", item)
    code, body = reserve(customer, item)
    log(f"A1. Khách cc498 giữ 1 suất item {item}: HTTP {code}, reservationId={body['reservationId']}, "
        f"expiresAt={body['expiresAt']}")
    show(admin, "A1. Sau khi giữ suất", item)
    log("Bây giờ tắt backend (kill tiến trình), bật lại, rồi chạy: e4_incident.py a2 " + body["reservationId"])


def step_a2(reservation_id):
    admin, customer = login("admin@shopflash.local"), login("cc498@shopflash.local")
    item = 9102
    log(f"A2. Backend đã bật lại. Suất {reservation_id}: {reservation_status(customer, reservation_id)}")
    show(admin, "A2. Ngay sau khi backend bật lại", item)
    r = requests.get(f"{BASE}/api/flash-sales/reservations/{reservation_id}", headers=customer, timeout=30).json()
    expires = datetime.fromisoformat(r["expiresAt"])
    wait = (expires - datetime.now(expires.tzinfo)).total_seconds() + 12
    if wait > 0:
        log(f"    đợi {wait:.0f} s cho quá hạn giữ suất và job dọn (mỗi 5 s) chạy ...")
        time.sleep(wait)
    log(f"A3. Sau hạn giữ suất. Suất {reservation_id}: {reservation_status(customer, reservation_id)}")
    show(admin, "A3. Sau khi job dọn chạy", item)


def redis_cli(*args):
    out = subprocess.run(["docker", "compose", "exec", "-T", "redis", "redis-cli", "-e", *args],
                         capture_output=True, text=True, check=True)
    return out.stdout.strip()


def step_b(b="500", h="499"):
    admin = login("admin@shopflash.local")
    buyer, holder, late = login(f"cc{b}@shopflash.local"), login(f"cc{h}@shopflash.local"), login("cc497@shopflash.local")
    item = 9103
    show(admin, "B0. Ban đầu", item)

    code, body = reserve(buyer, item)
    r = requests.post(f"{BASE}/api/flash-sales/reservations/{body['reservationId']}/checkout", headers=buyer, timeout=30,
                      json={"recipientName": "Khách E4", "recipientPhone": "0900000500", "shippingAddress": "Thí nghiệm E4"})
    log(f"B1. Khách cc{b} giữ suất (HTTP {code}) và đặt đơn: HTTP {r.status_code}, "
        f"{r.json().get('orderCode', r.json())}")
    code, held = reserve(holder, item)
    log(f"B2. Khách cc{h} giữ 1 suất, chưa đặt đơn: HTTP {code}, reservationId={held['reservationId']}")
    show(admin, "B2. Một suất đã bán, một suất đang giữ", item)

    keys = [f"flash:sale:{SALE}"] + [f"flash:item:{i}{suffix}" for i in (9101, 9102, 9103)
                                      for suffix in ("", ":user_qty", ":pending")]
    log(f"B3. Xóa key runtime trên Redis: UNLINK {len(keys)} key -> {redis_cli('UNLINK', *keys)} key bị xóa")
    show(admin, "B3. Sau khi mất key Redis", item)
    code, body = reserve(late, item)
    log(f"    Khách cc497 thử giữ suất lúc Redis thiếu key: HTTP {code} {body.get('error', {}).get('code', body)}")

    r = requests.post(f"{BASE}/api/admin/flash-sales/{SALE}/runtime/rebuild", headers=admin, timeout=30)
    log(f"B4. Dựng lại runtime từ Oracle: HTTP {r.status_code} {r.json()}")
    show(admin, "B4. Sau khi dựng lại", item)
    log(f"    Suất đang giữ dở của cc{h}: {reservation_status(holder, held['reservationId'])}")
    r = requests.post(f"{BASE}/api/flash-sales/reservations/{held['reservationId']}/checkout", headers=holder, timeout=30,
                      json={"recipientName": "Khách E4", "recipientPhone": "0900000499", "shippingAddress": "Thí nghiệm E4"})
    log(f"    cc{h} đặt đơn bằng suất đã bị bỏ: HTTP {r.status_code} {r.json().get('error', {}).get('code', r.json())}")
    code, body = reserve(buyer, item)
    log(f"    cc{b} (đã mua 1, giới hạn 1) giữ suất lần nữa: HTTP {code} {body.get('error', {}).get('code', body)}")
    code, body = reserve(late, item)
    log(f"    cc497 giữ suất sau khi dựng lại: HTTP {code}")
    if code in (200, 201):
        requests.delete(f"{BASE}/api/flash-sales/reservations/{body['reservationId']}", headers=late, timeout=30)
    show(admin, "B5. Cuối cùng (cc497 đã hủy giữ suất)", item)


if __name__ == "__main__":
    step = sys.argv[1] if len(sys.argv) > 1 else ""
    if step == "a1":
        step_a1()
    elif step == "a2" and len(sys.argv) == 3:
        step_a2(sys.argv[2])
    elif step == "b":
        step_b(*sys.argv[2:4])
    else:
        sys.exit(__doc__)
