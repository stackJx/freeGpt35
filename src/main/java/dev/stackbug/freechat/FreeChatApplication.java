package dev.stackbug.freechat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FreeChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(FreeChatApplication.class, args);
    }

}
