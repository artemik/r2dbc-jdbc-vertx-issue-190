package org.example;

import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlClient;

public class DataSource {
    private static final SqlClient CLIENT;

    static {
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

        CLIENT = PgPool.client(connectOptions, poolOptions);
    }

    public static SqlClient getClient() {
        return CLIENT;
    }
}
