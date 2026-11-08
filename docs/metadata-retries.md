# Retrying unavailable token and contract metadata

Metadata synchronization runs after block-range ingestion when the connected
node reports that it is synced. A SQL `NULL` interface ID is the persistent
checkpoint for work that is not complete. A failed/unavailable interface lookup
must not be confused with a successful lookup that explicitly identifies a
nonstandard contract.

Unavailable interface results leave the checkpoint pending. For supported
fungible tokens, NFTs, NFT collections and royalty collections, unavailable
metadata also leaves it pending. Metadata and its successful interface
classification are stored together in the existing database transaction. Once
that transaction succeeds, ordinary syncs no longer fetch the item.

Each pending item is attempted once per metadata-sync pass. A failed future or
synchronous nonfatal exception for one item is logged and does not prevent
other items from being processed. The next normal synchronization pass retries
that item. Processing remains sequential to avoid overwhelming the database
executor. Failure to list pending items still fails the pass normally.

There is no immediate retry loop, new timer, schema migration, retry limit or
exponential backoff. Persistently unavailable standard metadata will therefore
be attempted once per normal pass until it becomes available. Destroyed
contracts remain excluded by the existing pending-contract query. A restart
retains pending work because its checkpoint is stored in the database.

An explicit `NonStandard` result remains terminal. Known but unsupported
interface IDs and existing classifications that do not require metadata keep
their previous behavior.

## Existing databases

Previously classified fungible tokens, NFTs, NFT collections and royalty
collections with no matching metadata row are automatically included in pending
work. Their classification is not reset; the missing row keeps them eligible
until retrieval succeeds. Completed items remain excluded.

Earlier versions also stored nonstandard classifications after unavailable
interface lookups. Those records cannot safely be distinguished from genuinely
nonstandard contracts. This change does not automatically rewrite them. After
identifying affected records from operational evidence, maintainers can perform
a targeted maintenance requeue by setting their interface ID and category to
SQL `NULL`; do not broadly reset valid classifications.

## Regression coverage

Database-backed tests cover unavailable interface then unavailable metadata
then success, fungible/NFT/collection/royalty metadata, terminal explicit
nonstandard results, recovery of previously classified rows missing metadata,
destroyed/unsupported collection exclusions, stopping requests after success,
and failed items not blocking the rest of a batch. The existing small-database-pool regression also
continues to exercise sequential completion.
