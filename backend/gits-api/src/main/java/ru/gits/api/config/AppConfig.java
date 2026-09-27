package ru.gits.api.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import ru.gits.api.session.SessionProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({GitsProperties.class, SessionProperties.class})
@EnableScheduling
@EnableAsync
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
