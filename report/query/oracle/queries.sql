-- 3.3 Query processing: các câu SQL Oracle dùng trong demo (Phụ lục C).
-- Bản chạy thật nằm ở backend/.../modules/query/repository/oracle/OracleReportSql.java.
-- Tất cả tham số là bind variable. Đơn được tính: status CONFIRMED hoặc COMPLETED, created_at trong [fromTs, toExclusiveTs).
-- Khi lấy kế hoạch thực thi, backend thêm hint GATHER_PLAN_STATISTICS rồi gọi DISPLAY_CURSOR ở cuối file.

-- F5. Doanh thu theo tháng (1 bảng)
SELECT TO_CHAR(o.created_at AT TIME ZONE '+07:00', 'YYYY-MM') AS month,
       COUNT(*) AS order_count,
       SUM(o.total_amount) AS revenue
FROM orders o
WHERE o.status IN ('CONFIRMED', 'COMPLETED')
  AND o.created_at >= :fromTs
  AND o.created_at < :toExclusiveTs
GROUP BY TO_CHAR(o.created_at AT TIME ZONE '+07:00', 'YYYY-MM')
ORDER BY month;

-- F5. Doanh thu theo danh mục (join 4 bảng)
SELECT c.category_id,
       c.name AS category_name,
       SUM(oi.quantity) AS quantity,
       SUM(oi.line_total) AS revenue
FROM orders o
JOIN order_items oi ON oi.order_id = o.order_id
JOIN products p ON p.product_id = oi.product_id
JOIN categories c ON c.category_id = p.category_id
WHERE o.status IN ('CONFIRMED', 'COMPLETED')
  AND o.created_at >= :fromTs
  AND o.created_at < :toExclusiveTs
GROUP BY c.category_id, c.name
ORDER BY revenue DESC, c.category_id;

-- F6. Top sản phẩm bán chạy (join 4 bảng, Top-N)
SELECT p.product_id,
       p.name AS product_name,
       c.name AS category_name,
       SUM(oi.quantity) AS quantity,
       SUM(oi.line_total) AS revenue
FROM orders o
JOIN order_items oi ON oi.order_id = o.order_id
JOIN products p ON p.product_id = oi.product_id
JOIN categories c ON c.category_id = p.category_id
WHERE o.status IN ('CONFIRMED', 'COMPLETED')
  AND o.created_at >= :fromTs
  AND o.created_at < :toExclusiveTs
GROUP BY p.product_id, p.name, c.name
ORDER BY quantity DESC, p.product_id
FETCH FIRST :limit ROWS ONLY;

-- F6. Khách VIP: tổng chi lớn hơn mức trung bình (subquery trong HAVING)
SELECT u.user_id,
       u.full_name,
       u.email,
       COUNT(*) AS order_count,
       SUM(o.total_amount) AS total_spent
FROM orders o
JOIN users u ON u.user_id = o.user_id
WHERE o.status IN ('CONFIRMED', 'COMPLETED')
  AND o.created_at >= :fromTs
  AND o.created_at < :toExclusiveTs
GROUP BY u.user_id, u.full_name, u.email
HAVING SUM(o.total_amount) > (
    SELECT AVG(t.spent)
    FROM (SELECT SUM(o2.total_amount) AS spent
          FROM orders o2
          WHERE o2.status IN ('CONFIRMED', 'COMPLETED')
            AND o2.created_at >= :fromTs
            AND o2.created_at < :toExclusiveTs
          GROUP BY o2.user_id) t)
ORDER BY total_spent DESC, u.user_id
OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY;

-- F6. Sản phẩm chưa từng bán (NOT EXISTS)
SELECT p.product_id,
       p.name AS product_name,
       c.name AS category_name,
       p.base_price
FROM products p
JOIN categories c ON c.category_id = p.category_id
WHERE p.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.product_id = p.product_id)
ORDER BY p.product_id
OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY;

-- Demo 2 (ép phương pháp join). Hint đặt ngay sau SELECT:
--   doanh thu theo danh mục, top sản phẩm:  /*+ LEADING(o oi p c) USE_NL(oi p c) */   (USE_HASH / USE_MERGE tương tự)
--   khách VIP:                              /*+ LEADING(o u) USE_NL(u) */
-- LEADING cố định thứ tự join; thiếu nó CBO có thể đảo thứ tự và bỏ qua USE_NL.

-- Xem kế hoạch thực thi kèm số dòng thực tế (chạy ngay sau câu có GATHER_PLAN_STATISTICS, cùng session)
SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR(NULL, NULL, 'ALLSTATS LAST'));

-- Demo 3 (thống kê). Chạy cho từng bảng ORDERS, ORDER_ITEMS, PRODUCTS, CATEGORIES, USERS.
-- no_invalidate => FALSE: bỏ ngay các cursor đã parse; để mặc định thì Oracle dùng lại kế hoạch cũ.
BEGIN DBMS_STATS.DELETE_TABLE_STATS(ownname => USER, tabname => :tableName, cascade_indexes => TRUE, no_invalidate => FALSE); END;
/
BEGIN DBMS_STATS.GATHER_TABLE_STATS(ownname => USER, tabname => :tableName, cascade => TRUE, no_invalidate => FALSE); END;
/
SELECT table_name, num_rows, last_analyzed FROM user_tables
WHERE table_name IN ('ORDERS', 'ORDER_ITEMS', 'PRODUCTS', 'CATEGORIES', 'USERS');
