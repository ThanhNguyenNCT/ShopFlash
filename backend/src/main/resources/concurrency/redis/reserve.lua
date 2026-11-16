-- Giữ suất flash sale. Kiểm tra và trừ suất trong cùng một script nên không có race.
-- Script không rollback: mọi điều kiện kiểm tra xong mới ghi.
--
-- KEYS[1] flash:sale:{saleId}            KEYS[2] flash:item:{itemId}
-- KEYS[3] flash:item:{itemId}:user_qty   KEYS[4] flash:item:{itemId}:pending
-- KEYS[5] flash:reservation:{newId}      KEYS[6] flash:reservations:expiry
-- ARGV[1] userId  ARGV[2] quantity  ARGV[3] newId  ARGV[4] saleId  ARGV[5] itemId
--
-- Trả về {'CREATED', id, expiresAt, now} khi tạo mới, {'EXISTING', id, expiresAt, now} khi người dùng
-- đang có reservation còn hiệu lực (bấm "Mua ngay" hai lần), hoặc {mã lỗi} như {'SOLD_OUT'}.
-- Reservation cũ được đọc qua key dựng trong script (chỉ đúng với Redis đơn, không phải cluster).

local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
local userId, q, newId, saleId, itemId = ARGV[1], tonumber(ARGV[2]), ARGV[3], ARGV[4], ARGV[5]
if not q or q <= 0 then
  return {'INVALID_QUANTITY'}
end

local sale = redis.call('HMGET', KEYS[1], 'status', 'startAt', 'endAt', 'maxPerUser', 'reservationTtl')
if not sale[1] or (sale[1] ~= 'SCHEDULED' and sale[1] ~= 'ACTIVE') then
  return {'SALE_NOT_FOUND'}
end
if now < tonumber(sale[2]) then
  return {'SALE_NOT_STARTED'}
end
if now >= tonumber(sale[3]) then
  return {'SALE_ENDED'}
end

local item = redis.call('HMGET', KEYS[2], 'saleId', 'status', 'remaining', 'productId')
if not item[1] or item[1] ~= saleId or item[2] ~= 'ACTIVE' then
  return {'ITEM_UNAVAILABLE'}
end

-- Người dùng đã có reservation cho sản phẩm này: còn hiệu lực thì trả lại đúng reservation đó.
local existing = redis.call('HGET', KEYS[4], userId)
if existing then
  local oldKey = 'flash:reservation:' .. existing
  local old = redis.call('HMGET', oldKey, 'status', 'expiresAt', 'quantity')
  local oldExpires = tonumber(old[2])
  if old[1] == 'CLAIMED' or (old[1] == 'PENDING' and now < oldExpires) then
    return {'EXISTING', existing, oldExpires, now}
  end
  if old[1] == 'PENDING' then
    -- Hết hạn mà job dọn chưa kịp xử lý: trả suất ngay rồi mới giữ suất mới.
    local oldQ = tonumber(old[3])
    redis.call('HINCRBY', KEYS[2], 'remaining', oldQ)
    if redis.call('HINCRBY', KEYS[3], userId, -oldQ) <= 0 then
      redis.call('HDEL', KEYS[3], userId)
    end
    redis.call('HSET', oldKey, 'status', 'RELEASED', 'releasedAt', now)
    redis.call('EXPIRE', oldKey, 3600)
    redis.call('ZREM', KEYS[6], existing)
    item[3] = tostring(tonumber(item[3]) + oldQ)
  end
  redis.call('HDEL', KEYS[4], userId)
end

if tonumber(item[3]) < q then
  return {'SOLD_OUT'}
end
local bought = tonumber(redis.call('HGET', KEYS[3], userId) or '0')
if bought + q > tonumber(sale[4]) then
  return {'USER_LIMIT_REACHED'}
end

local expiresAt = now + tonumber(sale[5]) * 1000
redis.call('HINCRBY', KEYS[2], 'remaining', -q)
redis.call('HINCRBY', KEYS[3], userId, q)
redis.call('HSET', KEYS[4], userId, newId)
-- Không đặt TTL khi còn PENDING/CLAIMED: key tự mất thì không còn gì để hoàn suất.
redis.call('HSET', KEYS[5],
  'saleId', saleId, 'itemId', itemId, 'productId', item[4], 'userId', userId,
  'quantity', q, 'status', 'PENDING', 'createdAt', now, 'expiresAt', expiresAt)
redis.call('ZADD', KEYS[6], expiresAt, newId)
return {'CREATED', newId, expiresAt, now}
