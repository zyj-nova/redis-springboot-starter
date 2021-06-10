package bjtu.zyj.redisspringbootstarter.service;


import bjtu.zyj.redisspringbootstarter.bean.Item;
import bjtu.zyj.redisspringbootstarter.bean.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.util.Random;
import java.util.UUID;

@Service
@Slf4j
public class OrderService {
    /**
     * 模拟商品数量固定的并发秒杀。入口应使用限流操作，确保并发下服务器不会崩溃
     * 此处将所有抢购商品置于一个redis hash，用单独的string也可但不利于管理
     *
     */
    @Autowired
    RedisTemplate<String, Serializable> redisTemplate;

    HashOperations<String,Integer,Integer> ho1;

    HashOperations<String,String,String> ho2;

    String key = "items";

    @PostConstruct
    public void init(){
        ho1 = redisTemplate.opsForHash();
        ho2 = redisTemplate.opsForHash();
    }

    public boolean addOrder(Item item) {
        if (!ho1.hasKey(key, item.getId())) {
            log.debug("商品不存在");
            return false;
        }
        // get()是多线程的，increment()/set()是单线程
        // 在并发下通过get()值判断商品当前剩余数量，是错误的
        // 此处在商品抢光后继续减为负数，从而计算总抢购人数。可按需修改
        if (ho1.increment(key, item.getId(), -1) < 0) {
            // log.debug("商品已被抢光");
            return false;
        }
        // 抢购成功，执行创建订单等操作
        // 商品已经抢到，为减轻服务器/数据库压力，可以将订单处理操作发送至消息队列异步处理
        try {
            Thread.sleep(20);
            ho2.put("user" ,UUID.randomUUID().toString(), key);
        } catch (InterruptedException ignored) {
        }

        return true;
    }
}
