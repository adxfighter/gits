package ru.gits.task.bank.t04;

import java.util.Objects;
import java.util.Optional;

/**
 * An incoming request of the internet bank.
 *
 * @param requestId  correlation id for the audit log
 * @param operation  requested operation, e.g. "rates" or "payment"
 * @param clientId   authenticated client, empty for anonymous requests
 */
public record BankRequest(String requestId, String operation, Optional<String> clientId) {

    public BankRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(clientId, "clientId");
    }

    public static BankRequest anonymous(String requestId, String operation) {
        return new BankRequest(requestId, operation, Optional.empty());
    }

    public static BankRequest ofClient(String requestId, String operation, String clientId) {
        return new BankRequest(requestId, operation, Optional.of(clientId));
    }
}
