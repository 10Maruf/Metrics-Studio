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

        AstMetrics astMetrics;
        if (javaFiles.isEmpty()) {
            astMetrics = AstMetrics.empty();
        } else {
            AstMetricsCalculator astCalc = new AstMetricsCalculator();
            astMetrics = astCalc.compute(javaFiles);
        }

        double commentDensity = 0.0;
        long nonBlankLoc = Math.max(0, textMetrics.loc - textMetrics.blankLoc);
        if (nonBlankLoc > 0) {
            commentDensity = (double) textMetrics.cloc / (double) nonBlankLoc;
        }

        return new AnalysisResult(
                projectRoot,
                profile,
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
}
