package ru.gits.sandbox;

/** A source file to place into the sandbox, e.g. {@code src/main/java/demo/Calc.java}. */
public record SourceFile(String path, String content) {
}
