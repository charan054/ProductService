package com.example.productservice.service;

import com.example.productservice.dto.ReorderSuggestion;
import com.example.productservice.entity.Product;
import com.example.productservice.entity.StockMovement;
import com.example.productservice.repository.ProductRepository;
import com.example.productservice.repository.StockMovementRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Reports over the stock ledger: a CSV of movements for the books, and a reorder list that turns recent sales into
 * "order this many of these".
 */
@Service
public class StockReportService {
    static final int MAX_EXPORT_ROWS = 10_000;
    static final int COVER_DAYS_THRESHOLD = 14;
    static final int TARGET_COVER_DAYS = 30;

    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final Clock clock;

    public StockReportService(ProductRepository products, StockMovementRepository movements, Clock clock) {
        this.products = products;
        this.movements = movements;
        this.clock = clock;
    }

    /** Movements from the start of {@code from} to the end of {@code to} (UTC days), optionally for one product and/or type. */
    @Transactional(readOnly = true)
    public String exportCsv(LocalDate from, LocalDate to, Integer productId, String type) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("Give a from date that is not after the to date");
        }
        String movementType = null;
        if (type != null && !type.isBlank()) {
            movementType = type.trim().toUpperCase(Locale.ROOT);
            if (!StockMovement.TYPES.contains(movementType)) {
                throw new IllegalArgumentException("Unknown stock movement type: " + type);
            }
        }
        List<StockMovement> rows = movements.search(from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), productId, movementType, PageRequest.of(0, MAX_EXPORT_ROWS + 1));
        if (rows.size() > MAX_EXPORT_ROWS) {
            throw new IllegalArgumentException("More than " + MAX_EXPORT_ROWS + " movements in that range - choose a shorter one");
        }
        Map<Integer, String> names = new HashMap<>();
        products.findAllById(rows.stream().map(StockMovement::getProductId).filter(Objects::nonNull).distinct().toList())
                .forEach(p -> names.put(p.getProductId().intValue(), p.getProductName()));
        StringBuilder csv = new StringBuilder("id,createdAt,productId,productName,type,delta,stockAfter,reference,reason,actor\n");
        for (StockMovement m : rows) {
            csv.append(m.getId()).append(',').append(m.getCreatedAt()).append(',').append(m.getProductId()).append(',')
                    .append(cell(names.getOrDefault(m.getProductId(), "(deleted product)"))).append(',').append(m.getType()).append(',')
                    .append(m.getDelta()).append(',').append(m.getStockAfter()).append(',')
                    .append(cell(m.getReference())).append(',').append(cell(m.getReason())).append(',').append(cell(m.getActor())).append('\n');
        }
        return csv.toString();
    }

    /**
     * Products that are out, at or under their low-stock threshold, or would sell out within two weeks at the rate they
     * sold over the last {@code days} days, with how many to order to cover a month. Most urgent first.
     */
    @Transactional(readOnly = true)
    public List<ReorderSuggestion> reorderList(int days) {
        if (days < 1 || days > 365) {
            throw new IllegalArgumentException("days must be between 1 and 365");
        }
        Instant since = clock.instant().minus(Duration.ofDays(days));
        Map<Integer, Long> netByProduct = new HashMap<>();
        for (Object[] row : movements.netOrderChangeSince(since)) {
            netByProduct.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        return products.findAll().stream()
                .map(p -> suggestion(p, netByProduct.getOrDefault(p.getProductId().intValue(), 0L), days))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing((ReorderSuggestion s) -> s.daysOfCover() == null ? Double.MAX_VALUE : s.daysOfCover())
                        .thenComparing(ReorderSuggestion::stock)
                        .thenComparing(ReorderSuggestion::productId))
                .toList();
    }

    private ReorderSuggestion suggestion(Product p, long netOrderChange, int days) {
        int sold = (int) Math.max(0, -netOrderChange);
        double perDay = (double) sold / days;
        Double cover = perDay > 0 ? Math.round(p.getProductStock() / perDay * 10) / 10.0 : null;
        boolean out = p.getProductStock() <= 0;
        boolean underThreshold = p.getLowStockThreshold() > 0 && p.getProductStock() <= p.getLowStockThreshold();
        boolean runningOut = cover != null && cover <= COVER_DAYS_THRESHOLD;
        if (!out && !underThreshold && !runningOut) {
            return null;
        }
        // Out of stock with no sales and no threshold set (0 = none): nothing says it is wanted, so do not nag.
        if (out && sold == 0 && !underThreshold) {
            return null;
        }
        int target = Math.max((int) Math.ceil(perDay * TARGET_COVER_DAYS), p.getLowStockThreshold() * 2);
        int qty = Math.max(0, target - p.getProductStock());
        String reason = out ? "Out of stock" : underThreshold ? "At or under its low-stock threshold"
                : "About " + cover + " days of stock left at the recent rate";
        return new ReorderSuggestion(p.getProductId(), p.getProductName(), p.getVariantLabel(), p.getProductCategory(),
                p.getProductStock(), p.getLowStockThreshold(), sold, Math.round(perDay * 100) / 100.0, cover, qty, reason);
    }

    // Quoted when needed; a leading = + - @ is neutralised so a spreadsheet never runs a typed reason as a formula.
    static String cell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String v = value;
        if ("=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
