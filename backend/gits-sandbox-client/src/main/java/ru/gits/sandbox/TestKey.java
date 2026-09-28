package ru.gits.sandbox;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A stable identifier of a test that does not reveal it: the first 16 hex digits of SHA-256 of
 * {@code className#name}. The runner stores it with the result of every hidden test and the task bank validator with
 * the hidden tests the starter passes, so that scoring can tell the tests a solution must fix from those that check
 * nothing got broken — without keeping hidden test names in the results.
 */
public final class TestKey {

    private TestKey() {
    }

    public static String of(String className, String name) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((className + "#" + name).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static String of(TestCaseResult testCase) {
        return of(testCase.className(), testCase.name());
    }
}
