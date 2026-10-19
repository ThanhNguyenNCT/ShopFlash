-- Returns a compact checkpoint for the entire ShopFlash Redis database.
-- It records counts only: no key name or document value is written to results.

local function count_pattern(pattern)
  local cursor = '0'
  local count = 0

  repeat
    local scan = redis.call('SCAN', cursor, 'MATCH', pattern, 'COUNT', 1000)
    cursor = scan[1]
    count = count + #scan[2]
  until cursor == '0'

  return count
end

return {
  'DBSIZE|' .. redis.call('DBSIZE'),
  'PRODUCT_DOCUMENTS|' .. count_pattern('product:*')
}
