-- E2, RediSearch TEXT: FT.SEARCH "@name:(tu_1 tu_2)" LIMIT 0 24 tren idx:idxlab-e2.
-- Mot lenh tra ca tong so ket qua lan 24 document dau (xep theo diem lien quan).
-- ARGV: so_lan_tim, tu_1, tu_2 ('-' = khong co). Tra ve tong so ket qua cua lan tim cuoi.
local k, w1, w2 = tonumber(ARGV[1]), ARGV[2], ARGV[3]
local q = '@name:(' .. w1 .. ((w2 ~= '-') and (' ' .. w2) or '') .. ')'
local total = 0
for i = 1, k do
  local res = redis.call('FT.SEARCH', 'idx:idxlab-e2', q, 'LIMIT', '0', '24', 'TIMEOUT', '0')
  total = res[1]
end
return total
