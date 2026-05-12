package com.metricsstudio.analysis;

public enum AstMetricsSource {
    NONE("None"),
    JAVA_PARSER("Java (AST)"),
    WEB_HEURISTIC("Web (heuristic)"),
    COMBINED("Combined (Java AST + Web heuristic)");

    public final String displayName;

    AstMetricsSource(String displayName) {
        this.displayName = displayName;
    }
}
