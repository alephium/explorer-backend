# Node connection retries

Startup fails on an unreachable node by default, preserving deployment behavior
where an external supervisor restarts the explorer. To wait for the node instead,
set `alephium.blockflow.retry-period = 5 seconds` in `user.conf`, or
`BLOCKFLOW_RETRY_PERIOD="5 seconds"`. The duration must be positive.

The first connection attempt is immediate. Transport I/O failures during chain
parameter or clique discovery are retried indefinitely, one attempt at a time,
with the configured delay after each failed attempt. API errors, an incompatible
network and an empty clique still fail startup. The delay does not change HTTP
request timeouts. Read-only mode does not perform node discovery.

This option covers startup discovery; it does not retry arbitrary API requests or
change the existing synchronization failure policy after startup. Shutdown closes
the scheduler and prevents queued retry attempts from executing. Remove the
setting or use `null` to restore fail-fast behavior.
