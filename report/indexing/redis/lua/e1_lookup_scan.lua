-- E1, khong index: tim ARGV[1] SKU bang cach SCAN moi key idxlab:product:* va doc $.sku tung document.
-- Doc het (khong dung som) de giong Oracle TABLE ACCESS FULL. Thoi gian do ben ngoai (INFO commandstats).
local k = tonumber(ARGV[1])
for i = 1, k do
  local sku = string.format('LAB-%07d', (i * 997) % 200000 + 1)
  local want = '["' .. sku .. '"]'
  local found, cursor = 0, '0'
  repeat
    local res = redis.call('SCAN', cursor, 'MATCH', 'idxlab:product:*', 'COUNT', 1000)
    cursor = res[1]
    for _, key in ipairs(res[2]) do
      if redis.call('JSON.GET', key, '$.sku') == want then found = found + 1 end
    end
  until cursor == '0'
  if found ~= 1 then return redis.error_reply('SCAN: tim thay ' .. found .. ' document cho ' .. sku) end
end
return k
