-- E3, index tu lam (bai 6): doc document ARGV[1]..ARGV[2], them vao Sorted Set (score = gia):
--   idxlab:price:cat:{categoryId}  - san pham dang ban cua tung danh muc
--   idxlab:price:active            - moi san pham dang ban
-- Dieu kien status = ACTIVE duoc "nuong san" vao index: san pham ngung ban khong duoc them.
local s, e = tonumber(ARGV[1]), tonumber(ARGV[2])
for n = s, e do
  local d = cjson.decode(redis.call('JSON.GET', 'idxlab:product:' .. n))
  if d.status == 'ACTIVE' then
    redis.call('ZADD', 'idxlab:price:cat:' .. d.categoryId, d.price, n)
    redis.call('ZADD', 'idxlab:price:active', d.price, n)
  end
end
return e - s + 1
