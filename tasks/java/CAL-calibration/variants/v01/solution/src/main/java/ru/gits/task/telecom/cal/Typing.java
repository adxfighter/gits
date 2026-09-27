package ru.gits.task.telecom.cal;

/**
 * Retype the fragment from {@link Reference} between the triple quotes below.
 */
final class Typing {

    static final String TYPED = """
            public final class TariffCalculator {

                private final Map<String, Tariff> tariffs = new HashMap<>();

                public void register(Tariff tariff) {
                    Objects.requireNonNull(tariff, "tariff");
                    tariffs.put(tariff.code(), tariff);
                }

                public long monthlyFee(String code, int extraGigabytes) {
                    Tariff tariff = tariffs.get(code);
                    if (tariff == null) {
                        throw new IllegalArgumentException("Unknown tariff: " + code);
                    }
                    if (extraGigabytes < 0) {
                        throw new IllegalArgumentException("Negative traffic");
                    }
                    long fee = tariff.baseFee();
                    fee += (long) extraGigabytes * tariff.pricePerGigabyte();
                    return Math.min(fee, tariff.maxFee());
                }

                public List<String> codes() {
                    return tariffs.keySet().stream().sorted().toList();
                }
            }
            """;

    private Typing() {
    }
}
