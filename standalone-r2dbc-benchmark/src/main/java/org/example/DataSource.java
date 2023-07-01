package org.example;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.reactivestreams.Publisher;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

public class DataSource {

    private static final int CONNECTION_POOL_SIZE = 100;

    private static final ConnectionPool CONNECTION_POOL;

    private static final DatabaseClient DATABASE_CLIENT;

    static {
        ConnectionFactory connectionFactory = ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(DRIVER, "postgresql")
                .option(PROTOCOL, "postgresql")
//                .option(HOST, "192.168.1.120")
                .option(HOST, "localhost")
                .option(PORT, 5432)
                .option(USER, "postgres")
                .option(PASSWORD, "postgres")
                .option(DATABASE, "postgres")
                .option(PostgresqlConnectionFactoryProvider.LOOP_RESOURCES, new NioClientEventLoopResources(Runtime.getRuntime().availableProcessors()))
//                .option(PostgresqlConnectionFactoryProvider.LOOP_RESOURCES, new SimpleEventLoopResource())
                .build());

        ConnectionPoolConfiguration configuration = ConnectionPoolConfiguration.builder(connectionFactory)
                .maxSize(CONNECTION_POOL_SIZE)

                // It makes minIdle and initialSize different from maxSize on startup.
                // To help mitigate collocation issue: this https://github.com/r2dbc/r2dbc-pool/issues/190
                .minIdle(CONNECTION_POOL_SIZE - 1)
                .initialSize(CONNECTION_POOL_SIZE - 2)

                .build();

        CONNECTION_POOL = new ConnectionPool(configuration);

        System.out.println("Warming up connection pool...");
        CONNECTION_POOL.warmup()
                .subscribeOn(Schedulers.single())
                .subscribe( i -> System.out.println("Warmed up connection pool." + i) );

        DATABASE_CLIENT = DatabaseClient.builder()
                .connectionFactory(CONNECTION_POOL)
                .build();
    }

    public static Publisher<? extends Connection> getConnection() {
        return CONNECTION_POOL.create();
    }

    public static DatabaseClient getDatabaseClient() {
        return DATABASE_CLIENT;
    }
}
