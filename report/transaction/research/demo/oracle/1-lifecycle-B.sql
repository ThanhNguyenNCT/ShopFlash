-- Scene 1 — Oracle Transaction Lifecycle                     EDITOR B (session độc lập, chỉ đọc)
-- Mở file này ở một SQL Editor KHÁC (DBeaver mặc định mỗi editor một kết nối riêng).
-- Chỉ chạy đúng STEP được nhắc trong 1-lifecycle-A.sql. Không COMMIT/ROLLBACK ở đây.

-- ===== STEP 0 [B] Xác nhận B là session khác A (SID khác) =====
{{SESSION}}

-- ===== STEP 3 [B] Session độc lập TRƯỚC COMMIT: chưa thấy Order, P1 vẫn 100 =====
{{SUMMARY}}

-- ===== STEP 5 [B] Session độc lập SAU COMMIT: thấy Order mới, P1 = 99, Cart mất P1 =====
{{SUMMARY}}

{{ORDERS}}

-- ===== STEP 9 [B] Sau ROLLBACK của A: Order thứ hai không tồn tại, P2 = 100, Order đã COMMIT vẫn còn =====
{{SUMMARY}}

{{ORDERS}}
