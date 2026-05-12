package com.metricsstudio.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristic metrics for Web projects (JS/TS/PHP). This is not a full parser; it
 * tokenizes source after stripping comments/strings and computes approximate
 * Halstead/design counts.
 */
public final class WebHeuristicMetricsCalculator {

    private static final class Halstead {
        final Set<String> distinctOperators = new HashSet<>();
        final Set<String> distinctOperands = new HashSet<>();
        long totalOperators = 0;
        long totalOperands = 0;

        void addOperator(String op) {
            if (op == null || op.isEmpty())
                return;
            distinctOperators.add(op);
            totalOperators++;
        }

        void addOperand(String operand) {
            if (operand == null || operand.isEmpty())
                return;
            distinctOperands.add(operand);
            totalOperands++;
        }
    }

    private enum Lang {
        JS_TS,
        PHP
    }

    public AstMetrics compute(List<Path> webFiles) throws IOException {
        if (webFiles == null || webFiles.isEmpty()) {
            return AstMetrics.empty();
        }

        List<Path> codeFiles = filterCodeFiles(webFiles);
        if (codeFiles.isEmpty()) {
            return AstMetrics.empty();
        }

        int classCount = 0;
        int interfaceCount = 0;
        int methodCount = 0;
        long executableLoc = 0;

        Halstead halstead = new Halstead();

        for (Path file : codeFiles) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String sanitized = sanitizeForTokenization(content, languageFor(file));

            Counts counts = countDesignTokens(sanitized, file);
            classCount += counts.classCount;
            interfaceCount += counts.interfaceCount;
            methodCount += counts.methodCount;
            executableLoc += counts.executableLoc;

            accumulateHalsteadTokens(sanitized, file, halstead);
        }

        double avgMethods = classCount == 0 ? 0.0 : ((double) methodCount / (double) classCount);

        return new AstMetrics(
                0,
                0,
                classCount,
                interfaceCount,
                methodCount,
                avgMethods,
                executableLoc,
                0L,
                halstead.distinctOperators.size(),
                halstead.distinctOperands.size(),
                halstead.totalOperators,
                halstead.totalOperands,
                0,
                0,
                0);
    }

    private static final class Counts {
        final int classCount;
        final int interfaceCount;
        final int methodCount;
        final long executableLoc;

        Counts(int classCount, int interfaceCount, int methodCount, long executableLoc) {
            this.classCount = classCount;
            this.interfaceCount = interfaceCount;
            this.methodCount = methodCount;
            this.executableLoc = executableLoc;
        }
    }

    private static Counts countDesignTokens(String sanitized, Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);

        int classCount = 0;
        int interfaceCount = 0;
        int methodCount = 0;

        if (name.endsWith(".php") || name.endsWith(".blade.php")) {
            classCount = countRegex(sanitized, Pattern.compile("\\bclass\\s+[A-Za-z_][A-Za-z0-9_]*\\b"));
            interfaceCount = countRegex(sanitized, Pattern.compile("\\binterface\\s+[A-Za-z_][A-Za-z0-9_]*\\b"));
            methodCount = countRegex(sanitized, Pattern.compile("\\bfunction\\b"));
        } else {
            // JS / TS
            classCount = countRegex(sanitized, Pattern.compile("\\bclass\\s+[A-Za-z_$][A-Za-z0-9_$]*\\b"));
            interfaceCount = countRegex(sanitized, Pattern.compile("\\binterface\\s+[A-Za-z_$][A-Za-z0-9_$]*\\b"));
            int fn = countRegex(sanitized, Pattern.compile("\\bfunction\\b"));
            int arrows = countRegex(sanitized, Pattern.compile("=>"));
            methodCount = fn + arrows;
        }

        long executableLoc = countExecutableLoc(sanitized);
        return new Counts(classCount, interfaceCount, methodCount, executableLoc);
    }

    private static long countExecutableLoc(String sanitized) {
        String[] lines = sanitized.split("\\R", -1);
        long count = 0;
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty())
                continue;
            // ignore lines that are only delimiters
            String stripped = t.replaceAll("[{}();,]", "").trim();
            if (stripped.isEmpty())
                continue;
            // require at least some identifier/number-like char
            if (stripped.matches(".*[A-Za-z0-9_$].*")) {
                count++;
            }
        }
        return count;
    }

    private static int countRegex(String text, Pattern p) {
        int n = 0;
        Matcher m = p.matcher(text);
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static List<Path> filterCodeFiles(List<Path> webFiles) {
        List<Path> out = new ArrayList<>();
        for (Path p : webFiles) {
            String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".js") || name.endsWith(".jsx") || name.endsWith(".ts") || name.endsWith(".tsx")
                    || name.endsWith(".php") || name.endsWith(".blade.php")) {
                out.add(p);
            }
        }
        return out;
    }

    private static Lang languageFor(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".php") || name.endsWith(".blade.php"))
            return Lang.PHP;
        return Lang.JS_TS;
    }

    private static String sanitizeForTokenization(String content, Lang lang) {
        // Preserve newlines. Replace comment and string content with spaces.
        StringBuilder out = new StringBuilder(content.length());

        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        char stringQuote = 0;
        boolean inBladeComment = false; // {{-- --}}

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            char next = (i + 1 < content.length()) ? content.charAt(i + 1) : '\0';

            if (c == '\n') {
                inLineComment = false;
                out.append('\n');
                continue;
            }

            if (inBladeComment) {
                // end: --}}
                if (c == '-' && next == '-' && i + 2 < content.length() && content.charAt(i + 2) == '}'
                        && i + 3 < content.length() && content.charAt(i + 3) == '}') {
                    out.append("    ");
                    i += 3;
                    inBladeComment = false;
                } else {
                    out.append(' ');
                }
                continue;
            }

            if (inLineComment) {
                out.append(' ');
                continue;
            }

            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    out.append("  ");
                    i++;
                    inBlockComment = false;
                } else {
                    out.append(' ');
                }
                continue;
            }

            if (inString) {
                if (c == '\\' && i + 1 < content.length()) {
                    // escape
                    out.append("  ");
                    i++;
                    continue;
                }
                out.append(' ');
                if (c == stringQuote) {
                    inString = false;
                    stringQuote = 0;
                }
                continue;
            }

            // Blade comment start: {{--
            if (lang == Lang.PHP) {
                if (c == '{' && next == '{' && i + 3 < content.length() && content.charAt(i + 2) == '-'
                        && content.charAt(i + 3) == '-') {
                    out.append("    ");
                    i += 3;
                    inBladeComment = true;
                    continue;
                }
            }

            // string start
            if (c == '\'' || c == '"') {
                inString = true;
                stringQuote = c;
                out.append(' ');
                continue;
            }

            // comments
            if (c == '/' && next == '/') {
                inLineComment = true;
                out.append("  ");
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                out.append("  ");
                i++;
                continue;
            }
            if (lang == Lang.PHP && c == '#') {
                inLineComment = true;
                out.append(' ');
                continue;
            }

            out.append(c);
        }

        return out.toString();
    }

    private static void accumulateHalsteadTokens(String sanitized, Path file, Halstead halstead) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        Set<String> operatorKeywords = (name.endsWith(".php") || name.endsWith(".blade.php"))
                ? PHP_OPERATOR_KEYWORDS
                : JS_OPERATOR_KEYWORDS;

        int i = 0;
        while (i < sanitized.length()) {
            char c = sanitized.charAt(i);

            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // multi-char operators (check first)
            String op = matchOperatorAt(sanitized, i);
            if (op != null) {
                halstead.addOperator(op);
                i += op.length();
                continue;
            }

            if (isIdentifierStart(c)) {
                int start = i;
                i++;
                while (i < sanitized.length() && isIdentifierPart(sanitized.charAt(i))) {
                    i++;
                }
                String ident = sanitized.substring(start, i);
                if (operatorKeywords.contains(ident)) {
                    halstead.addOperator(ident);
                } else {
                    halstead.addOperand(ident);
                }
                continue;
            }

            if (Character.isDigit(c)) {
                int start = i;
                i++;
                while (i < sanitized.length()) {
                    char cc = sanitized.charAt(i);
                    if (Character.isDigit(cc) || cc == '.') {
                        i++;
                    } else {
                        break;
                    }
                }
                halstead.addOperand(sanitized.substring(start, i));
                continue;
            }

            // single char fallback operators / punctuation
            halstead.addOperator(String.valueOf(c));
            i++;
        }
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static final String[] OPERATORS = {
            "===", "!==", ">>>=", "<<=", ">>=", "==", "!=", "<=", ">=", "&&", "||", "++", "--",
            "+=", "-=", "*=", "/=", "%=", "=>", "->", "::", "??", "?.", "...", "**", "::=",
            "<<", ">>", "==="
    };

    private static String matchOperatorAt(String s, int index) {
        for (String op : OPERATORS) {
            if (index + op.length() <= s.length() && s.startsWith(op, index)) {
                return op;
            }
        }
        return null;
    }

    private static final Set<String> JS_OPERATOR_KEYWORDS = Set.of(
            "if", "else", "for", "while", "do", "switch", "case", "break", "continue",
            "return", "throw", "try", "catch", "finally",
            "new", "delete", "typeof", "instanceof", "in", "of",
            "await", "async", "yield",
            "function", "class", "extends", "implements", "interface",
            "public", "private", "protected", "static",
            "const", "let", "var", "import", "export", "from");

    private static final Set<String> PHP_OPERATOR_KEYWORDS = Set.of(
            "if", "elseif", "else", "for", "foreach", "while", "do", "switch", "case", "break", "continue",
            "return", "throw", "try", "catch", "finally",
            "new", "instanceof",
            "function", "class", "interface", "trait", "extends", "implements",
            "public", "private", "protected", "static",
            "namespace", "use", "require", "include");
}
