package ru.gits.runner;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = "gits.runner.heartbeat-ms=3600000")
class GitsRunnerApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextStartsWithoutWebServer() {
        assertThat(context.getBeansOfType(RunnerHeartbeat.class)).hasSize(1);
        assertThat(context.containsBean("tomcatServletWebServerFactory")).isFalse();
    }
}
