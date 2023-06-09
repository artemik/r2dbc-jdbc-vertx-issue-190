package org.example;

import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

@Component
public class BenchmarkService {

    private static final int SELECTS_COUNT = 500_000;

    @Autowired
    private SqlClient sqlClient;

    /**
     * @return benchmark run duration Mono in milliseconds
     */
    public Mono<Long> runBenchmark() {
        List<Mono<Long>> dbCallMonos = LongStream.rangeClosed(1, SELECTS_COUNT)
                .boxed()
                .map(i -> selectCompanyById(10L)) // Hardcoded the same id 10. Or use "i" for more random.
                .toList();

        // Same comment like in R2DBC sample:
        // Because timeStartNs is defined here, the duration time counts from now. However, monos execution will start
        // a bit later when WebFlux actually subscribes to the monos.
        // Nevertheless, the time is counted from here on purpose, because:
        //   1. WebFlux subscribes very fast, and SELECTS_COUNT is high enough to forget about microsecond variations,
        //   so no need to bother about it.
        //   2. Otherwise, if it's slow, it'd be another indicator something is wrong in WebFlux+R2DBC setup.
        // Take it into account, if you decide to add more logic when playing with it.
        long timeStartNs = System.nanoTime();
        return Mono.zip(dbCallMonos, objects -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - timeStartNs));
    }

    private Mono<Long> selectCompanyById(Long id) {
        return Mono.fromFuture(() -> sqlClient
                .preparedQuery("SELECT * FROM companies WHERE company_id = $1")
                .execute(Tuple.of(id))
                .toCompletionStage()
                .toCompletableFuture()
                .thenApplyAsync(rows -> rows.iterator().next().getLong(0)) // Returns company_id.
        );
    }
}
