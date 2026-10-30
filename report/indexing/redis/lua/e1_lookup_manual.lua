-- E1, index tu lam: GET idxlab:sku:{sku} ra id, roi JSON.GET document. Thoi gian do ben ngoai (INFO commandstats).
local k = tonumber(ARGV[1])
for i = 1, k do
  local sku = string.format('LAB-%07d', (i * 997) % 200000 + 1)
  local id = redis.call('GET', 'idxlab:sku:' .. sku)
  if not id then return redis.error_reply('Khong co idxlab:sku:' .. sku) end
  local doc = redis.call('JSON.GET', 'idxlab:product:' .. id)
  if not doc then return redis.error_reply('Khong co document ' .. id) end
end
return k
