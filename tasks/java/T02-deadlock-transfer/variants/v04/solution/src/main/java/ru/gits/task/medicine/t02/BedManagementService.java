package ru.gits.task.medicine.t02;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntSupplier;

/**
 * Places patients across wards. Called concurrently by the admission desk, doctors and the nightly
 * redistribution job. Lock protocol: wards are always locked in ascending ward number (see {@link Ward}).
 */
public final class BedManagementService {

    /**
     * Transfers the longest-staying patient of {@code from} to {@code to}.
     *
     * @return the transferred patient, or empty when nothing could be moved
     */
    public Optional<Patient> transfer(Ward from, Ward to) {
        requireDifferent(from, to);
        return from.transferTo(to);
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
        return lockAll(wards, () -> {
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
                fullest.transferTo(emptiest);  // monitors are already held, re-entry is safe
                moves++;
            }
        });
    }

    /** Patients in the given wards, counted at one consistent moment. */
    public int totalPatients(List<Ward> wards) {
        Objects.requireNonNull(wards, "wards");
        return lockAll(wards, () -> wards.stream().mapToInt(Ward::occupied).sum());
    }

    /** Runs the action while holding the monitors of all wards, acquired in ascending ward number. */
    private static int lockAll(List<Ward> wards, IntSupplier action) {
        List<Ward> ordered = wards.stream().distinct().sorted(Comparator.comparingInt(Ward::number)).toList();
        return lockFrom(ordered, 0, action);
    }

    private static int lockFrom(List<Ward> ordered, int index, IntSupplier action) {
        if (index == ordered.size()) {
            return action.getAsInt();
        }
        synchronized (ordered.get(index)) {
            return lockFrom(ordered, index + 1, action);
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
