package com.tars.model.enums;

public enum AnalysisStatus {
    PENDING,      // Gemini not yet called or in flight
    COMPLETED,    // Gemini responded and parsed successfully
    FAILED,       // API quota / key error - hard failure
    UNRESOLVED,    // Gemini responded but JSON parsing failed twice
}
