package ru.gits.task.logistics.t06;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScanJournalVisibleTest {

    @Test
    void scansOfOneParcelAreCollectedTogether() {
        var journal = new ScanJournal();
        journal.record(new ParcelRef("CDEK", "TRK-1", "MSK-1", 1), "MSK-1");
        journal.record(new ParcelRef("CDEK", "TRK-1", "MSK-1", 1), "TVR-2");

        assertThat(journal.history(new ParcelRef("CDEK", "TRK-1", "MSK-1", 1))).containsExactly("MSK-1", "TVR-2");
        assertThat(journal.parcelCount()).isEqualTo(1);
    }

    @Test
    void differentParcelsAreKeptApart() {
        var journal = new ScanJournal();
        journal.record(new ParcelRef("CDEK", "TRK-1", "MSK-1", 1), "MSK-1");
        journal.record(new ParcelRef("CDEK", "TRK-2", "MSK-1", 1), "MSK-1");

        assertThat(journal.parcelCount()).isEqualTo(2);
    }
}
