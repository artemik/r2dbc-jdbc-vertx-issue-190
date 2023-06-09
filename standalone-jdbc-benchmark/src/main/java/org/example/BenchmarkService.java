package org.example;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.LongStream;

public class BenchmarkService {

    private static final int SELECTS_COUNT = 500_000;

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(DataSource.CONNECTION_POOL_SIZE);

    public static void runBenchmark() {
        List<Callable<Long>> dbCallCallables = LongStream.rangeClosed(1, SELECTS_COUNT)
                .boxed()
                .map(i -> (Callable<Long>) () -> selectCompanyById(10L)) // Hardcoded the same id 10. Or use "i" for more random.
                .toList();

        executeAllAndPrintDuration(dbCallCallables);
    }

    private static void executeAllAndPrintDuration(List<Callable<Long>> dbCallCallables) {
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

        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - timeStartNs);
        System.out.println(durationMs);
    }

    private static long selectCompanyById(Long id) {
        try (
                Connection connection = DataSource.getConnection();
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
