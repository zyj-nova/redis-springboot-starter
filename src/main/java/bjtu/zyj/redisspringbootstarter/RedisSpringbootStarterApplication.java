package bjtu.zyj.redisspringbootstarter;

import bjtu.zyj.redisspringbootstarter.receiver.ChatMessageRecevier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@SpringBootApplication
public class RedisSpringbootStarterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedisSpringbootStarterApplication.class, args);
    }

    @Bean
    RedisMessageListenerContainer container(JedisConnectionFactory jedisConnectionFactory,
                                            MessageListenerAdapter listenerAdapter){
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(jedisConnectionFactory);
        //spring启动后，listenercontainer 自动监听来自 “chat” 频道的信息，若有新消息到达，则适配器会自动调用指定方法获取消息
        container.addMessageListener(listenerAdapter, new PatternTopic("chat"));
        return container;
    }

    @Bean
    public MessageListenerAdapter listenerAdapter(ChatMessageRecevier recevier){
        // 将对应的Recevier注册到listener
        return new MessageListenerAdapter(recevier, "receiveMessage");
    }

}
