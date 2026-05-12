package com.metricsstudio.text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class TextMetricsCalculator {

    private enum SingleQuoteMode {
        NONE,
        CHAR,
        STRING
    }

    private enum CommentSyntax {
        JAVA(true, true, false, false, SingleQuoteMode.CHAR, true),
        C_LIKE(true, true, false, false, SingleQuoteMode.STRING, true),
        PHP(true, true, true, false, SingleQuoteMode.STRING, true),
        CSS(false, true, false, false, SingleQuoteMode.NONE, false),
        HTML(false, false, false, true, SingleQuoteMode.NONE, false),
        BLADE_PHP(true, true, true, true, SingleQuoteMode.STRING, true);

        final boolean supportsSlashSlash;
        final boolean supportsSlashStar;
        final boolean supportsHashLine;
        final boolean supportsHtmlComment;

        final SingleQuoteMode singleQuoteMode;
        final boolean supportsDoubleQuoteStrings;

        CommentSyntax(
                boolean supportsSlashSlash,
                boolean supportsSlashStar,
                boolean supportsHashLine,
                boolean supportsHtmlComment,
                SingleQuoteMode singleQuoteMode,
                boolean supportsDoubleQuoteStrings) {
            this.supportsSlashSlash = supportsSlashSlash;
            this.supportsSlashStar = supportsSlashStar;
            this.supportsHashLine = supportsHashLine;
            this.supportsHtmlComment = supportsHtmlComment;
            this.singleQuoteMode = singleQuoteMode;
            this.supportsDoubleQuoteStrings = supportsDoubleQuoteStrings;
        }

        static CommentSyntax forPath(Path file) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);

            if (name.endsWith(".java"))
                return JAVA;
            if (name.endsWith(".blade.php"))
                return BLADE_PHP;
            if (name.endsWith(".php"))
                return PHP;
            if (name.endsWith(".js") || name.endsWith(".jsx") || name.endsWith(".ts") || name.endsWith(".tsx"))
                return C_LIKE;
            if (name.endsWith(".css"))
                return CSS;
            if (name.endsWith(".html") || name.endsWith(".htm"))
                return HTML;

            return C_LIKE;
        }
    }

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

            CommentSyntax syntax = CommentSyntax.forPath(file);
            LineMetrics lm = computeLineMetrics(content, syntax);
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

    LineMetrics computeLineMetrics(String content, CommentSyntax syntax) {
        // Basic comment counter that avoids counting comment tokens inside
        // strings/chars for languages that have them.
        String[] lines = content.split("\\R", -1);

        boolean inBlockComment = false; // /* ... */
        boolean inHtmlComment = false; // <!-- ... -->
        boolean inDoubleString = false;
        boolean inSingleString = false;
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

                // HTML block comments: <!-- ... -->
                if (syntax.supportsHtmlComment) {
                    if (inHtmlComment) {
                        hasComment = true;
                        if (c == '-' && next == '-' && (i + 2 < line.length()) && line.charAt(i + 2) == '>') {
                            inHtmlComment = false;
                            i += 2;
                        }
                        continue;
                    }

                    if (!inBlockComment && !inDoubleString && !inSingleString && !inChar) {
                        if (c == '<' && next == '!' && (i + 3 < line.length())
                                && line.charAt(i + 2) == '-' && line.charAt(i + 3) == '-') {
                            hasComment = true;
                            inHtmlComment = true;
                            i += 3;
                            continue;
                        }
                    }
                }

                if (inBlockComment) {
                    hasComment = true;
                    if (c == '*' && next == '/') {
                        inBlockComment = false;
                        i++;
                    }
                    continue;
                }

                if (inDoubleString) {
                    if (c == '\\' && i + 1 < line.length()) {
                        i++;
                        continue;
                    }
                    if (c == '"') {
                        inDoubleString = false;
                    }
                    hasCodeOutsideComment = true;
                    continue;
                }

                if (inSingleString) {
                    if (c == '\\' && i + 1 < line.length()) {
                        i++;
                        continue;
                    }
                    if (c == '\'') {
                        inSingleString = false;
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

                if (syntax.supportsDoubleQuoteStrings && c == '"') {
                    inDoubleString = true;
                    hasCodeOutsideComment = true;
                    continue;
                }

                if (c == '\'' && syntax.singleQuoteMode != SingleQuoteMode.NONE) {
                    if (syntax.singleQuoteMode == SingleQuoteMode.CHAR) {
                        inChar = true;
                    } else {
                        inSingleString = true;
                    }
                    hasCodeOutsideComment = true;
                    continue;
                }

                if (!inDoubleString && !inSingleString && !inChar) {
                    if (syntax.supportsSlashSlash && c == '/' && next == '/') {
                        hasComment = true;
                        break;
                    }
                    if (syntax.supportsSlashStar && c == '/' && next == '*') {
                        hasComment = true;
                        inBlockComment = true;
                        i++;
                        continue;
                    }
                    if (syntax.supportsHashLine && c == '#') {
                        hasComment = true;
                        break;
                    }
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
