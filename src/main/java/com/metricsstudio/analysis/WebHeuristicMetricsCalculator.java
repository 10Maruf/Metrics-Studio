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

    private static final Pattern PHP_NAMESPACE = Pattern
            .compile("(?m)^\\s*namespace\\s+([A-Za-z_\\\\][A-Za-z0-9_\\\\]*)\\s*;");

    private static final Pattern PHP_CLASS = Pattern.compile("\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)\\b");
    private static final Pattern PHP_INTERFACE = Pattern.compile("\\binterface\\s+([A-Za-z_][A-Za-z0-9_]*)\\b");
    private static final Pattern PHP_METHOD_NAMED = Pattern.compile("\\bfunction\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");

    private static final Pattern JS_CLASS = Pattern.compile("\\bclass\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");
    private static final Pattern JS_INTERFACE = Pattern.compile("\\binterface\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");
    private static final Pattern JS_FUNCTION = Pattern.compile("\\bfunction\\b");
    // Count arrow function definitions, avoid counting type arrows like "() =>
    // void" by requiring assignment.
    private static final Pattern JS_ARROW_DEF = Pattern.compile(
            "(?m)\\b[A-Za-z_$][A-Za-z0-9_$]*\\s*=\\s*(?:async\\s*)?(?:\\([^\\)]*\\)|[A-Za-z_$][A-Za-z0-9_$]*)\\s*=>");

    private static final Pattern JS_CLASS_METHOD_DECL = Pattern.compile(
            "^\\s*(?:(?:public|private|protected|static|async|get|set|readonly)\\s+)*(?!if\\b|for\\b|while\\b|switch\\b|catch\\b)([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(");

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
        long totalClassChars = 0;
        int classCountForAvgChars = 0;

        int designPatternCount = 0;

        Set<String> packages = new HashSet<>();

        Halstead halstead = new Halstead();

        for (Path file : codeFiles) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String sanitized = sanitizeForTokenization(content, languageFor(file));

            packages.addAll(extractPackages(file, sanitized, codeFiles));

            Counts counts = countDesignTokens(sanitized, file);
            classCount += counts.classCount;
            interfaceCount += counts.interfaceCount;
            methodCount += counts.methodCount;
            executableLoc += counts.executableLoc;

            List<ClassBlock> blocks = findClassBlocks(sanitized, file);
            for (ClassBlock b : blocks) {
                classCountForAvgChars++;
                totalClassChars += computeCharLength(content, b.startIndexInclusive, b.endIndexInclusive);
                if (languageFor(file) == Lang.JS_TS) {
                    // JS/TS: count class methods (not counted by 'function' keyword).
                    methodCount += countTopLevelJsMethods(
                            sanitized.substring(b.startIndexInclusive, b.endIndexInclusive + 1));
                }
                if (looksLikeSingleton(sanitized.substring(b.startIndexInclusive, b.endIndexInclusive + 1), b.className,
                        languageFor(file))) {
                    designPatternCount++;
                }
            }

            accumulateHalsteadTokens(sanitized, file, halstead);
        }

        int packageCount = packages.isEmpty() ? 0 : packages.size();
        int subPackageCount = computeSubPackageCount(packages);

        double avgMethods = classCount == 0 ? 0.0 : ((double) methodCount / (double) classCount);
        long avgCharsPerClass = classCountForAvgChars == 0 ? 0L : (totalClassChars / classCountForAvgChars);

        return new AstMetrics(
                packageCount,
                subPackageCount,
                classCount,
                interfaceCount,
                methodCount,
                avgMethods,
                executableLoc,
                avgCharsPerClass,
                halstead.distinctOperators.size(),
                halstead.distinctOperands.size(),
                halstead.totalOperators,
                halstead.totalOperands,
                0,
                designPatternCount,
                0);
    }

    private static final class ClassBlock {
        final String className;
        final int startIndexInclusive;
        final int endIndexInclusive;

        ClassBlock(String className, int startIndexInclusive, int endIndexInclusive) {
            this.className = className;
            this.startIndexInclusive = startIndexInclusive;
            this.endIndexInclusive = endIndexInclusive;
        }
    }

    private static long computeCharLength(String content, int startInclusive, int endInclusive) {
        if (content == null)
            return 0;
        int start = Math.max(0, startInclusive);
        int end = Math.min(content.length() - 1, endInclusive);
        if (end < start)
            return 0;
        return (long) (end - start + 1);
    }

    private static List<ClassBlock> findClassBlocks(String sanitized, Path file) {
        List<ClassBlock> blocks = new ArrayList<>();
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        Pattern classPattern = (name.endsWith(".php") || name.endsWith(".blade.php")) ? PHP_CLASS : JS_CLASS;

        Matcher m = classPattern.matcher(sanitized);
        while (m.find()) {
            String className = m.group(1);
            int afterDecl = m.end();

            int open = indexOfChar(sanitized, '{', afterDecl);
            if (open < 0)
                continue;
            int close = findMatchingBrace(sanitized, open);
            if (close < 0)
                continue;
            blocks.add(new ClassBlock(className, open, close));
        }
        return blocks;
    }

    private static int indexOfChar(String s, char ch, int start) {
        int i = Math.max(0, start);
        while (i < s.length()) {
            if (s.charAt(i) == ch)
                return i;
            i++;
        }
        return -1;
    }

    private static int findMatchingBrace(String sanitized, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < sanitized.length(); i++) {
            char c = sanitized.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int countTopLevelJsMethods(String classBlockSanitized) {
        if (classBlockSanitized == null || classBlockSanitized.isBlank()) {
            return 0;
        }

        int depth = 0;
        int count = 0;
        String[] lines = classBlockSanitized.split("\\R", -1);
        for (String line : lines) {
            int depthAtLineStart = depth;
            if (depthAtLineStart == 1) {
                Matcher m = JS_CLASS_METHOD_DECL.matcher(line);
                if (m.find()) {
                    count++;
                }
            }
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
            }
        }
        return Math.max(0, count);
    }

    private static boolean looksLikeSingleton(String classBodySanitized, String className, Lang lang) {
        if (classBodySanitized == null)
            return false;
        String s = classBodySanitized;

        if (lang == Lang.PHP) {
            boolean hasStaticInstance = s.matches("(?s).*\\bstatic\\s+\\$instance\\b.*");
            boolean hasGetInstance = s.matches("(?s).*\\bfunction\\s+getInstance\\b.*");
            boolean constructsSelf = s.matches("(?s).*\\bnew\\s+(?:self|static)\\b.*");
            return hasStaticInstance && hasGetInstance && constructsSelf;
        }

        // JS / TS heuristic
        boolean hasStaticInstance = s.matches("(?s).*\\bstatic\\s+(?:#?instance|INSTANCE)\\b.*");
        boolean hasGetInstance = s.matches("(?s).*\\bgetInstance\\s*\\(.*");
        boolean constructsSelf = className != null && !className.isBlank()
                && s.matches("(?s).*\\bnew\\s+" + Pattern.quote(className) + "\\b.*");
        return hasStaticInstance && hasGetInstance && constructsSelf;
    }

    private static Set<String> extractPackages(Path file, String sanitized, List<Path> allCodeFiles) {
        Set<String> pkgs = new HashSet<>();

        // PHP namespaces.
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".php") || name.endsWith(".blade.php")) {
            Matcher m = PHP_NAMESPACE.matcher(sanitized);
            while (m.find()) {
                String ns = m.group(1);
                if (ns != null && !ns.isBlank()) {
                    pkgs.add(normalizePackage(ns));
                }
            }
        }

        // Directory-based packages for all code.
        Path base = commonAncestor(allCodeFiles);
        if (base != null) {
            Path parent = file.getParent();
            if (parent != null) {
                String rel = base.relativize(parent).toString().replace('\\', '/');
                if (!rel.isBlank()) {
                    pkgs.add(normalizePackage(rel));
                }
            }
        }

        return pkgs;
    }

    private static Path commonAncestor(List<Path> files) {
        if (files == null || files.isEmpty()) {
            return null;
        }
        Path base = files.get(0).toAbsolutePath().getParent();
        if (base == null)
            return null;

        for (Path f : files) {
            Path p = f.toAbsolutePath().getParent();
            if (p == null)
                continue;
            while (base != null && !p.startsWith(base)) {
                base = base.getParent();
            }
            if (base == null)
                return null;
        }
        return base;
    }

    private static String normalizePackage(String raw) {
        String s = raw.trim();
        s = s.replace('\\', '.').replace('/', '.');
        s = s.replaceAll("\\.+", ".");
        if (s.startsWith("."))
            s = s.substring(1);
        if (s.endsWith("."))
            s = s.substring(0, s.length() - 1);
        return s;
    }

    private static int computeSubPackageCount(Set<String> allPackages) {
        if (allPackages == null || allPackages.isEmpty()) {
            return 0;
        }

        Set<String> normalized = new HashSet<>();
        for (String p : allPackages) {
            if (p != null && !p.isBlank()) {
                normalized.add(p);
            }
        }
        if (normalized.isEmpty()) {
            return 0;
        }

        String base = longestCommonPackagePrefix(normalized);
        if (base.isEmpty()) {
            return Math.max(0, normalized.size() - 1);
        }

        int sub = normalized.size();
        if (normalized.contains(base)) {
            sub -= 1;
        }
        return Math.max(0, sub);
    }

    private static String longestCommonPackagePrefix(Set<String> packages) {
        String[] prefixParts = null;
        for (String pkg : packages) {
            String[] parts = pkg.split("\\.");
            if (prefixParts == null) {
                prefixParts = parts;
                continue;
            }

            int common = 0;
            int max = Math.min(prefixParts.length, parts.length);
            while (common < max && prefixParts[common].equals(parts[common])) {
                common++;
            }
            if (common == 0) {
                return "";
            }
            String[] newPrefix = new String[common];
            System.arraycopy(prefixParts, 0, newPrefix, 0, common);
            prefixParts = newPrefix;
        }

        if (prefixParts == null || prefixParts.length == 0) {
            return "";
        }
        return String.join(".", prefixParts);
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
            classCount = countRegex(sanitized, PHP_CLASS);
            interfaceCount = countRegex(sanitized, PHP_INTERFACE);
            methodCount = countRegex(sanitized, PHP_METHOD_NAMED);
        } else {
            // JS / TS
            classCount = countRegex(sanitized, JS_CLASS);
            interfaceCount = countRegex(sanitized, JS_INTERFACE);
            int fn = countRegex(sanitized, JS_FUNCTION);
            int arrowDefs = countRegex(sanitized, JS_ARROW_DEF);
            // Additional class methods will be counted by brace-based scan later; keep this
            // as baseline.
            methodCount = fn + arrowDefs;
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
