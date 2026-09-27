package ru.gits.task.education.cal;

/**
 * Retype the fragment from {@link Reference} between the triple quotes below.
 */
final class Typing {

    static final String TYPED = """
            public final class GradeBook {

                private final Map<String, List<Integer>> grades = new TreeMap<>();

                public void add(String studentId, int grade) {
                    if (grade < 2 || grade > 5) {
                        throw new IllegalArgumentException("Grade must be from 2 to 5");
                    }
                    grades.computeIfAbsent(studentId, id -> new ArrayList<>()).add(grade);
                }

                public OptionalDouble average(String studentId) {
                    return grades.getOrDefault(studentId, List.of()).stream()
                            .mapToInt(Integer::intValue)
                            .average();
                }

                public List<String> excellentStudents() {
                    List<String> result = new ArrayList<>();
                    for (Map.Entry<String, List<Integer>> entry : grades.entrySet()) {
                        boolean allFives = entry.getValue().stream().allMatch(g -> g == 5);
                        if (allFives && !entry.getValue().isEmpty()) {
                            result.add(entry.getKey());
                        }
                    }
                    return result;
                }
            }
            """;

    private Typing() {
    }
}
