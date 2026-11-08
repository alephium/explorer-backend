# Holder synchronization

By default, holder balances are synchronized daily at
`alephium.explorer.holder-service-schedule-time` (04:00 UTC).
Set `alephium.explorer.holder-service-sync-period = 2 hours` or
`EXPLORER_HOLDER_SERVICE_SYNC_PERIOD="2 hours"` to use an interval instead.
The interval must be positive. It takes precedence over the daily schedule time.
The first synchronization runs one interval after startup, then each successful
synchronization schedules the next after the configured delay. Runs do not overlap.
A failed synchronization retains the existing scheduler failure/reporting behavior.
Remove the interval or set it to `null` to restore the daily schedule.
Shorter intervals can increase database load.
