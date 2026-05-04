package com.metricsstudio.analysis;

import com.metricsstudio.scan.ProjectScanner;
import com.metricsstudio.text.TextMetrics;
import com.metricsstudio.text.TextMetricsCalculator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class ProjectAnalyzer {

    public AnalysisResult analyze(Path projectRoot) throws IOException {
        ProjectScanner scanner = ProjectScanner.defaultJavaScanner();
        List<Path> javaFiles = scanner.findJavaFiles(projectRoot);

        TextMetricsCalculator calc = new TextMetricsCalculator();
        TextMetrics textMetrics = calc.compute(javaFiles);

        return new AnalysisResult(
                projectRoot,
                javaFiles.size(),
                textMetrics.totalBytes,
                textMetrics.totalCharacters,
                textMetrics.loc,
                textMetrics.blankLoc,
                textMetrics.cloc);
    }
}
