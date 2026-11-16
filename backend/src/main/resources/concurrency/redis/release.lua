-- Trả suất của một reservation (bù trừ trong Try-Confirm-Cancel). Kiểm tra trạng thái ngay trong script nên
-- gọi trùng (hook rollback và job dọn cùng chạy) cũng chỉ trả suất đúng một lần.
--
-- KEYS[1] flash:reservation:{id}   KEYS[2] flash:item:{itemId}   KEYS[3] flash:item:{itemId}:user_qty
-- KEYS[4] flash:item:{itemId}:pending   KEYS[5] flash:reservations:expiry
-- ARGV[1] reservationId
-- ARGV[2] các trạng thái được phép trả, cách nhau bởi dấu phẩy, ví dụ "PENDING" hoặc "CLAIMED"
-- ARGV[3] graceMs: -1 là không xét giờ; >= 0 thì chỉ trả khi now >= expiresAt + graceMs
-- ARGV[4] userId của chủ reservation, rỗng là không kiểm tra chủ
--
-- Trả về 1 khi đã trả suất, 0 khi không đủ điều kiện (đã xử lý, chưa hết hạn, sai chủ).

local r = redis.call('HMGET', KEYS[1], 'status', 'userId', 'quantity', 'expiresAt')
if not r[1] then
  return 0
end
if not string.find(',' .. ARGV[2] .. ',', ',' .. r[1] .. ',', 1, true) then
  return 0
end
if ARGV[4] ~= '' and r[2] ~= ARGV[4] then
  return 0
end
local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
local grace = tonumber(ARGV[3])
if grace >= 0 and now < tonumber(r[4]) + grace then
  return 0
end

local q = tonumber(r[3])
-- Chỉ cộng suất khi reservation còn được ghi trong hash pending của sản phẩm. Runtime đã bị xóa (chương trình kết
-- thúc), bị mất hoặc đã dựng lại thì suất này đã nằm trong remaining = allocated - sold; cộng nữa là dư suất.
local registered = redis.call('HGET', KEYS[4], r[2]) == ARGV[1]
if registered and redis.call('EXISTS', KEYS[2]) == 1 then
  redis.call('HINCRBY', KEYS[2], 'remaining', q)
  if redis.call('HINCRBY', KEYS[3], r[2], -q) <= 0 then
    redis.call('HDEL', KEYS[3], r[2])
  end
end
redis.call('HSET', KEYS[1], 'status', 'RELEASED', 'releasedAt', now)
redis.call('EXPIRE', KEYS[1], 3600)
redis.call('ZREM', KEYS[5], ARGV[1])
if registered then
  redis.call('HDEL', KEYS[4], r[2])
end
return 1
