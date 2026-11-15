-- Bắt đầu đặt đơn: PENDING -> CLAIMED nếu đúng chủ và chưa hết hạn.
-- Sau bước này job dọn không trả suất nữa cho tới khi quá expiresAt + 30 s (xem AGENTS.md của module).
--
-- KEYS[1] flash:reservation:{id}
-- ARGV[1] userId
--
-- Trả về {'CLAIMED', saleId, itemId, productId, quantity}, {'CONFIRMED'} khi đã đặt đơn xong trước đó,
-- {'ALREADY_CLAIMED'} khi đang có lần đặt đơn khác, {'EXPIRED'} hoặc {'NOT_FOUND'}.

local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
local r = redis.call('HMGET', KEYS[1], 'status', 'userId', 'expiresAt', 'saleId', 'itemId', 'productId', 'quantity')
if not r[1] or r[2] ~= ARGV[1] then
  return {'NOT_FOUND'}
end
if r[1] == 'CONFIRMED' then
  return {'CONFIRMED'}
end
if r[1] == 'CLAIMED' then
  return {'ALREADY_CLAIMED'}
end
if r[1] ~= 'PENDING' or now >= tonumber(r[3]) then
  return {'EXPIRED'}
end
redis.call('HSET', KEYS[1], 'status', 'CLAIMED', 'claimedAt', now)
return {'CLAIMED', r[4], r[5], r[6], r[7]}
