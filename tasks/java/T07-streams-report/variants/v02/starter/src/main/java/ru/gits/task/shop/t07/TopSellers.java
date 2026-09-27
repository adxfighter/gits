package ru.gits.task.shop.t07;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * "Best sellers" block of the main page.
 */
public final class TopSellers {

    /**
     * Returns up to {@code n} products with the most units sold: by units descending,
     * products with equal units by SKU ascending.
     */
    public List<ProductSales> top(List<Sale> sales, int n) {
        Objects.requireNonNull(sales, "sales");
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive: " + n);
        }
        Map<String, Integer> unitsBySku = sales.stream()
                .collect(Collectors.toMap(Sale::sku, Sale::units, Integer::sum));

        TreeMap<Integer, String> ranking = unitsBySku.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey,
                        (first, second) -> second, TreeMap::new));

        return ranking.entrySet().stream()
                .limit(n)
                .map(entry -> new ProductSales(entry.getValue(), entry.getKey()))
                .toList();
    }
}
