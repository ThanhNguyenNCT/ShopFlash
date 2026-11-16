-- Guarded Checkout NORMAL một mặt hàng. KEYS: cart, order, product, inventory.
-- ARGV: prefix, product_id, quantity. Tất cả validation trước ghi đầu tiên.
local function reject(code) return redis.error_reply(code) end
local function integer(s)
  if type(s) ~= 'string' or not string.match(s, '^%d+$') then return nil end
  local n = tonumber(s)
  if not n or n > 1000000000 or tostring(n) ~= s then return nil end
  return n
end
if #KEYS ~= 4 or #ARGV ~= 3 then return reject('INVALID_ARGUMENTS') end
local prefix, id = ARGV[1], ARGV[2]
if not string.match(prefix, '^txresearch:[a-z0-9]+:$') or not integer(id) or integer(id) < 1 then
  return reject('INVALID_PRODUCT_ID')
end
if KEYS[1] ~= prefix..'cart' or KEYS[2] ~= prefix..'order'
   or KEYS[3] ~= prefix..'product:'..id or KEYS[4] ~= prefix..'inventory:'..id then
  return reject('PRODUCT_INVENTORY_MISMATCH')
end
local types = {}
for i=1,4 do
  types[i] = redis.call('TYPE', KEYS[i]).ok
  if redis.call('PTTL', KEYS[i]) >= 0 then return reject('EXPIRING_KEY') end
end
if types[3] == 'none' then return reject('PRODUCT_NOT_FOUND') end
if types[1] ~= 'hash' or types[3] ~= 'hash' or types[4] ~= 'hash' then
  return reject('INVALID_KEY_TYPE')
end
if types[2] ~= 'none' then return reject('ORDER_ALREADY_EXISTS') end
if redis.call('HGET', KEYS[3], 'product_id') ~= id
   or redis.call('HGET', KEYS[3], 'status') ~= 'ACTIVE' then
  return reject('INVALID_PRODUCT')
end
local quantity = integer(ARGV[3])
if not quantity or quantity < 1 then return reject('INVALID_QUANTITY') end
local stock = integer(redis.call('HGET', KEYS[4], 'on_hand'))
local reserved = integer(redis.call('HGET', KEYS[4], 'reserved'))
local version = integer(redis.call('HGET', KEYS[4], 'version'))
if not stock or not reserved or not version or reserved > stock or version >= 1000000000 then
  return reject('INVALID_INVENTORY')
end
if stock - reserved < quantity then return reject('OUT_OF_STOCK') end
if integer(redis.call('HGET', KEYS[1], id)) ~= quantity then return reject('CART_MISMATCH') end
-- Không còn thao tác đọc/parse có thể lỗi sau mốc này.
redis.call('HSET', KEYS[2], 'product_id', id, 'quantity', quantity, 'status', 'PENDING')
redis.call('HSET', KEYS[4], 'on_hand', stock - quantity, 'version', version + 1)
redis.call('HDEL', KEYS[1], id)
return 'OK'
