package ru.gits.taskbank.demo;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;

import ru.gits.core.CorePersistenceConfig;
import ru.gits.core.account.AppUserRepository;
import ru.gits.core.invite.ConsentRepository;
import ru.gits.core.invite.InviteRepository;
import ru.gits.core.run.RunJobRepository;
import ru.gits.core.run.RunResultRepository;
import ru.gits.core.session.AssessmentSessionRepository;
import ru.gits.core.session.SessionTaskRepository;
import ru.gits.core.task.TaskVariantRepository;
import ru.gits.core.telemetry.TelemetryBatchRepository;
import ru.gits.taskbank.TaskBankCli;

/**
 * {@code gits-taskbank demo-seed}: like {@code load}, a short-lived Spring context with JPA only; the database comes
 * from SPRING_DATASOURCE_*. The schema, the task bank and the demo employer must already be there (gits-api makes
 * them at start).
 */
public final class DemoSeedCommand {

    private DemoSeedCommand() {
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(CorePersistenceConfig.class)
    static class SeedContext {
    }

    public static DemoSessionImporter.Summary run(Path directory, String employerEmail, PrintStream out) {
        var builder = new SpringApplicationBuilder(SeedContext.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .properties("spring.jpa.hibernate.ddl-auto=validate");
        if (TaskBankCli.CLI_LOGGING_CONFIG.equals(System.getProperty("logback.configurationFile"))) {
            builder.properties("logging.config=classpath:" + TaskBankCli.CLI_LOGGING_CONFIG);
        }
        try (ConfigurableApplicationContext context = builder.run()) {
            var importer = new DemoSessionImporter(new DemoSessionImporter.Repositories(
                    context.getBean(AppUserRepository.class), context.getBean(InviteRepository.class),
                    context.getBean(ConsentRepository.class), context.getBean(AssessmentSessionRepository.class),
                    context.getBean(SessionTaskRepository.class), context.getBean(TaskVariantRepository.class),
                    context.getBean(RunJobRepository.class), context.getBean(RunResultRepository.class),
                    context.getBean(TelemetryBatchRepository.class)),
                    context.getBean(PlatformTransactionManager.class), Clock.systemUTC());
            DemoSessionImporter.Summary summary = importer.importDirectory(directory, employerEmail);
            summary.messages().forEach(out::println);
            out.println("Демо-сессии: загружено " + summary.imported() + ", пропущено " + summary.skipped());
            return summary;
        }
    }
}
