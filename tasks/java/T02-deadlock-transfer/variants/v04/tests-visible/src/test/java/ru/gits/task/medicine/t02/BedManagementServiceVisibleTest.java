package ru.gits.task.medicine.t02;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class BedManagementServiceVisibleTest {

    private final BedManagementService service = new BedManagementService();

    @Test
    void transfersTheLongestStayingPatient() {
        var surgery = new Ward(1, 3);
        var therapy = new Ward(2, 3);
        surgery.admit(new Patient("MR-1"));
        surgery.admit(new Patient("MR-2"));

        assertThat(service.transfer(surgery, therapy)).contains(new Patient("MR-1"));
        assertThat(surgery.occupied()).isEqualTo(1);
        assertThat(therapy.occupied()).isEqualTo(1);
    }

    @Test
    void redistributionEvensOutOccupancy() {
        var surgery = new Ward(1, 10);
        var therapy = new Ward(2, 10);
        var cardiology = new Ward(3, 10);
        for (int i = 0; i < 7; i++) {
            surgery.admit(new Patient("MR-" + i));
        }

        service.redistribute(List.of(surgery, therapy, cardiology));

        assertThat(List.of(surgery.occupied(), therapy.occupied(), cardiology.occupied()))
                .containsExactlyInAnyOrder(3, 2, 2);
        assertThat(service.totalPatients(List.of(cardiology, surgery, therapy))).isEqualTo(7);
    }
}
