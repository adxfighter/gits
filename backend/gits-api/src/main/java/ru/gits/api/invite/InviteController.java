package ru.gits.api.invite;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import ru.gits.api.security.CurrentUser;
import ru.gits.core.common.Level;

@RestController
@RequestMapping("/invites")
class InviteController {

    record CreateInviteRequest(@NotBlank @Size(max = 200) String candidateLabel, @NotNull Level targetLevel) {
    }

    private final InviteService invites;

    InviteController(InviteService invites) {
        this.invites = invites;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    InviteService.CreatedInvite create(@Valid @RequestBody CreateInviteRequest body) {
        return invites.create(CurrentUser.employer(), body.candidateLabel(), body.targetLevel());
    }

    @GetMapping
    List<InviteService.InviteView> list() {
        return invites.list(CurrentUser.employer().companyId());
    }
}
