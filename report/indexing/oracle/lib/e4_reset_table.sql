-- Dung boi e4_write.sql: tao lai bang IDXLAB_WRITE = ban sao 200.000 dong cua IDXLAB_PRODUCTS, chi co khoa chinh.
-- Moi cau hinh bat dau tu cung mot trang thai; cac index them sau do do e4_write.sql tao.
BEGIN
  EXECUTE IMMEDIATE 'DROP TABLE idxlab_write PURGE';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
CREATE TABLE idxlab_write AS SELECT * FROM idxlab_products;
ALTER TABLE idxlab_write ADD CONSTRAINT pk_idxlab_write PRIMARY KEY (product_id);
