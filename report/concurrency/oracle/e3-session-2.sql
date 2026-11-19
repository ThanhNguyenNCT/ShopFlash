-- E3, phiên 2: bắt đầu sau phiên 1 khoảng 2 giây, đọc cùng giá trị, ghi và commit trước.
SET ECHO ON FEEDBACK ON
VARIABLE sold NUMBER

SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
BEGIN SELECT sold INTO :sold FROM cc_lab_items WHERE lab_item_id = 1; END;
/
PRINT sold
UPDATE cc_lab_items SET sold = :sold + 1, version = version + 1 WHERE lab_item_id = 1;
COMMIT;
EXIT
