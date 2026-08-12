# Explorer Backend

Alephium blockchain explorer backend — Scala 3, sbt, PostgreSQL (Slick), Tapir HTTP API.

## Commands

| Task | Command |
|---|---|
| Compile | `sbt app/compile` |
| Test | `sbt test` |
| Format | `sbt scalafmt test:scalafmt scalafmtSbt` |
| Style check | `sbt scalastyle test:scalastyle` |
| Full CI check | `make test-all` |
| Update OpenAPI spec | `sbt "tools/runMain org.alephium.tools.OpenApiUpdate"` |
| Benchmark | `sbt "benchmark/jmh:run"` |

## Source layout

```
app/src/main/scala/.../
  api/               Tapir endpoint definitions (AddressesEndpoints, BlockEndpoints, …)
  persistence/
    dao/             Data access objects
    queries/         Slick query definitions
    schema/          Table schemas and custom get/set parameters
    model/           DB-layer models
    Migrations.scala append-only migration list
  service/           Business logic
  web/               HTTP server wiring
app/src/main/resources/
  explorer-backend-openapi.json   generated — never edit by hand
  application-{mainnet,testnet,devnet}.conf
```

## Invariants — never violate these

1. **`Migrations.scala` is append-only.** Never edit or reorder existing migration entries.
   Only append new ones at the bottom. Migrations run in order against live databases.

2. **`explorer-backend-openapi.json` is generated.** After any API change (endpoint added,
   request/response model changed), regenerate with:
   `sbt "tools/runMain org.alephium.tools.OpenApiUpdate"`
   Then commit the updated spec alongside the code change.

3. **Format before every commit.** All `.scala` files must pass `scalafmt`. The PostToolUse
   hook checks this on each edit — fix violations before moving on.

4. **No `git push --force` on `master`.** The PreToolUse guardrail blocks this. If you
   genuinely need it, the user must run it manually.

5. **No destructive DB statements.** Never write `DROP TABLE`, `DROP DATABASE`, or bare
   `TRUNCATE` in migration or query code. Use soft deletes or additive migrations instead.

## Multi-agent workflows

Spawn parallel sub-agents whenever a task crosses more than one layer. Coordinate outcomes
before writing shared files (routes, models, OpenAPI).

### Adding a new API endpoint

Spawn three agents **in parallel** and merge their outputs:

| Agent | Scope | Key files |
|---|---|---|
| **db-agent** | Slick query + DAO + schema additions | `persistence/queries/`, `persistence/dao/`, `persistence/schema/` |
| **api-agent** | Tapir endpoint definition + HTTP server wiring + request/response models | `api/`, `web/`, `ExploreHttpServer.scala` |
| **test-agent** | Integration test covering the happy path and a 404/error case | `app/src/test/scala/.../` |

After both db-agent and api-agent complete, run the spec regeneration step and verify the
OpenAPI diff is exactly what you intended.

### Reviewing a PR

Spawn three agents **in parallel**, each reading the same diff:

| Agent | Focus |
|---|---|
| **correctness-agent** | Logic, edge cases, error handling, concurrency |
| **db-agent** | Query safety, index usage, migration append-only rule, N+1 patterns |
| **contract-agent** | API signature consistency, codec completeness, OpenAPI impact |

Collect and deduplicate findings before presenting them to the user.

### Cross-layer refactor

Before touching any files, write a brief plan to `.claude/plans/<feature>.md` listing which
packages change and in which order. Then spawn one agent per package in parallel, keeping
each agent's scope strictly within its package. Re-integrate via the plan file.

## Feedback loop

The PostToolUse hook runs `scalafmt --check` on every `.scala` file you edit and reports
violations inline. Treat a violation as a compile error — fix it immediately, not at the end.

The Stop hook prints uncommitted changes and a `make test-all` reminder when the session ends
with a dirty working tree.
