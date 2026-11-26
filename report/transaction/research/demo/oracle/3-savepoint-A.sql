-- Scene 3 — Oracle SAVEPOINT và partial rollback                         EDITOR A (một session)
-- Nguồn: oracle/experiments/A3.sql (cùng các UPDATE, cùng savepoint before_p2, cùng lỗi CHECK).
-- DBeaver: Manual commit. Chạy từng câu bằng Ctrl+Enter. Không dùng Alt+X cho cả file.
-- Fixture {{TAG}}: P1={{P1}}, P2={{P2}}, Cart={{CART_ID}}.

-- ===== STEP 1 [A] Trạng thái ban đầu: P1 và P2 = 100 =====
{{STOCK}}

-- ===== STEP 2 [A] UPDATE P1 = 99 =====
UPDATE product_inventory SET on_hand_quantity = 99, updated_at = SYSTIMESTAMP
WHERE product_id = {{P1}};

-- ===== STEP 3 [A] SAVEPOINT before_p2 =====
SAVEPOINT before_p2;

-- ===== STEP 4 [A] UPDATE P2 = 98, quan sát trong cùng session =====
UPDATE product_inventory SET on_hand_quantity = 98, updated_at = SYSTIMESTAMP
WHERE product_id = {{P2}};

{{STOCK}}

-- ===== STEP 5 [A] (Tùy chọn, như A3) Statement lỗi CHECK: chỉ câu lỗi bị hoàn tác =====
-- Lỗi ORA-02290 là dự kiến. P1 = 99, P2 = 98 vẫn giữ nguyên sau lỗi.
UPDATE cart_items SET quantity = 0
WHERE cart_id = {{CART_ID}} AND product_id = {{P1}};

{{STOCK}}

-- ===== STEP 6 [A] ROLLBACK TO SAVEPOINT: chỉ hoàn tác phần sau before_p2 =====
-- Bọc BEGIN/END: DBeaver coi câu mở đầu bằng ROLLBACK là lệnh rollback của nó và hoàn tác CẢ transaction.
BEGIN
  ROLLBACK TO SAVEPOINT before_p2;
END;
/

{{STOCK}}

-- ===== STEP 7 [A] Full ROLLBACK: hoàn tác toàn bộ transaction =====
ROLLBACK;

{{STOCK}}
