package co.edu.corhuila.opti.sales.application.usecase;

import java.util.UUID;

import co.edu.corhuila.opti.sales.application.port.in.ReportUseCases;
import co.edu.corhuila.opti.sales.application.port.out.SalesReportRepository;
import co.edu.corhuila.opti.sales.domain.model.DomainException;

/** Sales revenue reports: a read-only view over the work order history. */
public class ReportService implements ReportUseCases {

    private final SalesReportRepository reports;

    public ReportService(SalesReportRepository reports) {
        this.reports = reports;
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

    private ReportPeriod checked(ReportPeriod period) {
        ReportPeriod p = period == null ? ReportPeriod.ALL : period;
        if (p.from() != null && p.to() != null && p.from().isAfter(p.to())) {
            throw DomainException.validation("from", "must not be after 'to'");
        }
        return p;
    }
}
