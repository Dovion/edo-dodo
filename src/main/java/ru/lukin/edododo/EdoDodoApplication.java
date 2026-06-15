package ru.lukin.edododo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EdoDodoApplication {

    public static void main(String[] args) {
        SpringApplication.run(EdoDodoApplication.class, args);
    }

}
