# Alephium explorer backend

Test are using H2 datbase, so no need to initialize anything.

## Testing

## Running

### Postgresql

If you want to `run`, you'll need to `CREATE DATABASE explorer` in your `postgresql`.

### H2

You can run using the h2 embedded database with the following command:

    sbt '; set javaOptions += "-Dconfig.resource=application-h2.conf" ; run'
