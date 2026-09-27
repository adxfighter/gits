package ru.gits.runner;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import ru.gits.core.run.RunJobRepository;

@SpringBootTest(properties = {
        "gits.runner.heartbeat-ms=3600000",
        "spring.flyway.locations=filesystem:../gits-api/src/main/resources/db/migration"
})
@Import(GitsRunnerApplicationTest.Postgres.class)
class GitsRunnerApplicationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Postgres {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"));
        }
    }

    @Autowired
    private ApplicationContext context;

    @Test
    void contextStartsWithoutWebServerAndValidatesSchema() {
        assertThat(context.getBeansOfType(RunnerHeartbeat.class)).hasSize(1);
        assertThat(context.getBeansOfType(RunJobRepository.class)).hasSize(1);
        assertThat(context.containsBean("tomcatServletWebServerFactory")).isFalse();
    }
}
