package co.edu.corhuila.opti.sales.application.port.in;

import java.time.Instant;
import java.util.UUID;

import co.edu.corhuila.opti.sales.application.port.out.Created;
import co.edu.corhuila.opti.sales.domain.model.WorkOrder;
import co.edu.corhuila.opti.sales.domain.model.WorkOrderStatus;

/** What the sales service offers about work orders. */
public interface WorkOrderUseCases {

    /** Opens a quotation and its invoice in one unit of work. */
    Created<WorkOrder> open(WorkOrder.Data data, String idempotencyKey);

    WorkOrder get(UUID id);

    PageResult<WorkOrder> search(WorkOrderFilter filter, PageQuery page);

    WorkOrder approve(UUID id);

    WorkOrder advance(UUID id);

    /** Cancels the order and voids its invoice. Repeating it is harmless. */
    WorkOrder cancel(UUID id);

    /**
     * Listing criteria; every field is optional. {@code q} is a simple, case-insensitive match on
     * the order number (HU-21); it does not join against the patient's name.
     */
    record WorkOrderFilter(WorkOrderStatus status, UUID patientId, Instant createdBefore, String q) {
    }
}
