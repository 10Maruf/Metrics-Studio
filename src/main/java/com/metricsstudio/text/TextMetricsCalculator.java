package com.metricsstudio.text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class TextMetricsCalculator {

    public TextMetrics compute(List<Path> files) throws IOException {
        long totalBytes = 0;
        long totalChars = 0;
        long loc = 0;
        long blank = 0;
        long cloc = 0;

        for (Path file : files) {
            byte[] bytes = Files.readAllBytes(file);
            totalBytes += bytes.length;

            String content = new String(bytes, StandardCharsets.UTF_8);
            totalChars += content.length();

            LineMetrics lm = computeLineMetrics(content);
            loc += lm.loc;
            blank += lm.blank;
            cloc += lm.comment;
        }

        return new TextMetrics(totalBytes, totalChars, loc, blank, cloc);
    }

    private static final class LineMetrics {
        final long loc;
        final long blank;
        final long comment;

        private LineMetrics(long loc, long blank, long comment) {
            this.loc = loc;
            this.blank = blank;
            this.comment = comment;
        }
    }

    LineMetrics computeLineMetrics(String content) {
        // Basic Java-aware comment counter that avoids counting comment tokens inside
        // strings/chars.
        String[] lines = content.split("\\R", -1);

        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;

        long loc = 0;
        long blank = 0;
        long commentLines = 0;

        for (String line : lines) {
            loc++;

            if (line.trim().isEmpty()) {
                blank++;
                continue;
            }

            boolean hasCodeOutsideComment = false;
            boolean hasComment = false;

            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                char next = (i + 1 < line.length()) ? line.charAt(i + 1) : '\0';

                if (inBlockComment) {
                    hasComment = true;
                    if (c == '*' && next == '/') {
                        inBlockComment = false;
                        i++;
                    }
                    continue;
                }

                if (inString) {
                    if (c == '\\' && i + 1 < line.length()) {
                        i++;
                        continue;
                    }
                    if (c == '"') {
                        inString = false;
                    }
                    hasCodeOutsideComment = true;
                    continue;
                }

                if (inChar) {
                    if (c == '\\' && i + 1 < line.length()) {
                        i++;
                        continue;
                    }
                    if (c == '\'') {
                        inChar = false;
                    }
                    hasCodeOutsideComment = true;
                    continue;
                }

                if (c == '"') {
                    inString = true;
                    hasCodeOutsideComment = true;
                    continue;
                }
                if (c == '\'') {
                    inChar = true;
                    hasCodeOutsideComment = true;
                    continue;
                }

                if (c == '/' && next == '/') {
                    hasComment = true;
                    break;
                }
                if (c == '/' && next == '*') {
                    hasComment = true;
                    inBlockComment = true;
                    i++;
                    continue;
                }

                if (!Character.isWhitespace(c)) {
                    hasCodeOutsideComment = true;
                }
            }

            // Count comment-only lines as CLOC. (If the line has code + comment, we still
            // count it as commented line.)
            if (hasComment && !hasCodeOutsideComment) {
                commentLines++;
            } else if (hasComment) {
                commentLines++;
            }
        }

        return new LineMetrics(loc, blank, commentLines);
    }
}
