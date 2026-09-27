package ru.gits.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import ru.gits.core.CorePersistenceConfig;

@SpringBootApplication
@Import(CorePersistenceConfig.class)
public class GitsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(GitsApiApplication.class, args);
    }
}
