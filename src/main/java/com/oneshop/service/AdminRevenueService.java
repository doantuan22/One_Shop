package com.oneshop.service;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.repository.OrderRepository;
import com.oneshop.service.query.SqlDayRange;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Revenue report of the whole chain. Revenue is the goods total (after discounts, WITHOUT the shipping fee) of the
 * Orders whose money was collected (payment status PAID); unpaid, failed, cancelled and refunded Orders never count.
 * The period is the day the Order was placed. Grouped by Store, Category or Brand the figure is built from the Order
 * lines (price x quantity minus the discount given to the line), so every grouping adds up to the same total.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class AdminRevenueService {

    public enum GroupBy {
        DAY("Ngày"), WEEK("Tuần"), MONTH("Tháng"), STORE("Chi nhánh"), CATEGORY("Danh mục"), BRAND("Thương hiệu"),
        PAYMENT_METHOD("Phương thức thanh toán"), FULFILLMENT_TYPE("Hình thức nhận hàng");

        private final String label;

        GroupBy(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Time groupings read best oldest first; the others biggest first. */
        boolean chronological() {
            return this == DAY || this == WEEK || this == MONTH;
        }
    }

    /** share: percent of the report total, one decimal. */
    public record Row(String label, long orders, BigDecimal revenue, BigDecimal share) {}

    public record Report(LocalDate from, LocalDate to, GroupBy groupBy, long orders, BigDecimal revenue,
                         BigDecimal average, List<Row> rows) {}

    private static final LocalDateTime END_OF_TIME = LocalDateTime.of(9999, 12, 31, 23, 59);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    private final OrderRepository orders;

    public AdminRevenueService(OrderRepository orders) {
        this.orders = orders;
    }

    public Report report(LocalDate from, LocalDate to, GroupBy groupBy) {
        var range = SqlDayRange.of(from, to);
        var start = range.fromInclusive();
        var end = range.toExclusive() == null ? END_OF_TIME : range.toExclusive();
        var collected = orders.collectedOrders(start, end);
        BigDecimal total = BigDecimal.ZERO;
        for (Object[] r : collected) {
            total = total.add((BigDecimal) r[3]);
        }
        List<Row> rows = switch (groupBy) {
            case DAY, WEEK, MONTH, PAYMENT_METHOD, FULFILLMENT_TYPE -> fromOrders(collected, groupBy);
            case STORE -> fromLines(orders.goodsRevenueByStore(start, end));
            case CATEGORY -> fromLines(orders.goodsRevenueByCategory(start, end));
            case BRAND -> fromLines(orders.goodsRevenueByBrand(start, end));
        };
        if (!groupBy.chronological()) {
            rows = rows.stream().sorted(Comparator.comparing(Row::revenue).reversed()).toList();
        }
        final BigDecimal sum = total;
        rows = rows.stream().map(r -> new Row(r.label(), r.orders(), r.revenue(), sum.signum() == 0 ? BigDecimal.ZERO
                : r.revenue().multiply(BigDecimal.valueOf(100)).divide(sum, 1, java.math.RoundingMode.HALF_UP))).toList();
        var count = collected.size();
        var average = count == 0 ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(count), 0, java.math.RoundingMode.HALF_UP);
        return new Report(from, to, groupBy, count, total, average, rows);
    }

    private static List<Row> fromOrders(List<Object[]> collected, GroupBy groupBy) {
        Map<String, long[]> counts = new TreeMap<>();
        Map<String, BigDecimal> sums = new TreeMap<>();
        for (Object[] r : collected) {
            var key = key((LocalDateTime) r[0], (PaymentMethod) r[1], (FulfillmentType) r[2], groupBy);
            counts.computeIfAbsent(key, k -> new long[1])[0]++;
            sums.merge(key, (BigDecimal) r[3], BigDecimal::add);
        }
        var rows = new ArrayList<Row>();
        sums.forEach((k, v) -> rows.add(new Row(display(k, groupBy), counts.get(k)[0], v, BigDecimal.ZERO)));
        return rows;
    }

    /** Sortable key of a group (ISO dates, so a TreeMap is chronological). */
    private static String key(LocalDateTime at, PaymentMethod method, FulfillmentType type, GroupBy groupBy) {
        var day = at.toLocalDate();
        return switch (groupBy) {
            case DAY -> day.toString();
            case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
            case MONTH -> day.getYear() + "-" + String.format("%02d", day.getMonthValue());
            case PAYMENT_METHOD -> method.name();
            default -> type.name();
        };
    }

    private static String display(String key, GroupBy groupBy) {
        return switch (groupBy) {
            case DAY -> LocalDate.parse(key).format(DAY);
            case WEEK -> {
                var monday = LocalDate.parse(key);
                yield "Tuần " + monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + " (" + monday.format(DAY_MONTH)
                        + " – " + monday.plusDays(6).format(DAY) + ")";
            }
            case MONTH -> key.substring(5) + "/" + key.substring(0, 4);
            case PAYMENT_METHOD -> switch (key) {
                case "ONLINE" -> "Thanh toán online";
                case "COD" -> "Thanh toán khi nhận hàng (COD)";
                default -> "Thanh toán tại cửa hàng";
            };
            default -> "STORE_PICKUP".equals(key) ? "Nhận tại cửa hàng" : "Giao tận nơi";
        };
    }

    private static List<Row> fromLines(List<Object[]> lines) {
        return lines.stream().map(r -> new Row((String) r[0], (Long) r[1], (BigDecimal) r[2], BigDecimal.ZERO)).toList();
    }
}
