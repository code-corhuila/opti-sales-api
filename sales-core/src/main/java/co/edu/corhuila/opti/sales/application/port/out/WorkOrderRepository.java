package co.edu.corhuila.opti.sales.application.port.out;

import java.util.Optional;
import java.util.UUID;

import co.edu.corhuila.opti.sales.application.port.in.PageQuery;
import co.edu.corhuila.opti.sales.application.port.in.PageResult;
import co.edu.corhuila.opti.sales.application.port.in.WorkOrderUseCases.WorkOrderFilter;
import co.edu.corhuila.opti.sales.domain.model.WorkOrder;

/** Persistence of work orders and their lines. */
public interface WorkOrderRepository {

    void insert(WorkOrder order);

    Optional<WorkOrder> findById(UUID id);

    /** Reads the order locking its row until the unit of work ends, so transitions are serialized. */
    Optional<WorkOrder> findByIdForUpdate(UUID id);

    PageResult<WorkOrder> search(WorkOrderFilter filter, PageQuery page);

    /** Persists the mutable part of the order: status and update time. */
    void update(WorkOrder order);
}
