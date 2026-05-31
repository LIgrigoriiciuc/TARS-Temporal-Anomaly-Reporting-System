package com.tars.model.enums;

public enum ReportStatus {
    DRAFT,
    PENDING_ANALYSIS,
    CONFIRMED,
    REJECTED,
    FLAGGED         // Prompt injection detected — report quarantined, excluded from all analysis and corroboration

    }
