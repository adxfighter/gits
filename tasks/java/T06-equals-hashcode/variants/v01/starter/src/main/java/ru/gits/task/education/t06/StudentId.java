package ru.gits.task.education.t06;

import java.util.Objects;

/**
 * Identifies a student: university code and record book number.
 */
public final class StudentId {

    private final String universityCode;
    private final String recordBookNo;

    public StudentId(String universityCode, String recordBookNo) {
        this.universityCode = Objects.requireNonNull(universityCode, "universityCode");
        this.recordBookNo = Objects.requireNonNull(recordBookNo, "recordBookNo");
    }

    public String universityCode() {
        return universityCode;
    }

    public String recordBookNo() {
        return recordBookNo;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StudentId that)) {
            return false;
        }
        return universityCode.equals(that.universityCode) && recordBookNo.equals(that.recordBookNo);
    }

    @Override
    public String toString() {
        return universityCode + "/" + recordBookNo;
    }
}
