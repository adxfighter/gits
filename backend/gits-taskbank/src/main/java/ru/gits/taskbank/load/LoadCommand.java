package ru.gits.taskbank.load;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Set;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;

import ru.gits.core.CorePersistenceConfig;
import ru.gits.core.task.TaskTemplateRepository;
import ru.gits.core.task.TaskVariantRepository;
import ru.gits.taskbank.TaskBankCli;

/**
 * {@code gits-taskbank load}: a short-lived Spring context with JPA only. The database comes from the usual
 * SPRING_DATASOURCE_URL / _USERNAME / _PASSWORD variables; the schema must already exist (gits-api owns Flyway),
 * Hibernate only validates it.
 */
public final class LoadCommand {

    private LoadCommand() {
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(CorePersistenceConfig.class)
    static class LoadContext {
    }

    public static TaskBankLoader.Summary run(Path bankRoot, Set<String> excludedTemplates, PrintStream out) {
        var builder = new SpringApplicationBuilder(LoadContext.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .properties("spring.jpa.hibernate.ddl-auto=validate");
        // Spring Boot re-initialises logging for every application: started from the CLI main, keep its config
        // (warnings to stderr); called in-process (tests) leave the logging of the host JVM alone
        if (TaskBankCli.CLI_LOGGING_CONFIG.equals(System.getProperty("logback.configurationFile"))) {
            builder.properties("logging.config=classpath:" + TaskBankCli.CLI_LOGGING_CONFIG);
        }
        try (ConfigurableApplicationContext context = builder.run()) {
            var loader = new TaskBankLoader(context.getBean(TaskTemplateRepository.class),
                    context.getBean(TaskVariantRepository.class), context.getBean(PlatformTransactionManager.class),
                    Clock.systemUTC(), excludedTemplates);
            // Warnings go to stderr through the logger; stdout gets the summary only
            TaskBankLoader.Summary summary = loader.load(bankRoot);
            out.println("Загрузка: " + summary);
            return summary;
        }
    }
}
