## SpringBoot 2.5整合Redis 6

### 1. 配置文件

`application.properties`配置内容如下：

```properties
spring.redis.host=192.168.209.101
spring.redis.port=6379
spring.redis.password=fxgh
spring.redis.timeout=3000
spring.redis.database=0

spring.redis.jedis.pool.max-idle=8
spring.redis.jedis.pool.max-wait=-1
```

**注意**：密码即`redis.conf`中`requirepass`选项配置的密码。若没有开启，则忽略该配置项。

### 2. 注册Redis连接工具类

```java
package bjtu.zyj.redisspringbootstarter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import redis.clients.jedis.JedisPoolConfig;

import java.io.Serializable;

@Configuration
public class RedisConfig {

    @Value("${spring.redis.host}")
    private String host;

    @Value("${spring.redis.port}")
    private int port;

    //timeout for jedis try to connect to redis server, not expire time! In milliseconds
    @Value("${spring.redis.timeout}")
    private final int timeout = 0;

    @Value("${spring.redis.password}")
    private String password;

    @Value("${spring.redis.jedis.pool.max-idle}")
    private int maxIdle;

    @Value("${spring.redis.jedis.pool.max-wait}")
    private int maxWaitMills;

    @Value("${spring.redis.database}")
    private int databaseIndex;

    @Bean
    public JedisPoolConfig jedisPoolConfig(){
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxIdle(maxIdle);
        jedisPoolConfig.setMaxWaitMillis(maxWaitMills);
        return jedisPoolConfig;
    }

    @Bean
    public JedisConnectionFactory jedisConnectionFactory(JedisPoolConfig jedisPoolConfig){
        JedisClientConfiguration jedisClientConfiguration =JedisClientConfiguration
                .builder()
                .usePooling()
                .poolConfig(jedisPoolConfig)
                .build();
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host,port);
        config.setDatabase(databaseIndex);
        config.setPassword(RedisPassword.of(password));
        return new JedisConnectionFactory(config, jedisClientConfiguration);
    }

    @Bean
    public RedisCacheManager cacheManager(JedisConnectionFactory jedisConnectionFactory){
        return RedisCacheManager.create(jedisConnectionFactory);
    }

    @Bean
    public RedisTemplate<String, Serializable> redisTemplate(JedisConnectionFactory jedisConnectionFactory){
        RedisTemplate<String, Serializable> redisTemplate = new RedisTemplate<>();
        redisTemplate.setKeySerializer(new StringRedisSerializer());//若不配置key序列化，可能会出现key值的乱码
        redisTemplate.setValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));
        redisTemplate.setConnectionFactory(jedisConnectionFactory);
        return redisTemplate;
    }

}
```

* 添加`@Configuration`，使得Spring IOC容器帮我们自动执行添加了`@Bean`的方法，创应相应对象，容器管理这些对象，自动注入。

### 3.Redis的发布订阅模式

```shell
subscribe channel1 channel2 ...
publish channel 
```



### 4.添加基于内存的秒杀案例
秒杀基本流程：
* 先将要秒杀的商品商量读取到库存中
* 限流，避免瞬间涌入过高并发量造成服务器崩溃
* 减少库存，若减为0返回失败；否则抢购成功，将处理订单的消息发送给消息队列异步处理

### 5. 添加基于Redis的分布式锁
分布式锁出现的原因：当不同线程分布在多个JVM中时，Java的锁会无效，因为Java锁只能在一个JVM中工作。
基于Redis的分布式锁的解决方案：

**版本一**：

* `setnx key value`命令 只允许设置一次，相当于获取锁；`del key`释放锁
* 存在的问题：如果设置锁的主机宕机，锁永远不会释放

**版本二**：

给锁（key）设置一个过期时间，到时间自动释放
* `setnx key value, expire key 10`
* 上述两个命令不是原子命令，若设置完锁刚好宕机，过期命令不会被执行
* `set key value nx ex 10` Jedis中的方法为：`Boolean setIfAbsent(K key, V value, long timeout, TimeUnit unit);`

**版本三**：

防止误删，若上锁的线程A突然中断一会，锁到期释放，B线程获得锁，如果锁过期之后A才来释放锁，此时释放的锁是B加的锁
因此，不同线程设置锁(key)的值要唯一，删除之前需要判断锁的值是否和自己设置的相同

**版本四**：

上述先get后del是两条命令，仍然不是原子操作,设想如下问题：
* A线程判断锁的值和自己设置的相同，准备执行删除操作，但此时A线程中断了一会，中断期间，锁恰好到时过期自动释放
* B线程获取到了锁
* A线程接着回来执行删除操作，造成了锁的误删除

**版本五（终极方案）**：

利用Lua脚本保证命令的原子性，Lua脚本会提交到Redis执行队列，由于Redis单线程，这些脚本会依次顺序执行，中途不可以被打断，这就保证了原子性。主要利用了Redis单线程的特点（类似Redis的事务）。
```shell
if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1])
else return 0 end
```

总结：加锁和解锁必须具有原子性
### 6. Redis的过期键删除策略
* 定时删除
* 定期删除
* 惰性删除
* Redis缓存淘汰策略

### 附录

redis-cli监控连接以及命令执行：`monitor`
