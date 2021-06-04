package bjtu.zyj.redisspringbootstarter.receiver;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ChatMessageRecevier {
    private static final Logger logger = LoggerFactory.getLogger(ChatMessageRecevier.class);

    private AtomicInteger counter = new AtomicInteger();

    public void receiveMessage(String message){
        logger.info("Received: <" + message + ">");
        counter.incrementAndGet();
    }

    public int getCount(){
        return counter.get();
    }
}
