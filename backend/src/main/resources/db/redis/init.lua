local function contains(value, expected)
  if type(value) == 'table' then
    for _, item in pairs(value) do
      if contains(item, expected) then
        return true
      end
    end
    return false
  end
  return tostring(value):lower() == expected:lower()
end

local function index_exists(name)
  for _, index_name in ipairs(redis.call('FT._LIST')) do
    local value = type(index_name) == 'table' and index_name.ok or index_name
    if value == name then
      return true
    end
  end
  return false
end

local function verify_index(name, required_tokens)
  local info = redis.call('FT.INFO', name)
  for _, token in ipairs(required_tokens) do
    if not contains(info, token) then
      error('Incompatible Redis index ' .. name .. ': missing ' .. token)
    end
  end
end

if not index_exists('idx:products') then
  redis.call(
    'FT.CREATE', 'idx:products', 'ON', 'JSON', 'PREFIX', '1', 'product:', 'SCHEMA',
    '$.name', 'AS', 'name', 'TEXT',
    '$.sku', 'AS', 'sku', 'TAG',
    '$.categoryId', 'AS', 'categoryId', 'NUMERIC',
    '$.categoryName', 'AS', 'categoryName', 'TAG',
    '$.price', 'AS', 'price', 'NUMERIC', 'SORTABLE',
    '$.status', 'AS', 'status', 'TAG',
    '$.inStock', 'AS', 'inStock', 'TAG'
  )
end

verify_index('idx:products', {
  'product:', 'name', 'sku', 'categoryId', 'categoryName', 'price',
  'status', 'inStock', 'TEXT', 'TAG', 'NUMERIC', 'SORTABLE'
})

if not index_exists('idx:order-analytics') then
  redis.call(
    'FT.CREATE', 'idx:order-analytics', 'ON', 'JSON', 'PREFIX', '1', 'order:analytics:', 'SCHEMA',
    '$.userId', 'AS', 'userId', 'NUMERIC',
    '$.status', 'AS', 'status', 'TAG',
    '$.orderType', 'AS', 'orderType', 'TAG',
    '$.totalAmount', 'AS', 'totalAmount', 'NUMERIC', 'SORTABLE',
    '$.createdAt', 'AS', 'createdAt', 'NUMERIC', 'SORTABLE',
    '$.items[*].productId', 'AS', 'itemProductId', 'NUMERIC',
    '$.items[*].categoryId', 'AS', 'itemCategoryId', 'NUMERIC',
    '$.items[*].quantity', 'AS', 'itemQuantity', 'NUMERIC',
    '$.items[*].lineTotal', 'AS', 'itemLineTotal', 'NUMERIC'
  )
end

verify_index('idx:order-analytics', {
  'order:analytics:', 'userId', 'status', 'orderType', 'totalAmount',
  'createdAt', 'itemProductId', 'itemCategoryId', 'itemQuantity',
  'itemLineTotal', 'TAG', 'NUMERIC', 'SORTABLE'
})

return 'Redis indexes are ready'
