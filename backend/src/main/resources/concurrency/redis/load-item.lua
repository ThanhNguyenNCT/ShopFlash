-- Nạp hoặc dựng lại runtime của một sản phẩm trong chương trình từ số liệu Oracle, trong một bước nguyên tử.
-- Reservation đang chờ của sản phẩm bị bỏ (RELEASED, không cộng suất): suất đó đã được tính lại
-- trong remaining = allocated - sold, nghĩa là được mở bán lại. Oracle vẫn chặn bán vượt khi đặt đơn.
--
-- KEYS[1] flash:item:{itemId}   KEYS[2] flash:item:{itemId}:user_qty   KEYS[3] flash:item:{itemId}:pending
-- KEYS[4] flash:reservations:expiry
-- ARGV[1] saleId  ARGV[2] productId  ARGV[3] allocated  ARGV[4] remaining  ARGV[5] status
-- ARGV[6..] cặp userId, số lượng đã mua (đơn chưa hủy trong Oracle)
--
-- Trả về số reservation đang chờ đã bị bỏ.

local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
local dropped = 0
local pending = redis.call('HGETALL', KEYS[3])
for i = 1, #pending, 2 do
  local id = pending[i + 1]
  local key = 'flash:reservation:' .. id
  local status = redis.call('HGET', key, 'status')
  if status == 'PENDING' or status == 'CLAIMED' then
    redis.call('HSET', key, 'status', 'RELEASED', 'releasedAt', now, 'releasedBy', 'REBUILD')
    redis.call('EXPIRE', key, 3600)
    dropped = dropped + 1
  end
  redis.call('ZREM', KEYS[4], id)
end
redis.call('DEL', KEYS[1], KEYS[2], KEYS[3])
redis.call('HSET', KEYS[1], 'saleId', ARGV[1], 'productId', ARGV[2], 'allocated', ARGV[3],
  'remaining', ARGV[4], 'status', ARGV[5])
for i = 6, #ARGV, 2 do
  redis.call('HSET', KEYS[2], ARGV[i], ARGV[i + 1])
end
return dropped
