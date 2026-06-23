package com.allcenter.modulesystem.util;

public final class JdbcUrlParser {

    private JdbcUrlParser() {}

    public record ConnectionInfo(String host, int port, String database) {}

    public static ConnectionInfo parse(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("JDBC URL vacía");
        }
        String remainder = jdbcUrl.trim();
        if (remainder.startsWith("jdbc:postgresql://")) {
            remainder = remainder.substring("jdbc:postgresql://".length());
        } else if (remainder.startsWith("jdbc:postgresql:")) {
            remainder = remainder.substring("jdbc:postgresql:".length());
        } else {
            throw new IllegalArgumentException("Solo se admite PostgreSQL: " + jdbcUrl);
        }
        int slash = remainder.indexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("URL JDBC inválida: " + jdbcUrl);
        }
        String hostPort = remainder.substring(0, slash);
        String dbPart = remainder.substring(slash + 1);
        int q = dbPart.indexOf('?');
        String database = q >= 0 ? dbPart.substring(0, q) : dbPart;
        if (database.isBlank()) {
            throw new IllegalArgumentException("Base de datos no indicada en URL: " + jdbcUrl);
        }

        int colon = hostPort.lastIndexOf(':');
        String host;
        int port = 5432;
        if (colon > 0) {
            String portPart = hostPort.substring(colon + 1);
            if (portPart.matches("\\d+")) {
                host = hostPort.substring(0, colon);
                port = Integer.parseInt(portPart);
            } else {
                host = hostPort;
            }
        } else {
            host = hostPort;
        }
        return new ConnectionInfo(host, port, database);
    }
}
