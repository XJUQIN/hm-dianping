package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.ibatis.annotations.Select;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryShopType() {
        String key=CACHE_SHOPTYPE_KEY;
        //查询redis中商铺类型
        Set<String> shopTypeStr = stringRedisTemplate.opsForZSet().range(key, 0, -1);

        //判断是否存在
        if(shopTypeStr != null && !shopTypeStr.isEmpty()){
            //存在，返回
            List<ShopType> shopTypes=new ArrayList<>();
            //循环遍历将string转为shopType
            for (String type : shopTypeStr) {
                shopTypes.add(JSONUtil.toBean(type,ShopType.class));
            }
            return Result.ok(shopTypes);
        }

        //不存在，去数据库查询
        List<ShopType> list = list();
        //数据库不存在，返回报错
        if (list.isEmpty()) {
            return Result.fail("商铺种类不存在");
        }
        //数据库存在，写入redis
       Set<ShopType> shopType = new HashSet<>(list);
        for (ShopType type : list) {
            stringRedisTemplate.opsForZSet().add(key,JSONUtil.toJsonStr(type),type.getSort());
        }
        //返回
        return Result.ok(list);
    }
}
