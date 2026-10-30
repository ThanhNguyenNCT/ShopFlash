-- E1, RediSearch: FT.SEARCH tren truong TAG sku cua idx:idxlab-e1. Thoi gian do ben ngoai (INFO commandstats).
local k = tonumber(ARGV[1])
for i = 1, k do
  local sku = string.format('LAB-%07d', (i * 997) % 200000 + 1)
  local q = '@sku:{' .. (string.gsub(sku, '%-', '\\-')) .. '}'
  local res = redis.call('FT.SEARCH', 'idx:idxlab-e1', q, 'LIMIT', '0', '1', 'TIMEOUT', '0')
  if res[1] ~= 1 then return redis.error_reply('FT.SEARCH tra ' .. tostring(res[1]) .. ' ket qua cho ' .. sku) end
end
return k
