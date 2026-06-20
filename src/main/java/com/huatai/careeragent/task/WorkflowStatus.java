package com.huatai.careeragent.task;

import java.util.EnumMap;
import java.util.Map;

public enum WorkflowStatus {
    PENDING(0),
    PARSING_FILE(10),
    EMBEDDING(30),
    MATCHING_JOB(50),
    ANALYZING_RESUME(65),
    GENERATING_QUESTIONS(80),
    SUCCESS(100),
    FAILED(100);

    private static final Map<WorkflowStatus, WorkflowStatus> NEXT = new EnumMap<>(WorkflowStatus.class);

    static {
        NEXT.put(PENDING, PARSING_FILE);
        NEXT.put(PARSING_FILE, EMBEDDING);
        NEXT.put(EMBEDDING, MATCHING_JOB);
        NEXT.put(MATCHING_JOB, ANALYZING_RESUME);
        NEXT.put(ANALYZING_RESUME, GENERATING_QUESTIONS);
        NEXT.put(GENERATING_QUESTIONS, SUCCESS);
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
