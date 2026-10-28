-- A5: Redis không rollback HINCRBY/HSET khi LPUSH sai kiểu sau đó.
redis.call('HINCRBY', KEYS[1], 'on_hand', -1)
redis.call('HSET', KEYS[2], 'status', 'PENDING')
return redis.call('LPUSH', KEYS[2], 'impossible')
