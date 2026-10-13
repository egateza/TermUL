package dev.egateza.termul.app.ui.streak;

/** Kurva easing untuk animasi streak; input dan output 0..1 (outBack sedikit melewati 1). */
final class Easing {

    private Easing() {
    }

    static double outBack(double x) {
        double c1 = 1.70158, c3 = c1 + 1;
        return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
    }

    static double inOutCubic(double x) {
        return x < .5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
    }

    static double lerp(double a, double b, double p) {
        return a + (b - a) * p;
    }
}
