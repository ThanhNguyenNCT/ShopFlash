-- E3, RediSearch: FT.SEARCH ... SORTBY price ASC LIMIT 0 24 NOCONTENT tren idx:idxlab-e3 (tra kem tong so).
-- ARGV: so_lan_tim, cau_hoi (A | B | C). Tra ve tong so ket qua cua lan tim cuoi.
local k, q = tonumber(ARGV[1]), ARGV[2]
local query
if q == 'A' then query = '@categoryId:[5 5] @price:[1000000 5000000] @status:{ACTIVE}'
elseif q == 'B' then query = '@price:[1000000 5000000] @status:{ACTIVE}'
else query = '@categoryId:[5 5] @status:{ACTIVE}' end
local total = 0
for i = 1, k do
  local res = redis.call('FT.SEARCH', 'idx:idxlab-e3', query, 'NOCONTENT', 'SORTBY', 'price', 'ASC',
    'LIMIT', '0', '24', 'TIMEOUT', '0')
  total = res[1]
end
return total
