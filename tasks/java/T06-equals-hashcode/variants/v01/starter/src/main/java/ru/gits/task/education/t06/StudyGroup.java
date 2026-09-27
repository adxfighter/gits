package ru.gits.task.education.t06;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Students of a study group. A student is included at most once.
 */
public final class StudyGroup {

    private final String name;
    private final Set<StudentId> students = new HashSet<>();

    public StudyGroup(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    /** @return whether the student was added (false if already in the group) */
    public boolean add(StudentId student) {
        return students.add(Objects.requireNonNull(student, "student"));
    }

    public void addAll(Collection<StudentId> imported) {
        imported.forEach(this::add);
    }

    public boolean contains(StudentId student) {
        return students.contains(student);
    }

    public boolean remove(StudentId student) {
        return students.remove(student);
    }

    public int size() {
        return students.size();
    }

    public String name() {
        return name;
    }
}
