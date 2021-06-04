package bjtu.zyj.redisspringbootstarter;

import bjtu.zyj.redisspringbootstarter.bean.Item;
import bjtu.zyj.redisspringbootstarter.bean.User;
import bjtu.zyj.redisspringbootstarter.receiver.ChatMessageRecevier;
import bjtu.zyj.redisspringbootstarter.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
@Slf4j
class RedisSpringbootStarterApplicationTests {

     @Test
     void contextLoads() {
     }

     @Autowired
     RedisTemplate<String, String> redisTemplate;

     @Autowired
     RedisTemplate<String, Serializable> objRedisTemplate;

     @Test
     public void testConnect(){
         System.out.println(redisTemplate.opsForValue().get("name"));
     }

     @Test
     public void testSetStrin(){
         redisTemplate.boundValueOps("name").set("bill");
     }

     @Test
     public void testObjSerializable(){
//         Item obj = new Item();
//         obj.setId(1);
//         obj.setName("tom");
//         ArrayList<Integer> list = new ArrayList<>();
//         list.add(1);
//         list.add(2);
//         obj.setList(list);
//
//         objRedisTemplate.opsForValue().set("item",obj);

     }

     @Autowired
    ChatMessageRecevier recevier;

     @Test
    public void testSendMessage(){
        while (true){//模拟spring一直运行
            //redisTemplate.convertAndSend("chat", "Hello from Redis");
            //Thread.sleep(100L);
        }
     }

     @Test
    public void addItems(){
         HashOperations<String, Integer, Integer> ho = objRedisTemplate.opsForHash();
         Item item = new Item();
         item.setId(1);
         item.setTotal(200);
         ho.put("items", item.getId(), item.getTotal());
         System.out.println("插入数量：" + ho.get("items",item.getId()));
     }

     @Autowired
    OrderService orderService;
     @Test
    public void purchase() throws InterruptedException {
         int num = 20_000;
         CountDownLatch latch = new CountDownLatch(num);
         AtomicInteger count = new AtomicInteger();
         Item item = new Item();
         item.setId(1);
         item.setTotal(200);
         for (int i = 0; i < num; i++){
             new Thread(() -> {
                 if (orderService.addOrder(item)) {
                     // 统计抢到的数量
                     count.incrementAndGet();
                 }
                 latch.countDown();
             }).start();
         }
         latch.await();
         System.out.println(count.get());
     }

}
