package com.metricsstudio.scan;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ProjectScanner {
    private final Set<String> excludedDirectoryNames;
    private final boolean skipTests;

    private ProjectScanner(Set<String> excludedDirectoryNames, boolean skipTests) {
        this.excludedDirectoryNames = excludedDirectoryNames;
        this.skipTests = skipTests;
    }

    public static ProjectScanner defaultJavaScanner() {
        Set<String> excluded = new HashSet<>();
        excluded.add(".git");
        excluded.add("target");
        excluded.add("build");
        excluded.add("out");
        excluded.add("node_modules");
        return new ProjectScanner(excluded, true);
    }

    public List<Path> findJavaFiles(Path projectRoot) throws IOException {
        if (projectRoot == null)
            throw new IllegalArgumentException("projectRoot is required");
        if (!Files.exists(projectRoot))
            throw new IllegalArgumentException("Project root does not exist: " + projectRoot);

        List<Path> results = new ArrayList<>();

        try (var stream = Files.walk(projectRoot)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .filter(p -> !isExcluded(p))
                    .filter(p -> !isTestPath(p))
                    .forEach(results::add);
        }

        return results;
    }

    private boolean isExcluded(Path path) {
        for (Path part : path) {
            String name = part.getFileName().toString();
            if (excludedDirectoryNames.contains(name))
                return true;
        }
        return false;
    }

    private boolean isTestPath(Path path) {
        if (!skipTests)
            return false;

        String normalized = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);

        if (normalized.contains("/src/test/"))
            return true;
        if (normalized.contains("/test/"))
            return true;
        if (normalized.contains("/tests/"))
            return true;

        String fileName = path.getFileName().toString();
        return fileName.endsWith("Test.java") || fileName.endsWith("Tests.java");
    }
}
