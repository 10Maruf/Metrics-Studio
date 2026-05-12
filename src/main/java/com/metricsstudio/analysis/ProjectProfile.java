package com.metricsstudio.analysis;

/**
 * Defines what kind of source files should be scanned and how results are
 * interpreted.
 */
public enum ProjectProfile {
    JAVA("Java"),
    WEB("Web (React/Laravel)"),
    MIXED("Mixed (Java + Web)");

    public final String displayName;

    ProjectProfile(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
