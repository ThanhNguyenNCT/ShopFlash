-- Scene 7 — Oracle Isolation                                     EDITOR B (session đọc)
-- Một transaction READ COMMITTED duy nhất cho cả ba lần đọc: không COMMIT/ROLLBACK giữa STEP 3, 5, 7.
-- TXN_ID giống nhau ở ba lần đọc chứng minh vẫn là cùng một transaction của B.

-- ===== STEP 1 [B] Session B: SID phải khác session A =====
{{SESSION}}

-- ===== STEP 2 [B] Bắt đầu transaction READ COMMITTED =====
ROLLBACK;

SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- ===== STEP 3 [B] Đọc lần 1 =====
{{READ_P1}}

-- ===== STEP 5 [B] Đọc lần 2 — A đã UPDATE nhưng chưa COMMIT =====
{{READ_P1}}

-- ===== STEP 7 [B] Đọc lần 3 — sau COMMIT của A =====
{{READ_P1}}

-- ===== STEP 8 [B] Kết thúc transaction của B =====
ROLLBACK;
