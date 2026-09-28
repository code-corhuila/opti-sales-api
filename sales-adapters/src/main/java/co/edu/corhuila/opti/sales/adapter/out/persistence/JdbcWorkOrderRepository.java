package co.edu.corhuila.opti.sales.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import co.edu.corhuila.opti.sales.application.port.in.PageQuery;
import co.edu.corhuila.opti.sales.application.port.in.PageResult;
import co.edu.corhuila.opti.sales.application.port.in.WorkOrderUseCases.WorkOrderFilter;
import co.edu.corhuila.opti.sales.application.port.out.WorkOrderRepository;
import co.edu.corhuila.opti.sales.domain.model.WorkOrder;
import co.edu.corhuila.opti.sales.domain.model.WorkOrderItem;
import co.edu.corhuila.opti.sales.domain.model.WorkOrderStatus;

/** PostgreSQL implementation of {@link WorkOrderRepository}. The schema belongs to sales-db. */
public class JdbcWorkOrderRepository implements WorkOrderRepository {

    private static final String COLUMNS = """
            id, number, patient_id, reference, status, total_cents, created_at, updated_at""";
    private static final String ITEM_COLUMNS = """
            id, work_order_id, frame_id, reservation_id, sku, description, quantity, unit_price_cents,
            subtotal_cents""";

    private final JdbcClient jdbc;

    public JdbcWorkOrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(WorkOrder o) {
        jdbc.sql("INSERT INTO work_order (" + COLUMNS + ") VALUES (:id, :number, :patient, :reference, :status,"
                        + " :total, :createdAt, :updatedAt)")
                .param("id", o.id()).param("number", o.number()).param("patient", o.patientId())
                .param("reference", o.reference()).param("status", o.status().name())
                .param("total", o.totalCents()).param("createdAt", Sql.ts(o.createdAt()))
                .param("updatedAt", Sql.ts(o.updatedAt()))
                .update();
        int position = 0;
        for (WorkOrderItem item : o.items()) {
            jdbc.sql("INSERT INTO work_order_item (" + ITEM_COLUMNS + ", position) VALUES (:id, :order, :frame,"
                            + " :reservation, :sku, :description, :quantity, :price, :subtotal, :position)")
                    .param("id", item.id()).param("order", o.id()).param("frame", item.frameId())
                    .param("reservation", item.reservationId()).param("sku", item.sku())
                    .param("description", item.description()).param("quantity", item.quantity())
                    .param("price", item.unitPriceCents()).param("subtotal", item.subtotalCents())
                    .param("position", position++)
                    .update();
        }
    }

    @Override
    public Optional<WorkOrder> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM work_order WHERE id = :id")
                .param("id", id).query(JdbcWorkOrderRepository::header).optional()
                .map(h -> h.withItems(itemsOf(List.of(h.id())).getOrDefault(h.id(), List.of())));
    }

    @Override
    public Optional<WorkOrder> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM work_order WHERE id = :id FOR UPDATE")
                .param("id", id).query(JdbcWorkOrderRepository::header).optional()
                .map(h -> h.withItems(itemsOf(List.of(h.id())).getOrDefault(h.id(), List.of())));
    }

    @Override
    public PageResult<WorkOrder> search(WorkOrderFilter filter, PageQuery page) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        if (filter.status() != null) {
            conditions.add("status = :status");
            params.put("status", filter.status().name());
        }
        if (filter.patientId() != null) {
            conditions.add("patient_id = :patient");
            params.put("patient", filter.patientId());
        }
        if (filter.createdBefore() != null) {
            conditions.add("created_at < :before");
            params.put("before", Sql.ts(filter.createdBefore()));
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

        long total = jdbc.sql("SELECT count(*) FROM work_order" + where).params(params).query(Long.class).single();
        List<Header> headers = jdbc.sql("SELECT " + COLUMNS + " FROM work_order" + where
                        + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .params(params).param("limit", page.limit()).param("offset", page.offset())
                .query(JdbcWorkOrderRepository::header).list();
        Map<UUID, List<WorkOrderItem>> items = itemsOf(headers.stream().map(Header::id).toList());
        List<WorkOrder> orders = headers.stream()
                .map(h -> h.withItems(items.getOrDefault(h.id(), List.of()))).toList();
        return new PageResult<>(orders, page.page(), page.limit(), total);
    }

    @Override
    public void update(WorkOrder o) {
        jdbc.sql("UPDATE work_order SET status = :status, updated_at = :updatedAt WHERE id = :id")
                .param("status", o.status().name()).param("updatedAt", Sql.ts(o.updatedAt())).param("id", o.id())
                .update();
    }

    /** Loads the lines of several orders with one query, avoiding one query per order. */
    private Map<UUID, List<WorkOrderItem>> itemsOf(List<UUID> orderIds) {
        Map<UUID, List<WorkOrderItem>> byOrder = new LinkedHashMap<>();
        if (orderIds.isEmpty()) {
            return byOrder;
        }
        jdbc.sql("SELECT " + ITEM_COLUMNS + " FROM work_order_item WHERE work_order_id IN (:ids)"
                        + " ORDER BY work_order_id, position")
                .param("ids", orderIds)
                .query((rs, row) -> Map.entry(rs.getObject("work_order_id", UUID.class), item(rs)))
                .list()
                .forEach(e -> byOrder.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        return byOrder;
    }

    private static WorkOrderItem item(ResultSet rs) throws SQLException {
        return new WorkOrderItem(rs.getObject("id", UUID.class), rs.getObject("frame_id", UUID.class),
                rs.getObject("reservation_id", UUID.class), rs.getString("sku"), rs.getString("description"),
                rs.getInt("quantity"), rs.getLong("unit_price_cents"), rs.getLong("subtotal_cents"));
    }

    private static Header header(ResultSet rs, int row) throws SQLException {
        return new Header(rs.getObject("id", UUID.class), rs.getString("number"),
                rs.getObject("patient_id", UUID.class), rs.getString("reference"),
                WorkOrderStatus.valueOf(rs.getString("status")), rs.getLong("total_cents"),
                Sql.instant(rs, "created_at"), Sql.instant(rs, "updated_at"));
    }

    /** The order row before its lines are attached. */
    private record Header(UUID id, String number, UUID patientId, String reference, WorkOrderStatus status,
                          long totalCents, java.time.Instant createdAt, java.time.Instant updatedAt) {

        WorkOrder withItems(List<WorkOrderItem> items) {
            return WorkOrder.rehydrate(id, number, patientId, reference, status, items, totalCents, createdAt,
                    updatedAt);
        }
    }
}
