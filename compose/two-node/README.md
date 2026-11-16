# Two-node test environment

**For testing only.** This is not a way to deploy XNAT. The passwords are fixed and public, nothing is
backed up, and nothing is tuned or secured for real data.

It runs two XNAT nodes that share one database and one data directory, the way the nodes of a multi-node
site do, with a load balancer in front that has **no session affinity**. It's for testing behaviour that
depends on which node answers a request: sessions, caches, and anything else a node keeps in memory.

| Service | Address | What it is |
|---|---|---|
| `lb` | http://localhost:8080 | nginx, round robin across both nodes, no affinity. Each response has an `X-Upstream` header with the address of the node that answered. |
| `xnat-a` | http://localhost:8081 | Node A, the primary node (it runs the scheduled tasks) |
| `xnat-b` | http://localhost:8082 | Node B |
| `postgres` | not published | Postgres 16, shared by both nodes, with `pg_stat_statements` loaded |
| `activemq` | not published | ActiveMQ Artemis, shared by both nodes |

Both nodes mount one `data` volume at `/data/xnat`, so the archive, prearchive, cache and build paths are
shared. Each has its own XNAT home (`/data/xnat/home`) for configuration and logs.

Both nodes run the [dist-events plugin](https://github.com/NrgXnat/dist-events), as multi-node sites are
told to. It tells the other node about some changes, such as project membership and preferences, over a
topic on the shared broker. It's loaded from `plugins/`, which both nodes mount as their plugins folder:

- `up.sh` downloads release 2.0.1 there (set `DIST_EVENTS_VERSION` for another) unless a
  `dist-events-*.jar` is already present.
- To test a local build, put the jar there instead, then `docker compose restart xnat-a xnat-b`.
- Other plugins in that folder are loaded on both nodes too.
- `GET /xapi/dist-events/node-id`, as an admin, says which node answered.

## Requirements

- Docker with Compose v2.
- About 7 GB of memory for containers. Each node is limited to 3 GB.

## Build an image

Any XNAT image works. To build one from an XNAT checkout with that checkout's own `Dockerfile`:

```bash
./build-image.sh ~/repos/xnat            # tags xnat:two-node-test
./build-image.sh ~/repos/xnat my:tag     # or choose the tag
```

It builds the WAR in the checkout and builds the image from a temporary copy, so the checkout isn't
otherwise changed.

## Start and stop

```bash
export XNAT_IMAGE=xnat:two-node-test
./up.sh
```

`up.sh` starts Postgres and node A, initializes and configures the site through node A, and only then
starts node B and the load balancer. Use it rather than `docker compose up`. Each node caches site
preferences in memory, so a node that's already running may not see a change made through the other.
The settings are the ones `jenkins.sh` applies before a CI run.

The first start takes a few minutes, because XNAT creates its database.

```bash
docker compose down        # stop, keeping the database and data
docker compose down -v     # stop and delete everything
```

After editing `nginx.conf` or `two-node-start.sh`, recreate the container that uses it, for example
`docker compose up -d --force-recreate --no-deps lb`. They're mounted as single files, so an edit that
replaces the file isn't seen by a running container.

## Run the REST tests against it

Copy `src/test/resources/config/two-node.properties.example` to `two-node.properties` in the same folder,
then:

```bash
mvn test -Dxnat.config=two-node.properties -Dtest=TestUserAccess
```

`xnat.baseurl` in that file chooses the target:

- **`http://localhost:8081` (node A):** the environment behaves as a single-node XNAT.
- **`http://localhost:8080` (load balancer):** consecutive requests alternate between nodes, so every
  other request reaches a node that doesn't know the session, and gets a 401. grxnat's session handling
  logs in again and repeats a rejected request once, but the new login and the repeat also land on
  different nodes. So until a login works on any node, the suite fails in its setup with "Provided
  credentials don't appear to be valid."

### The session tests

The tests in `org.nrg.testing.xnat.tests.sessions` (group `sessions`) send bare session IDs and never log in
again, so a lost session fails them. Keep `xnat.baseurl` on node A for these:

- `TestSessionLifecycle` and `TestSessionLoginPaths` test one node. They work against any single XNAT.
  `testSessionTimeoutSettingIsHonoured` waits 75 seconds.
- `TestSessionsCrossNode` uses `xnat.nodeB.baseurl` (node B, `http://localhost:8082`) and is skipped without
  it. Most of its tests describe what a login should do on a multi-node XNAT but doesn't yet, so they're
  marked as expected failures. The example config ignores those. To see them fail, add
  `-Dxnat.testBehavior.expectedFailures=run`.

```bash
mvn test -Dxnat.config=two-node.properties -Dtest='TestSessionLifecycle,TestSessionLoginPaths,TestSessionsCrossNode' \
    -Dxnat.testBehavior.expectedFailures=run
```

## Inspecting it

```bash
docker compose logs -f xnat-a                                         # Tomcat console
docker compose exec xnat-a tail -f /data/xnat/home/logs/application.log
docker compose exec postgres psql -U xnat xnat                        # the database
docker compose exec lb getent hosts xnat-a xnat-b                     # map X-Upstream addresses to nodes
```

Browsers share cookies between ports on the same host. So a browser logged in to one node sends that
session's cookie to the other node as well. To keep them apart, use `http://127.0.0.1:8082` for node B.
