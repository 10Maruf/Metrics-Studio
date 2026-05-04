package com.metricsstudio.analysis;

import java.util.Objects;

/**
 * Basic COCOMO (Boehm 1981) effort and schedule estimation.
 *
 * Effort (person-months): E = a1 * (KLOC ^ a2)
 * Development time (months): Tdev = 2.5 * (E ^ b2)
 */
public final class CocomoBasic {

    private CocomoBasic() {
    }

    public enum Mode {
        ORGANIC("Organic", 2.4, 1.05, 0.38),
        SEMI_DETACHED("Semi-detached", 3.0, 1.12, 0.35),
        EMBEDDED("Embedded", 3.6, 1.20, 0.32);

        public final String displayName;
        public final double a1;
        public final double a2;
        public final double b1;
        public final double b2;

        Mode(String displayName, double a1, double a2, double b2) {
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            this.a1 = a1;
            this.a2 = a2;
            this.b1 = 2.5;
            this.b2 = b2;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public record Estimate(double effortPersonMonths, double tdevMonths) {
    }

    public static Estimate estimate(Mode mode, double kloc) {
        Objects.requireNonNull(mode, "mode");
        if (!(kloc >= 0.0) || Double.isNaN(kloc) || Double.isInfinite(kloc)) {
            throw new IllegalArgumentException("KLOC must be a finite number >= 0");
        }

        double effort = mode.a1 * Math.pow(kloc, mode.a2);
        double tdev = mode.b1 * Math.pow(effort, mode.b2);
        return new Estimate(effort, tdev);
    }
}
