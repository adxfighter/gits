package ru.gits.core.task;

/**
 * Role of a task file. SOLUTION and HIDDEN_TEST must never reach a candidate.
 */
public enum FileKind {
    STARTER,
    SOLUTION,
    VISIBLE_TEST,
    HIDDEN_TEST,
    READONLY;

    public boolean isCandidateVisible() {
        return this == STARTER || this == VISIBLE_TEST || this == READONLY;
    }
}
