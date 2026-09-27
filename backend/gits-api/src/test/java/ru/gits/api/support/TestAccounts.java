package ru.gits.api.support;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ru.gits.core.account.AppUser;
import ru.gits.core.account.AppUserRepository;
import ru.gits.core.account.Company;
import ru.gits.core.account.CompanyRepository;
import ru.gits.core.account.UserRole;

/** Creates committed employer accounts with a known password for MockMvc tests. */
@Component
public class TestAccounts {

    public static final String PASSWORD = "test-password-123";

    public record Account(UUID userId, UUID companyId, String email) {
    }

    private final CompanyRepository companies;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public TestAccounts(CompanyRepository companies, AppUserRepository users, PasswordEncoder encoder) {
        this.companies = companies;
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public Account employer() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Company company = companies.save(new Company("Компания " + suffix, Instant.now()));
        AppUser user = users.save(new AppUser(company, "employer-" + suffix + "@test.local",
                encoder.encode(PASSWORD), UserRole.EMPLOYER, Instant.now()));
        return new Account(user.getId(), company.getId(), user.getEmail());
    }
}
