package ru.gits.api.demo;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import ru.gits.api.config.GitsProperties;
import ru.gits.core.account.AppUser;
import ru.gits.core.account.AppUserRepository;
import ru.gits.core.account.Company;
import ru.gits.core.account.CompanyRepository;
import ru.gits.core.account.UserRole;

/**
 * Creates the demo company, employer and admin from environment variables (DEMO_*). Idempotent: an
 * existing account keeps its data; only its password is updated when the configured one changed.
 */
@Component
class DemoAccountsInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountsInitializer.class);

    private final GitsProperties.Demo demo;
    private final CompanyRepository companies;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    DemoAccountsInitializer(GitsProperties properties, CompanyRepository companies, AppUserRepository users,
                            PasswordEncoder encoder, Clock clock) {
        this.demo = properties.demo();
        this.companies = companies;
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (configured(demo.employerEmail(), demo.employerPassword())) {
            Company company = companies.findByName(demo.companyName())
                    .orElseGet(() -> companies.save(new Company(demo.companyName(), clock.instant())));
            ensureUser(company, demo.employerEmail(), demo.employerPassword(), UserRole.EMPLOYER);
        }
        if (configured(demo.adminEmail(), demo.adminPassword())) {
            ensureUser(null, demo.adminEmail(), demo.adminPassword(), UserRole.ADMIN);
        }
    }

    private void ensureUser(Company company, String email, String password, UserRole role) {
        users.findByEmail(email).ifPresentOrElse(user -> {
            if (!encoder.matches(password, user.getPasswordHash())) {
                user.changePasswordHash(encoder.encode(password));
                log.info("Demo {} account password updated", role);
            }
        }, () -> {
            users.save(new AppUser(company, email.strip(), encoder.encode(password), role, clock.instant()));
            log.info("Demo {} account created", role);
        });
    }

    private static boolean configured(String email, String password) {
        return StringUtils.hasText(email) && StringUtils.hasText(password);
    }
}
