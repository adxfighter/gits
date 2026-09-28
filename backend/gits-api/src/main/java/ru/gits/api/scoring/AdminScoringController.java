package ru.gits.api.scoring;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ru.gits.api.security.CurrentUser;
import ru.gits.core.session.AssessmentSessionRepository;

/** Recalculation of indicators and score by an administrator (role ADMIN, see SecurityConfig), e.g. after new rules. */
@RestController
@RequestMapping("/admin/sessions")
class AdminScoringController {

    private final ScoringService scoring;
    private final AssessmentSessionRepository sessions;

    AdminScoringController(ScoringService scoring, AssessmentSessionRepository sessions) {
        this.scoring = scoring;
        this.sessions = sessions;
    }

    @PostMapping("/{sessionId}/scoring")
    ScoringService.Result recompute(@PathVariable UUID sessionId) {
        if (!sessions.existsById(sessionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Сессия не найдена");
        }
        ScoringService.Result result = scoring.recomputeByAdmin(sessionId, CurrentUser.employer().getUsername());
        if (!result.scored()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Сессия ещё идёт или не все решения проверены — пересчёт невозможен");
        }
        return result;
    }
}
