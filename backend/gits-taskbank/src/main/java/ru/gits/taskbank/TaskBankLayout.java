package ru.gits.taskbank;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Finds templates and variants under a bank root such as tasks/java. */
public final class TaskBankLayout {

    /**
     * @param templateDir tasks/java/T01-race-counter
     * @param variantDir  tasks/java/T01-race-counter/variants/v01
     * @param code        T01-v01, derived from the directory names
     */
    public record VariantLocation(Path templateDir, Path variantDir, String code) {
    }

    private final Path root;

    public TaskBankLayout(Path root) {
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Task bank directory not found: " + root);
        }
        this.root = root;
    }

    /** tasks/, the parent of the bank root (tasks/java): holds schema/ and competencies.yaml. */
    public Path tasksDirectory() {
        return root.toAbsolutePath().getParent();
    }

    /** tasks/schema, next to the bank root (tasks/java). */
    public Path schemaDirectory() {
        return tasksDirectory().resolve("schema");
    }

    /** Template directories that contain a template.yaml, with or without variants. */
    public List<Path> templates() {
        return children(root).stream().filter(dir -> Files.isRegularFile(dir.resolve("template.yaml"))).toList();
    }

    public List<VariantLocation> variants() {
        List<VariantLocation> result = new ArrayList<>();
        for (Path templateDir : children(root)) {
            Path variantsDir = templateDir.resolve("variants");
            if (!Files.isRegularFile(templateDir.resolve("template.yaml")) || !Files.isDirectory(variantsDir)) {
                continue;
            }
            String templateCode = templateDir.getFileName().toString().split("-", 2)[0];
            for (Path variantDir : children(variantsDir)) {
                result.add(new VariantLocation(templateDir, variantDir, templateCode + "-" + variantDir.getFileName()));
            }
        }
        return result;
    }

    private static List<Path> children(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
