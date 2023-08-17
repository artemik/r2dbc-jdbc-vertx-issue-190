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
CREATE TABLE companies (
    company_id SERIAL PRIMARY KEY,
    company_name VARCHAR(255)
);

-- Some sample data generation.
INSERT INTO companies (company_name)
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
- In each R2DBC test, there were actually 8 R2DBC setup permutations tested: with/without custom LoopResources; with/without `ConnectionPool.warmup()`; equal/non-equal `initialSize` and `maxSize`. I'd not like to make strong judgements based on +-500ms difference (which is only ~2% of 20 secs, for example), I'm interested more about magnitude differences. Therefore, I rounded their results between each other within ~500ms for simplicity. That's why they mostly share a single measurement result, and only a few cases that differ a lot are displayed separately.

----
## Results (SELECT single record)
**Note**: for readability, **+** means with, **-** means without, `...` means all other settings not affecting the result. 
### Standalone

| App                  | Duration              |
|----------------------|-----------------------|
| JDBC                 | **18 sec** (baseline) |
| R2DBC Connection     | **18.3 sec** (+1.5%)  |
| R2DBC DatabaseClient | **19.5 sec** (+8.3%)  |
| Vertx                | **18.1 sec** (+0.5%)  |

### Web App

| App                                                                                                                                                                                                                                  | Duration                                                                                                          |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| Spring MVC, JDBC                                                                                                                                                                                                                     | **18 sec** (baseline)                                                                                             |
| Spring WebFlux, R2DBC Connection: <br> &nbsp;&nbsp;(**+** LoopResources, `...`) <br> &nbsp;&nbsp;(**-** LoopResources, `...`)* <br> &nbsp;&nbsp;&nbsp;&nbsp;*(**-** LoopResources, **-** warmup, `initialSize` **!=** `maxSize`)     | <br> **25.5 sec** (+42% = 1.42 times) <br> **41.5 sec** (+130% = 2.3 times) <br> **25.5 sec** (+42% = 1.42 times) |
| Spring WebFlux, R2DBC DatabaseClient: <br> &nbsp;&nbsp;(**+** LoopResources, `...`) <br> &nbsp;&nbsp;(**-** LoopResources, `...`)* <br> &nbsp;&nbsp;&nbsp;&nbsp;*(**-** LoopResources, **-** warmup, `initialSize` **!=** `maxSize`) | <br> **31.5 sec** (+75% = 1.75 times) <br> **51 sec** (+183% = 2.83 times) <br> **31.5 sec** (+75% = 1.75 times)  |
| Spring WebFlux, Vertx                                                                                                                                                                                                                | **19 sec** (+5.5%)                                                                                                |

### How to Interpret R2DBC Results
R2DBC results are a bit tricky to understand, let me explain and highlight something:
1. First of all, in WebFlux, all R2DBC setups are slower than its standalone counterpart by at least ~40% (25.5 sec vs 18.3 sec), and are slower than JDBC.
2. In WebFlux, using LoopResources, regardless of other settings, gives better performance.
2. In WebFlux, without LoopResources the performance is worse, however there is one exception where it shows the same performance as with LoopResources: (**-** LoopResources, **-** warmup, `initialSize` **!=** `maxSize`) and where it's strange that warmup MUST NOT be run.
3. In WebFlux, the choice of `initialSize` and `maxSize` matters only when without custom LoopResources and without warmup - must be non-equal, otherwise the performance is worse. In all other cases, this choice doesn't matter.
4. In standalone, all R2DBC settings perform the same.

## Results (SELECT 100 records)

### Standalone
Not tested. Because WebFlux shows a case where R2DBC matches the JDBC performance, likely in standalone it will be even more so.

### Web App
For **R2DBC**, I only tested DatabaseClient. **Note**: for readability, **+** means with, **-** means without, `...` means all other settings not affecting the result.

| App                                                                                                                                                                                                                                           | Duration                                                                                                        |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| Spring MVC, JDBC                                                                                                                                                                                                                              | **51 sec** (baseline)                                                                                           |
| Spring WebFlux, R2DBC DatabaseClient: <br> &nbsp;&nbsp;(**+** LoopResources, `...`) <br> &nbsp;&nbsp;(**-** LoopResources, `initialSize` **!=**  `maxSize`, `...`) <br> &nbsp;&nbsp;(**-** LoopResources, `initialSize` **==**  `maxSize`, `...`) | <br> **51 sec** <br> **111 sec** (+117% = 2.17 times) <br> **120 sec** (+135% = 2.35 times) |
| Spring WebFlux, Vertx                                                                                                                                                                                                                         | **51 sec**                                                                                                      |

### How to Interpret R2DBC Results
1. As in single-record test, the best R2DBC performance is with custom LoopResources.
2. But now the best R2DBC performance matches the performance of JDBC and Vertx: 51 secs. Apparently, the longer DB processing time (and larger data to pull) in multi-record test makes R2DBC slowness unnoticeable compared to the total processing time.
3. Lack of custom LoopResources drops performance at least 2.17 times:
   - and additionally having `initialSize` == `maxSize` drops it a bit more: 2.35 times.

## Results (Connections concurrency / Threading)

Trying to find the cause of R2DBC slowness, it makes sense to check connections and threading usage. In the tests above, it was visible on DB side monitoring that R2DBC **established** all `maxSize` 
connections just fine, but it wasn't clear how many of them were concurrently **active** - the queries completed too fast to build up concurrent processing on DB side. 

Let's do a test aimed specifically for that. I just modified the single-record query to select `pg_sleep(2)` as well, to simulate 2 secs
processing time. It means in ideal case, for example, for 100 connections pool size, all 100 sessions should
become seen as active, proving that all connections are used. Let's see.

### Standalone and Web App
#### Threads

| App                                                                                                                                                                                                                                | Threads                                                    |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------|
| Spring WebFlux, R2DBC: <br> &nbsp;&nbsp;(**+** LoopResources, `...`) <br> &nbsp;&nbsp;(**-** LoopResources, `initialSize` **!=**  `maxSize`, `...`) <br> &nbsp;&nbsp;(**-** LoopResources, `initialSize` **==**  `maxSize`, `...`) | <br> **8** (as many as I configured) <br> **2** <br> **1** |
| Vertx                                                                                                                                                                                                                              | **1**                                                      |

#### Connections

**All applications** had **all 100 connections** established and **active**, as per max connection pool size.

R2DBC had one issue though, without pool warmup it suffered on the first run - before all SELECT `Mono`s were completed, it used only ~40 out of 100 connections (exact number was different on each app launch, but it remained unchanged during the first run), and TPS dropped accordingly. Only on the next run all 100 connections became in use.

### How to Interpret R2DBC Results
1. Threading results explain why R2DBC has a better performance with `initialSize` !=  `maxSize`, and the best performance with LoopResources - the more threads, the faster it is. However, its best case is still slower than JDBC, at least in single-record test.
2. Unlike R2DBC, Vertx not only used just a single thread, but was also basically as fast as JDBC. 
3. Connections usage is fine and doesn't depend on threads. However, remember to use warmup for R2DBC, otherwise things like scheduled tasks, where your app starts to run a batch of queries and exit, may be stuck slow. Apparently, R2DBC requires zero load to trigger some internal reconfiguration.   
4. It's not in the benchmarks, but in addition to triggering SELECTs from a single http request, I also separately tested multiple concurrent http requests, to simulate query calls from different WebFlux threads - R2DBC showed the same threading usage like above. Vertx, however, used multiple threads (~CPU cores) - seems like it's able to adapt.

----

## Conclusions

1. In standalone apps, R2DBC seems fine.
2. However, in **WebFlux R2DBC has some unknown issues**. Part of that can be mitigated by writing a custom LoopResources class to force R2DBC to use more threads. But it's still slower than JDBC, at least in single-record test. The issue should be somewhere in the driver implementation, because Vert.x is another reactive driver example based on Netty, but it doesn't have any issues, even using a single thread.
3. Neither LoopResources nor `ConnectionPool.warmup()` nor `initialSize`-vs-`maxSize` are mentioned in the r2dbc-pool documentation, not even counting that you need to know how to write a LoopResources class that isn't just a setting to enable. So without knowing this issues in advance, if you just follow the documentation, your performance with WebFlux will be around **2 times lower than JDBC**, generally speaking.
4. R2DBC is positioned as the main DB driver for WebFlux, but apparently it can't work properly in its main usage environment.
5. The issue is apparently in r2dbc-pool, so all drivers might be affected. Obviously, most people do use pools.
6. Vertx DB driver performs great in both WebFlux and standalone environments, and close to JDBC. This is a good alternative, especially considering development of [Hibernate Reactive](https://hibernate.org/reactive/) built on that.

Lastly, this benchmark is not an all-around drivers comparison, however the R2DBC issues observed here seem to be fundamental and therefore affecting different types of queries/workloads.    

This article on Medium: https://medium.com/@temanovikov/r2dbc-vs-jdbc-vs-vert-x-not-so-fast-benchmark-c0a9fcabb274
