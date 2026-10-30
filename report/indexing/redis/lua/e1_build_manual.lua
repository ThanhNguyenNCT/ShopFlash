-- E1, index tu lam (bai 6): tao key idxlab:sku:{sku} -> id cho san pham ARGV[1]..ARGV[2].
local s, e = tonumber(ARGV[1]), tonumber(ARGV[2])
for n = s, e do
  redis.call('SET', 'idxlab:sku:' .. string.format('LAB-%07d', n), n)
end
return e - s + 1
