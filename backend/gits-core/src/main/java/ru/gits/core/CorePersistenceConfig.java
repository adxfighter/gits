package ru.gits.core;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Registers GITS entities and repositories; import it from each Spring Boot application. */
@Configuration(proxyBeanMethods = false)
@EntityScan(basePackages = "ru.gits.core")
@EnableJpaRepositories(basePackages = "ru.gits.core")
public class CorePersistenceConfig {
}
