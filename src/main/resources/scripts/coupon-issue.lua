-- KEYS[1] = coupon:{id}:count
-- KEYS[2] = coupon:{id}:total
local total = redis.call('GET', KEYS[2])
if total == false then
    return -2
end

local current = redis.call('GET', KEYS[1])
if current == false then
    current = 0
else
    current = tonumber(current)
end

if current >= tonumber(total) then
    return -1
else
    return redis.call('INCR', KEYS[1])
end