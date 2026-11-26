-- Scene 1 — Oracle Transaction Lifecycle: COMMIT và ROLLBACK            EDITOR A (session giao dịch)
-- Nguồn: oracle/experiments/A1-commit.sql và A1-rollback.sql; khối PL/SQL được tách thành từng câu
--        lệnh với cùng bảng, cùng giá trị, cùng điều kiện tồn kho để quan sát từng mốc.
-- DBeaver: Manual commit. Chạy từng câu bằng Ctrl+Enter, theo số STEP; [B] nằm ở 1-lifecycle-B.sql.
-- Fixture {{TAG}}: P1={{P1}}, P2={{P2}}, P3={{P3}}, Cart={{CART_ID}}, User={{USER_ID}}.

-- ===== STEP 0 [A] Xác nhận session của editor A =====
{{SESSION}}

-- ===== STEP 1 [A] Trạng thái ban đầu =====
{{SUMMARY}}

-- ===== STEP 2 [A] Checkout P1: ghi Order, Order Item, trừ tồn, xóa dòng Cart (chưa COMMIT) =====
INSERT INTO orders (order_code, user_id, order_type, status, subtotal, discount_amount, total_amount,
                    recipient_name, recipient_phone, shipping_address, idempotency_key, created_at, updated_at)
VALUES ('{{TAG}}_A1C', {{USER_ID}}, 'NORMAL', 'PENDING', 100, 0, 100,
        'TX Research', '0900000000', 'Local fixture', '{{TAG}}:A1C', SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO order_items (order_id, product_id, product_name, product_sku, unit_price, quantity, line_total)
SELECT o.order_id, p.product_id, p.name, p.sku, 100, 1, 100
FROM orders o JOIN products p ON p.product_id = {{P1}} AND p.status = 'ACTIVE' AND p.base_price = 100
WHERE o.order_code = '{{TAG}}_A1C';

UPDATE product_inventory
SET on_hand_quantity = on_hand_quantity - 1, version = version + 1, updated_at = SYSTIMESTAMP
WHERE product_id = {{P1}} AND on_hand_quantity - reserved_flash_quantity >= 1;

DELETE FROM cart_items WHERE cart_id = {{CART_ID}} AND product_id = {{P1}};

-- ===== STEP 3 [A] Trạng thái trung gian — chính session A thấy thay đổi chưa COMMIT =====
{{SUMMARY}}

-- ===== STEP 4 [A] COMMIT =====
COMMIT;

-- ===== STEP 6 [A] Checkout P2 (chưa COMMIT) =====
INSERT INTO orders (order_code, user_id, order_type, status, subtotal, discount_amount, total_amount,
                    recipient_name, recipient_phone, shipping_address, idempotency_key, created_at, updated_at)
VALUES ('{{TAG}}_A1R', {{USER_ID}}, 'NORMAL', 'PENDING', 100, 0, 100,
        'TX Research', '0900000000', 'Local fixture', '{{TAG}}:A1R', SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO order_items (order_id, product_id, product_name, product_sku, unit_price, quantity, line_total)
SELECT o.order_id, p.product_id, p.name, p.sku, 100, 1, 100
FROM orders o JOIN products p ON p.product_id = {{P2}} AND p.status = 'ACTIVE' AND p.base_price = 100
WHERE o.order_code = '{{TAG}}_A1R';

UPDATE product_inventory
SET on_hand_quantity = on_hand_quantity - 1, version = version + 1, updated_at = SYSTIMESTAMP
WHERE product_id = {{P2}} AND on_hand_quantity - reserved_flash_quantity >= 1;

DELETE FROM cart_items WHERE cart_id = {{CART_ID}} AND product_id = {{P2}};

-- ===== STEP 7 [A] Trạng thái trung gian của Checkout P2 (chỉ session A thấy) =====
{{SUMMARY}}

{{ORDERS}}

-- ===== STEP 8 [A] ROLLBACK =====
ROLLBACK;

-- ===== STEP 9 [A] Trạng thái cuối trong session A =====
{{SUMMARY}}

{{ORDERS}}
