package com.example.productservice.service;

import com.example.productservice.dto.ReorderSuggestion;
import com.example.productservice.entity.Product;
import com.example.productservice.entity.StockMovement;
import com.example.productservice.repository.ProductRepository;
import com.example.productservice.repository.StockMovementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockReportServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    @Mock
    private ProductRepository products;
    @Mock
    private StockMovementRepository movements;

    private StockReportService service() {
        return new StockReportService(products, movements, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Product product(long id, String name, int stock, int threshold) {
        Product p = new Product();
        p.setProductId(id);
        p.setProductName(name);
        p.setProductCategory("misc");
        p.setProductStock(stock);
        p.setLowStockThreshold(threshold);
        return p;
    }

    private StockMovement movement(long id, int productId, int delta, String type, String reason) {
        StockMovement m = new StockMovement();
        m.setId(id);
        m.setProductId(productId);
        m.setDelta(delta);
        m.setStockAfter(10);
        m.setType(type);
        m.setReason(reason);
        m.setActor("admin");
        m.setCreatedAt(NOW);
        return m;
    }

    // ---------- CSV export ----------

    @Test
    void exportHasAHeaderOneRowPerMovementAndTheProductName() {
        when(movements.search(any(), any(), eq(null), eq(null), any())).thenReturn(List.of(movement(1, 5, -2, "SALE", "ok")));
        when(products.findAllById(any())).thenReturn(List.of(product(5, "Soap", 10, 0)));

        String csv = service().exportCsv(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8), null, null);

        String[] lines = csv.split("\n");
        assertEquals("id,createdAt,productId,productName,type,delta,stockAfter,reference,reason,actor", lines[0]);
        assertEquals("1,2026-10-08T12:00:00Z,5,Soap,SALE,-2,10,,ok,admin", lines[1]);
    }

    @Test
    void exportQuotesCommasAndNeutralisesFormulas() {
        when(movements.search(any(), any(), eq(null), eq(null), any())).thenReturn(List.of(
                movement(1, 5, 3, "RESTOCK", "From \"Acme\", boxed"), movement(2, 5, 1, "CORRECTION", "=HYPERLINK(\"x\")")));
        when(products.findAllById(any())).thenReturn(List.of(product(5, "Soap", 10, 0)));

        String csv = service().exportCsv(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8), null, null);

        assertTrue(csv.contains("\"From \"\"Acme\"\", boxed\""));
        assertTrue(csv.contains("'=HYPERLINK(\"\"x\"\")") || csv.contains("\"'=HYPERLINK(\"\"x\"\")\""));
        assertTrue(!csv.contains(",=HYPERLINK"));
    }

    @Test
    void exportRejectsABackwardsRangeAndAnUnknownType() {
        assertThrows(IllegalArgumentException.class, () -> service().exportCsv(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 1), null, null));
        assertThrows(IllegalArgumentException.class, () -> service().exportCsv(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8), null, "THEFT"));
    }

    @Test
    void exportUsesTheTypeFilterInUpperCase() {
        when(movements.search(any(), any(), eq(null), eq("RESTOCK"), any())).thenReturn(List.of());
        when(products.findAllById(any())).thenReturn(List.of());

        assertEquals(1, service().exportCsv(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8), null, "restock").split("\n").length);
    }

    // ---------- reorder list ----------

    @Test
    void recentChangesMergesCorrectionsAndRestocksNewestFirstWithProductNames() {
        StockMovement older = movement(1, 5, 20, "RESTOCK", "supplier delivery");
        older.setCreatedAt(NOW.minusSeconds(7200));
        StockMovement newer = movement(2, 6, -3, "CORRECTION", "damaged");
        newer.setCreatedAt(NOW.minusSeconds(60));
        when(movements.search(any(), any(), eq(null), eq("CORRECTION"), any())).thenReturn(List.of(newer));
        when(movements.search(any(), any(), eq(null), eq("RESTOCK"), any())).thenReturn(List.of(older));
        when(products.findAllById(any())).thenReturn(List.of(product(5, "Soap", 30, 0), product(6, "Rice", 7, 0)));

        List<com.example.productservice.dto.RecentStockChange> changes = service().recentChanges(24);

        assertEquals(List.of("Rice", "Soap"), changes.stream().map(c -> c.productName()).toList());
        assertEquals("CORRECTION", changes.get(0).type());
        assertEquals(-3, changes.get(0).delta());
    }

    @Test
    void recentChangesLooksBackTheRequestedHoursAndOnlyAtHandMadeTypes() {
        when(movements.search(any(), any(), eq(null), any(), any())).thenReturn(List.of());
        org.mockito.ArgumentCaptor<Instant> from = org.mockito.ArgumentCaptor.forClass(Instant.class);
        org.mockito.ArgumentCaptor<String> type = org.mockito.ArgumentCaptor.forClass(String.class);

        assertTrue(service().recentChanges(6).isEmpty());

        org.mockito.Mockito.verify(movements, org.mockito.Mockito.times(2)).search(from.capture(), any(), eq(null), type.capture(), any());
        assertEquals(List.of("CORRECTION", "RESTOCK"), type.getAllValues());
        assertEquals(NOW.plusSeconds(1).minusSeconds(6 * 3600), from.getValue());
    }

    @Test
    void recentChangesRejectsAnOutOfRangeWindow() {
        assertThrows(IllegalArgumentException.class, () -> service().recentChanges(0));
        assertThrows(IllegalArgumentException.class, () -> service().recentChanges(169));
    }

    @Test
    void reorderListSuggestsWhatSellsOutSoonAndHowMuchToOrder() {
        // 30 sold over 30 days = 1/day. Soap has 5 left (5 days of cover): order enough for 30 days = 25 more.
        when(movements.netOrderChangeSince(any())).thenReturn(java.util.Collections.singletonList(new Object[]{1, -30L}));
        when(products.findAll()).thenReturn(List.of(product(1, "Soap", 5, 0)));

        List<ReorderSuggestion> list = service().reorderList(30);

        assertEquals(1, list.size());
        ReorderSuggestion s = list.get(0);
        assertEquals(30, s.soldInPeriod());
        assertEquals(1.0, s.soldPerDay());
        assertEquals(5.0, s.daysOfCover());
        assertEquals(25, s.suggestedQuantity());
        assertTrue(s.reason().contains("5.0 days"));
    }

    @Test
    void cancelsAndReturnsReduceWhatCountsAsSold() {
        // Net -6 over 30 days: only 0.2/day, and 10 left is 50 days of cover - not on the list.
        when(movements.netOrderChangeSince(any())).thenReturn(java.util.Collections.singletonList(new Object[]{1, -6L}));
        when(products.findAll()).thenReturn(List.of(product(1, "Soap", 10, 0)));

        assertTrue(service().reorderList(30).isEmpty());
    }

    @Test
    void anOutOfStockProductWithRecentSalesIsMostUrgentAndOneWithNoSalesAndNoThresholdIsLeftOut() {
        when(movements.netOrderChangeSince(any())).thenReturn(List.<Object[]>of(new Object[]{2, -15L}));
        when(products.findAll()).thenReturn(List.of(product(1, "Dormant", 0, 0), product(2, "Seller", 0, 0), product(3, "Slow", 3, 0)));

        List<ReorderSuggestion> list = service().reorderList(30);

        assertEquals(List.of("Seller"), list.stream().map(ReorderSuggestion::productName).toList());
        assertEquals(0.0, list.get(0).daysOfCover());
        assertEquals(15, list.get(0).suggestedQuantity());
    }

    @Test
    void aProductUnderItsThresholdIsListedEvenWithoutSalesAndNeverHasADaysOfCoverFigure() {
        when(movements.netOrderChangeSince(any())).thenReturn(List.of());
        when(products.findAll()).thenReturn(List.of(product(1, "Soap", 2, 5)));

        List<ReorderSuggestion> list = service().reorderList(30);

        assertEquals(1, list.size());
        assertNull(list.get(0).daysOfCover());
        assertEquals(8, list.get(0).suggestedQuantity());
    }

    @Test
    void reorderListRejectsAnAbsurdWindow() {
        assertThrows(IllegalArgumentException.class, () -> service().reorderList(0));
        assertThrows(IllegalArgumentException.class, () -> service().reorderList(366));
    }
}
