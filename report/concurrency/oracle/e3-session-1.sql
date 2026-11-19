-- E3, phiên 1: đọc trước, ghi sau. Ở SERIALIZABLE, câu UPDATE gặp dòng đã bị phiên 2 sửa và commit
-- sau thời điểm transaction này bắt đầu, nên nhận ORA-08177. Chạy bằng e3-run.sh (hoặc dán tay vào cửa sổ 1).
SET ECHO ON FEEDBACK ON SERVEROUTPUT ON
VARIABLE sold NUMBER

SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
BEGIN SELECT sold INTO :sold FROM cc_lab_items WHERE lab_item_id = 1; END;
/
PRINT sold

-- Chờ 6 giây: trong lúc này phiên 2 đọc cùng giá trị, ghi và commit.
EXEC DBMS_SESSION.SLEEP(6)

-- Đọc lại vẫn thấy giá trị cũ (snapshot của transaction), dù phiên 2 đã commit.
SELECT sold AS sold_phien_1_thay FROM cc_lab_items WHERE lab_item_id = 1;

UPDATE cc_lab_items SET sold = :sold + 1, version = version + 1 WHERE lab_item_id = 1;
ROLLBACK;

-- Thử lại trong transaction mới: đọc được giá trị phiên 2 đã ghi, cập nhật không bị mất.
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
BEGIN SELECT sold INTO :sold FROM cc_lab_items WHERE lab_item_id = 1; END;
/
PRINT sold
UPDATE cc_lab_items SET sold = :sold + 1, version = version + 1 WHERE lab_item_id = 1;
COMMIT;

SELECT allocated, sold, version FROM cc_lab_items WHERE lab_item_id = 1;
EXIT
