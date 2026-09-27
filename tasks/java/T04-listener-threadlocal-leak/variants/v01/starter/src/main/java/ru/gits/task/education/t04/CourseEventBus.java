package ru.gits.task.education.t04;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Delivers course events to subscribed pages. Used by a single notification thread.
 */
public final class CourseEventBus {

    /** A subscription returned to the subscriber; closing it stops the delivery. */
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    private final class Registration implements Subscription {
        private final Consumer<CourseEvent> listener;
        private boolean closed;

        Registration(Consumer<CourseEvent> listener) {
            this.listener = listener;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private final List<Registration> registrations = new ArrayList<>();

    public Subscription subscribe(Consumer<CourseEvent> listener) {
        Objects.requireNonNull(listener, "listener");
        var registration = new Registration(listener);
        registrations.add(registration);
        return registration;
    }

    /** Delivers the event to every open subscription, in subscription order. */
    public void publish(CourseEvent event) {
        Objects.requireNonNull(event, "event");
        for (Registration registration : List.copyOf(registrations)) {
            if (!registration.closed) {
                registration.listener.accept(event);
            }
        }
    }

    /** Number of listeners the bus holds. */
    public int listenerCount() {
        return registrations.size();
    }
}
