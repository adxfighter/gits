package ru.gits.task.medicine.t03;

/**
 * Regional registry of patient cards. Each call is a slow remote request.
 */
@FunctionalInterface
public interface CardRegistry {

    /** Loads the current card of a patient. */
    PatientCard load(String patientId);
}
