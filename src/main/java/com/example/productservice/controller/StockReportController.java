package com.example.productservice.controller;

import com.example.productservice.dto.RecentStockChange;
import com.example.productservice.dto.ReorderSuggestion;
import com.example.productservice.service.StockReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

// Both endpoints are admin only: they are not in SecurityConfig's public list, so they need the X-Service-Key.
@RestController
@RequestMapping("/product")
public class StockReportController {
    private final StockReportService reports;
    private final Clock clock;

    public StockReportController(StockReportService reports, Clock clock) {
        this.reports = reports;
        this.clock = clock;
    }

    // CSV of stock movements; dates are yyyy-MM-dd (UTC), inclusive; the default is the last 30 days.
    @GetMapping(value = "/stock-movements/export", produces = "text/csv")
    public ResponseEntity<String> exportMovements(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                  @RequestParam(required = false) Integer productId,
                                                  @RequestParam(required = false) String type) {
        LocalDate end = to != null ? to : LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate start = from != null ? from : end.minusDays(30);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"stock-movements-" + start + "-to-" + end + ".csv\"")
                .body(reports.exportCsv(start, end, productId, type));
    }

    // Corrections and restock receipts of the last N hours (default 24, max 168), newest first - the admin digest.
    @GetMapping("/stock-movements/recent")
    public List<RecentStockChange> recentChanges(@RequestParam(defaultValue = "24") int hours) {
        return reports.recentChanges(hours);
    }

    // What to restock: out, under threshold, or selling out within two weeks, with a suggested quantity.
    @GetMapping("/reorder-list")
    public List<ReorderSuggestion> reorderList(@RequestParam(defaultValue = "30") int days) {
        return reports.reorderList(days);
    }
}
