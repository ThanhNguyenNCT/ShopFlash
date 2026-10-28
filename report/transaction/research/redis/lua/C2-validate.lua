-- C2: mô phỏng validation phải viết tường minh; không có FK/CHECK Redis tự sinh.
local product_id = redis.call('HGET', KEYS[1], 'product_id')
if not product_id then
  return redis.error_reply('MISSING_PRODUCT_REFERENCE')
end
if redis.call('EXISTS', KEYS[2]) == 0 then
  return redis.error_reply('INVALID_PRODUCT_REFERENCE')
end
local stock = tonumber(redis.call('HGET', KEYS[2], 'on_hand'))
if not stock or stock < 0 then
  return redis.error_reply('INVALID_STOCK')
end
return 'VALID'
