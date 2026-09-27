package ru.gits.task.logistics.t06;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ParcelRefHiddenTest {

    @Test
    void historySurvivesLabelReprint() {
        var journal = new ScanJournal();
        var original = new ParcelRef("CDEK", "TRK-10", "MSK-1", 1);
        journal.record(original, "MSK-1");
        journal.record(original.reprinted(), "TVR-2");
        journal.record(original.reprinted().reprinted(), "SPB-3");

        assertThat(journal.history(original)).containsExactly("MSK-1", "TVR-2", "SPB-3");
        assertThat(journal.history(new ParcelRef("CDEK", "TRK-10", "MSK-1", 7))).containsExactly("MSK-1", "TVR-2", "SPB-3");
        assertThat(journal.parcelCount()).isEqualTo(1);
    }

    @Test
    void manyReprintedParcelsAreNotDuplicated() {
        var journal = new ScanJournal();
        for (int i = 0; i < 200; i++) {
            journal.record(new ParcelRef("PEK", "TRK-" + i, "KZN-1", 1), "KZN-1");
            journal.record(new ParcelRef("PEK", "TRK-" + i, "KZN-1", 1 + i % 5), "NN-2");
        }
        assertThat(journal.parcelCount()).isEqualTo(200);
    }

    @Test
    void equalRefsHaveEqualHashCodes() {
        for (int version = 1; version <= 50; version++) {
            var first = new ParcelRef("CDEK", "TRK-" + version, "MSK-1", 1);
            var reprinted = new ParcelRef("CDEK", "TRK-" + version, "MSK-1", version);
            assertThat(first).isEqualTo(reprinted);
            assertThat(reprinted).isEqualTo(first);
            assertThat(first.hashCode()).as("version %d", version).isEqualTo(reprinted.hashCode());
        }
    }

    @Test
    void trackingNumberCaseDoesNotSplitHistory() {
        var journal = new ScanJournal();
        journal.record(new ParcelRef("CDEK", "trk-77ab", "MSK-1", 1), "MSK-1");
        journal.record(new ParcelRef("CDEK", "TRK-77AB", "MSK-1", 1), "TVR-2");
        journal.record(new ParcelRef("CDEK", "Trk-77aB", "MSK-1", 1), "SPB-3");

        assertThat(journal.history(new ParcelRef("CDEK", "TRK-77AB", "MSK-1", 1))).containsExactly("MSK-1", "TVR-2", "SPB-3");
        assertThat(journal.history(new ParcelRef("CDEK", "trk-77ab", "MSK-1", 1))).containsExactly("MSK-1", "TVR-2", "SPB-3");
        assertThat(journal.parcelCount()).isEqualTo(1);
    }

    @Test
    void refsDifferingOnlyInTrackingCaseHaveEqualHashCodes() {
        for (int i = 0; i < 100; i++) {
            var lower = new ParcelRef("PEK", "abc-" + i + "-xz", "KZN-1", 1);
            var upper = new ParcelRef("PEK", "ABC-" + i + "-XZ", "KZN-1", 1 + i % 4);
            assertThat(lower).isEqualTo(upper);
            assertThat(upper).isEqualTo(lower);
            assertThat(lower.hashCode()).as("parcel %d", i).isEqualTo(upper.hashCode());
        }
    }

    @Test
    void labelVersionIsStillAvailable() {
        var ref = new ParcelRef("CDEK", "TRK-1", "MSK-1", 1).reprinted().reprinted();
        assertThat(ref.labelVersion()).isEqualTo(3);
    }

    @Test
    void everyIdentityFieldTakesPartInEquality() {
        var ref = new ParcelRef("CDEK", "TRK-1", "MSK-1", 1);
        assertThat(ref).isNotEqualTo(new ParcelRef("PEK", "TRK-1", "MSK-1", 1));
        assertThat(ref).isNotEqualTo(new ParcelRef("CDEK", "TRK-2", "MSK-1", 1));
        assertThat(ref).isNotEqualTo(new ParcelRef("CDEK", "TRK-1", "SPB-1", 1));
        assertThat(ref).isNotEqualTo(new ParcelRef("cdek", "TRK-1", "MSK-1", 1));
        assertThat(ref).isNotEqualTo(new ParcelRef("CDEK", "TRK-1", "msk-1", 1));
        assertThat(ref).isNotEqualTo(null).isNotEqualTo("CDEK:TRK-1");
    }
}
