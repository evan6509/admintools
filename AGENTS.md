# AGENTS.md — AdminTools (Fabric Mod)

## Project state

Fabric mod providing server-authoritative admin tools. The multi-project Gradle
build currently targets one supported version:

| Module | Target | Build command |
|---|---|---|
| `version-26-2` | MC 26.2, Java 25 | `./gradlew :version-26-2:build` |

- `build-26-2/` is a **stale standalone snapshot** (git-tracked, predates the item
  identity feature — no `ItemTraceCommand`, no mixins). Do not edit it;
  `version-26-2/` is canonical.
- The module uses **Mojang official mapping names** (not Yarn): `Component` not
  `Text`, `AbstractContainerMenu` not `Menu`, `Identifier` not `ResourceLocation`.
- Default JDK on this machine is 25.

## Features

| Feature | Command | Notes |
|---|---|---|
| Player inventory viewer | `/invsee <player> [edit]` | 36-slot vanilla chest GUI backed by the target's real `Inventory`; `edit` makes it writable |
| Ender chest viewer | `/endersee <player>` | 27-slot single-chest GUI, read-only |
| Role/Permission management | `/adminrole grant\|remove\|assign` | JSON config (`config/admintools/roles.json`), hot-reloadable |
| Item identity & anti-dupe (26.2) | `/itemtrace <uuid>` | Per-stack persistent identity (`admintools:uid` inside vanilla custom data), lineage, movement log, duplicate detection |
| Admin item give/remove (26.2) | `/adminitem give\|remove` | Grants items with `ADMIN_GIVE` identity / removes items with `ADMIN_REMOVE` |

Gating: viewer/trace/item commands require OP4
(`Permissions.COMMANDS_GAMEMASTER`); `/adminrole` requires OP2
(`Permissions.COMMANDS_ADMIN`). Viewers are additionally toggleable via config
(`enable_inventory_viewer`, etc.).

### Item identity system (26.2 only)

- Every stack gets a persistent `admintools:uid` key inside vanilla
  `minecraft:custom_data` on first observation (not re-generated on copy;
  survives moves/restarts). `DataComponentPatchNetworkMixin` strips the key
  from outbound item packets, so it never syncs to clients. The legacy custom
  component remains registered only to migrate items saved by older builds;
  `RegistrySyncCompatibilityMixin` prevents that legacy-only entry from making
  Fabric API reject vanilla clients. Other mods' custom component entries still
  trigger Fabric's normal client compatibility check.
- `ItemStackMatchingMixin` exempts the uid from `isSameItemSameComponents` so
  vanilla stacking/splitting/merging is unaffected.
- Split stacks (same uid twice in one inventory) are re-identified same-tick with
  `SPLIT` lineage; merges record `MERGE` (absorbed parent); player↔player moves
  are correlated as `TRANSFER`.
- Persistence: `config/admintools/item_ledger.json` (snapshot) +
  `logs/admintools/item_ledger.jsonl` (events) + `item_duplicates.jsonl`.
- `ItemDuplicateDetector` flags the same uid in ≥2 independent locations (player
  inventories/ender chests) — alert-only, no auto-removal; creative excluded by
  `detect_creative_duplicates` config.
- Mixins (26.2): `DataComponentPatchNetworkMixin` (outbound UID filtering),
  `RegistrySyncCompatibilityMixin` (legacy-only registry compatibility),
  `ItemStackMatchingMixin`, `PlayerItemMixin` (pickup/drop), `ItemEntityMixin`
  (spawn), `BlockItemPlaceMixin` (place).
  `admintools.mixins.json` is `required: true` — a missed injection crashes the
  game, it does not just log.

## Architecture — read before editing

- **Common classes are vendored in `version-26-2`.** The module owns its copy of
  `com.echubbuck.admintools.common.*` under `src/main/java` and builds a
  self-contained jar.
- **No client source set, no client entrypoint.** There is no `src/client/java`
  and no `client` entrypoint in `fabric.mod.json` (`environment: "server"`). The
  viewer GUIs are vanilla `ChestMenu` subclasses (`InvSeeScreenHandler`,
  `EnderSeeScreenHandler`) opened via `SimpleMenuProvider` and rendered by the
  vanilla client screen — there are no custom `Screen` classes.
- The version module holds: commands, screen handlers, event hooks, and the
  `AdminToolsMod` initializer (static singletons exposed via getters).
- Permission gate via custom `PermissionManager` layered on vanilla OP levels.

## Key build details

- Gradle 9.5.1 (wrapper); Loom 1.17.14.
- 26.2 uses `net.fabricmc.fabric-loom` (non-obfuscated pipeline) and
  `--release 25`.
- Dependencies: Gson 2.10.1, Fabric Loader, and Fabric API. No
  third-party mod dependencies.

### 26.2 API details

| Area | 26.2 |
|---|---|
| Render class | `GuiGraphicsExtractor` |
| `Screen` background hook | `extractBackground(GuiGraphicsExtractor, int, int, float)` |
| `Screen` main render | `extractRenderState(GuiGraphicsExtractor, int, int, float)` |
| Textured blit pipeline | `RenderPipelines.GUI_TEXTURED` in `net.minecraft.client.renderer` |
| `ServerTickEvents` | `START_LEVEL_TICK` / `StartLevelTick` |
| Loader requirement | `>=0.19.3` |

## Tests

- `./gradlew :version-26-2:runUnitTests` — dependency-free logic tests. This is a
  plain `main`-class runner (`ItemLogicTestRunner`), **not JUnit**.
- `./gradlew :version-26-2:runGametest` — `@GameTest` methods, which live in
  `src/main/java/com/echubbuck/admintools/test/` (not `src/test`).

## Manual testing (26.2)

```bash
./test-server/run-26-2.sh            # = ./gradlew :version-26-2:runServer (foreground, Ctrl+C)
./test-server/run-26-2.sh --offline  # if Gradle can't reach the network
```

- Listens on `localhost:25565`; server data (world/logs/config) goes to
  `version-26-2/run/` (gitignored). `test-server/` is itself gitignored — a
  local-only helper.
- The server runs commands and item tracking. Viewer GUIs use
  vanilla menu types, so vanilla clients can connect without AdminTools, Fabric
  Loader, or Fabric API installed.
- Make yourself OP in the server console (`op <name>`): OP4 for viewers/trace/item commands,
  OP2 for `/adminrole`.

## Git workflow

Conventional-commit style (`feat:`, `refactor:`, `chore:`). Commit after each
logical unit. Verify with `./gradlew :<module>:build`.
