# Alephium explorer backend

## Development

### 1. Install the dependencies

You need to have [Postgresql][postgresql] and [sbt][sbt] installed in your system.

### 2. Create the database

1. Start the `postgresql` service.
2. Login to the PostgreSQL shell with the default `postgres` user:
   ```shell
   psql postgres # or `psql -U postgres` depending on your OS
   ```
3. Ensure that the `postgres` role exists, and if not, create it.  
   List all roles:
   ```shell
   postgres=# \du
   ```
   Create `postgres` role:
   ```shell
   postgres=# CREATE ROLE postgres WITH LOGIN;
   ```
4. Then, create the database:
   ```shell
   postgres=# CREATE DATABASE explorer;
   ```

### 3. Start the server

```shell
sbt app/run
```

### 4. Single Jar

```shell
sbt app/assembly
```

The resulting assembly file will appear in `app/target/scala-2.13/` directory.


### Querying hashes

Hash strings are stored as [bytea][bytea]. To query a hash string in
SQL use the Postgres function `decode` which converts it to `bytea`.

```sql
select *
from "utransactions"
where "hash" = decode('f25f43b7fb13b1ec5f1a2d3acd1bebb9d27143cdc4586725162b9d88301b9bd7', 'hex');
```

## Benchmark

### 1. Create benchmark database

The benchmark database (set
via [dbName](/benchmark/src/main/scala/org/alephium/explorer/benchmark/db/BenchmarkSettings.scala)) should exist:

```sql
CREATE DATABASE benchmarks;
```

### 2. Set benchmark duration

Update the `time` value in the following annotation
in [DBBenchmark](/benchmark/src/main/scala/org/alephium/explorer/benchmark/db/DBBenchmark.scala) to set the benchmark
run duration:

```scala
@Measurement(iterations = 1, time = 1, timeUnit = TimeUnit.MINUTES)
```

### 3. Executing benchmarks

Execute the following `sbt` commands to run JMH benchmarks

```
sbt benchmark/jmh:run
```

## Testing

The tests are using the Postgresql database and the default `postgres` table.

```shell
sbt test
```

[postgresql]: https://www.postgresql.org/
[sbt]: https://www.scala-sbt.org/
[bytea]: https://www.postgresql.org/docs/9.0/datatype-binary.html

## Scaladoc

To generate scala-doc run: `sbt unidoc`

### Referencing external libraries in scala-docs

To reference external libraries in scala-docs make sure the library is recognised by adding an `apiMapping`.

See `scalaDocsAPIMapping` in `build.sbt` file as a reference for creating this `apiMapping` for an external library.

```scala
def myLibraryAPIMapping(classPath: Classpath, scalaVersion: String): (sbt.File, sbt.URL) =
  ??? //follow `scalaDocsAPIMapping` in build.sbt

//add the apiMapping to the project that depends on `myLibrary`
apiMappings ++=
  Map(
    myLibraryAPIMapping(
      classPath = (Compile / fullClasspath).value,
      scalaVersion = scalaVersion.value
    )
  )
```
