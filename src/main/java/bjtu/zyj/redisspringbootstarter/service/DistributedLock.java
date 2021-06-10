package bjtu.zyj.redisspringbootstarter.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class DistributedLock {
    @Autowired
    RedisTemplate<String,String> redisTemplate;

    public void service() throws InterruptedException {
        //设置锁的过期时间
        String id = UUID.randomUUID().toString();
        Boolean lock = redisTemplate.opsForValue().setIfAbsent("lock", id,20, TimeUnit.SECONDS);
        if (lock == false){
            return;
        }
        //获取到锁
        //执行业务
        int cnt = Integer.parseInt(redisTemplate.opsForValue().get("item"));
        cnt++;
        redisTemplate.opsForValue().set("item",String.valueOf(cnt));
        //使用Lua脚本原子性的释放锁：检查key值是否为当前id，删除key
        String script = "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end";
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(Long.class);
        redisTemplate.execute(redisScript, Arrays.asList("lock"),id);
        // 使用自旋+事务(乐观锁)的方式释放锁
//        while (true){
//            redisTemplate.watch("lock");
//            //防止删除不是自己的锁
//            if (redisTemplate.opsForValue().get("lock").equalsIgnoreCase(id)){
//                //造成的问题：判断和删除仍然不是原子操作，如果服务器宕机，下面代码不会执行，就会一直拿着锁造成死锁，
//                redisTemplate.setEnableTransactionSupport(true);
//                redisTemplate.multi();
//                redisTemplate.delete("lock");
//                List<Object> ret = redisTemplate.exec();
//                if (ret == null){//删除失败
//                    continue;
//                }
//                redisTemplate.unwatch();
//                break;
//            }
//        }

    }
}
