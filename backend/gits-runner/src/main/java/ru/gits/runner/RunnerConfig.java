package ru.gits.runner;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ru.gits.sandbox.SandboxConfig;
import ru.gits.sandbox.SandboxExecutor;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RunnerProperties.class)
class RunnerConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SandboxExecutor sandboxExecutor(RunnerProperties properties) {
        return new SandboxExecutor(new SandboxConfig(properties.dockerBinary(), properties.sandboxImage(),
                properties.sandboxRuntime(), properties.timeout(), 2 * 1024 * 1024));
    }

    @Bean(destroyMethod = "shutdownNow")
    ExecutorService runExecutor(RunnerProperties properties) {
        var counter = new AtomicInteger();
        return Executors.newFixedThreadPool(properties.concurrency(), runnable -> {
            Thread thread = new Thread(runnable, "run-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }
}
