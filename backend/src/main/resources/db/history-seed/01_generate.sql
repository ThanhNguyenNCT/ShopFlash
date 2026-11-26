-- Sinh dữ liệu benchmark cho module query (F5 doanh thu, F6 top bán chạy / khách VIP).
-- Script chạy tay, KHÔNG phải migration Flyway. Chạy lại được: xóa dữ liệu sinh cũ rồi sinh lại.
-- Tham số ở 00_params.sql. Cách chạy: xem README.md.
--
-- Ngẫu nhiên tái lập được: mọi giá trị "ngẫu nhiên" là ORA_HASH(số thứ tự, bucket, seed + k).
-- Không dùng DBMS_RANDOM vì chuỗi số của nó phụ thuộc thứ tự Oracle duyệt dòng (đổi theo plan),
-- còn ORA_HASH là hàm thuần của đầu vào: chạy lại ra đúng cùng dữ liệu, chỉ khác ID identity.
-- Mọi bước là INSERT ... SELECT theo tập (row generator CONNECT BY), không lặp từng dòng.
-- Số thứ tự đọc lại từ sku/email/order_code bằng TO_NUMBER(... DEFAULT NULL ON CONVERSION ERROR):
-- Oracle có thể tính TO_NUMBER trước bộ lọc LIKE, gặp dòng lõi như 'CORE-NORMAL-001' sẽ lỗi ORA-01722.
-- Không dùng /*+ APPEND */: bảng có khóa ngoại nên Oracle tự bỏ qua direct-path.

whenever sqlerror exit sql.sqlcode rollback
set define on verify off feedback on timing on serveroutput on
set lines 200 pages 100

@@00_params.sql

variable t0 varchar2(40)
exec :t0 := TO_CHAR(SYSTIMESTAMP, 'YYYY-MM-DD HH24:MI:SS.FF3 TZH:TZM')

@@98_delete_generated.sql

-- ---------------------------------------------------------------- 1. Danh mục
-- Không sinh danh mục: sản phẩm sinh nằm trong các danh mục thật của catalog dev (R__00_catalog_seed.sql).
prompt == Kiem tra catalog dev ==
DECLARE
  v_n NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_n FROM products WHERE sku LIKE 'SF-2%' AND thumbnail_url IS NOT NULL;
  IF v_n = 0 THEN
    RAISE_APPLICATION_ERROR(-20001, 'Chua co catalog dev (sku SF-2xxx). Chay backend profile dev mot lan truoc.');
  END IF;
END;
/

-- ---------------------------------------------------------------- 2. Sản phẩm
-- Mỗi sản phẩm sinh là một "mẫu" của một sản phẩm thật trong catalog (chọn theo hash): cùng danh mục, cùng ảnh,
-- tên "<tên gốc> · Mẫu 00123", giá lệch ±30% quanh giá gốc (tròn 1.000). Khoảng pct_inactive % INACTIVE.
-- Chữ có dấu viết bằng UNISTR để không phụ thuộc bảng mã của SQL*Plus.
prompt == Sinh products ==
INSERT INTO products (category_id, sku, name, slug, description, base_price, thumbnail_url, status, created_at, updated_at)
WITH src AS (
  SELECT category_id, name, description, base_price, thumbnail_url,
         ROW_NUMBER() OVER (ORDER BY product_id) k, COUNT(*) OVER () cnt
  FROM   products
  WHERE  sku LIKE 'SF-2%' AND thumbnail_url IS NOT NULL
), g AS (
  SELECT LEVEL n FROM dual CONNECT BY LEVEL <= &n_products
)
SELECT src.category_id,
       'gen-p-' || LPAD(g.n, 7, '0'),
       SUBSTR(src.name, 1, 180) || UNISTR(' \00B7 M\1EABu ') || LPAD(g.n, 5, '0'),
       'gen-p-' || LPAD(g.n, 7, '0'),
       src.description,
       GREATEST(1000, ROUND(src.base_price * (0.7 + ORA_HASH(g.n, 600, &seed + 12) / 1000), -3)),
       src.thumbnail_url,
       CASE WHEN ORA_HASH(g.n, 99, &seed + 13) < &pct_inactive THEN 'INACTIVE' ELSE 'ACTIVE' END,
       FROM_TZ(CAST(DATE '&start_date' - 180 AS TIMESTAMP), '+07:00')
         + NUMTODSINTERVAL(ORA_HASH(g.n, 180 * 86400 - 1, &seed + 14), 'SECOND'),
       FROM_TZ(CAST(DATE '&start_date' - 180 AS TIMESTAMP), '+07:00')
         + NUMTODSINTERVAL(ORA_HASH(g.n, 180 * 86400 - 1, &seed + 14), 'SECOND')
FROM   g
JOIN   src ON src.k = MOD(ORA_HASH(g.n, 4294967295, &seed + 11), src.cnt) + 1;

prompt == Sinh product_inventory ==
INSERT INTO product_inventory (product_id, on_hand_quantity, reserved_flash_quantity, version, updated_at)
SELECT product_id,
       ORA_HASH(TO_NUMBER(SUBSTR(sku, 7) DEFAULT NULL ON CONVERSION ERROR), 500, &seed + 15),
       0, 0, created_at
FROM   products
WHERE  sku LIKE 'gen-p-%';

-- ---------------------------------------------------------------- 3. Khách hàng
-- Email c0000001@gen.shopflash.local (chữ thường). password_hash là chuỗi giả dạng bcrypt, không đăng nhập được.
prompt == Sinh users (CUSTOMER) ==
INSERT INTO users (email, password_hash, full_name, phone, role, status, created_at, updated_at)
SELECT 'c' || LPAD(n, 7, '0') || '@gen.shopflash.local',
       '$2b$12$GENERATED.PLACEHOLDER.HASH.NOT.A.REAL.BCRYPT.VALUE.000000',
       CASE ORA_HASH(n, 9, &seed + 51)
         WHEN 0 THEN 'Nguyen' WHEN 1 THEN 'Tran' WHEN 2 THEN 'Le'   WHEN 3 THEN 'Pham' WHEN 4 THEN 'Hoang'
         WHEN 5 THEN 'Huynh'  WHEN 6 THEN 'Phan' WHEN 7 THEN 'Vu'   WHEN 8 THEN 'Vo'   ELSE 'Dang' END || ' ' ||
       CASE ORA_HASH(n, 7, &seed + 52)
         WHEN 0 THEN 'Van' WHEN 1 THEN 'Thi' WHEN 2 THEN 'Minh' WHEN 3 THEN 'Ngoc'
         WHEN 4 THEN 'Duc' WHEN 5 THEN 'Thu' WHEN 6 THEN 'Quoc' ELSE 'Gia' END || ' ' ||
       CASE ORA_HASH(n, 11, &seed + 53)
         WHEN 0 THEN 'An'   WHEN 1 THEN 'Binh' WHEN 2 THEN 'Chau' WHEN 3 THEN 'Dung' WHEN 4 THEN 'Giang'
         WHEN 5 THEN 'Hanh' WHEN 6 THEN 'Khoa' WHEN 7 THEN 'Linh' WHEN 8 THEN 'Nam'  WHEN 9 THEN 'Phuc'
         WHEN 10 THEN 'Quan' ELSE 'Trang' END,
       '09' || LPAD(ORA_HASH(n, 99999999, &seed + 54), 8, '0'),
       'CUSTOMER', 'ACTIVE',
       FROM_TZ(CAST(DATE '&start_date' - 300 AS TIMESTAMP), '+07:00')
         + NUMTODSINTERVAL(ORA_HASH(n, 300 * 86400 - 1, &seed + 55), 'SECOND'),
       FROM_TZ(CAST(DATE '&start_date' - 300 AS TIMESTAMP), '+07:00')
         + NUMTODSINTERVAL(ORA_HASH(n, 300 * 86400 - 1, &seed + 55), 'SECOND')
FROM  (SELECT LEVEL n FROM dual CONNECT BY LEVEL <= &n_customers);

-- ---------------------------------------------------------------- 4. Đơn hàng
-- Số đơn mỗi ngày theo trọng số: hệ số tháng (11, 12, 1 cao; 2 thấp), cuối tuần x1.15,
-- ngày 11/11 và 12/12 x2.5, xu hướng tăng 30% trong kỳ. Tổng đúng bằng n_orders.
-- Khách: pct_vip_orders % đơn rơi vào nhóm VIP (pct_vip_customers % khách), còn lại chia đều;
-- hạng khách đổi sang số thứ tự bằng hoán vị MOD(rank * 7919, n_customers) để VIP rải đều, không dồn đầu bảng.
-- subtotal/discount/total tạm là 0 (thỏa CHECK), cập nhật ở bước 6 sau khi có order_items.
prompt == Sinh orders ==
INSERT INTO orders (order_code, user_id, order_type, status, subtotal, discount_amount, total_amount,
                    recipient_name, recipient_phone, shipping_address, idempotency_key,
                    created_at, updated_at, cancelled_at)
WITH days AS (
  SELECT LEVEL - 1 d, DATE '&start_date' + LEVEL - 1 dt, DATE '&end_date' - DATE '&start_date' + 1 n_days
  FROM   dual
  CONNECT BY LEVEL <= DATE '&end_date' - DATE '&start_date' + 1
), weighted AS (
  SELECT d, dt,
         ROUND(CASE EXTRACT(MONTH FROM dt)
                 WHEN 11 THEN 1.35 WHEN 12 THEN 1.25 WHEN 1 THEN 1.45 WHEN 2 THEN 0.70
                 WHEN 3 THEN 0.85  WHEN 4 THEN 0.90  WHEN 5 THEN 0.95 WHEN 6 THEN 1.05
                 WHEN 7 THEN 0.90  WHEN 8 THEN 1.00  WHEN 9 THEN 1.10 ELSE 1.20 END
             * CASE WHEN TO_CHAR(dt, 'DY', 'NLS_DATE_LANGUAGE=AMERICAN') IN ('SAT', 'SUN') THEN 1.15 ELSE 1 END
             * CASE WHEN TO_CHAR(dt, 'MMDD') IN ('1111', '1212') THEN 2.5 ELSE 1 END
             * (1 + 0.3 * d / n_days), 6) w
  FROM   days
), cum AS (
  SELECT d, dt, FLOOR(&n_orders * SUM(w) OVER (ORDER BY d) / SUM(w) OVER ()) hi
  FROM   weighted
), per_day AS (
  SELECT d, dt, hi - NVL(LAG(hi) OVER (ORDER BY d), 0) c
  FROM   cum
), gen_orders AS (
  SELECT ROW_NUMBER() OVER (ORDER BY p.d, k.k) n, p.dt
  FROM   per_day p
  JOIN  (SELECT LEVEL k FROM dual CONNECT BY LEVEL <= (SELECT MAX(c) FROM per_day)) k
    ON   k.k <= p.c
), o AS (
  SELECT n,
         FROM_TZ(CAST(dt AS TIMESTAMP), '+07:00')
           + NUMTODSINTERVAL(ORA_HASH(n, 86399, &seed + 24), 'SECOND') created_at,
         ORA_HASH(n, 99, &seed + 23) status_bucket,
         MOD(FLOOR(ORA_HASH(n, 999999, &seed + 22) / 1000000
                   * CASE WHEN ORA_HASH(n, 99, &seed + 21) < &pct_vip_orders
                          THEN CEIL(&n_customers * &pct_vip_customers / 100)
                          ELSE &n_customers END) * 7919, &n_customers) + 1 cust_no
  FROM   gen_orders
), s AS (
  SELECT o.*,
         CASE WHEN status_bucket < 5  THEN 'PENDING'
              WHEN status_bucket < 30 THEN 'CONFIRMED'
              WHEN status_bucket < 92 THEN 'COMPLETED'
              ELSE 'CANCELLED' END status
  FROM   o
)
SELECT 'GEN-' || LPAD(s.n, 8, '0'),
       u.user_id,
       'NORMAL',
       s.status,
       0, 0, 0,
       u.full_name,
       u.phone,
       (1 + ORA_HASH(s.n, 298, &seed + 26)) || ' ' ||
       CASE ORA_HASH(s.n, 7, &seed + 27)
         WHEN 0 THEN 'Ly Thuong Kiet' WHEN 1 THEN 'Nguyen Trai'   WHEN 2 THEN 'Le Loi'
         WHEN 3 THEN 'Tran Hung Dao'  WHEN 4 THEN 'Hai Ba Trung'  WHEN 5 THEN 'Vo Van Tan'
         WHEN 6 THEN 'Dien Bien Phu'  ELSE 'Cach Mang Thang Tam' END || ', ' ||
       CASE ORA_HASH(s.n, 3, &seed + 28)
         WHEN 0 THEN 'TP. Ho Chi Minh' WHEN 1 THEN 'Ha Noi' WHEN 2 THEN 'Da Nang' ELSE 'Can Tho' END,
       'gen-' || LPAD(s.n, 8, '0'),
       s.created_at,
       CASE s.status
         WHEN 'PENDING'   THEN s.created_at
         WHEN 'CONFIRMED' THEN s.created_at + NUMTODSINTERVAL(1 + ORA_HASH(s.n, 59, &seed + 29), 'MINUTE')
         WHEN 'COMPLETED' THEN s.created_at + NUMTODSINTERVAL(24 + ORA_HASH(s.n, 119, &seed + 29), 'HOUR')
         ELSE s.created_at + NUMTODSINTERVAL(1 + ORA_HASH(s.n, 47, &seed + 25), 'HOUR') END,
       CASE WHEN s.status = 'CANCELLED'
            THEN s.created_at + NUMTODSINTERVAL(1 + ORA_HASH(s.n, 47, &seed + 25), 'HOUR') END
FROM   s
JOIN  (SELECT user_id, full_name, phone, TO_NUMBER(SUBSTR(email, 2, 7) DEFAULT NULL ON CONVERSION ERROR) cust_no
       FROM   users WHERE email LIKE '%@gen.shopflash.local') u
  ON   u.cust_no = s.cust_no;

-- ---------------------------------------------------------------- 5. Dòng đơn hàng
-- pct_unsold % sản phẩm (chọn theo hash) bị loại khỏi tập bán -> báo cáo "chưa bán" có dữ liệu.
-- Số dòng mỗi đơn 1..5 (25/30/25/12/8 %, TB ~2.5). Sản phẩm chọn theo hạng phổ biến
-- FLOOR(n_sold * u^popularity_skew) + 1 trên một hoán vị ngẫu nhiên của tập bán -> có top bán chạy rõ.
-- Trùng sản phẩm trong cùng đơn thì giữ dòng đầu. Giá chụp từ products.base_price.
prompt == Sinh order_items ==
INSERT INTO order_items (order_id, product_id, flash_sale_item_id, product_name, product_sku,
                         unit_price, quantity, line_total)
WITH p AS (
  SELECT product_id, name, sku, base_price, TO_NUMBER(SUBSTR(sku, 7) DEFAULT NULL ON CONVERSION ERROR) p_no
  FROM   products WHERE sku LIKE 'gen-p-%'
), p_flag AS (
  SELECT p.*, ROW_NUMBER() OVER (ORDER BY ORA_HASH(p_no, 4294967295, &seed + 30), p_no) unsold_rank
  FROM   p
), sold AS (
  SELECT product_id, name, sku, base_price,
         ROW_NUMBER() OVER (ORDER BY ORA_HASH(p_no, 4294967295, &seed + 31), p_no) pop_rank
  FROM   p_flag
  WHERE  unsold_rank > FLOOR(&n_products * &pct_unsold / 100)
), n_sold AS (
  SELECT COUNT(*) n_sold FROM sold
), o AS (
  SELECT order_id, TO_NUMBER(SUBSTR(order_code, 5) DEFAULT NULL ON CONVERSION ERROR) n
  FROM   orders WHERE order_code LIKE 'GEN-%'
), lines AS (
  -- pick_rank tính sẵn ở đây để bước sau là equi-join (hash join). Nếu để biểu thức chứa
  -- cột của sold ở vế phải điều kiện join, Oracle phải nested loop ~250k x 9k dòng (lần đầu mất 28 phút).
  SELECT o.order_id, o.n, k.k,
         FLOOR(c.n_sold * POWER(ORA_HASH(o.n * 8 + k.k, 999999, &seed + 33) / 1000000, &popularity_skew)) + 1 pick_rank,
         ORA_HASH(o.n * 8 + k.k, 99, &seed + 34) q_bucket
  FROM   o
  CROSS JOIN n_sold c
  JOIN  (SELECT LEVEL k FROM dual CONNECT BY LEVEL <= 5) k
    ON   k.k <= CASE WHEN ORA_HASH(o.n, 99, &seed + 32) < 25 THEN 1
                     WHEN ORA_HASH(o.n, 99, &seed + 32) < 55 THEN 2
                     WHEN ORA_HASH(o.n, 99, &seed + 32) < 80 THEN 3
                     WHEN ORA_HASH(o.n, 99, &seed + 32) < 92 THEN 4
                     ELSE 5 END
), picked AS (
  SELECT l.order_id, l.k, l.q_bucket, sp.product_id, sp.name, sp.sku, sp.base_price,
         ROW_NUMBER() OVER (PARTITION BY l.order_id, sp.product_id ORDER BY l.k) dup
  FROM   lines l
  JOIN   sold sp
    ON   sp.pop_rank = l.pick_rank
), q AS (
  SELECT picked.*,
         CASE WHEN q_bucket < 70 THEN 1 WHEN q_bucket < 90 THEN 2 WHEN q_bucket < 97 THEN 3
              WHEN q_bucket < 99 THEN 4 ELSE 5 END quantity
  FROM   picked
  WHERE  dup = 1
)
SELECT order_id, product_id, NULL, name, sku, base_price, quantity, base_price * quantity
FROM   q;

-- ---------------------------------------------------------------- 6. Tổng tiền đơn
-- subtotal = SUM(line_total); ~15% đơn có giảm giá 1..10% subtotal (làm tròn xuống 1.000).
prompt == Cap nhat subtotal/discount/total ==
MERGE INTO orders o
USING (
  SELECT order_id, subtotal,
         CASE WHEN ORA_HASH(n, 99, &seed + 41) < 15
              THEN FLOOR(subtotal * (1 + ORA_HASH(n, 9, &seed + 42)) / 100 / 1000) * 1000
              ELSE 0 END discount
  FROM  (SELECT x.order_id, TO_NUMBER(SUBSTR(x.order_code, 5) DEFAULT NULL ON CONVERSION ERROR) n, SUM(oi.line_total) subtotal
         FROM   orders x
         JOIN   order_items oi ON oi.order_id = x.order_id
         WHERE  x.order_code LIKE 'GEN-%'
         GROUP BY x.order_id, x.order_code)
) t
ON (o.order_id = t.order_id)
WHEN MATCHED THEN UPDATE SET
  o.subtotal        = t.subtotal,
  o.discount_amount = t.discount,
  o.total_amount    = t.subtotal - t.discount;

-- Commit một lần: xóa dữ liệu cũ + sinh mới nằm chung một transaction.
COMMIT;

-- ---------------------------------------------------------------- 7. Thống kê cho optimizer
prompt == Gather schema stats ==
exec DBMS_STATS.GATHER_SCHEMA_STATS(ownname => USER)

-- ---------------------------------------------------------------- 8. Tóm tắt
set timing off
prompt == Tom tat du lieu sinh ==
SELECT 'categories co san pham sinh' item, COUNT(DISTINCT category_id) value FROM products WHERE sku LIKE 'gen-%'
UNION ALL SELECT 'products', COUNT(*) FROM products WHERE sku LIKE 'gen-%'
UNION ALL SELECT 'products INACTIVE', COUNT(*) FROM products WHERE sku LIKE 'gen-%' AND status = 'INACTIVE'
UNION ALL SELECT 'products chua ban', COUNT(*) FROM products p
          WHERE p.sku LIKE 'gen-%' AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.product_id = p.product_id)
UNION ALL SELECT 'customers', COUNT(*) FROM users WHERE email LIKE '%@gen.shopflash.local'
UNION ALL SELECT 'customers co don', COUNT(DISTINCT user_id) FROM orders WHERE order_code LIKE 'GEN-%'
UNION ALL SELECT 'orders', COUNT(*) FROM orders WHERE order_code LIKE 'GEN-%'
UNION ALL SELECT 'orders co giam gia', COUNT(*) FROM orders WHERE order_code LIKE 'GEN-%' AND discount_amount > 0
UNION ALL SELECT 'order_items', COUNT(*) FROM order_items oi
          WHERE oi.order_id IN (SELECT order_id FROM orders WHERE order_code LIKE 'GEN-%');

SELECT status, COUNT(*) orders, ROUND(100 * RATIO_TO_REPORT(COUNT(*)) OVER (), 1) pct
FROM   orders WHERE order_code LIKE 'GEN-%'
GROUP BY status ORDER BY status;

SELECT TO_CHAR(MIN(created_at), 'YYYY-MM-DD HH24:MI:SS TZH:TZM') min_created_at,
       TO_CHAR(MAX(created_at), 'YYYY-MM-DD HH24:MI:SS TZH:TZM') max_created_at,
       TO_CHAR(SUM(CASE WHEN status IN ('CONFIRMED', 'COMPLETED') THEN total_amount END),
               'FM999,999,999,999,999') revenue_confirmed_completed
FROM   orders WHERE order_code LIKE 'GEN-%';

SELECT TO_CHAR(created_at AT TIME ZONE '+07:00', 'YYYY-MM') month, COUNT(*) orders
FROM   orders WHERE order_code LIKE 'GEN-%'
GROUP BY TO_CHAR(created_at AT TIME ZONE '+07:00', 'YYYY-MM') ORDER BY 1;

-- Dấu vân tay nội dung (không phụ thuộc ID identity): hai lần chạy phải ra cùng giá trị.
SELECT TO_CHAR(SUM(ORA_HASH(o.order_code || '|' || u.email || '|' || oi.product_sku || '|' || oi.quantity
                    || '|' || oi.unit_price || '|' || o.status || '|' || o.total_amount))) fingerprint
FROM   orders o
JOIN   users u ON u.user_id = o.user_id
JOIN   order_items oi ON oi.order_id = o.order_id
WHERE  o.order_code LIKE 'GEN-%';

SELECT COUNT(*) inventory_invariant_violations FROM v_inventory_invariant_check;

exec DBMS_OUTPUT.PUT_LINE('Tong thoi gian: ' || TO_CHAR(SYSTIMESTAMP - TO_TIMESTAMP_TZ(:t0, 'YYYY-MM-DD HH24:MI:SS.FF3 TZH:TZM')))
