-- E3, index tu lam: chon dung Sorted Set roi ZRANGE ... BYSCORE LIMIT 0 24 (trang dau) + ZCOUNT/ZCARD (tong so).
-- ARGV: so_lan_tim, cau_hoi (A | B | C). Tra ve so ket qua cua lan tim cuoi.
local k, q = tonumber(ARGV[1]), ARGV[2]
local total = 0
for i = 1, k do
  if q == 'A' then
    redis.call('ZRANGE', 'idxlab:price:cat:5', '1000000', '5000000', 'BYSCORE', 'LIMIT', '0', '24')
    total = redis.call('ZCOUNT', 'idxlab:price:cat:5', '1000000', '5000000')
  elseif q == 'B' then
    redis.call('ZRANGE', 'idxlab:price:active', '1000000', '5000000', 'BYSCORE', 'LIMIT', '0', '24')
    total = redis.call('ZCOUNT', 'idxlab:price:active', '1000000', '5000000')
  else
    redis.call('ZRANGE', 'idxlab:price:cat:5', '0', '23')
    total = redis.call('ZCARD', 'idxlab:price:cat:5')
  end
end
return total
