package co.edu.corhuila.opti.sales.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;

/** Small JDBC helpers shared by the repositories. */
final class Sql {

    private Sql() {
    }

    /** Escapes LIKE wildcards so user text is matched literally. */
    static String contains(String text) {
        String escaped = text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped.toLowerCase() + "%";
    }

    /** timestamptz parameter: an {@link Instant} always travels as UTC. */
    static java.time.OffsetDateTime ts(Instant instant) {
        return instant == null ? null : java.time.OffsetDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, java.time.OffsetDateTime.class).toInstant();
    }

    static LocalDate date(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDate.class);
    }

    static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
