-- Dem key khop mau ARGV[1] va cong MEMORY USAGE cua chung. Tra ve {so_key, so_byte}.
local cursor, count, bytes = '0', 0, 0
repeat
  local res = redis.call('SCAN', cursor, 'MATCH', ARGV[1], 'COUNT', 1000)
  cursor = res[1]
  for _, key in ipairs(res[2]) do
    count = count + 1
    bytes = bytes + (redis.call('MEMORY', 'USAGE', key, 'SAMPLES', '0') or 0)
  end
until cursor == '0'
return {count, bytes}
