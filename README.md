# Benchmarks of r2dbc, jdbc, vertx
It shows how slow r2dbc pool is under WebFlux environment. Due to threading issue: https://github.com/r2dbc/r2dbc-pool/issues/190. 

## Benchmark Scenario
There are 6 applications doing the same - run 500 000 SELECTs and measure the total time spent. Each SELECT returns the same (for max stability) single record by ID. All SELECTs are executed with concurrency equal to max DB connection pool size:
- 3 standalone apps (one per r2dbc, jdbc and vertx DB clients): command line app, each run prints the duration, and is repeated 50 times;
- 3 web apps (one per r2dbc, jdbc and vertx DB clients): endpoint `/benchmark` triggers a single run and returns duration in response. You call it manually. Repeat to get the average.   
 
Sweet spots for my system setup (will be different for yours):
- 500 000 SELECTs: is many enough to have the time spent in the order of several seconds, to avoid nano/millisecond fluctuations due to JVM, network, etc;
- 200 connection pool size: for larger than that, the JDBC app wasn't becoming faster.

The app and database are on different hardware machines:
- app machine: Intel i7-7700K, 4.2 GHz, 4(8) cores, Ubuntu 22.04.2 LTS (-Xmx2G for JVM)
- database machine: Intel i7-8550U, 1.8 GHz, 4(8) cores, 16GB RAM, Windows 10

## Results
### Standalone
- JDBC: **18 sec**
- R2DBC
  - Connection: **18.3 sec** (with/without custom LoopResources, with/without warmup)
  - DatabaseClient: **19.5 sec** (with/without custom LoopResources, with/without warmup)
- Vertx: **18.1** sec

### Web App
- JDBC: **18 sec**
- R2DBC
  - Connection: **22.5** sec (with/without custom LoopResources, with/without warmup)*
    - ***41.5** sec in (without custom LoopResources, with (!) warmup) case
  - DatabaseClient: **31.5 sec** (with/without custom LoopResources, with/without warmup)*
    - ***51 sec** in (without custom LoopResources, with (!) warmup) case
- Vertx: **19 sec**
     
## Conclusions
- In WebFlux case (the main usage environment for R2DBC), in the default setup as per R2DBC documentation (it doesn't mention custom LoopResources or warmup), R2DBC shows **22.5 sec** (25% slower than JDBC).
- Most likely you'd use the default Spring's DatabaseClient - it shows even slower: **31.5 sec** (75% slower than JDBC). However, DatabaseClient is something on top of R2DBC, and even though it's not a full ORM, a more fair comparison would be probably to Hibernate, than raw JDBC (Hibernate wasn't tested here). However, DatabaseClient still seems too slow, and I expect Hibernate to perform better (especially with projections/DTOs), even without 1st level Hibernate cache. 