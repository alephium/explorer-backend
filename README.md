# Alephium explorer backend

Alephium's explorer backend is an indexer that provides a RESTful API to query the Alephium blockchain.

It serves https://explorer.alephium.org/ as well as our wallets.


## Prerequisites

- Java (11 or 17 is recommended)
- [PostgreSQL](https://www.postgresql.org)
- A running [full node](full-node/getting-started.md)

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

### 3. Run explorer-backend
#### 3.1 Using `sbt`
##### Start the server

```shell
sbt app/run
```

##### Build the Jar

```shell
sbt app/assembly
```

The resulting assembly file will appear in `app/target/scala-2.13/` directory.

#### 3.2 Run the released jar

Download the lastest jar in our [release page](https://github.com/alephium/explorer-backend/releases/latest)

Run it with:

```shell
java -jar explorer-backend-x.x.x.jar
```

### 4. Configuration

Configuration file at [`/app/src/main/resources/application.conf`](https://github.com/alephium/explorer-backend/blob/master/app/src/main/resources/application.conf) can be customized using environment variables

Everything can be overridden in two ways:

#### `user.conf` file

You can change the config in the `~/.alephium-explorer-backend/user.conf` file. e.g:

```conf
alephium {
  explorer {
      port = 9191 //Change default 9090 port
  }
}
```

#### Environment variables

Every value has a corresponding environment variable, you can find all of them in the [application.conf](https://github.com/alephium/explorer-backend/blob/master/app/src/main/resources/application.conf).  e.g:

```shell
export EXPLORER_PORT=9191
```

### 5. Restore archived database

Syncing all data from scratch can take a while, you can choose to start from a snapshot instead.

Alephium [archives repository](https://archives.alephium.org) contains the snapshots for explorer backend database.
The snapshot can be loaded in the postgresql database of the explorer-backend at the first run, using the command below.

* Make sure to use the network you want to load the snapshot for, and the correct database name and user.
* The database must be created before running the command and must be empty.

```shell
alephium_network=mainnet
pg_user=postgres
database=explorer
curl -L $(curl -L -s https://archives.alephium.org/archives/${alephium_network}/explorer-db/_latest.txt) | gunzip -c | psql -U $pg_user -d $database
```

### Querying hashes

Hash strings are stored as [bytea][bytea]. To query a hash string in
SQL use the Postgres function `decode` which converts it to `bytea`.

```sql
select *
from "utransactions"
where "hash" = decode('f25f43b7fb13b1ec5f1a2d3acd1bebb9d27143cdc4586725162b9d88301b9bd7', 'hex');
```

### 6. Configuration

There are two ways to configure the application:

#### Environment variables

Every value in [application.conf](/app/src/main/resources/application.conf) file can be overridden by an environment variable.

```shell
export BLOCKFLOW_NETWORK_ID = 1
export DB_NAME = "testnet"
```

#### Using `user.conf` file

The same way it's done in our full node, you can override the [application.conf](/app/src/main/resources/application.conf) file by creating a `user.conf` file in the `EXPLORER_HOME` folder, which default to `~/.alephium-explorer-backend`.

```conf
alephium.blockflow.network-id = 1
db.db.name = "testnet"
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
