package com.huatai.careeragent.task;

import java.util.EnumMap;
import java.util.Map;

public enum WorkflowStatus {
    PENDING(0),
    MATCHING_JOB(10),
    ANALYZING_RESUME(40),
    GENERATING_QUESTIONS(70),
    GENERATING_FINAL_REPORT(90),
    SUCCESS(100),
    FAILED(100),
    /** Legacy document states; never used by career analysis execution. */
    @Deprecated PARSING_FILE(0),
    @Deprecated EMBEDDING(0);

    private static final Map<WorkflowStatus, WorkflowStatus> NEXT = new EnumMap<>(WorkflowStatus.class);

    static {
        NEXT.put(PENDING, MATCHING_JOB);
        NEXT.put(MATCHING_JOB, ANALYZING_RESUME);
        NEXT.put(ANALYZING_RESUME, GENERATING_QUESTIONS);
        NEXT.put(GENERATING_QUESTIONS, GENERATING_FINAL_REPORT);
        NEXT.put(GENERATING_FINAL_REPORT, SUCCESS);
    }

    private final int progress;

    WorkflowStatus(int progress) {
        this.progress = progress;
    }

    public int progress() {
        return progress;
    }

    public boolean canTransitionTo(WorkflowStatus next) {
        return next == FAILED && !isTerminal() || NEXT.get(this) == next;
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }
}
