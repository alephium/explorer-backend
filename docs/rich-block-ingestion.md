# Rich block ingestion

The explorer reads `/blockflow/rich-blocks/{block_hash}` and
`/blockflow/rich-blocks?fromTs=...&toTs=...` for block ingestion, including
missing parents, uncles, and conflict repair. The node must support these
endpoints and have `alephium.node.indexes.tx-output-ref-index = true` with the
referenced output index populated for the requested history. Enabling this flag
on an existing node does not by itself establish that old index data is present;
follow the node's supported index rebuilding procedure. Test rich-block queries
against the oldest history needed before upgrading the explorer.

The node's rich API can omit asset input references whose outputs are unavailable
in its index. The explorer cannot certify completeness from that rich payload
alone; it depends on a populated historical output-reference index. The legacy
backfill can detect a missing expected input because its reference already exists
in the explorer database, and fails without committing that block. This is not a
general completeness check for newly ingested blocks.

The returned input address, amount, tokens and creating transaction are stored
with that specific block. Inputs no longer obtain their metadata by joining
outputs on output key: generated contract outputs on competing forks can share
a key but have different values. Sender and token transaction indexes are
created from enriched inputs within the ingestion transaction, even when a
sender has no outputs in that transaction. There is no fallback to ordinary
blocks or to ambiguous output-key joins when the rich API fails.

## Existing databases

Before starting any synchronization or finalization services, a writing explorer
finishes legacy inputs whose amount is still NULL. It processes at most 100
block hashes at a time, fetching blocks sequentially and committing each block's
input metadata and address/token indexes atomically. Stored main-chain and
conflict flags are retained. A failed request or missing rich input stops startup;
resolve the node/index problem and restart. Successfully repaired blocks no
longer appear in the pending query, so restart resumes the remaining work.
This one-time catchup can take a long time for a large backlog; progress batches
are logged. A read-only explorer does not perform this repair.

Complete existing input rows, finalized output spending markers, finalization
watermarks and transaction counts are not rewritten. Normal repeated ingestion
also preserves existing input rows. This change prevents new fork-ambiguous
input amounts and completes missing metadata; it does not detect or correct
already-populated historical amounts that an older explorer may have resolved
incorrectly. Such historical corrections require a separately reviewed repair
with node evidence. No database reset is required for the incomplete-input
catchup.

The former `InputUpdateQueries` is retained only under test sources to construct
legacy test fixtures. Production has no output-key-based metadata updater.
