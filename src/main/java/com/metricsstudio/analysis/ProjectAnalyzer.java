package com.metricsstudio.analysis;

import com.metricsstudio.scan.ProjectScanner;
import com.metricsstudio.text.TextMetrics;
import com.metricsstudio.text.TextMetricsCalculator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ProjectAnalyzer {

    public AnalysisResult analyze(Path projectRoot) throws IOException {
        return analyze(projectRoot, ProjectProfile.JAVA);
    }

    public AnalysisResult analyze(Path projectRoot, ProjectProfile profile) throws IOException {
        ProjectScanner scanner = ProjectScanner.defaultJavaScanner();

        List<Path> javaFiles = List.of();
        List<Path> webFiles = List.of();

        if (profile == null) {
            profile = ProjectProfile.JAVA;
        }

        switch (profile) {
            case JAVA -> javaFiles = scanner.findJavaFiles(projectRoot);
            case WEB -> webFiles = scanner.findWebFiles(projectRoot);
            case MIXED -> {
                javaFiles = scanner.findJavaFiles(projectRoot);
                webFiles = scanner.findWebFiles(projectRoot);
            }
        }

        Set<Path> unique = new LinkedHashSet<>();
        unique.addAll(javaFiles);
        unique.addAll(webFiles);
        List<Path> allFiles = new ArrayList<>(unique);

        TextMetricsCalculator calc = new TextMetricsCalculator();
        TextMetrics textMetrics = calc.compute(allFiles);

        AstMetrics astMetrics = AstMetrics.empty();
        AstMetricsSource astSource = AstMetricsSource.NONE;

        AstMetrics javaAst = AstMetrics.empty();
        AstMetrics webAst = AstMetrics.empty();

        if (!javaFiles.isEmpty()) {
            AstMetricsCalculator astCalc = new AstMetricsCalculator();
            javaAst = astCalc.compute(javaFiles);
        }
        if (!webFiles.isEmpty()) {
            WebHeuristicMetricsCalculator webCalc = new WebHeuristicMetricsCalculator();
            webAst = webCalc.compute(webFiles);
        }

        if (!javaFiles.isEmpty() && !webFiles.isEmpty()) {
            astMetrics = merge(javaAst, webAst);
            astSource = AstMetricsSource.COMBINED;
        } else if (!javaFiles.isEmpty()) {
            astMetrics = javaAst;
            astSource = AstMetricsSource.JAVA_PARSER;
        } else if (!webFiles.isEmpty()) {
            astMetrics = webAst;
            astSource = AstMetricsSource.WEB_HEURISTIC;
        }

        double commentDensity = 0.0;
        long nonBlankLoc = Math.max(0, textMetrics.loc - textMetrics.blankLoc);
        if (nonBlankLoc > 0) {
            commentDensity = (double) textMetrics.cloc / (double) nonBlankLoc;
        }

        return new AnalysisResult(
                projectRoot,
                profile,
                astSource,
                allFiles.size(),
                javaFiles.size(),
                webFiles.size(),
                textMetrics.totalBytes,
                textMetrics.totalCharacters,
                textMetrics.loc,
                textMetrics.blankLoc,
                textMetrics.cloc,
                commentDensity,
                astMetrics);
    }

    private static AstMetrics merge(AstMetrics a, AstMetrics b) {
        if (a == null)
            return b;
        if (b == null)
            return a;

        int classCount = a.classCount + b.classCount;
        int interfaceCount = a.interfaceCount + b.interfaceCount;
        int methodCount = a.methodCount + b.methodCount;

        double avgMethods = classCount == 0 ? 0.0 : ((double) methodCount / (double) classCount);

        long totalCharsEstimate = ((long) a.averageCharactersPerClass * (long) Math.max(0, a.classCount))
                + ((long) b.averageCharactersPerClass * (long) Math.max(0, b.classCount));
        long avgChars = classCount == 0 ? 0L : (totalCharsEstimate / classCount);

        // Distinct operator/operand union cannot be computed from counts alone; sum is
        // an upper bound.
        int distinctOps = a.halsteadDistinctOperator + b.halsteadDistinctOperator;
        int distinctOperands = a.halsteadDistinctOperands + b.halsteadDistinctOperands;

        return new AstMetrics(
                a.packageCount + b.packageCount,
                a.subPackageCount + b.subPackageCount,
                classCount,
                interfaceCount,
                methodCount,
                avgMethods,
                a.executableLoc + b.executableLoc,
                avgChars,
                distinctOps,
                distinctOperands,
                a.halsteadTotalOperators + b.halsteadTotalOperators,
                a.halsteadTotalOperands + b.halsteadTotalOperands,
                a.halsteadUniqueIoParams + b.halsteadUniqueIoParams,
                a.designPatternCount + b.designPatternCount,
                a.parseFailureCount + b.parseFailureCount);
    }
}
