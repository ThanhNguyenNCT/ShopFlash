-- E3, khong index: SCAN moi document, giai ma JSON, loc theo dieu kien, xep theo gia trong Lua, lay 24 dau + dem.
-- ARGV: so_lan_tim, cau_hoi (A | B | C). Tra ve so ket qua cua lan tim cuoi.
--   A: categoryId = 5, gia 1-5 trieu, ACTIVE;  B: gia 1-5 trieu, ACTIVE;  C: categoryId = 5, ACTIVE
local k, q = tonumber(ARGV[1]), ARGV[2]
local function match(d)
  if d.status ~= 'ACTIVE' then return false end
  if (q == 'A' or q == 'C') and d.categoryId ~= 5 then return false end
  if (q == 'A' or q == 'B') and (d.price < 1000000 or d.price > 5000000) then return false end
  return true
end
local total = 0
for i = 1, k do
  local hits = {}
  local cursor = '0'
  repeat
    local res = redis.call('SCAN', cursor, 'MATCH', 'idxlab:product:*', 'COUNT', 1000)
    cursor = res[1]
    for _, key in ipairs(res[2]) do
      local d = cjson.decode(redis.call('JSON.GET', key))
      if match(d) then hits[#hits + 1] = { d.price, d.id } end
    end
  until cursor == '0'
  table.sort(hits, function(a, b) return a[1] < b[1] end)
  local page = {}
  for j = 1, math.min(24, #hits) do page[j] = hits[j][2] end
  total = #hits
end
return total
