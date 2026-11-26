#!/usr/bin/env bash
# Demo phần 2.3 chạy thẳng trên hai DBMS (SQL*Plus trong container Oracle, redis-cli trong container Redis).
# Chạy từ thư mục gốc repo, stack Docker Compose đang chạy và đã có dữ liệu lịch sử (scripts/dev.sh nạp sẵn).
#
#   bash report/query/demo/dbms-demo.sh oracle        # top sản phẩm, Oracle tự chọn kế hoạch (AUTO)
#   bash report/query/demo/dbms-demo.sh oracle-nl     # cùng câu, ép nested loops bằng hint
#   bash report/query/demo/dbms-demo.sh xoa-thong-ke  # xóa thống kê 5 bảng rồi chạy lại: kế hoạch đổi
#   bash report/query/demo/dbms-demo.sh thu-thap      # thu thập lại thống kê rồi chạy lại: kế hoạch về như cũ
#   bash report/query/demo/dbms-demo.sh redis         # doanh thu theo danh mục bằng FT.AGGREGATE, kèm FT.PROFILE
#
# Mỗi bước Oracle in thời gian chạy (Elapsed) và kế hoạch thực thi thật: E-Rows là số dòng CBO ước lượng,
# A-Rows là số dòng thực tế, Buffers là số block đã đọc.
# Mật khẩu lấy từ biến môi trường trong container, không xuất hiện trên dòng lệnh.
set -euo pipefail
cd "$(dirname "$0")/../../.."

sqlplus_shop() {
  docker compose exec -T oracle bash -c \
    '{ printf "connect shop/%s@//localhost:1521/FREEPDB1\n" "$SHOP_PASSWORD"; cat; } | sqlplus -s -L /nolog'
}

# Câu SQL giống báo cáo top sản phẩm của backend; $1 là hint thêm vào sau GATHER_PLAN_STATISTICS.
top_products() {
  cat <<SQL
set linesize 200 pagesize 100 feedback off timing on
column product_name format a40 truncate
column category_name format a24 truncate
SELECT /*+ GATHER_PLAN_STATISTICS $1 */ p.product_id, p.name AS product_name, c.name AS category_name,
       SUM(oi.quantity) AS quantity, SUM(oi.line_total) AS revenue
FROM orders o
JOIN order_items oi ON oi.order_id = o.order_id
JOIN products p ON p.product_id = oi.product_id
JOIN categories c ON c.category_id = p.category_id
WHERE o.status IN ('CONFIRMED', 'COMPLETED')
  AND o.created_at >= TIMESTAMP '2025-11-01 00:00:00 +07:00'
  AND o.created_at <  TIMESTAMP '2026-10-19 00:00:00 +07:00'
GROUP BY p.product_id, p.name, c.name
ORDER BY quantity DESC, p.product_id
FETCH FIRST 5 ROWS ONLY;
set timing off
SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR(format => 'IOSTATS LAST -PREDICATE -NOTE'));
SQL
}

stats() {
  local proc=$1 extra=$2
  cat <<SQL
set feedback off
BEGIN
  FOR t IN (SELECT column_value AS name FROM TABLE(sys.odcivarchar2list('ORDERS','ORDER_ITEMS','PRODUCTS','CATEGORIES','USERS'))) LOOP
    DBMS_STATS.$proc(ownname => USER, tabname => t.name, $extra, no_invalidate => FALSE);
  END LOOP;
END;
/
SQL
}

case "${1:-}" in
  oracle)       top_products "" | sqlplus_shop ;;
  oracle-nl)    top_products "LEADING(o oi p c) USE_NL(oi p c)" | sqlplus_shop ;;
  xoa-thong-ke) { stats DELETE_TABLE_STATS "cascade_indexes => TRUE"; top_products ""; } | sqlplus_shop ;;
  thu-thap)     { stats GATHER_TABLE_STATS "cascade => TRUE"; top_products ""; } | sqlplus_shop ;;
  redis)
    Q='@status:{CONFIRMED|COMPLETED} @createdAt:[1761930000 (1792342800]'
    docker compose exec -T redis redis-cli FT.AGGREGATE idx:order-line-analytics "$Q" \
      GROUPBY 2 @categoryId @categoryName REDUCE SUM 1 @quantity AS quantity REDUCE SUM 1 @lineTotal AS revenue \
      SORTBY 4 @revenue DESC @categoryId ASC LIMIT 0 5
    echo '--- FT.PROFILE (thời gian tính bằng ms, phía server) ---'
    docker compose exec -T redis redis-cli FT.PROFILE idx:order-line-analytics AGGREGATE QUERY "$Q" \
      GROUPBY 2 @categoryId @categoryName REDUCE SUM 1 @quantity AS quantity REDUCE SUM 1 @lineTotal AS revenue \
      SORTBY 4 @revenue DESC @categoryId ASC LIMIT 0 5 | grep -A1 -iE 'total profile time|^Type$|GROUPBY|SORTBY' | head -20
    ;;
  *) sed -n '2,13p' "$0"; exit 1 ;;
esac
