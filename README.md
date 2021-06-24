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
* 定时删除（基本不用）
* 定期删除
* 惰性删除
* Redis缓存淘汰策略

### 7. 持久化技术

#### 7.1 RDB

#### 7.2 AOF

### 8. 主从复制(Replication)

#### 8.1 主从复制工作流程

​	我们可以通过`slaveof <host> <port>`命令，或者通过配置`slaveof`选项，来使当前的服务器（slave）复制指定服务器（master）的内容，被复制的服务器称为主服务器（master），对主服务器进行复制操作的为从服务器（slave）。

​        主服务器master可以进行读写操作，当主服务器的数据发生变化，master会发出命令流来保持对salve的更新，而从服务器slave通常是只读的（可以通过`slave-read-only`指定），在主从复制模式下，即便master宕机了，slave是不能变为主服务器进行写操作的。

​		一个master可以有多个slave，即一主多从；而slave也可以接受其他slave的连接，形成“主从链”层叠状结构（cascading-like structure），自 Redis 4.0 起，所有的sub-slave也会从master收到完全一样的复制流。

#### 主从复制配置

启动三个redis服务，分别写三个redis的配置文件：`redis6379.conf,redis6380.conf,redis6391.conf`

在三个配置文件中写入如下内容：

```shell
# 公共配置文件
include /home/zyj/local/redis-6.2/redis.conf
# 指定进程信息
pidfile ./redis_6379.pid
port 6379
dbfilename dump6379.rdb
# 日志文件
logfile "6379.log"
```

启动三个redis服务：

```shell
./local/redis-6.2/bin/redis-server ./local/redis-6.2/redis6381.conf
```

客户端连接到指定端口：

```shell
./local/redis-6.2/bin/redis-cli -p 6379
```

查看主从复制情况

```shell
info replication
```

配置从机：

```shell
# 配置从机，ip是主机ip和端口号
replicaof 192.168.209.101 6379
# 如果主机设置了密码，务必配置！
masterauth fxgh
```

再次查看6380、6381两个端口的redis服务，role已经变成了slave。

#### 8.1 主从复制的作用

* 读写分离：master负责读，slave负责写
* 负载均衡：
* 故障恢复：
* 数据冗余：容灾快速恢复
* 高可用基石

#### 8.3 主从复制的实现原理

​		主从复制的配置还是比较简单的，下面来了解下主从复制的实现原理，Redis的主从复制过程大体上分3个阶段：**建立连接**、**数据同步**、**命令传播**。

#### 8.4 哨兵模式



### 9. 缓存雪崩、缓存击穿、缓存穿透

#### 9.1 缓存雪崩

​		缓存雪崩指的是在一个**较短的时间内**，缓存中**较多的key集中过期失效**，此周期内请求访问过期的数据，redis未命中，redis向数据库获取数据，导致大量请求积压到数据库服务器，进而导致应用服务器和数据库服务器崩溃。

**解决方案：**

* 构建多级缓存架构
* 灾难预警机制
* 限流、降级

​        缓存雪崩就是瞬间**过期数据量太大**，导致对数据库服务器造成压力。如能够有效避免过期时间集中，可以有效解决雪崩现象的出现（约40%），配合其他策略一起使用，并监控服务器的运行数据，根据运行记录做快速调整。

#### 9.2 缓存击穿

​        缓存击穿指的是某一时刻某个被大量访问的key过期失效，此时Redis内存平稳，无波动，Redis服务器CPU正常。多个数据请求从服务器直接压到Redis后，均未命中，Redis在短时间内发起了大量对数据库中同一数据的访问。

**解决方案：**

* 二级缓存。可以设置不同的失效时间，保障不会被同时淘汰就行。
* 现场调整。监控访问量，对自然流量激增的数据延长过期时间或设置为永久性key。

​        缓存击穿就是**单个**高热数据过期的瞬间，数据访问量较大，未命中redis后，发起了大量对同一数据的数据库访问，导致对数据库服务器造成压力。应对策略应该在业务数据分析与预防方面进行，配合运行监控测试与即时调整策略，毕竟单个key的过期监控难度较高，配合雪崩处理策略即可。

#### 9.3 缓存穿透

​		缓存穿透指的是指缓存服务器收到大量不存在缓存中key的请求，导致缓存大面积未命中，这些请求积压到数据库服务器，

​		缓存击穿访问了不存在的数据，跳过了合法数据的redis数据缓存阶段，每次访问数据库，导致对数据库服务器造成压力。通常此类数据的出现量是一个较低的值，当出现此类情况以毒攻毒，并及时报警。应对策略应该在临时预案防范方面多做文章。
​		无论是黑名单还是白名单，都是对整体系统的压力，警报解除后尽快移除。

### 附录

redis-cli监控连接以及命令执行：`monitor`
