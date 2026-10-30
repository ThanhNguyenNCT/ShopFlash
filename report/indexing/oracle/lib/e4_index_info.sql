-- In danh sach index dang co tren IDXLAB_WRITE (ca index Oracle Text).
COLUMN index_name FORMAT A26
COLUMN index_type FORMAT A12
COLUMN cot FORMAT A32
SELECT i.index_name, i.index_type,
       (SELECT LISTAGG(column_name, ', ') WITHIN GROUP (ORDER BY column_position)
        FROM user_ind_columns c WHERE c.index_name = i.index_name) AS cot
FROM user_indexes i WHERE i.table_name = 'IDXLAB_WRITE' ORDER BY i.index_name;
