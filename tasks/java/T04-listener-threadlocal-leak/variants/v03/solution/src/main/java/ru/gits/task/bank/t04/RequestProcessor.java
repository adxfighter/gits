package ru.gits.task.bank.t04;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Runs request handlers with the request's client in {@link SecurityContext} and writes the audit log.
 * Called from the request thread pool; threads are reused between requests.
 */
public final class RequestProcessor {

    private final List<String> auditLog = new ArrayList<>();

    /**
     * Processes a request.
     *
     * @param handler business logic; reads the client via {@link SecurityContext#currentClient()}
     * @return the handler's response
     */
    public synchronized String handle(BankRequest request, Function<BankRequest, String> handler) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(handler, "handler");
        // A pooled thread may still carry a client from an earlier request: always start clean
        SecurityContext.clear();
        request.clientId().ifPresent(SecurityContext::setClient);
        try {
            String response = handler.apply(request);
            audit(request);
            return response;
        } finally {
            SecurityContext.clear();
        }
    }

    /** Audit entries: "requestId operation client". */
    public synchronized List<String> auditLog() {
        return List.copyOf(auditLog);
    }

    private void audit(BankRequest request) {
        String client = SecurityContext.currentClient().orElse("anonymous");
        auditLog.add(request.requestId() + " " + request.operation() + " " + client);
    }
}
