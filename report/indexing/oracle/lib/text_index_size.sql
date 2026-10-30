-- In dung luong cac segment ma mot index Oracle Text tao ra (bang DR$<ten>$..., index va LOB cua chung).
-- &1 = ten index viet hoa, vd. IX_IDXLAB_NAME_TXT
SET VERIFY OFF
COLUMN segment_name FORMAT A32
COLUMN segment_type FORMAT A16
COLUMN thuoc_bang FORMAT A28
WITH tabs AS (
  SELECT table_name FROM user_tables WHERE table_name LIKE 'DR$' || '&1' || '$%'
), segs AS (
  SELECT table_name AS segment_name, table_name AS thuoc_bang FROM tabs
  UNION ALL SELECT index_name, table_name FROM user_indexes WHERE table_name IN (SELECT table_name FROM tabs)
  UNION ALL SELECT segment_name, table_name FROM user_lobs WHERE table_name IN (SELECT table_name FROM tabs)
  UNION ALL SELECT index_name, table_name FROM user_lobs WHERE table_name IN (SELECT table_name FROM tabs)
)
SELECT s.segment_name, us.segment_type, s.thuoc_bang, ROUND(us.bytes / 1024 / 1024, 2) AS mb
FROM segs s JOIN user_segments us ON us.segment_name = s.segment_name
ORDER BY us.bytes DESC;

SELECT ROUND(SUM(us.bytes) / 1024 / 1024, 2) AS tong_mb
FROM user_segments us
WHERE us.segment_name IN (
  SELECT table_name FROM user_tables WHERE table_name LIKE 'DR$' || '&1' || '$%'
  UNION ALL SELECT index_name FROM user_indexes WHERE table_name LIKE 'DR$' || '&1' || '$%'
  UNION ALL SELECT segment_name FROM user_lobs WHERE table_name LIKE 'DR$' || '&1' || '$%'
  UNION ALL SELECT index_name FROM user_lobs WHERE table_name LIKE 'DR$' || '&1' || '$%'
);
