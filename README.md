# Benchmark of R2DBC vs JDBC vs Vertx
It shows how slow R2DBC pool is under WebFlux environment due to threading/collocation issue: https://github.com/r2dbc/r2dbc-pool/issues/190.

It compares 3 drivers with PostgreSQL db:
- [JDBC](https://github.com/pgjdbc/pgjdbc) + [HikariCP pool](https://github.com/brettwooldridge/HikariCP);
- [R2DBC](https://github.com/pgjdbc/r2dbc-postgresql) + [R2DBC pool](https://github.com/r2dbc/r2dbc-pool/);
- [Vertx PG reactive client](https://github.com/eclipse-vertx/vertx-sql-client), no external pool needed.

## Benchmark Scenario
There are 6 applications doing the same - run lots of **SELECTs** and measure the total time spent. All SELECTs are executed with concurrency equal to max DB connection pool size:
- **3 standalone apps** (one per R2DBC, JDBC and Vertx DB clients): command line app, prints the duration on each run and repeats it 50 times;
- **3 web apps** (one per R2DBC, JDBC and Vertx DB clients): endpoint `/benchmark` does a single run and returns duration in response. No automatic repeats - you call it and repeat manually.

There are 2 separate tests - single-record SELECT, and multi-record SELECT.

#### Web Apps Note
In web app tests, it's a single web request doing all SELECTs, instead of bombarding N times the endpoint doing a single SELECT, because it helps to avoid measuring network stack latency of the framework on each request, but yet keeps the framework around which, as you see from results, is enough to affect R2DBC performance.

## SQL Schema
```sql
CREATE TABLE companies(
    company_id SERIAL PRIMARY KEY,
    company_name VARCHAR(255)
);

-- Some sample data generation.
INSERT INTO companies(company_name)
    SELECT md5(random()::text) FROM generate_series(1, 100000);
```

## Settings
Sweet spots for my system setup (will be different for yours):
- single-record SELECTs:
  - **500 000 SELECTs**: is many enough to have the total time in the order of several seconds, instead of nano/milliseconds, to avoid small fluctuations due to JVM, GC, network, etc;
  - **200 connection pool size**: for larger than that, the JDBC app wasn't becoming faster.
- multi-record SELECTs:
  - **250 000 SELECTs**;
  - **100 connection pool size**.

Also:
- **Vertx pipelining is disabled** (set to 1), to make the comparison more fair;
- Benchamrks are done **with/without custom LoopResources** class (with 8 threads = CPU cores). When set, it forces R2DBC to use specific number of threads. The class is copy pasted from https://github.com/r2dbc/r2dbc-pool/issues/190#issuecomment-1566845190 .

## Hardware
The app and database are on different hardware machines:
- app machine: Intel i7-7700K, 4.2 GHz, 4(8) cores, Ubuntu 22.04.2 LTS (-Xmx2G for JVM)
- database machine: Intel i7-8550U, 1.8 GHz, 4(8) cores, 16GB RAM, Windows 10

## Measurements
- I let each app run for several minutes (for all things to stabilize), and then took average of last 10 durations.
- Because even the average fluctuates a bit, and because I'd not like to make strong judgements based on +-500ms difference (which is only ~2% of 20 secs, for example), and because I'm interested more about magnitude differences - I rounded the results of all R2DBC setups between each other within ~500ms, and highlighted separately only those that differ a lot. That's why all 8 R2DBC setup permutations (with/without custom LoopResources; with/without `ConnectionPool.warmup()`; equal/non-equal `initialSize` and `maxSize`) mostly share a single measurement result, and only a few cases are displayed separately.

----
## Results (SELECT single record)
**Note:** In all R2DBC tests below, all 8 setup permutations were tested: with/without custom LoopResources; with/without `ConnectionPool.warmup()`; equal/non-equal `initialSize` and `maxSize` - they're displayed as one shared measurement, only those that differ are displayed separately. 
### Standalone

| App                  | Duration              |
|----------------------|-----------------------|
| JDBC                 | **18 sec** (baseline) |
| R2DBC Connection     | **18.3 sec** (+1.5%)  |
| R2DBC DatabaseClient | **19.5 sec** (+8.3%)  |
| Vertx                | **18.1 sec** (+0.5%)  |

### Web App

| App                                  | Duration                                                                                                                                                                                                                                                                                      |
|--------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Spring MVC, JDBC                     | **18 sec** (baseline)                                                                                                                                                                                                                                                                         |
| Spring WebFlux, R2DBC Connection     | **25.5 sec** (+42% = 1.42 times)* <br> - \***41.5 sec** (+130% = 2.3 times) (without custom LoopResources; with (!) `ConnectionPool.warmup()`) <br> - \***41.5 sec** (+130% = 2.3 times) (without custom LoopResources; without `ConnectionPool.warmup()`; equal `initialSize` and `maxSize`) |
| Spring WebFlux, R2DBC DatabaseClient | **31.5 sec** (+75% = 1.75 times)* <br> -\***51 sec** (+183% = 2.83 times) (without custom LoopResources; with (!) `ConnectionPool.warmup()`) <br> -\***51 sec** (+183% = 2.83 times) (without custom LoopResources; without `ConnectionPool.warmup()`; equal `initialSize` and `maxSize`)     |
| Spring WebFlux, Vertx                | **19 sec** (+5.5%)                                                                                                                                                                                                                                                                            |

### How to Interpret R2DBC Results
R2DBC results are a bit tricky to understand, let me explain and highlight something:
1. First of all, in WebFlux, all R2DBC setups are slower than its standalone counterpart by at least ~40% (25.5 sec vs 18.3 sec).
2. In WebFlux, without custom LoopResources, you MUST NOT run warmup(), otherwise things are slow.
3. In WebFlux, the choice of equal vs non-equal initialSize and maxSize matters only when without custom LoopResources and without warmup - must be non-equal, otherwise things are slow. In all other cases, this choice doesn't matter.
4. In standalone, all R2DBC settings perform the same.

## Results (SELECT 100 records)

### Standalone
Not tested. WebFlux results show it's not needed.

### Web App
Both **MVC JDBC** and **WebFlux Vertx**: **51 sec** (baseline).

**R2DBC** results are more diverse, so I provide a table specifically for it with all setups. I only tested DatabaseClient.
Note: for readability, + means with, - means without. 

| WebFlux R2DBC DatabaseClient Setup                                     | Duration                          |
|------------------------------------------------------------------------|-----------------------------------|
| **+** LoopResources, **+** `warmup()`, `initialSize` **!=** `maxSize`  | **51 sec**                        |
| **+** LoopResources, **+** `warmup()`, `initialSize` **==**  `maxSize` | **51 sec**                        |
| **+** LoopResources, **-** `warmup()`, `initialSize` **!=**  `maxSize` | **51 sec**                        |
| **+** LoopResources, **-** `warmup()`, `initialSize` **==**  `maxSize` | **51 sec**                        |
| **-** LoopResources, **+** `warmup()`, `initialSize` **!=**  `maxSize` | **111 sec** (+117% = 2.17 times)  |
| **-** LoopResources, **-** `warmup()`, `initialSize` **!=**  `maxSize` | **111 sec** (+117% = 2.17 times)  |
| **-** LoopResources, **+** `warmup()`, `initialSize` **==**  `maxSize` | **120 sec** (+135% = 2.35 times)  |
| **-** LoopResources, **-** `warmup()`, `initialSize` **==**  `maxSize` | **120 sec** (+135% = 2.35 times)  |

### How to Interpret R2DBC Results
1. Unlike with fast single-record selects, longer DB processing time of multi-record SELECTs probably makes R2DBC slowness itself unnoticeable, compared to the total processing time. So R2DBC has 4 setups where it performs normally (similarly to JDBC and Vertx) with 51 secs.
2. Lack of custom LoopResources drops performance ~2.17 times:
   - and additionally having `initialSize` == `maxSize` drops it a bit more: ~2.35 times.
3. R2DBC performs the worst in the default setup (-LoopResources, -warmup(), `initialSize`==`maxSize`).

## Results (Connections concurrency / Threading)

In all the tests above, it was visible on DB side monitoring that R2DBC **established** all `maxSize` 
connections just fine, but it wasn't really clear how many of them were concurrently **active**,
because the queries were too fast to build up concurrent processing on DB side. 

You saw 4 cases with multi-record SELECTs where R2DBC performed as fast as JDBC and Vertx, but from
single-record SELECT tests we know R2DBC is slower, so longer DB processing time of multi-record
SELECTs just helps to hide R2DBC slowness itself. Moreover, those multi-record SELECTs were still too
fast to simulate some heavy long query.

It all means that we need to make a test specifically for concurrent connections usage. To do that,
I just modified the single-record SELECT to select `pg_sleep(2)` as well, to simulate 2 secs
processing time. It means in ideal case, for 100 connections pool size, for example, all 100 sessions should
become seen as active, proving that all connections are used. Let's see.

I also included observations on threading usage. It corresponds to what I saw in single/multi record
SELECT tests as well.

### Standalone and Web App

**Vertx** - all 100 connections were active in parallel. As for threading, it used only 1 thread, like in all
previous benchmarks above as well, but it doesn't seem to cause performance issues.

**R2DBC**
 - **Threads**
   - with LoopResources: **8** threads (as many as specified, in my case it's CPU_CORES);
   - without LoopResources:
     - `initialSize` !=  `maxSize`: **2** threads;
     - `initialSize` ==  `maxSize`: **1** thread.
 - **Connections**
   - all 100 connections were active in parallel; 
   - but, without warmup() R2DBC suffered on the first run - before all SELECT Monos were completed, it used only ~40 out of 100 connections (exact number was different on each app launch, but it remained unchanged during the first run), and TPS dropped accordingly. Only on the next run all 100 connections became in use.     

### How to Interpret R2DBC Results
1. Because R2DBC underutilizes connections on first runs, it's a problem, for example, for specific cases where you have a cron job starting your app to run a batch of Mono queries in one go, and then shutdown - you'll must use warmup() in this case.   
2. Otherwise, connections usage is fine and I didn't see, for example, R2DBC being stuck with a single connection (regardless of how many threads were used).  
2. Threading - not clear whether it's an issue, because even with LoopResources R2DBC performed slow in single-record SELECTs for example. And also Vertx, for example, was able to perform fine even with 1 thread in all tests.
5. It's not in the benchmarks, but in addition to triggering SELECTs from the single http request, I also separately tested triggering them from multiple http requests bombarded from a load testing tool, to simulate query calls triggered from different WebFlux threads - R2DBC showed the same threading usage like mentioned above.
   - Vertx, however, used multiple threads (~ CPU cores) just fine.

----

## Conclusions

### Single-record SELECT
- R2DBC is definitely affected by WebFlux somehow, because there it performs slower than its standalone version by at least **40%**.
- R2DBC is slower than JDBC by at least **42%** in WebFlux, and by **1.5%** in standalone.
- Vertx DB driver performs great in both WebFlux and standalone environments, and close to JDBC (especially in standalone). It doesn't seem to be affected by WebFlux like R2DBC. Though it's still **5%** slower in WebFlux (19 secs vs 18 secs in standalone).

Also:
- WebFlux is the main usage environment for R2DBC, and the default setup there is - without custom LoopResources; without `ConnectionPool.warmup()` - because these things aren't mentioned in the docs. And likely you'll have equal `initialSize` and `maxSize`. In this case, R2DBC will be slower than JDBC by **130%**, i.e. **2.3 times**.
- Even more so, in WebFlux you'll probably use the default Spring's DatabaseClient - then R2DBC will be slower than JDBC by **183%**, i.e. **2.83 times**.
  - However, DatabaseClient is something on top of R2DBC, and even though it's not a full ORM, a more fair comparison would probably be to Hibernate than raw JDBC (Hibernate wasn't tested here). Anyway, DatabaseClient still seems too slow, and I expect Hibernate to perform better (especially with projections/DTOs), even without 1st level Hibernate cache.
- Weird case - in WebFlux, without custom LoopResources but with (!) `ConnectionPool.warmup()` (who is supposed to make things faster), the performance drops from 25.5 secs to 41.5 secs. Without warmup - it's fine.
- Without `ConnectionPool.warmup()`, the very first R2DBC run is several times slower than subsequent ones. It's the whole full first run - it doesn't speed up to the end or anything like that (even though I see all connections being established on DB side). Apparently, it means R2DBC needs zero load to reconfigure something internally. In theory, it means if you chip in your app into high load cluster without warmup (manual, not just R2DBC `ConnectionPool.warmup()`), your app may be very slow forever. It doesn't happen to Vertx.

### Multi-record SELECT
- R2DBC is affected by WebFlux here as well. Details are mentioned in "How to Interpret R2DBC Results" sections above.
- Vertx has no issues.

Lastly, this benchmark is not an all-around drivers comparison, however the R2DBC issues observed here seem to be fundamental and therefore affecting different types of queries/workloads.    
