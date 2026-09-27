package ru.gits.task.medicine.t02;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntSupplier;

/**
 * Places patients across wards. Called concurrently by the admission desk, doctors and the nightly
 * redistribution job.
 */
public final class BedManagementService {

    /**
     * Transfers the longest-staying patient of {@code from} to {@code to}.
     *
     * @return the transferred patient, or empty when nothing could be moved
     */
    public Optional<Patient> transfer(Ward from, Ward to) {
        requireDifferent(from, to);
        synchronized (from) {
            return from.transferTo(to);
        }
    }

    /**
     * Moves patients from the fullest to the emptiest ward until the occupancy of the given wards
     * differs by at most one patient.
     *
     * @return number of transfers made
     */
    public int redistribute(List<Ward> wards) {
        Objects.requireNonNull(wards, "wards");
        if (wards.size() < 2) {
            return 0;
        }
        return lockAll(wards, 0, () -> {
            int moves = 0;
            while (true) {
                Ward fullest = wards.stream().max(Comparator.comparingInt(Ward::occupied)).orElseThrow();
                Ward emptiest = wards.stream()
                        .filter(Ward::hasFreeBed)
                        .min(Comparator.comparingInt(Ward::occupied))
                        .orElse(null);
                if (emptiest == null || fullest.occupied() - emptiest.occupied() <= 1) {
                    return moves;
                }
                fullest.transferTo(emptiest);
                moves++;
            }
        });
    }

    /** Patients in the given wards, counted at one consistent moment. */
    public int totalPatients(List<Ward> wards) {
        Objects.requireNonNull(wards, "wards");
        return lockAll(wards, 0, () -> wards.stream().mapToInt(Ward::occupied).sum());
    }

    /** Runs the action while holding the monitors of all wards, acquired in list order. */
    private static int lockAll(List<Ward> wards, int index, IntSupplier action) {
        if (index == wards.size()) {
            return action.getAsInt();
        }
        synchronized (wards.get(index)) {
            return lockAll(wards, index + 1, action);
        }
    }

    private static void requireDifferent(Ward from, Ward to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from == to || from.number() == to.number()) {
            throw new IllegalArgumentException("Transfer within ward " + from.number());
        }
    }
}
