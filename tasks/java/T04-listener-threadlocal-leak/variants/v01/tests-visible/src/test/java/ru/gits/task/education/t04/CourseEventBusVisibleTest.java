package ru.gits.task.education.t04;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CourseEventBusVisibleTest {

    private static final CourseEvent LESSON = new CourseEvent("JAVA-101", CourseEvent.Type.LESSON_PUBLISHED, "Урок 3");

    @Test
    void deliversEventsToSubscribers() {
        var bus = new CourseEventBus();
        List<CourseEvent> received = new ArrayList<>();
        bus.subscribe(received::add);

        bus.publish(LESSON);

        assertThat(received).containsExactly(LESSON);
    }

    @Test
    void closedSubscriptionReceivesNothing() {
        var bus = new CourseEventBus();
        List<CourseEvent> received = new ArrayList<>();
        var subscription = bus.subscribe(received::add);

        subscription.close();
        bus.publish(LESSON);

        assertThat(received).isEmpty();
    }
}
