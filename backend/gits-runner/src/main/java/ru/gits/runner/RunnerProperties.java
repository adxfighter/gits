package ru.gits.runner;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param concurrency    parallel sandbox containers
 * @param pollMs         queue polling interval
 * @param dockerBinary   docker CLI executable
 * @param sandboxImage   sandbox image tag
 * @param sandboxRuntime runc or runsc
 * @param timeout        wall-clock limit per run
 */
@ConfigurationProperties(prefix = "gits.runner")
public record RunnerProperties(int concurrency, long pollMs, String dockerBinary, String sandboxImage,
                               String sandboxRuntime, Duration timeout) {
}
