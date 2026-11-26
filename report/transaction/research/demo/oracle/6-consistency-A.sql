-- Scene 6 (phía Oracle) — Consistency do constraint khai báo                EDITOR A
-- Nguồn: lỗi CHECK trong oracle/experiments/D1.sql; metadata như oracle/experiments/C1.sql.
-- DBeaver: Manual commit. Chạy từng câu bằng Ctrl+Enter.

-- ===== STEP 1 [A] Định nghĩa CK_CART_ITEMS_QUANTITY đọc từ data dictionary =====
SELECT constraint_name, table_name, constraint_type, search_condition_vc,
       status, validated, deferrable, deferred
FROM user_constraints
WHERE constraint_name = 'CK_CART_ITEMS_QUANTITY';

-- ===== STEP 2 [A] (Tùy chọn) Oracle chặn ngay tại statement: ORA-02290, dữ liệu không đổi =====
UPDATE cart_items SET quantity = 0
WHERE cart_id = {{CART_ID}} AND product_id = {{P3}};

{{SUMMARY}}

-- ===== STEP 3 [A] Kết thúc transaction (không có gì để hoàn tác) =====
ROLLBACK;
