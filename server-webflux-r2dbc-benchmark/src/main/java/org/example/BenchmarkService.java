package org.example;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.Connection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

@Component
public class BenchmarkService {

    private static final int SELECTS_COUNT = 500_000;

    @Autowired
    private DatabaseClient databaseClient;

    //
    // Uncomment this to try raw Connections instead of DatabaseClient. It'll be a bit faster.
    //
    /*@Autowired
    private ConnectionPool connectionPool;*/

    /**
     * @return benchmark run duration Mono in milliseconds
     */
    public Mono<Long> runBenchmark() {
        List<Mono<Long>> dbCallMonos = LongStream.rangeClosed(1, SELECTS_COUNT)
                .boxed()
                .map(i -> selectCompanyById(10L)) // Hardcoded the same id 10. Or use "i" for more random.
                //.map(i -> selectCompanyByIdWithPause(10L)) // Uncomment to test connections concurrency.
                //.map(i -> selectCompanies()) // Uncomment to test multiple records select.
                .toList();

        // Because timeStartNs is defined here, the duration time counts from now. However, monos execution will start
        // a bit later when WebFlux actually subscribes to the monos.
        // Nevertheless, the time is counted from here on purpose, because:
        //   1. WebFlux subscribes very fast, and SELECTS_COUNT is high enough to forget about microsecond variations,
        //   so no need to bother about it.
        //   2. Otherwise, if it's slow, it'd be another indicator something is wrong in WebFlux+R2DBC setup.
        // Take it into account, if you decide to add more logic when playing with it.
        long timeStartNs = System.nanoTime();
        Mono<Long> zip = Mono.zip(dbCallMonos, objects -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - timeStartNs));
        zip.subscribe(durationMs -> {
            long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs);
            durationMs = durationMs - TimeUnit.SECONDS.toMillis(seconds);

            System.out.println("select " + SELECTS_COUNT + " in " + seconds + "." + durationMs + "s.");
        });

        return zip;
    }

    private Mono<Long> selectCompanyById(Long id) {
        return databaseClient.sql("SELECT * FROM companies WHERE company_id = $1")
                .bind(0, id)
                .fetch()
                .first()
                .map(row -> ((Number) row.get("company_id")).longValue());
    }

    private Mono<Long> selectCompanyByIdWithPause(Long id) {
        return databaseClient.sql("SELECT *, pg_sleep(2) FROM companies WHERE company_id = $1")
                .bind(0, id)
                .fetch()
                .first()
                .map(row -> ((Number) row.get("company_id")).longValue());
    }


    private Mono<Long> selectCompanies() {
        return databaseClient.sql("SELECT * FROM companies LIMIT 100000")
                .fetch()
                .all()
                .last()
                .map(row -> ((Number) row.get("company_id")).longValue());
    }

    //
    // Uncomment this to try raw Connections instead of DatabaseClient. It's a bit faster.
    //
    /*private Mono<Long> selectCompanyById(Long id) {
        return Mono.usingWhen(
                connectionPool.create(),
                connection -> Mono.from(connection
                        .createStatement("SELECT * FROM companies WHERE company_id = $1")
                        .bind("$1", id)
                        .execute()
                ).flatMap(result -> Mono.from(result.map((row, rowMetadata) -> row.get(0, Long.class)))),
                Connection::close
        );
    }*/
}
