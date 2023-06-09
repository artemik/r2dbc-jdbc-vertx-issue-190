package org.example;

import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceConfiguration {

    @Bean
    public SqlClient sqlClient() {
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setPort(5432)
                .setHost("192.168.1.120")
                .setDatabase("postgres")
                .setUser("postgres")
                .setPassword("postgres")
                .setCachePreparedStatements(true)

                // Disables pipelining to make it a more fair comparison with JDBC and R2DBC - they don't support it.
                // Feel free to try with default value 256. Makes it noticeably faster.
                .setPipeliningLimit(1)

                .setSsl(false);

        PoolOptions poolOptions = new PoolOptions()
                .setMaxSize(200);

        return PgPool.client(connectOptions, poolOptions);
    }
}
