package co.edu.corhuila.opti.sales.adapter.in.http;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.corhuila.opti.sales.application.port.in.ReportUseCases;
import co.edu.corhuila.opti.sales.application.port.in.ReportUseCases.ReportPeriod;
import co.edu.corhuila.opti.sales.application.port.in.ReportUseCases.SalesSummary;

import jakarta.servlet.http.HttpServletRequest;

/** Read-only revenue reports. Shape and role checks here; aggregation in the core. */
@RestController
@RequestMapping("/api/v1/reports")
class ReportsController {

    private final ReportUseCases useCases;

    ReportsController(ReportUseCases useCases) {
        this.useCases = useCases;
    }

    /** Across every seller, for example for a dashboard. */
    @GetMapping("/sales-summary")
    SalesSummary salesSummary(HttpServletRequest http, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        RequestRules.requireRole(http, Roles.ADMIN);
        RequestRules.onlyParams(http, "from", "to");
        return useCases.salesSummary(period(from, to));
    }

    /** Always the caller's own figures, from the token subject — never a sellerId taken from the client. */
    @GetMapping("/my-sales")
    SalesSummary mySales(HttpServletRequest http, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        var caller = RequestRules.requireRole(http, Roles.ADMIN, Roles.SELLER);
        RequestRules.onlyParams(http, "from", "to");
        return useCases.mySales(UUID.fromString(caller.subject()), period(from, to));
    }

    /** SERVICE only (the worker checks sales goals): the one caller trusted with an explicit sellerId. */
    @GetMapping("/seller-sales")
    SalesSummary sellerSales(HttpServletRequest http, @RequestParam String sellerId,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        RequestRules.requireRole(http, Roles.SERVICE);
        RequestRules.onlyParams(http, "sellerId", "from", "to");
        return useCases.mySales(RequestRules.uuid(sellerId, "sellerId"), period(from, to));
    }

    private static ReportPeriod period(String from, String to) {
        return new ReportPeriod(parseInstant(from, "from"), parseInstant(to, "to"));
    }

    private static Instant parseInstant(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            throw ApiException.validation(field, "must be an RFC 3339 date-time");
        }
    }
}
