package ru.gits.api.admin;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import ru.gits.api.security.CurrentUser;

/** The administrator's section (role ADMIN, see SecurityConfig): task bank, all sessions, research export. */
@RestController
@RequestMapping("/admin")
class AdminController {

    private final AdminService admin;
    private final ExportService export;

    AdminController(AdminService admin, ExportService export) {
        this.admin = admin;
        this.export = export;
    }

    @GetMapping("/tasks")
    List<AdminService.TemplateRow> tasks() {
        return admin.tasks();
    }

    /** A variant with every file, the solution and the hidden tests included. */
    @GetMapping("/tasks/variants/{variantId}")
    AdminService.VariantDetails variant(@PathVariable UUID variantId) {
        return admin.variant(variantId);
    }

    @GetMapping("/sessions")
    List<AdminService.SessionRow> sessions() {
        return admin.sessions();
    }

    /**
     * The research archive of sessions started from {@code from} to {@code to} (days in UTC, both included, both
     * optional). Streamed: a long period does not have to fit in memory.
     */
    @GetMapping("/export")
    ResponseEntity<StreamingResponseBody> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ExportService.Period period = ExportService.Period.of(from, to);
        export.recordExport(CurrentUser.employer().getUsername(), period);
        String name = "gits-export" + (from == null ? "" : "-" + from) + (to == null ? "" : "-" + to) + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build()
                        .toString())
                .body(out -> export.write(period, out));
    }
}
