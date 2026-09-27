package ru.gits.runner;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import ru.gits.core.run.RunJobRepository;

@SpringBootTest(properties = "spring.flyway.locations=filesystem:../gits-api/src/main/resources/db/migration")
@Import(RunnerTestSupport.Postgres.class)
class GitsRunnerApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextStartsWithoutWebServerAndValidatesSchema() {
        assertThat(context.getBeansOfType(RunJobWorker.class)).hasSize(1);
        assertThat(context.getBeansOfType(RunJobRepository.class)).hasSize(1);
        assertThat(context.containsBean("tomcatServletWebServerFactory")).isFalse();
    }
}
