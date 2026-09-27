package ru.gits.taskbank;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * SHA-256 over all variant files except validation.json: for each file in path order,
 * {@code path \n byteLength \n content \n}. Line endings are normalised to LF before hashing, so the
 * hash is the same on Windows and Linux checkouts.
 */
public final class ContentHash {

    private ContentHash() {
    }

    public static String of(Map<String, String> filesByRelativePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            filesByRelativePath.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        byte[] content = VariantSources.normalise(entry.getValue()).getBytes(StandardCharsets.UTF_8);
                        digest.update((entry.getKey() + "\n" + content.length + "\n").getBytes(StandardCharsets.UTF_8));
                        digest.update(content);
                        digest.update((byte) '\n');
                    });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
