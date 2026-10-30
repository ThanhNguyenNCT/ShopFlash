-- Nap san pham thi nghiem idxlab:product:{n} voi n tu ARGV[1] den ARGV[2].
-- Cung cong thuc voi oracle/00_setup.sql nen du lieu hai ben giong het nhau.
-- Prefix idxlab: khong trung product: (projection that cua TV1) va order: (TV3).
local kinds = {'Tai nghe', 'Dien thoai', 'Laptop', 'Chuot', 'Ban phim', 'Man hinh', 'Loa',
  'Sac du phong', 'Dong ho', 'May anh', 'Tivi', 'Noi chien', 'Quat', 'May loc khi', 'Balo'}
local brands = {'Sony', 'Samsung', 'Apple', 'Xiaomi', 'Asus', 'Dell', 'Logitech', 'Anker',
  'JBL', 'LG', 'Philips', 'Panasonic'}
local tags = {'Pro', 'Mini', 'Max', 'Lite', 'Plus', 'Ultra', 'Air', 'Gaming', 'Slim', 'Neo'}

local s, e = tonumber(ARGV[1]), tonumber(ARGV[2])
for n = s, e do
  local doc = {
    id = n,
    sku = string.format('LAB-%07d', n),
    name = kinds[n % 15 + 1] .. ' ' .. brands[math.floor(n / 15) % 12 + 1] .. ' '
      .. tags[math.floor(n / 180) % 10 + 1] .. ' ' .. string.format('%d', (n * 37) % 997),
    categoryId = (n * 7) % 50 + 1,
    price = ((n * 7919) % 49951 + 50) * 1000,
    status = (n % 20 == 0) and 'INACTIVE' or 'ACTIVE',
    inStock = (n % 7 ~= 0)
  }
  redis.call('JSON.SET', 'idxlab:product:' .. n, '$', cjson.encode(doc))
end
return e - s + 1
