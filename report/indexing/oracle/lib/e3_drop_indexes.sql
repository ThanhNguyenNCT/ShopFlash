-- Xoa moi index thi nghiem cua E3 tren bang lab (giu nguyen bang).
BEGIN
  FOR ix IN (SELECT index_name FROM user_indexes
             WHERE table_name = 'IDXLAB_PRODUCTS' AND index_name LIKE 'IX\_E3\_%' ESCAPE '\') LOOP
    EXECUTE IMMEDIATE 'DROP INDEX ' || ix.index_name;
  END LOOP;
END;
/
