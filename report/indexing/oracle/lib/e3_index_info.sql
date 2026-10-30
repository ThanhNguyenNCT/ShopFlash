-- In hinh dang va dung luong cac index E3 dang co.
COLUMN index_name FORMAT A22
COLUMN cot FORMAT A32
SELECT i.index_name,
       (SELECT LISTAGG(column_name, ', ') WITHIN GROUP (ORDER BY column_position)
        FROM user_ind_columns c WHERE c.index_name = i.index_name) AS cot,
       i.blevel, i.leaf_blocks, i.clustering_factor, ROUND(s.bytes / 1024 / 1024, 1) AS mb
FROM user_indexes i JOIN user_segments s ON s.segment_name = i.index_name
WHERE i.table_name = 'IDXLAB_PRODUCTS' AND i.index_name LIKE 'IX\_E3\_%' ESCAPE '\'
ORDER BY i.index_name;
