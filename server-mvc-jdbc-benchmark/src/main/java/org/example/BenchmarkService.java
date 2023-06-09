package org.example;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.LongStream;

@Component
public class BenchmarkService {

    private static final int SELECTS_COUNT = 500_000;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(DataSourceConfiguration.CONNECTION_POOL_SIZE);

    @Autowired
    private DataSource dataSource;

    public long runBenchmark() {
        List<Callable<Long>> dbCallCallables = LongStream.rangeClosed(1, SELECTS_COUNT)
                .boxed()
                .map(i -> (Callable<Long>) () -> selectCompanyById(10L)) // Hardcoded the same id 10. Or use "i" for more random.
                .toList();

        return executeAllAndGetDuration(dbCallCallables);
    }

    private long executeAllAndGetDuration(List<Callable<Long>> dbCallCallables) {
        long timeStartNs = System.nanoTime();

        List<Future<Long>> futures;
        try {
            futures = EXECUTOR.invokeAll(dbCallCallables);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        for (Future<Long> dbCallFuture : futures) {
            try {
                dbCallFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - timeStartNs);
    }

    private long selectCompanyById(Long id) {
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM companies WHERE company_id = ?");
        ) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next(); // Single row is always expected.
                return rs.getLong(1); // Returns company_id.
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
