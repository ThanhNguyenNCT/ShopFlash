-- E2, khong index: SCAN moi key idxlab:product:*, doc $.name tung document, kiem tra co chua tu khoa
-- (khong phan biet hoa thuong, giong UPPER(name) LIKE '%TU%'). Lay 24 ket qua dau va dem tong.
-- ARGV: so_lan_tim, tu_1, tu_2 ('-' = khong co). Tra ve so ket qua cua lan tim cuoi.
local k, w1, w2 = tonumber(ARGV[1]), string.lower(ARGV[2]), ARGV[3]
if w2 == '-' then w2 = nil else w2 = string.lower(w2) end
local total = 0
for i = 1, k do
  total = 0
  local page = {}
  local cursor = '0'
  repeat
    local res = redis.call('SCAN', cursor, 'MATCH', 'idxlab:product:*', 'COUNT', 1000)
    cursor = res[1]
    for _, key in ipairs(res[2]) do
      local name = string.lower(redis.call('JSON.GET', key, '$.name'))
      if string.find(name, w1, 1, true) and (not w2 or string.find(name, w2, 1, true)) then
        total = total + 1
        if #page < 24 then page[#page + 1] = key end
      end
    end
  until cursor == '0'
end
return total
