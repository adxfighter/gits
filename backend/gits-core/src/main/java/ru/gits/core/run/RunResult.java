package ru.gits.core.run;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import ru.gits.core.common.BaseEntity;

@Entity
@Table(name = "run_result")
public class RunResult extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_job_id", nullable = false, unique = true)
    private RunJob runJob;

    @Column(nullable = false)
    private boolean compiled;

    @Column(name = "compile_output")
    private String compileOutput;

    @Column(name = "tests_total", nullable = false)
    private int testsTotal;

    @Column(name = "tests_passed", nullable = false)
    private int testsPassed;

    /** JSON array of {name, status, message}; hidden test names are anonymised by the runner. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "test_cases", nullable = false)
    private String testCases;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "stdout_trunc")
    private String stdoutTrunc;

    @Column(name = "stderr_trunc")
    private String stderrTrunc;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RunResult() {
    }

    public RunResult(RunJob runJob, boolean compiled, String compileOutput, int testsTotal, int testsPassed,
                     String testCases, Long durationMs, String stdoutTrunc, String stderrTrunc, Instant createdAt) {
        this.runJob = runJob;
        this.compiled = compiled;
        this.compileOutput = compileOutput;
        this.testsTotal = testsTotal;
        this.testsPassed = testsPassed;
        this.testCases = testCases;
        this.durationMs = durationMs;
        this.stdoutTrunc = stdoutTrunc;
        this.stderrTrunc = stderrTrunc;
        this.createdAt = createdAt;
    }

    public RunJob getRunJob() {
        return runJob;
    }

    public boolean isCompiled() {
        return compiled;
    }

    public String getCompileOutput() {
        return compileOutput;
    }

    public int getTestsTotal() {
        return testsTotal;
    }

    public int getTestsPassed() {
        return testsPassed;
    }

    public String getTestCases() {
        return testCases;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getStdoutTrunc() {
        return stdoutTrunc;
    }

    public String getStderrTrunc() {
        return stderrTrunc;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
