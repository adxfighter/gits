package ru.gits.core.run;

public enum RunStatus {
    QUEUED,
    RUNNING,
    DONE,
    ERROR,
    TIMEOUT;

    public boolean isFinal() {
        return this == DONE || this == ERROR || this == TIMEOUT;
    }
}
