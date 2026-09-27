package ru.gits.task.telecom.cal;

/**
 * Fragment to retype into {@link Typing}. It is a comment on purpose: nothing here can be referenced from code.
 */
final class Reference {

    /*
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
    */

    private Reference() {
    }
}
