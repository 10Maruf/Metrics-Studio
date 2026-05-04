package com.metricsstudio.analysis;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.Range;
import com.github.javaparser.TokenRange;
import com.github.javaparser.JavaToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class AstMetricsCalculator {

    public AstMetrics compute(List<Path> javaFiles) throws IOException {
        ParserConfiguration config = new ParserConfiguration();
        config.setStoreTokens(true);
        JavaParser parser = new JavaParser(config);

        Set<String> packages = new HashSet<>();

        int classCount = 0;
        int interfaceCount = 0;
        int methodCount = 0;
        int methodCountForAvg = 0;
        int classCountForAvg = 0;

        long executableLoc = 0;

        long totalClassChars = 0;

        HalsteadAccumulator halstead = new HalsteadAccumulator();

        int designPatternCount = 0;
        int parseFailures = 0;

        for (Path file : javaFiles) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            int[] lineStartOffsets = computeLineStartOffsets(content);

            ParseResult<CompilationUnit> parsed = parser.parse(file);
            Optional<CompilationUnit> maybeCu = parsed.getResult();
            if (maybeCu.isEmpty()) {
                parseFailures++;
                continue;
            }

            CompilationUnit cu = maybeCu.get();

            cu.getPackageDeclaration().ifPresent(pd -> packages.add(pd.getNameAsString()));

            Set<Integer> executableLinesInFile = new HashSet<>();
            cu.findAll(Statement.class).forEach(stmt -> addRangeLines(stmt.getRange(), executableLinesInFile));
            executableLoc += executableLinesInFile.size();

            for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                if (type.isInterface()) {
                    interfaceCount++;
                } else {
                    classCount++;
                }

                int typeMethods = type.getMethods().size();
                int constructors = type.getConstructors().size();
                methodCount += typeMethods + constructors;

                if (!type.isInterface()) {
                    methodCountForAvg += typeMethods + constructors;
                    classCountForAvg++;

                    totalClassChars += computeNodeCharLength(type, content, lineStartOffsets);

                    if (looksLikeSingleton(type)) {
                        designPatternCount++;
                    }
                }
            }

            cu.getTokenRange().ifPresent(halstead::accumulate);
        }

        int packageCount = packages.size();
        int subPackageCount = packageCount;

        double avgMethods = (classCountForAvg == 0) ? 0.0 : ((double) methodCountForAvg / (double) classCountForAvg);
        long avgCharsPerClass = (classCountForAvg == 0) ? 0L : (totalClassChars / classCountForAvg);

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
                designPatternCount,
                parseFailures);
    }

    private static void addRangeLines(Optional<Range> maybeRange, Set<Integer> lines) {
        if (maybeRange.isEmpty())
            return;
        Range r = maybeRange.get();
        for (int line = r.begin.line; line <= r.end.line; line++) {
            lines.add(line);
        }
    }

    private static int[] computeLineStartOffsets(String content) {
        // lineStartOffsets[i] = char offset of the first char of line (i+1)
        int estimatedLines = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n')
                estimatedLines++;
        }

        int[] starts = new int[estimatedLines];
        int line = 0;
        starts[line] = 0;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
                if (line < starts.length)
                    starts[line] = i + 1;
            }
        }
        return starts;
    }

    private static long computeNodeCharLength(Node node, String content, int[] lineStarts) {
        Optional<Range> maybeRange = node.getRange();
        if (maybeRange.isEmpty())
            return 0;
        Range r = maybeRange.get();

        int beginOffset = toOffset(r.begin.line, r.begin.column, lineStarts);
        int endOffsetExclusive = toOffset(r.end.line, r.end.column, lineStarts) + 1;

        if (beginOffset < 0)
            beginOffset = 0;
        if (endOffsetExclusive < beginOffset)
            return 0;
        if (endOffsetExclusive > content.length())
            endOffsetExclusive = content.length();

        return (long) (endOffsetExclusive - beginOffset);
    }

    private static int toOffset(int line1Based, int column1Based, int[] lineStarts) {
        int lineIndex = Math.max(0, line1Based - 1);
        int colIndex = Math.max(0, column1Based - 1);
        if (lineIndex >= lineStarts.length)
            return -1;
        return lineStarts[lineIndex] + colIndex;
    }

    private static boolean looksLikeSingleton(ClassOrInterfaceDeclaration clazz) {
        if (clazz.isInterface())
            return false;
        String className = clazz.getNameAsString();

        boolean hasPrivateConstructor = clazz.getConstructors().stream().anyMatch(ConstructorDeclaration::isPrivate);
        if (!hasPrivateConstructor)
            return false;

        boolean hasStaticSelfField = clazz.getFields().stream()
                .anyMatch(field -> isStaticSelfTypeField(field, className));
        if (!hasStaticSelfField)
            return false;

        boolean hasPublicStaticSelfReturningMethod = clazz.getMethods().stream()
                .anyMatch(m -> isPublicStaticReturnType(m, className));
        return hasPublicStaticSelfReturningMethod;
    }

    private static boolean isStaticSelfTypeField(FieldDeclaration field, String className) {
        if (!field.isStatic())
            return false;
        if (!field.isPrivate())
            return false;
        return simpleTypeName(field.getElementType()).equals(className);
    }

    private static boolean isPublicStaticReturnType(MethodDeclaration method, String className) {
        if (!method.isPublic() || !method.isStatic())
            return false;
        Type type = method.getType();
        if (type instanceof VoidType)
            return false;
        return simpleTypeName(type).equals(className);
    }

    private static String simpleTypeName(Type type) {
        String raw = type.asString();
        int generic = raw.indexOf('<');
        if (generic >= 0)
            raw = raw.substring(0, generic);
        raw = raw.trim();
        int lastDot = raw.lastIndexOf('.');
        if (lastDot >= 0)
            raw = raw.substring(lastDot + 1);
        return raw;
    }

    private static final class HalsteadAccumulator {
        final Set<String> distinctOperators = new HashSet<>();
        final Set<String> distinctOperands = new HashSet<>();
        long totalOperators = 0;
        long totalOperands = 0;

        void accumulate(TokenRange tokenRange) {
            for (JavaToken token : tokenRange) {
                String text = token.getText();
                JavaToken.Category category = token.getCategory();
                if (category.isWhitespaceOrComment()) {
                    continue;
                }

                if (category.isIdentifier() || category.isLiteral()) {
                    totalOperands++;
                    distinctOperands.add(text);
                } else {
                    totalOperators++;
                    distinctOperators.add(text);
                }
            }
        }
    }
}
