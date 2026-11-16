-- Ghi một trường của HASH chỉ khi key đã có (ví dụ status, endAt của flash:sale:{id}).
-- Không tạo hash thiếu trường: hash thiếu trường làm job tưởng runtime đã nạp và bỏ qua.
-- KEYS[1] hash; ARGV[1] field; ARGV[2] value. Trả 1 nếu đã ghi, 0 nếu key chưa có.
if redis.call('EXISTS', KEYS[1]) == 0 then
  return 0
end
redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
return 1
