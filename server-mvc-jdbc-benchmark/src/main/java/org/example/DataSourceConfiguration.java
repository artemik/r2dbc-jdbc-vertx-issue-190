package org.example;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfiguration {

    public static final int CONNECTION_POOL_SIZE = 200;

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://192.168.1.120:5432/postgres");
        config.setUsername("postgres");
        config.setPassword("postgres");
        config.setDriverClassName("org.postgresql.Driver");
        config.setMinimumIdle(CONNECTION_POOL_SIZE);
        config.setMaximumPoolSize(CONNECTION_POOL_SIZE);
        config.setConnectionTimeout(90000000);
        config.setMaxLifetime(90000000);
        config.setIdleTimeout(90000000);
        return new HikariDataSource(config);
    }
}
