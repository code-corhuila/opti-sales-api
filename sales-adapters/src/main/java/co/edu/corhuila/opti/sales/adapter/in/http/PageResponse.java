package co.edu.corhuila.opti.sales.adapter.in.http;

import java.util.List;

import co.edu.corhuila.opti.sales.application.port.in.PageResult;

/** The listing envelope: {@code {"data": [...], "meta": {page, limit, total, totalPages}}}. */
public record PageResponse<T>(List<T> data, Meta meta) {

    public record Meta(int page, int limit, long total, int totalPages) {
    }

    public static <T> PageResponse<T> of(PageResult<T> result) {
        return new PageResponse<>(result.data(),
                new Meta(result.page(), result.limit(), result.total(), result.totalPages()));
    }
}
