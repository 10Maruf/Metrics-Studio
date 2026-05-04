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

    public final double commentDensity;

    public final AstMetrics ast;

    public AnalysisResult(
            Path rootPath,
            int javaFileCount,
            long totalBytes,
            long totalCharacters,
            long loc,
            long blankLoc,
            long cloc,
            double commentDensity,
            AstMetrics ast) {
        this.rootPath = Objects.requireNonNull(rootPath, "rootPath");
        this.javaFileCount = javaFileCount;
        this.totalBytes = totalBytes;
        this.totalCharacters = totalCharacters;
        this.loc = loc;
        this.blankLoc = blankLoc;
        this.cloc = cloc;
        this.ncloc = Math.max(0, loc - blankLoc - cloc);
        this.commentDensity = commentDensity;
        this.ast = Objects.requireNonNull(ast, "ast");
    }

    public String toPrettyText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Project: ").append(rootPath).append('\n');
        sb.append("Java files analyzed: ").append(javaFileCount).append('\n');
        sb.append('\n');
        sb.append("Determining Code Size:\n");
        sb.append("- LOC: ").append(loc).append('\n');
        sb.append("- Blank LOC: ").append(blankLoc).append('\n');
        sb.append("- CLOC: ").append(cloc).append('\n');
        sb.append("- NCLOC: ").append(ncloc).append('\n');
        sb.append("- Executable LOC: ").append(ast.executableLoc).append('\n');
        sb.append("- Density of comments: ").append(String.format(java.util.Locale.ROOT, "%.4f", commentDensity))
                .append('\n');
        sb.append('\n');
        sb.append("Halstead's Approach:\n");
        sb.append("- Distinct operators (n1): ").append(ast.halsteadDistinctOperators).append('\n');
        sb.append("- Distinct operands (n2): ").append(ast.halsteadDistinctOperands).append('\n');
        sb.append("- Total operators (N1): ").append(ast.halsteadTotalOperators).append('\n');
        sb.append("- Total operands (N2): ").append(ast.halsteadTotalOperands).append('\n');
        sb.append('\n');

        sb.append("Storage & text:\n");
        sb.append("- Bytes: ").append(totalBytes).append('\n');
        sb.append("- Characters: ").append(totalCharacters).append('\n');
        sb.append("- Avg characters per class: ").append(ast.averageCharactersPerClass).append('\n');
        sb.append('\n');

        sb.append("Determining Design Size:\n");
        sb.append("- Sub-packages: ").append(ast.subPackageCount).append('\n');
        sb.append("- Classes: ").append(ast.classCount).append('\n');
        sb.append("- Interfaces: ").append(ast.interfaceCount).append('\n');
        sb.append("- Design patterns: ").append(ast.designPatternCount).append('\n');
        sb.append("- Methods: ").append(ast.methodCount).append('\n');
        sb.append("- Avg methods per class: ")
                .append(String.format(java.util.Locale.ROOT, "%.2f", ast.averageMethodsPerClass)).append('\n');

        if (ast.parseFailureCount > 0) {
            sb.append('\n');
            sb.append("Warnings:\n");
            sb.append("- Failed to parse ").append(ast.parseFailureCount)
                    .append(" file(s); AST-based metrics may be incomplete.\n");
        }
        return sb.toString();
    }
}
