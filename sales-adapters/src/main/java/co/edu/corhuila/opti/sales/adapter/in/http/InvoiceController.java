package co.edu.corhuila.opti.sales.adapter.in.http;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.corhuila.opti.sales.adapter.in.http.SalesDtos.InvoiceResponse;
import co.edu.corhuila.opti.sales.adapter.in.http.SalesDtos.PaymentRequest;
import co.edu.corhuila.opti.sales.adapter.in.http.SalesDtos.PaymentResponse;
import co.edu.corhuila.opti.sales.application.port.in.InvoiceUseCases;
import co.edu.corhuila.opti.sales.application.port.in.InvoiceUseCases.InvoiceFilter;
import co.edu.corhuila.opti.sales.domain.model.InvoiceStatus;

import jakarta.servlet.http.HttpServletRequest;

/** HTTP adapter of the invoice and payment use cases. */
@RestController
@RequestMapping("/api/v1/invoices")
class InvoiceController {

    private static final String INVOICES = "/api/v1/invoices";

    private final InvoiceUseCases useCases;

    InvoiceController(InvoiceUseCases useCases) {
        this.useCases = useCases;
    }

    @GetMapping
    PageResponse<InvoiceResponse> search(HttpServletRequest http, @RequestParam(required = false) String status,
            @RequestParam(required = false) String workOrderId,
            @RequestParam(required = false) String page, @RequestParam(required = false) String limit) {
        RequestRules.onlyParams(http, "status", "workOrderId", "page", "limit");
        var filter = new InvoiceFilter(parseStatus(status),
                workOrderId == null || workOrderId.isBlank() ? null : RequestRules.uuid(workOrderId, "workOrderId"));
        return PageResponse.of(useCases.search(filter, RequestRules.page(page, limit)).map(InvoiceResponse::from));
    }

    @GetMapping("/{id}")
    InvoiceResponse get(@PathVariable String id) {
        return InvoiceResponse.from(useCases.get(RequestRules.uuid(id, "id")));
    }

    @PostMapping("/{id}/payments")
    ResponseEntity<Responses.CreatedBody> pay(HttpServletRequest http, @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody PaymentRequest body) {
        RequestRules.requireRole(http, Roles.ADMIN, Roles.SELLER);
        UUID invoiceId = RequestRules.uuid(id, "id");
        var result = useCases.pay(invoiceId, body.toData(), key);
        return Responses.created(result, result.value().id(), INVOICES + "/" + invoiceId + "/payments");
    }

    @GetMapping("/{id}/payments")
    PageResponse<PaymentResponse> payments(HttpServletRequest http, @PathVariable String id,
            @RequestParam(required = false) String page, @RequestParam(required = false) String limit) {
        RequestRules.onlyParams(http, "page", "limit");
        return PageResponse.of(useCases.payments(RequestRules.uuid(id, "id"), RequestRules.page(page, limit))
                .map(PaymentResponse::from));
    }

    private static InvoiceStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return InvoiceStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("status", "must be PENDING, PARTIAL, PAID or VOID");
        }
    }
}
