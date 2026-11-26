-- Scene 4 — Oracle Atomicity & Error Handling                           EDITOR A (một session)
-- Nguồn: oracle/experiments/D1.sql — Checkout NORMAL hai mặt hàng; lỗi CHECK sau khi đã ghi
--        Order, Order Items, Inventory, Cart; quan sát trạng thái dở dang rồi full ROLLBACK.
-- DBeaver: Manual commit. Chạy từng câu bằng Ctrl+Enter. Không dùng Alt+X cho cả file.
-- Fixture {{TAG}}: P1={{P1}}, P2={{P2}}, P3={{P3}}, Cart={{CART_ID}}, User={{USER_ID}}.

-- ===== STEP 1 [A] BEFORE =====
{{SUMMARY}}

-- ===== STEP 2 [A] Checkout P1 + P2: ghi 4 bảng (chưa COMMIT) =====
INSERT INTO orders (order_code, user_id, order_type, status, subtotal, discount_amount, total_amount,
                    recipient_name, recipient_phone, shipping_address, idempotency_key, created_at, updated_at)
VALUES ('{{TAG}}_D1', {{USER_ID}}, 'NORMAL', 'PENDING', 200, 0, 200,
        'TX Research', '0900000000', 'Local fixture', '{{TAG}}:D1', SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO order_items (order_id, product_id, product_name, product_sku, unit_price, quantity, line_total)
SELECT o.order_id, p.product_id, p.name, p.sku, 100, 1, 100
FROM orders o JOIN products p ON p.product_id IN ({{P1}}, {{P2}}) AND p.status = 'ACTIVE' AND p.base_price = 100
WHERE o.order_code = '{{TAG}}_D1';

UPDATE product_inventory
SET on_hand_quantity = on_hand_quantity - 1, version = version + 1, updated_at = SYSTIMESTAMP
WHERE product_id IN ({{P1}}, {{P2}}) AND on_hand_quantity - reserved_flash_quantity >= 1;

DELETE FROM cart_items WHERE cart_id = {{CART_ID}} AND product_id IN ({{P1}}, {{P2}});

-- ===== STEP 3 [A] Statement lỗi: quantity P3 = 0 vi phạm CK_CART_ITEMS_QUANTITY =====
-- Lỗi ORA-02290 là dự kiến. Đừng ROLLBACK ở đây: quan sát STEP 4 trước.
UPDATE cart_items SET quantity = 0
WHERE cart_id = {{CART_ID}} AND product_id = {{P3}};

-- ===== STEP 4 [A] AFTER_FAILED_STATEMENT: các ghi trước lỗi vẫn còn trong transaction =====
{{SUMMARY}}

-- ===== STEP 5 [A] Full ROLLBACK =====
ROLLBACK;

-- ===== STEP 6 [A] AFTER_FULL_ROLLBACK: về đúng BEFORE =====
{{SUMMARY}}
