package org.example;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class DataSource {

    public static final int CONNECTION_POOL_SIZE = 100;

    private static final HikariDataSource DATA_SOURCE;

    static {
        HikariConfig config = new HikariConfig();
//        config.setJdbcUrl("jdbc:postgresql://192.168.1.120:5432/postgres");
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/postgres");
        config.setUsername("postgres");
        config.setPassword("postgres");
        config.setDriverClassName("org.postgresql.Driver");
        config.setMinimumIdle(CONNECTION_POOL_SIZE);
        config.setMaximumPoolSize(CONNECTION_POOL_SIZE);
        config.setConnectionTimeout(90000000);
        config.setMaxLifetime(90000000);
        config.setIdleTimeout(90000000);
        DATA_SOURCE = new HikariDataSource(config);
    }

    public static Connection getConnection() {
        try {
            return DATA_SOURCE.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
