# Alephium explorer backend

## Testing

Tests are using Postgresql database and the default `postgres` table.

Run the tests with:

```
sbt test
```

## Running

### Postgresql

If you want to `run`, you'll need to `CREATE DATABASE explorer` in your `postgresql`.

#### Querying hashes

Hash strings are stored as [bytea](https://www.postgresql.org/docs/9.0/datatype-binary.html). To query a hash string in
SQL use the Postgres function `decode` which converts it to `bytea`.

```sql
select *
from "utransactions"
where "hash" = decode('f25f43b7fb13b1ec5f1a2d3acd1bebb9d27143cdc4586725162b9d88301b9bd7', 'hex');
```
## Benchmark

### Create benchmark database

The benchmark database (set
via [dbName](/benchmark/src/main/scala/org/alephium/explorer/benchmark/db/BenchmarkSettings.scala)) should exist.

```sql
CREATE DATABASE benchmarks;
```

### Set benchmark duration

Update the `time` value in the following annotation
in [DBBenchmark](/benchmark/src/main/scala/org/alephium/explorer/benchmark/db/DBBenchmark.scala) to set the benchmark
run duration

```scala
@Measurement(iterations = 1, time = 1, timeUnit = TimeUnit.MINUTES)
```

### Executing benchmarks

Execute the following sbt commands to run JMH benchmarks

```
sbt benchmark/jmh:run
```
