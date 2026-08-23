# AdminTools

Server-authoritative admin tools for Minecraft: Fabric 26.2. View player
inventories and ender chests in live vanilla GUIs, audit players with X-ray
heuristics, manage custom roles, and track per-stack item identity to catch
dupes.

## Supported versions

| Module | Minecraft | Java | Fabric Loader | Fabric API |
|---|---|---|---|---|
| `version-26-2` | 26.2 | 25 | >= 0.19.3 | >= 0.154.2 |

Each version module builds a self-contained jar:
`version-<ver>/build/libs/admintools-<ver>-1.0.0.jar`.

## Features

| Feature | Command | Permission | Versions |
|---|---|---|---|
| Player inventory viewer | `/invsee <player> [edit]` | OP4 | 26.2 |
| Ender chest viewer | `/endersee <player>` | OP4 | 26.2 |
| X-ray heuristic audit | `/xrayaudit <player>` | OP4 | 26.2 |
| Role/permission management | `/adminrole grant\|remove\|assign` | OP2 | 26.2 |
| Item identity & anti-dupe | `/itemtrace <uuid>` | OP4 | 26.2 |
| Admin item give/remove | `/adminitem give\|remove <player> <item> [count]` | OP4 | 26.2 |
| Container audit | `/containertrace <x> <y> <z>` | OP4 | 26.2 |

- `/invsee` opens a 36-slot vanilla chest GUI backed by the target's real
  inventory; with `edit` (or `invsee_edit_mode` in config) it is writable.
- `/endersee` opens a 27-slot single-chest GUI, read-only.
- `/xrayaudit` reports dimension-specific heuristic signals: mining speed,
  torch ratio, ore exposure, and chunk-update patterns.
- `/adminrole` manages JSON-defined roles (`config/admintools/roles.json`),
  hot-reloadable.

### Item identity (26.2)

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
- Persistence: `config/admintools/item_ledger.json` (snapshot),
  `logs/admintools/item_ledger.jsonl` (movement events), and
  `logs/admintools/item_duplicates.jsonl` (duplicate alerts).

## Configuration

`config/admintools/config.json` (created on first run):

| Key | Default | Description |
|---|---|---|
| `enable_inventory_viewer` | `true` | Toggle `/invsee` |
| `enable_ender_chest_viewer` | `true` | Toggle `/endersee` |
| `enable_xray_audit` | `true` | Toggle `/xrayaudit` |
| `max_command_rate` | `10` | Reserved (currently unused) |
| `log_actions_to_file` | `true` | Write action log |
| `invsee_edit_mode` | `false` | Make `/invsee` writable by default |
| `detect_creative_duplicates` | `false` | Include creative players in dupe detection |
| `enable_container_audit` | `true` | Log player-opened chests, barrels, and shulker boxes |

Roles live in `config/admintools/roles.json`.

## Building

Requires the Gradle wrapper (Gradle 9.5.1, Loom 1.17.14):

```bash
./gradlew :version-26-2:build      # MC 26.2, Java 25
```

## Testing

```bash
./gradlew :version-26-2:runUnitTests   # dependency-free logic tests
./gradlew :version-26-2:runGametest    # @GameTest suite
```

Manual server testing (26.2):

```bash
./test-server/run-26-2.sh            # foreground, localhost:25565
./test-server/run-26-2.sh --offline  # if Gradle can't reach the network
```

Server data goes to `version-26-2/run/`. AdminTools is server-only; clients do
not need the AdminTools jar, Fabric Loader, or Fabric API to connect. The
viewer GUIs use vanilla menu types and are rendered by the vanilla client.
Make yourself OP in the server console: OP4 for viewers/audit/trace, OP2 for
`/adminrole`.

## Development notes

See [AGENTS.md](AGENTS.md) for architecture details: common classes are
vendored per version module, the 26.2 mixin list is `required: true`, and
`build-26-2/` is a stale standalone snapshot that should not be edited.

## License

MIT
