-- KEYS[1] = coupon:{id}:count
-- ARGV[1] = totalQuantity
local current = redis.call('GET', KEYS[1])
if current == false then
    current = 0
else
    current = tonumber(current)
end

if current >= tonumber(ARGV[1]) then
    return -1
else
    return redis.call('INCR', KEYS[1])
end