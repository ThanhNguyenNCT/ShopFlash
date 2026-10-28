-- D2: precheck toàn bộ Cart/Inventory/quota trước lệnh ghi đầu tiên.
-- KEYS: cart, order, inventory P1, inventory P2, quota.
-- ARGV: user_id, p1_id, p2_id, quantity1, quantity2.
local q1, q2 = tonumber(ARGV[4]), tonumber(ARGV[5])
if not q1 or not q2 or q1 < 1 or q2 < 1 then
  return redis.error_reply('INVALID_QUANTITY')
end
if redis.call('EXISTS', KEYS[2]) ~= 0 then
  return redis.error_reply('ORDER_ALREADY_EXISTS')
end
local stock1 = tonumber(redis.call('HGET', KEYS[3], 'on_hand'))
local reserved1 = tonumber(redis.call('HGET', KEYS[3], 'reserved'))
local stock2 = tonumber(redis.call('HGET', KEYS[4], 'on_hand'))
local reserved2 = tonumber(redis.call('HGET', KEYS[4], 'reserved'))
if not stock1 or not reserved1 or stock1 - reserved1 < q1 then
  return redis.error_reply('OUT_OF_STOCK_P1')
end
if not stock2 or not reserved2 or stock2 - reserved2 < q2 then
  return redis.error_reply('OUT_OF_STOCK_P2')
end
local limit = tonumber(redis.call('HGET', KEYS[5], 'limit'))
local remaining = tonumber(redis.call('HGET', KEYS[5], 'remaining'))
local held = tonumber(redis.call('HGET', KEYS[5], 'held'))
if not limit or not remaining or not held or remaining < 1 or remaining + held ~= limit then
  return redis.error_reply('QUOTA_UNAVAILABLE')
end
if tonumber(redis.call('HGET', KEYS[1], ARGV[2])) ~= q1
   or tonumber(redis.call('HGET', KEYS[1], ARGV[3])) ~= q2 then
  return redis.error_reply('CART_MISMATCH')
end

redis.call('HSET', KEYS[2], 'user_id', ARGV[1], 'status', 'PENDING',
  'product_1', ARGV[2], 'product_2', ARGV[3], 'quantity_1', q1, 'quantity_2', q2)
redis.call('HINCRBY', KEYS[3], 'on_hand', -q1)
redis.call('HINCRBY', KEYS[4], 'on_hand', -q2)
redis.call('HDEL', KEYS[1], ARGV[2], ARGV[3])
redis.call('HINCRBY', KEYS[5], 'remaining', -1)
redis.call('HINCRBY', KEYS[5], 'held', 1)
return 'OK'
