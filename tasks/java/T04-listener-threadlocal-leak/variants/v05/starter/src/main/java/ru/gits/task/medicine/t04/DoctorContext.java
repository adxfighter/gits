package ru.gits.task.medicine.t04;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The doctor on whose behalf the current thread acts. Contexts may be nested: a consultation runs
 * on behalf of the consultant inside the attending doctor's action.
 */
public final class DoctorContext {

    private static final ThreadLocal<Doctor> CURRENT = new ThreadLocal<>();

    private DoctorContext() {
    }

    public static Optional<Doctor> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** Runs the action on behalf of the doctor and returns its result. */
    public static <T> T runAs(Doctor doctor, Supplier<T> action) {
        Objects.requireNonNull(doctor, "doctor");
        Objects.requireNonNull(action, "action");
        CURRENT.set(doctor);
        T result = action.get();
        CURRENT.remove();
        return result;
    }

    /** Runs the action on behalf of the doctor. */
    public static void runAs(Doctor doctor, Runnable action) {
        Objects.requireNonNull(action, "action");
        runAs(doctor, () -> {
            action.run();
            return null;
        });
    }
}
