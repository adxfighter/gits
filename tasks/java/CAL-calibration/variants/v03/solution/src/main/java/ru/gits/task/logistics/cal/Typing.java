package ru.gits.task.logistics.cal;

/**
 * Retype the fragment from {@link Reference} between the triple quotes below.
 */
final class Typing {

    static final String TYPED = """
            public final class RoutePlanner {

                private final List<Stop> stops = new ArrayList<>();

                public void addStop(String address, int unloadMinutes) {
                    if (address == null || address.isBlank()) {
                        throw new IllegalArgumentException("Address is required");
                    }
                    stops.add(new Stop(address.strip(), unloadMinutes));
                }

                public int totalMinutes(int drivingMinutesBetweenStops) {
                    int total = 0;
                    for (int i = 0; i < stops.size(); i++) {
                        total += stops.get(i).unloadMinutes();
                        if (i > 0) {
                            total += drivingMinutesBetweenStops;
                        }
                    }
                    return total;
                }

                public Optional<Stop> longestUnload() {
                    return stops.stream().max(Comparator.comparingInt(Stop::unloadMinutes));
                }

                public record Stop(String address, int unloadMinutes) {
                }
            }
            """;

    private Typing() {
    }
}
