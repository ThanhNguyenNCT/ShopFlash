-- B5: kiểm tra–ghi quota trong một lần EVAL, trên duy nhất key fixture.
local limit = tonumber(redis.call('HGET', KEYS[1], 'limit'))
local remaining = tonumber(redis.call('HGET', KEYS[1], 'remaining'))
local held = tonumber(redis.call('HGET', KEYS[1], 'held'))
if not limit or not remaining or not held or remaining < 0 or held < 0 or remaining + held ~= limit then
  return redis.error_reply('INVALID_QUOTA_STATE')
end
if remaining == 0 then
  return redis.error_reply('QUOTA_EXHAUSTED')
end
redis.call('HINCRBY', KEYS[1], 'remaining', -1)
redis.call('HINCRBY', KEYS[1], 'held', 1)
return remaining - 1
