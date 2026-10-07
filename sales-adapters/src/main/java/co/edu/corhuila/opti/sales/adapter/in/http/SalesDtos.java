package co.edu.corhuila.opti.sales.adapter.in.http;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import co.edu.corhuila.opti.sales.domain.model.Invoice;
import co.edu.corhuila.opti.sales.domain.model.InvoiceStatus;
import co.edu.corhuila.opti.sales.domain.model.Payment;
import co.edu.corhuila.opti.sales.domain.model.PaymentMethod;
import co.edu.corhuila.opti.sales.domain.model.WorkOrder;
import co.edu.corhuila.opti.sales.domain.model.WorkOrderItem;
import co.edu.corhuila.opti.sales.domain.model.WorkOrderStatus;

/**
 * Request and response objects of the public contract. The domain entities are never serialized
 * directly, so renaming an internal field cannot break a client.
 */
final class SalesDtos {

    private SalesDtos() {
    }

    record ItemRequest(UUID frameId, UUID reservationId, String sku, String description, Integer quantity,
                       Long unitPriceCents) {

        WorkOrderItem.Data toData() {
            return new WorkOrderItem.Data(frameId, reservationId, sku, description, quantity, unitPriceCents);
        }
    }

    record OpenWorkOrderRequest(UUID patientId, String reference, List<ItemRequest> items, UUID sellerId) {

        WorkOrder.Data toData() {
            List<WorkOrderItem.Data> lines = items == null ? null
                    : items.stream().map(i -> i == null ? null : i.toData()).toList();
            return new WorkOrder.Data(patientId, reference, lines, sellerId);
        }
    }

    record PaymentRequest(Long amountCents, PaymentMethod method, String reference) {

        Payment.Data toData() {
            return new Payment.Data(amountCents, method, reference);
        }
    }

    record ItemResponse(UUID id, UUID frameId, UUID reservationId, String sku, String description, int quantity,
                        long unitPriceCents, long subtotalCents) {

        static ItemResponse from(WorkOrderItem i) {
            return new ItemResponse(i.id(), i.frameId(), i.reservationId(), i.sku(), i.description(), i.quantity(),
                    i.unitPriceCents(), i.subtotalCents());
        }
    }

    record WorkOrderResponse(UUID id, String number, UUID patientId, String reference, WorkOrderStatus status,
                             List<ItemResponse> items, long totalCents, UUID sellerId, Instant createdAt,
                             Instant updatedAt) {

        static WorkOrderResponse from(WorkOrder o) {
            return new WorkOrderResponse(o.id(), o.number(), o.patientId(), o.reference(), o.status(),
                    o.items().stream().map(ItemResponse::from).toList(), o.totalCents(), o.sellerId(),
                    o.createdAt(), o.updatedAt());
        }
    }

    record InvoiceResponse(UUID id, String number, UUID workOrderId, UUID patientId, long totalCents,
                           long paidCents, long balanceCents, InvoiceStatus status, Instant createdAt,
                           Instant updatedAt) {

        static InvoiceResponse from(Invoice i) {
            return new InvoiceResponse(i.id(), i.number(), i.workOrderId(), i.patientId(), i.totalCents(),
                    i.paidCents(), i.balanceCents(), i.status(), i.createdAt(), i.updatedAt());
        }
    }

    record PaymentResponse(UUID id, UUID invoiceId, long amountCents, PaymentMethod method, String reference,
                           Instant paidAt) {

        static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.id(), p.invoiceId(), p.amountCents(), p.method(), p.reference(),
                    p.paidAt());
        }
    }
}
