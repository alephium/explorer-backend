# Database schema versions

`app_state.db_version` identifies the schema generation independently from
`app_state.migrations_version`, which tracks work within that generation.
The first versioned generation is `1`. The `/infos` response exposes it as
`dbVersion`; `0` in that response denotes an unversioned legacy database.
The stored value uses the same integer serialization as the migration counter.

Startup validates a stored generation before creating tables or indexes, also
in read-only mode. A different generation fails startup with a compatibility
error; it is never overwritten, and matching migration counters do not bypass
this check. Use a release supporting that generation or rebuild the database.
Do not manually reset `db_version` to bypass compatibility checks.

For initial adoption, an absent version is accepted through the existing
migration path. The new version is recorded transactionally with successful
synchronous migrations. Background data migrations continue to update only
the migration counter. A fresh database receives both versions. An unversioned
legacy schema cannot be identified retrospectively from a reset migration counter;
operators must use the supported upgrade path before adopting this release.

When making an incompatible schema reset in a future release, increment
`latestDbVersion` rather than resetting it. Migration versions may restart within
that new generation. To support an in-place transition between generations, add
an explicit checked migration path before relaxing the compatibility check.
Releases with the same schema generation may have different migration counters.
