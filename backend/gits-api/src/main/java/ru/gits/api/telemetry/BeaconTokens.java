package ru.gits.api.telemetry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import ru.gits.api.security.Hashing;
import ru.gits.core.session.SessionTask;

/**
 * One-time tokens for sendBeacon telemetry batches. A token is valid for one beacon request of its task; only its
 * hash is stored.
 */
public final class BeaconTokens {

    private BeaconTokens() {
    }

    /** Issues a new token for the task, replacing the previous one. */
    public static String issue(SessionTask task) {
        String token = Hashing.newToken();
        task.issueBeaconToken(Hashing.sha256Hex(token));
        return token;
    }

    /**
     * Checks the token without spending it: the caller spends it with {@link SessionTask#consumeBeaconToken()} once a
     * new batch is stored, so a rejected or duplicate beacon leaves the page's token usable.
     */
    static boolean matches(SessionTask task, String token) {
        String expected = task.getBeaconTokenHash();
        if (expected == null || token == null || token.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                Hashing.sha256Hex(token).getBytes(StandardCharsets.US_ASCII));
    }
}
