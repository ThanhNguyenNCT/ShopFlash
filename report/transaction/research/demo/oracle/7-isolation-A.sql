-- Scene 7 — Oracle Isolation: READ COMMITTED không đọc bẩn          EDITOR A (session ghi)
-- Nguồn: oracle/experiments/two-session.py, hàm b1 (cùng câu lệnh, cùng thứ tự).
-- Mở 7-isolation-B.sql ở editor KHÁC. Chạy xen kẽ theo số STEP. Manual commit, Ctrl+Enter.
-- Fixture {{TAG}}: P1={{P1}}.

-- ===== STEP 1 [A] Session A: ghi lại SID =====
{{SESSION}}

-- ===== STEP 2 [A] Bắt đầu transaction READ COMMITTED =====
ROLLBACK;

SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- ===== STEP 4 [A] UPDATE P1 = 99, CHƯA COMMIT =====
UPDATE product_inventory SET on_hand_quantity = 99 WHERE product_id = {{P1}};

-- ===== STEP 6 [A] COMMIT =====
COMMIT;

-- ===== STEP 9 [A] (Không quay) Khôi phục P1 = 100 như B1 gốc =====
UPDATE product_inventory SET on_hand_quantity = 100 WHERE product_id = {{P1}};

COMMIT;
