package ru.gits.api.employer;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ru.gits.api.security.CurrentUser;
import ru.gits.core.invite.InviteStatus;

/** Employer dashboard API (role EMPLOYER, see SecurityConfig); only the employer's own company is visible. */
@RestController
@RequestMapping("/employer")
class EmployerController {

    private final EmployerService employer;

    EmployerController(EmployerService employer) {
        this.employer = employer;
    }

    @GetMapping("/invites")
    List<EmployerService.InviteRow> invites(@RequestParam(required = false) InviteStatus status) {
        return employer.invites(companyId(), status);
    }

    @DeleteMapping("/invites/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable UUID inviteId) {
        employer.revoke(CurrentUser.employer(), inviteId);
    }

    @GetMapping("/sessions/{sessionId}/report")
    EmployerService.SessionReport report(@PathVariable UUID sessionId) {
        return employer.report(companyId(), sessionId);
    }

    @GetMapping("/session-tasks/{sessionTaskId}/replay")
    EmployerService.Replay replay(@PathVariable UUID sessionTaskId,
                                  @RequestParam(defaultValue = "0") int fromSeq,
                                  @RequestParam(required = false) Integer limit) {
        return employer.replay(companyId(), sessionTaskId, fromSeq, limit);
    }

    private static UUID companyId() {
        return CurrentUser.employer().companyId();
    }
}
