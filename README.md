# AdminTools

Server-authoritative admin tools for Minecraft 26.2/26.3 on Fabric and 26.2 on Paper. View
player inventories and ender chests in vanilla GUIs, manage per-player command
access, and audit item and container movement.

## Supported versions

| Module | Platform | Minecraft | Java | Platform version |
|---|---|---|---|---|
| `version-26-2` | Fabric | 26.2 | 25 | Loader >= 0.19.3, Fabric API >= 0.154.2 |
| `version-26-3` | Fabric (review branch) | 26.3 | 25 | Loader >= 0.19.3, Fabric API >= 0.165.0 |
| `paper-26-2` | Paper | 26.2 | 25 | Paper API 26.2 build 121 |

The current published **v1.0.0 release contains only Fabric 26.2 and Paper 26.2 artifacts**.
Fabric 26.3 is available as source on this review branch and is not yet released.

The modules build server JARs. Install exactly one artifact for
the server platform; clients do not install AdminTools.

## Features

| Feature | Command | Permission | Versions |
|---|---|---|---|
| Player inventory viewer | `/invsee <player> [edit]` | `admintools.invsee` | Fabric, Paper |
| Ender chest viewer | `/endersee <player>` | `admintools.endersee` | Fabric, Paper |
| Per-player command access | `/adminaccess grant\|remove\|list` | Operator | Fabric, Paper |
| Item identity and audit | `/itemtrace <uuid> [page]` | `admintools.itemtrace` | Fabric, Paper* |
| Admin item give/remove | `/adminitem give\|remove <player> <item> [count]` | `admintools.adminitem.*` | Fabric, Paper |
| Container audit | `/containertrace <x> <y> <z> [dimension/world]` | `admintools.containertrace` | Fabric, Paper |

- `/invsee` opens a 36-slot vanilla chest GUI backed by the target's real
  inventory; with `edit` (or `invsee_edit_mode` in config) it is writable and
  each item move is attributed to the administrator in the audit log.
- `/endersee` opens a 27-slot single-chest GUI, read-only.
- `/adminaccess` grants AdminTools command permissions directly to player UUIDs;
  OP4 always retains full access.

### Fabric item identity

Every stack is assigned a persistent `admintools:uid` key inside vanilla
`minecraft:custom_data` on first observation. The key is removed from outbound
item packets and ignored during server-side item matching, so vanilla clients
can connect and normal stacking, splitting, and merging are unaffected.

- Splits record `SPLIT` lineage, merges record `MERGE` (absorbed parent), and
  player-to-player moves are correlated as `TRANSFER`.
- `ItemDuplicateDetector` flags the same uid in two or more independent
  locations (player inventories / ender chests). Detection is alert-only —
  nothing is auto-removed. Creative-mode players are excluded unless
  `detect_creative_duplicates` is enabled.
- Container audit records player sessions for chests, barrels, and shulker boxes,
  including the opener, open duration, and item quantities added or removed.
  Logs are written per container under `logs/admintools/containers/`.
  Double chests share one log. Overlapping viewers produce one session attributed
  to all participants; automated changes during an open session are included in
  its net totals rather than attributed to a specific player.
- Persistence: `config/admintools/item_ledger.json` (snapshot),
  `logs/admintools/item_ledger.jsonl` (movement events), and
  `logs/admintools/item_duplicates.jsonl` (duplicate alerts).
- `/itemtrace` reads the durable movement log and displays it in newest-first,
  ten-event pages.

### Paper item identity

Paper implements the same commands, ledger format, duplicate alerts, and
container session logs using supported Paper APIs. There is one deliberate
platform difference:

- Non-stackable items carry a persistent `admintools:uid` in their Paper PDC,
  allowing durable identity and clone detection.
- Stackable items are correlated through persisted player slot observations.
  A unique PDC on every stack would change vanilla stack equality, so Paper does
  not tag stackable items. Their movement history is best-effort across merges,
  splits, unobserved containers, and restarts.

Fabric remains the platform with exact split lineage and outbound UID hiding,
because those guarantees require its server mixins.

## Configuration

`config/admintools/config.json` (created on first run):

| Key | Default | Description |
|---|---|---|
| `enable_inventory_viewer` | `true` | Toggle `/invsee` |
| `enable_ender_chest_viewer` | `true` | Toggle `/endersee` |
| `log_actions_to_file` | `true` | Write action log |
| `invsee_edit_mode` | `false` | Make `/invsee` writable by default |
| `detect_creative_duplicates` | `false` | Include creative players in dupe detection |
| `ledger_max_entries` | `5000` | Soft cap; active identities are never evicted |
| `enable_container_audit` | `true` | Log player-opened chests, barrels, and shulker boxes |

Per-player grants live in `config/admintools/permissions.json`.
Run `/admintools reload` as OP4 to validate and apply both files without restarting.

Available `/adminaccess` permission nodes:

- `admintools.invsee` and `admintools.invsee.edit`
- `admintools.endersee`
- `admintools.itemtrace`
- `admintools.containertrace`
- `admintools.adminitem.give`, `admintools.adminitem.remove`, and
  `admintools.adminitem.*`
- `admintools.*` for every AdminTools command

## Building

Requires the Gradle wrapper (Gradle 9.5.1, Loom 1.17.14):

```bash
./gradlew :version-26-2:build   # Fabric 26.2
./gradlew :version-26-3:build   # Fabric 26.3 (review branch)
./gradlew :paper-26-2:build     # Paper
```

Artifacts:

- `version-26-2/build/libs/admintools-26.2-1.0.0.jar`
- `version-26-3/build/libs/admintools-26.3-1.0.0.jar` (unreleased)
- `paper-26-2/build/libs/admintools-paper-26.2-1.0.0.jar`

## Testing

```bash
./gradlew :version-26-2:runUnitTests   # dependency-free logic tests
./gradlew :version-26-2:runGametest    # @GameTest suite
./gradlew :version-26-3:runUnitTests
./gradlew :version-26-3:runGametest
./gradlew :paper-26-2:build           # Paper compile/package check
python3 tests/test_fabric_targets.py
python3 tests/test_fabric_targets.py --jars # after building both Fabric jars
```

Use Java 25 and append `--no-daemon --max-workers=1` for sequential verification.

### Verification status (2026-10-24)

- The rerun Gradle build passed for Fabric 26.2, Fabric 26.3, and Paper 26.2.
- The Fabric GameTest suites passed all 17 required tests on 26.2 and all 18 on
  26.3. The 26.3-specific suite exercises a player drop and verifies one `DROP`
  ledger event with the item UID preserved.
- Both production Fabric JARs passed metadata and test-code exclusion checks.
- These GameTests use a disposable harness. No connected vanilla-client or
  manual gameplay smoke test was performed for this branch build. The 26.3
  artifact remains unreleased and is not part of public v1.0.0.

Do not accept the EULA or run the manual server as part of automated verification.

Manual server testing (26.2):

```bash
./test-server/run-26-2.sh            # foreground, localhost:25565
./test-server/run-26-2.sh --offline  # if Gradle can't reach the network
```

Server data goes to `version-26-2/run/`. AdminTools is server-only; clients do
not need the AdminTools jar, Fabric Loader, or Fabric API to connect. The
viewer GUIs use vanilla menu types and are rendered by the vanilla client.
Make yourself OP4 in the server console to manage AdminTools access.

The Paper artifact is copied into a Paper 26.2 server's `plugins/` directory.
Paper operators bypass per-player grants; non-operators can be granted the same
AdminTools permission nodes with `/adminaccess` or a Paper permission plugin.

## Development notes

See [AGENTS.md](AGENTS.md) for architecture details. `build-26-2/` is a stale
standalone Fabric snapshot and must not be edited.

## License

MIT
