package ru.gits.api.taskbank;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import ru.gits.api.config.GitsProperties;
import ru.gits.core.task.TaskTemplateRepository;
import ru.gits.core.task.TaskVariantRepository;
import ru.gits.taskbank.load.TaskBankLoader;

/**
 * Loads the task bank mounted at {@code gits.taskbank.path} (TASKBANK_PATH) when the API starts. Loading is off when
 * the path is blank; a configured but missing directory stops the start, since sessions would have no tasks.
 */
@Component
public class TaskBankStartupLoader implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(TaskBankStartupLoader.class);

    private final GitsProperties properties;
    private final TaskTemplateRepository templates;
    private final TaskVariantRepository variants;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    public TaskBankStartupLoader(GitsProperties properties, TaskTemplateRepository templates,
                                 TaskVariantRepository variants, PlatformTransactionManager transactionManager,
                                 Clock clock) {
        this.properties = properties;
        this.templates = templates;
        this.variants = variants;
        this.transactionManager = transactionManager;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        GitsProperties.Taskbank taskbank = properties.taskbank();
        if (taskbank == null || taskbank.path() == null || taskbank.path().isBlank()) {
            LOG.info("TASKBANK_PATH не задан — банк задач не загружается");
            return;
        }
        Path bank = Path.of(taskbank.path()).resolve("java");
        if (!Files.isDirectory(bank)) {
            throw new IllegalStateException("Каталог банка задач не найден: " + bank);
        }
        var loader = new TaskBankLoader(templates, variants, transactionManager, clock,
                Set.copyOf(taskbank.excludedTemplates()));
        loader.load(bank);
    }
}
