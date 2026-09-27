package ru.gits.sandbox;

/** The submitted files cannot be placed into the sandbox (bad path, too large, too many files). */
public class InvalidSourceException extends RuntimeException {

    public InvalidSourceException(String message) {
        super(message);
    }
}
