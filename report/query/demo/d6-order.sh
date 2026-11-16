#!/usr/bin/env bash
# Demo D6 (nhất quán): chèn thẳng một đơn CONFIRMED vào Oracle, không qua read model Redis.
# Chạy từ thư mục gốc repo (Git Bash hoặc macOS/Linux), stack Docker Compose đang chạy.
#
#   bash report/query/demo/d6-order.sh insert   # chèn 1 đơn mã LAB-D6 (hôm nay, 1 sản phẩm)
#   bash report/query/demo/d6-order.sh delete   # xóa đơn LAB-D6
#
# Sau mỗi lệnh: trang Query Lab, tab Nhất quán, bấm Làm mới (thấy lệch 1 đơn), rồi Dựng lại read model (khớp lại).
# Mật khẩu SHOP lấy từ biến môi trường trong container, không xuất hiện trên dòng lệnh.
set -euo pipefail

case "${1:-}" in
  insert)
    SQL="
delete from order_items where order_id in (select order_id from orders where order_code = 'LAB-D6');
delete from orders where order_code = 'LAB-D6';
declare
  v_order number;
  v_user  number;
  v_prod  number;
  v_price number;
begin
  select min(user_id) into v_user from users where role = 'CUSTOMER' and status = 'ACTIVE';
  select product_id, base_price into v_prod, v_price
    from (select product_id, base_price from products where status = 'ACTIVE' order by product_id)
   where rownum = 1;
  insert into orders (order_code, user_id, order_type, status, subtotal, discount_amount, total_amount,
                      recipient_name, recipient_phone, shipping_address, idempotency_key, created_at, updated_at)
  values ('LAB-D6', v_user, 'NORMAL', 'CONFIRMED', v_price, 0, v_price,
          'Query Lab D6', '0900000000', 'Don demo nhat quan', 'lab-d6', systimestamp, systimestamp)
  returning order_id into v_order;
  insert into order_items (order_id, product_id, product_name, product_sku, unit_price, quantity, line_total)
  select v_order, product_id, name, sku, base_price, 1, base_price from products where product_id = v_prod;
  commit;
end;
/
select 'Da chen don LAB-D6, tong ' || total_amount || ' VND' as ket_qua from orders where order_code = 'LAB-D6';"
    ;;
  delete)
    SQL="
delete from order_items where order_id in (select order_id from orders where order_code = 'LAB-D6');
delete from orders where order_code = 'LAB-D6';
commit;
select 'Con ' || count(*) || ' don LAB-D6' as ket_qua from orders where order_code = 'LAB-D6';"
    ;;
  *)
    echo "Dùng: bash report/query/demo/d6-order.sh insert|delete" >&2
    exit 2
    ;;
esac

docker compose exec -T oracle bash -c '
  { printf "whenever sqlerror exit failure rollback\nset heading off feedback off\nconnect shop/%s@//localhost:1521/FREEPDB1\n" "$SHOP_PASSWORD"; cat; printf "\nexit\n"; } |
  sqlplus -s -L /nolog' <<< "$SQL"
