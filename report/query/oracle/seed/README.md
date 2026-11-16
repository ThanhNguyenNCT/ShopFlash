# Dữ liệu benchmark cho module query (Oracle)

Script SQL*Plus sinh dữ liệu lớn cho báo cáo F5 (doanh thu) và F6 (top bán chạy, khách VIP).
Đây là script **chạy tay**, **không phải migration Flyway**. Không đặt vào `backend/src/main/resources/db/`.

## File

| File | Nội dung |
|---|---|
| `00_params.sql` | Tham số quy mô, khoảng ngày, tỉ lệ, seed |
| `01_generate.sql` | Xóa dữ liệu sinh cũ, sinh mới, cập nhật tổng tiền, gather stats, in tóm tắt |
| `98_delete_generated.sql` | Các lệnh DELETE dữ liệu sinh, không commit (01 gọi file này) |
| `99_cleanup.sql` | Chỉ xóa dữ liệu sinh rồi commit |

## Dữ liệu tạo ra (mặc định)

- 50 danh mục (`slug` `gen-cat-0001`…), 10.000 sản phẩm (`sku`/`slug` `gen-p-0000001`…), mỗi sản phẩm có một dòng `product_inventory` (reserved = 0, version = 0).
  Giá 50.000–30.000.000 VND (log-uniform, tròn 1.000), khoảng 5% INACTIVE.
- 20.000 khách hàng CUSTOMER/ACTIVE, email `c0000001@gen.shopflash.local`, password_hash là chuỗi giả dạng bcrypt (không đăng nhập được).
- 100.000 đơn NORMAL (`order_code` `GEN-00000001`, `idempotency_key` `gen-00000001`), `created_at` từ 2025-11-01 tới 2026-10-18 (+07:00).
  Số đơn theo tháng có chênh lệch (tháng 1 cao nhất, tháng 2 thấp nhất, ngày 11/11 và 12/12 tăng mạnh).
  Trạng thái ~ PENDING 5%, CONFIRMED 25%, COMPLETED 62%, CANCELLED 8%.
  25% số đơn dồn vào 5% khách (khách VIP), còn lại chia đều.
- Khoảng 248.000 order_items (1–5 dòng/đơn, trung bình 2,48). Đúng 10% sản phẩm không bao giờ được bán.
  Độ phổ biến sản phẩm lệch mạnh: sản phẩm bán chạy nhất chiếm ~1% tổng số lượng.
  `subtotal = SUM(line_total)`; ~15% đơn có giảm giá 1–10% subtotal.

Dữ liệu lõi (ID < 10000, từ `R__00_core_seed.sql`) không bị đụng tới.

## Cách chạy

Từ thư mục gốc repo, stack Docker Compose đang chạy và backend đã chạy ít nhất một lần (Flyway tạo bảng). Trên Git Bash
(macOS/Linux: bỏ `MSYS_NO_PATHCONV=1` và thay `"$(cygpath -w report/query/oracle/seed)"` bằng `report/query/oracle/seed`):

```bash
cid=$(docker compose ps -q oracle)
MSYS_NO_PATHCONV=1 docker exec -u 0 "$cid" rm -rf /tmp/seed
MSYS_NO_PATHCONV=1 docker cp "$(cygpath -w report/query/oracle/seed)" "$cid:/tmp/seed"
docker compose exec -T oracle bash -c 'printf "connect shop/%s@//localhost:1521/FREEPDB1\n@/tmp/seed/01_generate.sql\nexit\n" "$SHOP_PASSWORD" | sqlplus -s -L /nolog'
```

Mật khẩu lấy từ biến `$SHOP_PASSWORD` bên trong container, không xuất hiện trên dòng lệnh.
Sinh xong (hoặc xóa xong) phải dựng lại read model Redis, nếu không báo cáo nguồn Redis sẽ lệch Oracle: xem bước 3 trong `report/query/README.md`.
Chạy lại bao nhiêu lần cũng được: script xóa dữ liệu sinh cũ rồi sinh lại, ra đúng cùng dữ liệu (chỉ khác ID identity).
Xóa và sinh lại nằm trong **một transaction**, commit một lần ở cuối, nên phiên khác vẫn thấy bộ dữ liệu cũ cho tới khi commit.
Lỗi giữa chừng thì rollback toàn bộ.

Cuối script in: số dòng mỗi bảng, số sản phẩm chưa bán, đơn theo trạng thái, đơn theo tháng, min/max `created_at`,
doanh thu CONFIRMED + COMPLETED, dấu vân tay nội dung (so sánh giữa hai lần chạy) và số dòng của `v_inventory_invariant_check` (phải là 0).

## Thời gian trên máy này

Oracle AI Database 26ai Free 23.26.3 trong Docker, 100.000 đơn:

| Lần chạy | Tổng | Trong đó |
|---|---|---|
| Chạy lại khi đã có dữ liệu (xóa + sinh) | ~86 giây | xóa 41 s, sinh 31 s, gather stats 12 s |

Bước order_items từng mất 28 phút ở bản đầu vì điều kiện join chứa biểu thức có cột của cả hai bảng, Oracle buộc phải nested loop.
Bản hiện tại tính sẵn hạng sản phẩm để join bằng dấu `=` (hash join), còn 15 giây.

## Đổi quy mô

Sửa `00_params.sql`, ví dụ `define n_orders = 500000`. Các tham số khác: `n_categories`, `n_products`, `n_customers`,
`start_date`/`end_date`, `pct_unsold`, `pct_inactive`, `pct_vip_customers`, `pct_vip_orders`, `popularity_skew`, `seed`.
Giới hạn định dạng: tối đa 9.999.999 sản phẩm/khách, 99.999.999 đơn; `n_customers` không được là bội của 7919 (dùng cho hoán vị khách).

## Xóa dữ liệu sinh

```bash
docker compose exec -T oracle bash -c 'printf "connect shop/%s@//localhost:1521/FREEPDB1\n@/tmp/seed/99_cleanup.sql\nexit\n" "$SHOP_PASSWORD" | sqlplus -s -L /nolog'
```

Chỉ xóa dòng có dấu hiệu `GEN-`/`gen-`/`@gen.shopflash.local`. Nếu ứng dụng đã tạo giỏ hàng, đơn hay flash sale
tham chiếu tới dữ liệu sinh, DELETE sẽ báo lỗi khóa ngoại và rollback: các dòng đó phải xóa tay.
