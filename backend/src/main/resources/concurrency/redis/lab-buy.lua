-- Concurrency Lab, chiến lược REDIS_LUA: kiểm tra và trừ trong một script, Redis chạy nguyên khối.
-- KEYS[1] = cclab:item (HASH allocated, remaining, version).
-- Trả 1 nếu mua được, 0 nếu hết suất, -1 nếu chưa reset (key chưa có).
local remaining = tonumber(redis.call('HGET', KEYS[1], 'remaining'))
if not remaining then
  return -1
end
if remaining <= 0 then
  return 0
end
redis.call('HINCRBY', KEYS[1], 'remaining', -1)
redis.call('HINCRBY', KEYS[1], 'version', 1)
return 1
