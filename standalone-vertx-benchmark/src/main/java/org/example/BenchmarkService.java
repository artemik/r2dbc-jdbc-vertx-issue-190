package org.example;

import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

public class BenchmarkService {

    private static final int SELECTS_COUNT = 500_000;

    public static void runBenchmark() {
        // Unlike in JDBC and R2DBC samples, where the list below contained the-work-to-be-done-in-future, here Futures
        // represent already started ongoing work. Therefore, the time starts counting from here.
        long timeStartNs = System.nanoTime();

        List<Future<Long>> dbCallFutures = LongStream.rangeClosed(1, SELECTS_COUNT)
                .boxed()
                .map(i -> selectCompanyById(10L)) // Hardcoded the same id 10. Or use "i" for more random.
                .toList();

        for (Future<Long> dbCallFuture : dbCallFutures) {
            try {
                dbCallFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - timeStartNs);
        System.out.println(durationMs);
    }

    private static Future<Long> selectCompanyById(Long id) {
        CompletableFuture<RowSet<Row>> rowsFuture = DataSource.getClient()
                .preparedQuery("SELECT * FROM companies WHERE company_id = $1")
                .execute(Tuple.of(id))
                .toCompletionStage()
                .toCompletableFuture();

        return rowsFuture.thenApplyAsync(rows -> rows.iterator().next().getLong(0)); // Returns company_id.
    }
}
