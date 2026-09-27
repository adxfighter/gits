package ru.gits.runner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import ru.gits.core.CorePersistenceConfig;

@SpringBootApplication
@EnableScheduling
@Import(CorePersistenceConfig.class)
public class GitsRunnerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GitsRunnerApplication.class, args);
    }
}
