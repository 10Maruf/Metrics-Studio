package com.metricsstudio.analysis;

public final class AstMetrics {
    public final int packageCount;
    public final int subPackageCount;

    public final int classCount;
    public final int interfaceCount;
    public final int methodCount;
    public final double averageMethodsPerClass;

    public final long executableLoc;

    public final long averageCharactersPerClass;

    public final int halsteadDistinctOperator;
    public final int halsteadDistinctOperands;
    public final long halsteadTotalOperators;
    public final long halsteadTotalOperands;

    /**
     * Approximation of Halstead's n2* (unique input/output parameters).
     * Currently computed as the number of unique method/constructor parameter
     * names.
     */
    public final int halsteadUniqueIoParams;

    public final int designPatternCount;

    public final int parseFailureCount;

    public AstMetrics(
            int packageCount,
            int subPackageCount,
            int classCount,
            int interfaceCount,
            int methodCount,
            double averageMethodsPerClass,
            long executableLoc,
            long averageCharactersPerClass,
            int halsteadDistinctOperators,
            int halsteadDistinctOperands,
            long halsteadTotalOperators,
            long halsteadTotalOperands,
            int halsteadUniqueIoParams,
            int designPatternCount,
            int parseFailureCount) {
        this.packageCount = packageCount;
        this.subPackageCount = subPackageCount;
        this.classCount = classCount;
        this.interfaceCount = interfaceCount;
        this.methodCount = methodCount;
        this.averageMethodsPerClass = averageMethodsPerClass;
        this.executableLoc = executableLoc;
        this.averageCharactersPerClass = averageCharactersPerClass;
        this.halsteadDistinctOperator = halsteadDistinctOperators;
        this.halsteadDistinctOperands = halsteadDistinctOperands;
        this.halsteadTotalOperators = halsteadTotalOperators;
        this.halsteadTotalOperands = halsteadTotalOperands;
        this.halsteadUniqueIoParams = halsteadUniqueIoParams;
        this.designPatternCount = designPatternCount;
        this.parseFailureCount = parseFailureCount;
    }
}
