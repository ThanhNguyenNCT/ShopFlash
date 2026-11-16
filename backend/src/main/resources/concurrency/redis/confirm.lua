-- Oracle đã commit đơn: CLAIMED -> CONFIRMED. Suất giữ nguyên vì đã bán; user_qty giữ nguyên vì đã mua.
--
-- KEYS[1] flash:reservation:{id}   KEYS[2] flash:item:{itemId}:pending   KEYS[3] flash:reservations:expiry
-- ARGV[1] reservationId
--
-- Trả về 1 khi đổi trạng thái, 0 khi reservation không còn ở CLAIMED (đã xử lý hoặc đã bị dựng lại runtime).

local r = redis.call('HMGET', KEYS[1], 'status', 'userId')
if r[1] ~= 'CLAIMED' then
  return 0
end
local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
redis.call('HSET', KEYS[1], 'status', 'CONFIRMED', 'confirmedAt', now)
redis.call('EXPIRE', KEYS[1], 3600)
redis.call('ZREM', KEYS[3], ARGV[1])
if redis.call('HGET', KEYS[2], r[2]) == ARGV[1] then
  redis.call('HDEL', KEYS[2], r[2])
end
return 1
