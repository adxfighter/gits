package ru.gits.task.shop.t07;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * "Best sellers" block of the main page.
 */
public final class TopSellers {

    private static final Comparator<ProductSales> BY_UNITS_DESC_THEN_SKU =
            Comparator.comparingInt(ProductSales::units).reversed().thenComparing(ProductSales::sku);

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

        return unitsBySku.entrySet().stream()
                .map(entry -> new ProductSales(entry.getKey(), entry.getValue()))
                .sorted(BY_UNITS_DESC_THEN_SKU)
                .limit(n)
                .toList();
    }
}
