# Alephium explorer backend

Test are using H2 datbase, so no need to initialize anything.

## Testing

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

### H2

You can run using the h2 embedded database with the following command:

    sbt '; set javaOptions += "-Dconfig.resource=application-h2.conf" ; run'

## Benchmark

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
project benchmark
jmh:run
```
