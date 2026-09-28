--1.优惠卷id
local voucherId = ARGV[1]
--1.2用户id
local userId = ARGV[2]
--1.3订单id
local orderId = ARGV[3]

--2.数据key
--2.1库存key
local stockKey = 'seckill:stock:'..voucherId
--2.2订单key
local orderKey = 'seckill:order:'..voucherId

--3.脚本业务
--3.1 安全获取库存，空值兜底为0
local stock = tonumber(redis.call('get',stockKey)) or 0
if stock <= 0 then
    --库存不足
    return 1
end
--3.2判断用户是否下单
if(redis.call('sismember',orderKey,userId) == 1 ) then
    --3.3 存在。说明是重复下单，返回2
    return 2
end
--3.4扣库存
redis.call('incrby',stockKey,-1)
--3.5下单
redis.call('sadd',orderKey,userId)
--发送消息到队列中
redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)
return 0

-- --1.优惠卷id
-- local voucherId = ARGV[1]
-- --1.2用户id
-- local userId = ARGV[2]
--
-- --2.数据key
-- --2.1库存key
-- local stockKey = 'seckill:stock:'..voucherId
-- --2.2订单key
-- local orderKey = 'seckill:order:'..voucherId
--
-- --3.脚本业务
-- --3.1判断库存是否充足
-- if(tonumber(redis.call('get',stockKey))<=0) then
--     --库存不足
--     return 1
-- end
-- --3.2判断用户是否下单
-- if(redis.call('sismember',orderKey,userId) == 1 ) then
--     --3.3 存在。说明是重复下单，返回2
--     return 2
-- end
-- --3.4扣库存
-- redis.call('incrby',stockKey,-1)
-- --3.5下单
-- redis.call('sadd',orderKey,userId)
-- return 0