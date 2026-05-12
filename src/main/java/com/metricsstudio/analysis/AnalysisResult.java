package com.metricsstudio.analysis;

import java.nio.file.Path;
import java.util.Objects;

public final class AnalysisResult {
        public final Path rootPath;
        public final ProjectProfile profile;

        public final AstMetricsSource astSource;

        public final int analyzedFileCount;
        public final int javaFileCount;
        public final int webFileCount;

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
                        ProjectProfile profile,
                        AstMetricsSource astSource,
                        int analyzedFileCount,
                        int javaFileCount,
                        int webFileCount,
                        long totalBytes,
                        long totalCharacters,
                        long loc,
                        long blankLoc,
                        long cloc,
                        double commentDensity,
                        AstMetrics ast) {
                this.rootPath = Objects.requireNonNull(rootPath, "rootPath");
                this.profile = Objects.requireNonNull(profile, "profile");
                this.astSource = Objects.requireNonNull(astSource, "astSource");
                this.analyzedFileCount = Math.max(0, analyzedFileCount);
                this.javaFileCount = javaFileCount;
                this.webFileCount = webFileCount;
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
                sb.append("Profile: ").append(profile.displayName).append('\n');
                sb.append("Files analyzed: ").append(analyzedFileCount).append('\n');
                if (javaFileCount > 0 || webFileCount > 0) {
                        sb.append("- Java: ").append(javaFileCount).append('\n');
                        sb.append("- Web: ").append(webFileCount).append('\n');
                }
                boolean hasAst = astSource != AstMetricsSource.NONE;
                sb.append('\n');
                sb.append("Determining Code Size:\n");
                sb.append("- LOC: ").append(loc).append('\n');
                sb.append("- Blank LOC: ").append(blankLoc).append('\n');
                sb.append("- CLOC: ").append(cloc).append('\n');
                sb.append("- NCLOC: ").append(ncloc).append('\n');
                long nonBlankLoc = Math.max(0, loc - blankLoc);
                sb.append("- Non-blank LOC: ").append(nonBlankLoc).append('\n');
                sb.append("  (Non-blank LOC = NCLOC + CLOC = ").append(ncloc).append(" + ").append(cloc)
                                .append(")\n");
                sb.append("- Executable LOC: ")
                                .append(hasAst ? String.valueOf(ast.executableLoc) : "N/A")
                                .append('\n');
                sb.append("- Density of comments: ")
                                .append(String.format(java.util.Locale.ROOT, "%.4f", commentDensity))
                                .append('\n');
                sb.append('\n');
                sb.append("Halstead's Approach:\n");

                if (!hasAst) {
                        sb.append("- N/A (no AST/token metrics available)\n");
                        sb.append('\n');

                        sb.append("Storage & text:\n");
                        sb.append("- Bytes: ").append(totalBytes).append('\n');
                        sb.append("- Characters: ").append(totalCharacters).append('\n');
                        sb.append("- Avg characters per class: N/A\n");
                        sb.append('\n');

                        sb.append("Determining Design Size:\n");
                        sb.append("- Sub-packages: N/A\n");
                        sb.append("- Classes: N/A\n");
                        sb.append("- Interfaces: N/A\n");
                        sb.append("- Design patterns: N/A\n");
                        sb.append("- Methods: N/A\n");
                        sb.append("- Avg methods per class: N/A\n");
                        return sb.toString();
                }

                sb.append("- Source: ").append(astSource.displayName).append('\n');

                long n1 = ast.halsteadDistinctOperator;
                long n2 = ast.halsteadDistinctOperands;
                long N1 = ast.halsteadTotalOperators;
                long N2 = ast.halsteadTotalOperands;
                long N = N1 + N2;
                long mu = n1 + n2;

                sb.append("- μ1 (unique operators): ").append(n1).append('\n');
                sb.append("- μ2 (unique operands): ").append(n2).append('\n');
                sb.append("- N1 (total operators): ").append(N1).append('\n');
                sb.append("- N2 (total operands): ").append(N2).append('\n');
                sb.append("- Length N = N1 + N2: ").append(N).append('\n');
                sb.append("- Vocabulary μ = μ1 + μ2: ").append(mu).append('\n');

                double volumeV = (mu > 1) ? (N * log2(mu)) : 0.0;
                sb.append("- Volume V = N × log2(μ): ").append(fmt4(volumeV)).append('\n');

                double estimatedLength = (n1 > 1 ? n1 * log2(n1) : 0.0) + (n2 > 1 ? n2 * log2(n2) : 0.0);
                sb.append("- Estimated program length: μ1×log2(μ1) + μ2×log2(μ2) = ").append(fmt4(estimatedLength))
                                .append('\n');

                int n2Star = ast.halsteadUniqueIoParams;
                double vStar = (2.0 + n2Star) > 1.0 ? ((2.0 + n2Star) * log2(2.0 + n2Star)) : 0.0;
                sb.append("- n2* (approx unique I/O params): ").append(n2Star).append('\n');
                sb.append("- Potential volume V* = (2 + n2*) × log2(2 + n2*): ").append(fmt4(vStar)).append('\n');

                double levelL = (volumeV > 0.0) ? (vStar / volumeV) : 0.0;
                double difficultyD = (levelL > 0.0) ? (1.0 / levelL) : 0.0;
                sb.append("- Level L = V* / V: ").append(fmt4(levelL)).append('\n');
                sb.append("- Difficulty D = 1 / L: ").append(fmt4(difficultyD)).append('\n');

                // Estimated level per provided formula: L' = (2/μ1) × (μ2/N2)
                double levelLEst = (n1 > 0 && N2 > 0) ? ((2.0 / (double) n1) * ((double) n2 / (double) N2)) : 0.0;
                double difficultyDEst = (levelLEst > 0.0) ? (1.0 / levelLEst) : 0.0;
                sb.append("- Estimated level L' = (2/μ1) × (μ2/N2): ").append(fmt4(levelLEst)).append('\n');
                sb.append("- Estimated difficulty D' = 1/L': ").append(fmt4(difficultyDEst)).append('\n');

                double effortE = (levelLEst > 0.0) ? (volumeV / levelLEst) : 0.0;
                double effortClosed = (mu > 1 && n2 > 0)
                                ? ((n1 * (double) N2 * (double) N * log2(mu)) / (2.0 * (double) n2))
                                : 0.0;
                sb.append("- Effort E = V / L' (used): ").append(fmt4(effortE)).append('\n');
                sb.append("- Effort (closed form) = μ1×N2×N×log2(μ) / (2×μ2): ").append(fmt4(effortClosed))
                                .append('\n');
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
                                .append(String.format(java.util.Locale.ROOT, "%.2f", ast.averageMethodsPerClass))
                                .append('\n');

                if (ast.parseFailureCount > 0) {
                        sb.append('\n');
                        sb.append("Warnings:\n");
                        sb.append("- Failed to parse ").append(ast.parseFailureCount)
                                        .append(" file(s); AST-based metrics may be incomplete.\n");
                }
                return sb.toString();
        }

        private static double log2(double x) {
                return Math.log(x) / Math.log(2.0);
        }

        private static String fmt4(double v) {
                return String.format(java.util.Locale.ROOT, "%.4f", v);
        }
}
