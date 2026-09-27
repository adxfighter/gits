package ru.gits.api.support;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import ru.gits.core.account.AppUser;
import ru.gits.core.account.AppUserRepository;
import ru.gits.core.account.Company;
import ru.gits.core.account.CompanyRepository;
import ru.gits.core.account.UserRole;
import ru.gits.core.common.Level;
import ru.gits.core.invite.Invite;
import ru.gits.core.invite.InviteRepository;
import ru.gits.core.session.AssessmentSession;
import ru.gits.core.session.AssessmentSessionRepository;
import ru.gits.core.session.SessionTask;
import ru.gits.core.session.SessionTaskRepository;
import ru.gits.core.task.FileKind;
import ru.gits.core.task.TaskKind;
import ru.gits.core.task.TaskTemplate;
import ru.gits.core.task.TaskTemplateRepository;
import ru.gits.core.task.TaskVariant;
import ru.gits.core.task.TaskVariantRepository;

/** Builds a minimal valid object graph up to a session task. Callers own the transaction. */
@Component
public class TestData {

    public static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

    private final CompanyRepository companies;
    private final AppUserRepository users;
    private final TaskTemplateRepository templates;
    private final TaskVariantRepository variants;
    private final InviteRepository invites;
    private final AssessmentSessionRepository sessions;
    private final SessionTaskRepository sessionTasks;

    public TestData(CompanyRepository companies, AppUserRepository users, TaskTemplateRepository templates,
                    TaskVariantRepository variants, InviteRepository invites, AssessmentSessionRepository sessions,
                    SessionTaskRepository sessionTasks) {
        this.companies = companies;
        this.users = users;
        this.templates = templates;
        this.variants = variants;
        this.invites = invites;
        this.sessions = sessions;
        this.sessionTasks = sessionTasks;
    }

    public SessionTask sessionTask() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Company company = companies.save(new Company("Company " + suffix, NOW));
        AppUser employer = users.save(new AppUser(company, "employer-" + suffix + "@test.local",
                "{bcrypt}not-a-real-hash", UserRole.EMPLOYER, NOW));

        TaskTemplate template = templates.save(new TaskTemplate("T" + suffix, "Template " + suffix,
                "[\"java.concurrency.atomicity\"]", Level.MIDDLE, "{\"threads\":[2,4,8]}", NOW));
        var variant = new TaskVariant(template, template.getCode() + "-v01", TaskKind.TASK, Level.MIDDLE, "bank",
                "{\"threads\":4}", "# Условие", 25, "0".repeat(64), "{\"status\":\"PASSED\"}", NOW);
        variant.addFile(FileKind.STARTER, "src/main/java/Counter.java", "class Counter {}", true);
        variant.addFile(FileKind.HIDDEN_TEST, "src/test/java/CounterHiddenTest.java", "class CounterHiddenTest {}", false);
        TaskVariant savedVariant = variants.save(variant);

        Invite invite = invites.save(new Invite(company, employer, "Кандидат " + suffix, Level.MIDDLE,
                UUID.randomUUID().toString().replace("-", "").repeat(2), NOW.plusSeconds(86_400), NOW));
        AssessmentSession session = sessions.save(new AssessmentSession(invite, 90, 42L, NOW));
        return sessionTasks.save(new SessionTask(session, savedVariant, 1, TaskKind.TASK));
    }
}
