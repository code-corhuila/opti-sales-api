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
    private final PaymentGuard guard;
    private final co.edu.corhuila.opti.sales.application.port.in.WorkOrderUseCases orders;

    InvoiceController(InvoiceUseCases useCases, co.edu.corhuila.opti.sales.application.port.in.WorkOrderUseCases orders, PaymentGuard guard) {
        this.useCases = useCases;
        this.orders = orders;
        this.guard = guard;
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

    @GetMapping("/{id}/pdf")
    ResponseEntity<byte[]> pdf(HttpServletRequest http, @PathVariable String id) {
        RequestRules.requireRole(http, Roles.ADMIN, Roles.SELLER);
        var invoice = useCases.get(RequestRules.uuid(id, "id"));
        var order = orders.get(invoice.workOrderId());
        requireInvoiceOwner(http, order);
        var lines = new java.util.ArrayList<String>();
        lines.add("OptiView - Factura " + invoice.number());
        lines.add("Documento interno. No es una factura fiscal certificada.");
        lines.add("Fecha: " + invoice.createdAt());
        lines.add("Orden: " + order.number());
        lines.add("Paciente (identificador): " + invoice.patientId());
        lines.add("Estado: " + invoice.status());
        for (var item : order.items()) {
            lines.add(item.sku() + " - " + item.description());
            lines.add("Cantidad: " + item.quantity() + " | Unitario COP: " + money(item.unitPriceCents())
                + " | Subtotal COP: " + money(item.subtotalCents()));
        }
        lines.add("Total COP: " + money(invoice.totalCents()));
        lines.add("Abonado COP: " + money(invoice.paidCents()));
        lines.add("Saldo COP: " + money(invoice.balanceCents()));
        return PdfDocument.download("factura-" + invoice.id() + ".pdf", lines);
    }

    private static String money(long cents) {
        return java.math.BigDecimal.valueOf(cents, 2).toPlainString();
    }

    @PostMapping("/{id}/payments")
    ResponseEntity<Responses.CreatedBody> pay(HttpServletRequest http, @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody PaymentRequest body) {
        RequestRules.requireRole(http, Roles.ADMIN, Roles.SELLER);
        UUID invoiceId = RequestRules.uuid(id, "id");
        var result = guard.pay(RequestRules.caller(http).subject(), invoiceId, body.toData(), key,
            body.challengeId(), body.otp(), body.receipt(), () -> useCases.pay(invoiceId, body.toData(), key));
        return Responses.created(result, result.value().id(), INVOICES + "/" + invoiceId + "/payments");
    }

    record ChallengeRequest(Long amountCents, co.edu.corhuila.opti.sales.domain.model.PaymentMethod method, String reference) { }

    @PostMapping("/{id}/payment-challenges")
    ResponseEntity<PaymentGuard.Challenge> challenge(HttpServletRequest http, @PathVariable String id, @RequestBody ChallengeRequest body) {
        var user = RequestRules.requireRole(http, Roles.ADMIN, Roles.SELLER);
        UUID invoiceId = RequestRules.uuid(id, "id");
        var invoice = useCases.get(invoiceId);
        var data = new co.edu.corhuila.opti.sales.domain.model.Payment.Data(body.amountCents(), body.method(), body.reference());
        co.edu.corhuila.opti.sales.domain.model.Payment.check(data);
        if (invoice.status() == InvoiceStatus.VOID || body.amountCents() > invoice.balanceCents())
            throw ApiException.validation("amountPesos", "El monto supera el saldo o la factura está anulada.");
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(guard.start(user.subject(), invoiceId, data));
    }

    @GetMapping("/{id}/payments/{paymentId}/receipt")
    ResponseEntity<byte[]> receipt(HttpServletRequest http, @PathVariable String id, @PathVariable String paymentId) {
        RequestRules.requireRole(http, Roles.ADMIN, Roles.SELLER);
        var invoiceId = RequestRules.uuid(id, "id");
        requireInvoiceOwner(http, orders.get(useCases.get(invoiceId).workOrderId()));
        var document = guard.receipt(RequestRules.uuid(paymentId, "paymentId"), invoiceId);
        String extension = document.contentType().equals("application/pdf") ? "pdf" : document.contentType().equals("image/png") ? "png" : "jpg";
        return ResponseEntity.ok().header("Content-Type", document.contentType())
            .header("Content-Disposition", "attachment; filename=\"comprobante-" + paymentId + "." + extension + "\"")
            .header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff").body(document.bytes());
    }

    @GetMapping("/{id}/payments")
    PageResponse<PaymentResponse> payments(HttpServletRequest http, @PathVariable String id,
            @RequestParam(required = false) String page, @RequestParam(required = false) String limit) {
        RequestRules.onlyParams(http, "page", "limit");
        return PageResponse.of(useCases.payments(RequestRules.uuid(id, "id"), RequestRules.page(page, limit))
                .map(p -> PaymentResponse.from(p, guard.hasReceipt(p.id(), p.invoiceId()))));
    }

    private static void requireInvoiceOwner(HttpServletRequest http,
            co.edu.corhuila.opti.sales.domain.model.WorkOrder order) {
        var user = RequestRules.requireRole(http, Roles.ADMIN, Roles.SELLER);
        if (!user.hasAnyRole(Roles.ADMIN)
                && (order.sellerId() == null || !order.sellerId().toString().equals(user.subject()))) {
            throw ApiException.forbidden();
        }
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
