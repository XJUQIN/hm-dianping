package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //不符合返回错误信息
            return Result.fail("手机格式错误");
        }

        //符合生成验证码
        String code = RandomUtil.randomNumbers(6);

        //保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,CACHE_NULL_TTL, TimeUnit.MINUTES);
        //保存验证码到session
        //session.setAttribute("code",code);
        //发送验证码
        log.debug("发送验证码成功，验证码是：{}",code);
        //返回
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //检验手机号
        String phone=loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            //不符合返回错误信息
            return Result.fail("手机格式错误");
        }
        //从redis获取验证码校验验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);

        String code=loginForm.getCode();
        if(cacheCode==null || !cacheCode.equals(code)){
            //不一致，报错
            return Result.fail("手机验证码错误");
        }


        //一致，查询手机用户是否存在
        User user=query().eq("phone",phone).one();
        if(user == null){
            //不存在，创建新用户
            user=createUserWithPhone(phone);
        }

        //保存用户信息到redis
        //随机生成tokken作为登陆令牌
        String token= UUID.randomUUID().toString(true);
        //将user对象转为Hash存储
        UserDTO userDTO= BeanUtil.copyProperties(user, UserDTO.class);
        Map <String,Object> usermap=BeanUtil.beanToMap(userDTO);
        // 把所有value转为String
        Map<String, String> newMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : usermap.entrySet()) {
            if (entry.getValue() != null) {
                newMap.put(entry.getKey(), entry.getValue().toString());
            }
        }
        //存储
        String tokenKey=LOGIN_USER_KEY+token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,newMap);
        //设置token有效期
        stringRedisTemplate.expire(tokenKey,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //返回token
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //获取日期
        LocalDateTime now=LocalDateTime.now();
        //拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key=USER_SIGN_KEY+userId+keySuffix;
        //获取今天是本月第几天
        int dayOfMonth = now.getDayOfMonth();
        //写入redis
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //获取日期
        LocalDateTime now=LocalDateTime.now();
        //拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key=USER_SIGN_KEY+userId+keySuffix;
        //获取今天是本月第几天
        int dayOfMonth = now.getDayOfMonth();
        //获取本月截至今天为止的所有签到记录，返回的是一个十进制的数字
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if(result == null || result.isEmpty()){
            //没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if(num == null || num == 0){
            return Result.ok(0);
        }
        //循环遍历
        int count = 0;
        while (true){
            //让这个数字和1与运算，得到数字的最后一个bit位
            //判断这个bit位是否为0
            if((num&1) == 0 ){
                //如果为0，说明未签到，结束
                break;
            }else {
                //如果为1,说明已签到，计数+1
                count++;
            }
            //把数字右移一次
            num >>>=1;
        }

        return Result.ok(count);
    }

    private User createUserWithPhone(String phone){
        //创建用户
        User user=new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
