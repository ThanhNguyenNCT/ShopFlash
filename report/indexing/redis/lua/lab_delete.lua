-- Xoa moi key khop mau ARGV[1] (vd. idxlab:sku:*). Tra ve so key da xoa.
local cursor, total = '0', 0
repeat
  local res = redis.call('SCAN', cursor, 'MATCH', ARGV[1], 'COUNT', 1000)
  cursor = res[1]
  if #res[2] > 0 then total = total + redis.call('UNLINK', unpack(res[2])) end
until cursor == '0'
return total
