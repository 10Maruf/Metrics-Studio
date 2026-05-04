package com.metricsstudio.text;

public final class TextMetrics {
    public final long totalBytes;
    public final long totalCharacters;

    public final long loc;
    public final long blankLoc;
    public final long cloc;

    public TextMetrics(long totalBytes, long totalCharacters, long loc, long blankLoc, long cloc) {
        this.totalBytes = totalBytes;
        this.totalCharacters = totalCharacters;
        this.loc = loc;
        this.blankLoc = blankLoc;
        this.cloc = cloc;
    }
}
