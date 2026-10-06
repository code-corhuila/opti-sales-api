package co.edu.corhuila.opti.sales.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import co.edu.corhuila.opti.sales.application.port.in.ReportUseCases;
import co.edu.corhuila.opti.sales.application.port.out.SalesReportRepository;
import co.edu.corhuila.opti.sales.domain.model.DomainException;
import co.edu.corhuila.opti.sales.domain.model.WorkOrderStatus;

/** Sales revenue reports: a read-only view over the work order history. */
public class ReportService implements ReportUseCases {

    private final SalesReportRepository reports;
    private final Clock clock;

    public ReportService(SalesReportRepository reports, Clock clock) {
        this.reports = reports;
        this.clock = clock;
    }

    @Override
    public SalesSummary salesSummary(ReportPeriod period) {
        return reports.aggregate(null, checked(period).from(), checked(period).to());
    }

    @Override
    public SalesSummary mySales(UUID sellerId, ReportPeriod period) {
        if (sellerId == null) {
            throw DomainException.validation("sellerId", "is required");
        }
        return reports.aggregate(sellerId, checked(period).from(), checked(period).to());
    }

    @Override
    public List<DailySales> salesTimeseries() {
        LocalDate today = LocalDate.now(clock);
        YearMonth month = YearMonth.from(today);
        var from = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        var to = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Map<LocalDate, Long> byDay = new java.util.HashMap<>();
        for (DailySales row : reports.dailyTotals(from, to)) {
            byDay.put(row.date(), row.totalCents());
        }
        List<DailySales> series = new ArrayList<>();
        for (LocalDate day = month.atDay(1); !day.isAfter(today); day = day.plusDays(1)) {
            series.add(new DailySales(day, byDay.getOrDefault(day, 0L)));
        }
        return series;
    }

    @Override
    public List<StatusCount> ordersByStatus() {
        Map<WorkOrderStatus, Long> byStatus = new EnumMap<>(WorkOrderStatus.class);
        for (StatusCount row : reports.countsByStatus()) {
            byStatus.put(row.status(), row.count());
        }
        List<StatusCount> counts = new ArrayList<>();
        for (WorkOrderStatus status : WorkOrderStatus.values()) {
            counts.add(new StatusCount(status, byStatus.getOrDefault(status, 0L)));
        }
        return counts;
    }

    private ReportPeriod checked(ReportPeriod period) {
        ReportPeriod p = period == null ? ReportPeriod.ALL : period;
        if (p.from() != null && p.to() != null && p.from().isAfter(p.to())) {
            throw DomainException.validation("from", "must not be after 'to'");
        }
        return p;
    }
}
