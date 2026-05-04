package com.metricsstudio.analysis;

import java.nio.file.Path;
import java.util.Objects;

public final class AnalysisResult {
    public final Path rootPath;
    public final int javaFileCount;

    public final long totalBytes;
    public final long totalCharacters;

    public final long loc;
    public final long blankLoc;
    public final long cloc;
    public final long ncloc;

    public AnalysisResult(
            Path rootPath,
            int javaFileCount,
            long totalBytes,
            long totalCharacters,
            long loc,
            long blankLoc,
            long cloc) {
        this.rootPath = Objects.requireNonNull(rootPath, "rootPath");
        this.javaFileCount = javaFileCount;
        this.totalBytes = totalBytes;
        this.totalCharacters = totalCharacters;
        this.loc = loc;
        this.blankLoc = blankLoc;
        this.cloc = cloc;
        this.ncloc = Math.max(0, loc - blankLoc - cloc);
    }

    public String toPrettyText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Project: ").append(rootPath).append('\n');
        sb.append("Java files analyzed: ").append(javaFileCount).append('\n');
        sb.append('\n');
        sb.append("Code size metrics (basic):\n");
        sb.append("- LOC: ").append(loc).append('\n');
        sb.append("- Blank LOC: ").append(blankLoc).append('\n');
        sb.append("- CLOC: ").append(cloc).append('\n');
        sb.append("- NCLOC: ").append(ncloc).append('\n');
        sb.append('\n');
        sb.append("Storage & text:\n");
        sb.append("- Bytes: ").append(totalBytes).append('\n');
        sb.append("- Characters: ").append(totalCharacters).append('\n');
        return sb.toString();
    }
}
