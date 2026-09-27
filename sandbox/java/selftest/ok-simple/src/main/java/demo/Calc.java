package demo;

public final class Calc {

    private Calc() {
    }

    public static int sum(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    public static int max(int[] values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("empty");
        }
        int best = values[0];
        for (int value : values) {
            best = Math.max(best, value);
        }
        return best;
    }
}
