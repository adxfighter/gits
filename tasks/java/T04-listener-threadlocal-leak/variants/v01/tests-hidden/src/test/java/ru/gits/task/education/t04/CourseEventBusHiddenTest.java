package ru.gits.task.education.t04;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

class CourseEventBusHiddenTest {

    private static final CourseEvent GRADES = new CourseEvent("JAVA-101", CourseEvent.Type.GRADES_PUBLISHED, "Оценки");

    @Test
    void closedSubscriptionsAreReleased() {
        var bus = new CourseEventBus();

        for (int page = 0; page < 100_000; page++) {
            bus.subscribe(event -> { }).close();
        }

        assertThat(bus.listenerCount()).isZero();
    }

    @Test
    void onlyOpenPagesAreCounted() {
        var bus = new CourseEventBus();
        List<CourseEventBus.Subscription> open = new ArrayList<>();
        for (int page = 0; page < 1_000; page++) {
            var subscription = bus.subscribe(event -> { });
            if (page % 4 == 0) {
                open.add(subscription);
            } else {
                subscription.close();
            }
        }

        assertThat(bus.listenerCount()).isEqualTo(open.size());
    }

    @Test
    void closingTwiceIsSafeAndDoesNotAffectOthers() {
        var bus = new CourseEventBus();
        List<String> received = new ArrayList<>();
        var first = bus.subscribe(event -> received.add("first"));
        bus.subscribe(event -> received.add("second"));

        first.close();
        first.close();
        bus.publish(GRADES);

        assertThat(received).containsExactly("second");
        assertThat(bus.listenerCount()).isEqualTo(1);
    }

    @Test
    void sameListenerSubscribedTwiceIsClosedIndependently() {
        var bus = new CourseEventBus();
        List<CourseEvent> received = new ArrayList<>();
        Consumer<CourseEvent> listener = received::add;
        var tabOne = bus.subscribe(listener);
        bus.subscribe(listener);

        tabOne.close();
        bus.publish(GRADES);

        assertThat(received).hasSize(1);
        assertThat(bus.listenerCount()).isEqualTo(1);
    }

    @Test
    void subscriptionClosedDuringDeliveryIsHandled() {
        var bus = new CourseEventBus();
        List<String> received = new ArrayList<>();
        CourseEventBus.Subscription[] self = new CourseEventBus.Subscription[1];
        self[0] = bus.subscribe(event -> {
            received.add("once");
            self[0].close();
        });
        bus.subscribe(event -> received.add("other"));

        bus.publish(GRADES);
        bus.publish(GRADES);

        assertThat(received).containsExactly("once", "other", "other");
        assertThat(bus.listenerCount()).isEqualTo(1);
    }
}
