package co.edu.corhuila.opti.sales.adapter.in.http;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.corhuila.opti.sales.adapter.in.http.SalesDtos.OpenWorkOrderRequest;
import co.edu.corhuila.opti.sales.adapter.in.http.SalesDtos.WorkOrderResponse;
import co.edu.corhuila.opti.sales.application.port.in.WorkOrderUseCases;
import co.edu.corhuila.opti.sales.application.port.in.WorkOrderUseCases.WorkOrderFilter;
import co.edu.corhuila.opti.sales.domain.model.WorkOrderStatus;

import jakarta.servlet.http.HttpServletRequest;

/** HTTP adapter of the work order use cases. Shape and role checks here; business rules in the core. */
@RestController
@RequestMapping("/api/v1/work-orders")
class WorkOrderController {

    private static final String WORK_ORDERS = "/api/v1/work-orders";

    private final WorkOrderUseCases useCases;

    WorkOrderController(WorkOrderUseCases useCases) {
        this.useCases = useCases;
    }

    /** Only the workflow (a SERVICE caller) opens orders: it is who prices them from the reserved stock. */
    @PostMapping
    ResponseEntity<Responses.CreatedBody> open(HttpServletRequest http,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody OpenWorkOrderRequest body) {
        RequestRules.requireRole(http, Roles.SERVICE);
        var result = useCases.open(body.toData(), key);
        return Responses.created(result, result.value().id(), WORK_ORDERS);
    }

    @GetMapping
    PageResponse<WorkOrderResponse> search(HttpServletRequest http, @RequestParam(required = false) String status,
            @RequestParam(required = false) String patientId, @RequestParam(required = false) String createdBefore,
            @RequestParam(required = false) String page, @RequestParam(required = false) String limit) {
        RequestRules.onlyParams(http, "status", "patientId", "createdBefore", "page", "limit");
        var filter = new WorkOrderFilter(parseStatus(status),
                patientId == null || patientId.isBlank() ? null : RequestRules.uuid(patientId, "patientId"),
                parseInstant(createdBefore));
        return PageResponse.of(useCases.search(filter, RequestRules.page(page, limit)).map(WorkOrderResponse::from));
    }

    @GetMapping("/{id}")
    WorkOrderResponse get(@PathVariable String id) {
        return WorkOrderResponse.from(useCases.get(RequestRules.uuid(id, "id")));
    }

    @PostMapping("/{id}/approve")
    WorkOrderResponse approve(HttpServletRequest http, @PathVariable String id) {
        RequestRules.requireRole(http, Roles.ADMIN, Roles.SELLER);
        return WorkOrderResponse.from(useCases.approve(RequestRules.uuid(id, "id")));
    }

    @PostMapping("/{id}/advance")
    WorkOrderResponse advance(HttpServletRequest http, @PathVariable String id) {
        RequestRules.requireRole(http, Roles.ADMIN, Roles.SELLER);
        return WorkOrderResponse.from(useCases.advance(RequestRules.uuid(id, "id")));
    }

    /** Idempotent: answers 200 also when the order was already cancelled. */
    @PostMapping("/{id}/cancel")
    WorkOrderResponse cancel(HttpServletRequest http, @PathVariable String id) {
        RequestRules.requireRole(http, Roles.ADMIN, Roles.SELLER, Roles.SERVICE);
        return WorkOrderResponse.from(useCases.cancel(RequestRules.uuid(id, "id")));
    }

    private static WorkOrderStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return WorkOrderStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("status",
                    "must be QUOTATION, APPROVED, IN_LABORATORY, READY, DELIVERED or CANCELLED");
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            throw ApiException.validation("createdBefore", "must be an RFC 3339 date-time");
        }
    }
}
