package ru.gits.api.scoring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The stored form of a task's indicators, {@code {name: {value, explanation}}} with explanations in Russian for the
 * employer's report (docs/api.md, docs/indicators.md).
 */
final class IndicatorTexts {

    private IndicatorTexts() {
    }

    record Entry(Object value, String explanation) {
    }

    static Map<String, Entry> of(IndicatorCalculator.TaskIndicators i, Double baseSpeed, TrustRules.Verdict verdict,
                                 boolean calibration) {
        Map<String, Entry> map = new LinkedHashMap<>();
        map.put("pasteRatio", new Entry(i.pasteRatio(),
                "Доля итогового кода, пришедшая вставкой: " + percent(i.pasteRatio()) + " (" + i.pastedChars()
                        + " симв. вставлено)."));
        map.put("largestPaste", new Entry(i.largestPaste(), i.largestPaste() == 0
                ? "Вставок не было."
                : "Наибольшая вставка — " + i.largestPaste() + " симв."));
        map.put("focusLoss", new Entry(Map.of("count", i.focusLossCount(), "seconds", i.focusLossSeconds()),
                i.focusLossCount() == 0
                        ? "Вкладку с задачей не покидали."
                        : "Уход со вкладки или из окна: " + i.focusLossCount() + " раз, всего "
                        + seconds(i.focusLossSeconds()) + "."));
        map.put("burstMax", new Entry(i.burstMax(), "Пиковая скорость набора — " + number(i.burstMax())
                + " симв./с за 5 с (без вставок и автодополнения)"
                + (i.burstRelative() == null ? "." : ", в " + number(i.burstRelative())
                + " раза выше обычной скорости кандидата на разминке (" + number(baseSpeed) + " симв./с).")));
        map.put("idleThenBurst", new Entry(i.idleThenBurst(), i.idleThenBurst() == 0
                ? "Эпизодов «долгая пауза, затем быстрый всплеск кода» не было."
                : "Эпизодов «пауза больше 30 с, затем больше 150 символов за 20 с без вставки»: " + i.idleThenBurst()
                + "."));
        map.put("linearity", new Entry(i.linearity(), "Доля правок, сделанных в конце набранного текста (набор "
                + "сверху вниз без возвратов): " + percent(i.linearity()) + "."));
        map.put("editRatio", new Entry(i.editRatio(), "Удалено символов на каждый вставленный: "
                + number(i.editRatio()) + "."));
        map.put("timeToFirstRun", new Entry(i.timeToFirstRunSeconds(), i.timeToFirstRunSeconds() == null
                ? "Тесты не запускались."
                : "Первый запуск тестов — через " + seconds(i.timeToFirstRunSeconds()) + " после открытия задачи."));
        map.put("runsCount", new Entry(i.runsCount(), "Запусков видимых тестов: " + i.runsCount() + "."));
        map.put("telemetryEvents", new Entry(i.telemetryEvents(), "Событий телеметрии: " + i.telemetryEvents() + "."));
        List<String> reasons = verdict.reasons();
        map.put("trustReasons", new Entry(reasons, calibration
                ? "Разминка: индикаторы посчитаны, но в уровень доверия сессии не входят."
                : reasons.isEmpty()
                ? "Ни одно правило не сработало. Правила экспериментальные (v1.0)."
                : "Сработавшие правила (экспериментальные, v1.0): " + String.join(" ", reasons)));
        return map;
    }

    private static String percent(double share) {
        return Math.round(share * 100) + "%";
    }

    private static String number(Double value) {
        return value == null ? "—" : String.format(Locale.ROOT, "%.1f", value).replace('.', ',');
    }

    private static String seconds(double value) {
        long total = Math.round(value);
        return total < 60 ? total + " с" : total / 60 + " мин " + total % 60 + " с";
    }
}
