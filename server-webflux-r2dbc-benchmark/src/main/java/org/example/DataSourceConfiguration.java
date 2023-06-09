package org.example;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

@Configuration
public class DataSourceConfiguration {

    private static final int CONNECTION_POOL_SIZE = 200;

    @Bean
    public ConnectionFactory connectionFactory() {
        return ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(DRIVER, "postgresql")
                .option(PROTOCOL, "postgresql")
                .option(HOST, "192.168.1.120")
                .option(PORT, 5432)
                .option(USER, "postgres")
                .option(PASSWORD, "postgres")
                .option(DATABASE, "postgres")
                //.option(PostgresqlConnectionFactoryProvider.LOOP_RESOURCES, new NioClientEventLoopResources(Runtime.getRuntime().availableProcessors()))
                .build());
    }

    @Bean
    public ConnectionPool connectionPool(ConnectionFactory connectionFactory) {
        ConnectionPoolConfiguration configuration = ConnectionPoolConfiguration.builder(connectionFactory)
                .maxSize(CONNECTION_POOL_SIZE)

                // It makes minIdle and initialSize different from maxSize on startup.
                // To help mitigate collocation issue: this https://github.com/r2dbc/r2dbc-pool/issues/190
                .minIdle(CONNECTION_POOL_SIZE - 1)
                .initialSize(CONNECTION_POOL_SIZE - 2)

                .build();

        ConnectionPool connectionPool = new ConnectionPool(configuration);

        System.out.println("Warming up connection pool...");
        //connectionPool.warmup().block();
        System.out.println("Warmed up connection pool.");

        return connectionPool;
    }

    @Bean
    public DatabaseClient databaseClient(ConnectionPool connectionPool) {
        return DatabaseClient.builder()
                .connectionFactory(connectionPool)
                .build();
    }
}
