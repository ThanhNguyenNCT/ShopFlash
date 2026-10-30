-- E4: do chi phi ghi. ARGV: thao_tac, muc_index_tu_lam, so_dong
--   thao_tac: THEM | HUY_THEM | SUA_GIA | HUY_SUA_GIA
--   muc_index_tu_lam: 0 = khong; 1 = idxlab:sku:{sku}; 2 = 1 + Sorted Set gia theo danh muc va chung (nhu E3)
-- THEM: tao document idxlab:product:200001.. (cung cong thuc voi Oracle). SUA_GIA: tang gia 1000 cho id 1..so_dong.
-- Index RediSearch (neu co) tu cap nhat trong chinh lenh ghi; index tu lam thi script nay phai tu cap nhat.
local kinds = {'Tai nghe', 'Dien thoai', 'Laptop', 'Chuot', 'Ban phim', 'Man hinh', 'Loa',
  'Sac du phong', 'Dong ho', 'May anh', 'Tivi', 'Noi chien', 'Quat', 'May loc khi', 'Balo'}
local brands = {'Sony', 'Samsung', 'Apple', 'Xiaomi', 'Asus', 'Dell', 'Logitech', 'Anker',
  'JBL', 'LG', 'Philips', 'Panasonic'}
local tags = {'Pro', 'Mini', 'Max', 'Lite', 'Plus', 'Ultra', 'Air', 'Gaming', 'Slim', 'Neo'}

local op, lvl, cnt = ARGV[1], tonumber(ARGV[2]), tonumber(ARGV[3])

local function sku(n) return string.format('LAB-%07d', n) end
local function cat(n) return (n * 7) % 50 + 1 end
local function price(n) return ((n * 7919) % 49951 + 50) * 1000 end
local function active(n) return n % 20 ~= 0 end

if op == 'THEM' then
  for n = 200001, 200000 + cnt do
    local doc = {
      id = n, sku = sku(n),
      name = kinds[n % 15 + 1] .. ' ' .. brands[math.floor(n / 15) % 12 + 1] .. ' '
        .. tags[math.floor(n / 180) % 10 + 1] .. ' ' .. string.format('%d', (n * 37) % 997),
      categoryId = cat(n), price = price(n),
      status = active(n) and 'ACTIVE' or 'INACTIVE', inStock = (n % 7 ~= 0)
    }
    redis.call('JSON.SET', 'idxlab:product:' .. n, '$', cjson.encode(doc))
    if lvl >= 1 then redis.call('SET', 'idxlab:sku:' .. sku(n), n) end
    if lvl >= 2 and active(n) then
      redis.call('ZADD', 'idxlab:price:cat:' .. cat(n), price(n), n)
      redis.call('ZADD', 'idxlab:price:active', price(n), n)
    end
  end
elseif op == 'HUY_THEM' then
  for n = 200001, 200000 + cnt do
    redis.call('UNLINK', 'idxlab:product:' .. n)
    if lvl >= 1 then redis.call('UNLINK', 'idxlab:sku:' .. sku(n)) end
    if lvl >= 2 and active(n) then
      redis.call('ZREM', 'idxlab:price:cat:' .. cat(n), n)
      redis.call('ZREM', 'idxlab:price:active', n)
    end
  end
elseif op == 'SUA_GIA' or op == 'HUY_SUA_GIA' then
  local delta = (op == 'SUA_GIA') and 1000 or -1000
  for n = 1, cnt do
    redis.call('JSON.NUMINCRBY', 'idxlab:product:' .. n, '$.price', delta)
    if lvl >= 2 and active(n) then
      local p = price(n) + ((op == 'SUA_GIA') and 1000 or 0)
      redis.call('ZADD', 'idxlab:price:cat:' .. cat(n), p, n)
      redis.call('ZADD', 'idxlab:price:active', p, n)
    end
  end
else
  return redis.error_reply('Thao tac khong hop le: ' .. tostring(op))
end
return cnt
