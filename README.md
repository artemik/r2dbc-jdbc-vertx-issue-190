# Benchmark of R2DBC vs JDBC vs Vertx
It shows how slow R2DBC pool is under WebFlux environment. Due to threading/collocation issue: https://github.com/r2dbc/r2dbc-pool/issues/190.

It compares 3 drivers with PostgreSQL db:
- [JDBC](https://pages.github.com/) + [HikariCP pool](https://github.com/brettwooldridge/HikariCP);
- [R2DBC](https://github.com/pgjdbc/r2dbc-postgresql) + [R2DBC pool](https://github.com/r2dbc/r2dbc-pool/);
- [Vertx PG reactive client](https://github.com/eclipse-vertx/vertx-sql-client), no external pool needed.

## Benchmark Scenario
There are 6 applications doing the same - run **500 000 SELECTs** and measure the total time spent. Each SELECT returns the same (for max stability) **single record by ID**. All SELECTs are executed with concurrency equal to max DB connection pool size:
- **3 standalone apps** (one per R2DBC, JDBC and Vertx DB clients): command line app, prints the duration on each run and repeats it 50 times;
- **3 web apps** (one per R2DBC, JDBC and Vertx DB clients): endpoint `/benchmark` does a single run and returns duration in response. No automatic repeats - you call it and repeat manually.

#### Why?
In web apps, it's a single web request doing all SELECTs, instead of doing bombarding 500 000 times the endpoint doing a single SELECT, because it helps to avoid measuring network stack latency of the framework on each request, but yet keeps the framework around which, as you see from results, is enough to affect R2DBC performance.
 
## Settings
Sweet spots for my system setup (will be different for yours):
- **500 000 SELECTs**: is many enough to have the total time in the order of several seconds, instead of nano/milliseconds, to avoid small fluctuations due to JVM, GC, network, etc;
- **200 connection pool size**: for larger than that, the JDBC app wasn't becoming faster.

Also:
- **Vertx pipelining is disabled** (set to 1), to make the comparison more fair;
- Benchamrks are done **with/without custom LoopResources** class. When set, it forces R2DBC to use specific number of threads. The class is copy pasted from https://github.com/r2dbc/r2dbc-pool/issues/190#issuecomment-1566845190 .

## Hardware
The app and database are on different hardware machines:
- app machine: Intel i7-7700K, 4.2 GHz, 4(8) cores, Ubuntu 22.04.2 LTS (-Xmx2G for JVM)
- database machine: Intel i7-8550U, 1.8 GHz, 4(8) cores, 16GB RAM, Windows 10

## Measurements
- I let each app run for several minutes (for all things to stabilize), and then took average of last 10 durations.
- Also, because each run fluctuates a bit, and because I'd not like to make strong judgements based on average difference of, for example, +-500ms (which is only ~2% of 20 secs, for example), and instead interested more about magnitute differences, I rounded all results between within ~500ms - that's why, for example, `R2DBC DatabaseClient (with/without custom LoopResources, with/without warmup)` shows a single number in results, even though it was 4 different setup launches.

## Results
### Standalone
- JDBC: **18 sec**
- R2DBC Connection: **18.3 sec** (with/without custom LoopResources, with/without warmup)
- R2DBC DatabaseClient: **19.5 sec** (with/without custom LoopResources, with/without warmup)
- Vertx: **18.1 sec**

### Web App
- Spring MVC, JDBC: **18 sec**
- Spring WebFlux, R2DBC Connection: **22.5 sec** (with/without custom LoopResources, with/without warmup)*
  - ***41.5 sec** in (without custom LoopResources, with (!) warmup) case
- Spring WebFlux, R2DBC DatabaseClient: **31.5 sec** (with/without custom LoopResources, with/without warmup)*
  - ***51 sec** in (without custom LoopResources, with (!) warmup) case
- Spring WebFlux, Vertx: **19 sec**
     
## Conclusions
- In WebFlux (the main usage environment for R2DBC), without custom LoopResources or warmup (it's the default setup as per R2DBC documentation, because those aren't mentioned there), R2DBC shows **22.5 sec** (**25% slower than JDBC**).
- In WebFlux, most likely you'd use the Spring's DatabaseClient - it shows even slower: **31.5 sec** (**75% slower than JDBC**). However, DatabaseClient is something on top of R2DBC, and even though it's not a full ORM, a more fair comparison would probably be to Hibernate than raw JDBC (Hibernate wasn't tested here). Anyway, DatabaseClient still seems too slow, and I expect Hibernate to perform better (especially with projections/DTOs), even without 1st level Hibernate cache.
- Weird case - in WebFlux, without custom LoopResources but with (!) warmup (who is supposed to make things faster), the performance drops to **41.5 secs** (**130% or 2.3 times slower than JDBC**).
- **So in WebFlux, R2DBC definitely performs noticeably slower and seems to be affected somehow by WebFlux environment, because in standalone cases R2DBC performs close to other drivers.**
- Vertx DB driver performs great in both standalone and WebFlux environments, and close to JDBC (especially in standalone). It doesn't seem to be affected by WebFlux like R2DBC. Though it's still ~19 secs in WebFlux vs 18 secs in standalone.
