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

### 4.添加基于内存的秒杀案例
秒杀基本流程：
* 先将要秒杀的商品商量读取到库存中
* 限流，避免瞬间涌入过高并发量造成服务器崩溃
* 减少库存，若减为0返回失败；否则抢购成功，将处理订单的消息发送给消息队列异步处理
