# AGENTS.md — AdminTools (Fabric Mod)

## Project state

Fabric mod providing server-authoritative admin tools. Two version modules in a
multi-project Gradle build:

| Module | Target | Build command |
|---|---|---|
| `version-1-21-11` | MC 1.21.11 (obfuscated + MojangMaps) | `./gradlew :version-1-21-11:build` |
| `version-26-2` | MC 26.2 (non-obfuscated, Mojang names) | `./gradlew :version-26-2:build` |

There is also a standalone build at `build-26-2/` (self-contained, no multi-project).

## Features

| Feature | Command | Notes |
|---|---|---|
| Player inventory viewer | `/invsee <player>` | 54-slot double-chest GUI, read-only |
| Ender chest viewer | `/endersee <player>` | 27-slot single-chest GUI, read-only |
| X-ray heuristic audit | `/xrayaudit <player>` | Dimension-specific rules; tracks mining speed, torch ratio, ore exposure, chunk updates |
| Role/Permission management | `/adminrole grant\|remove\|assign` | JSON config (`config/admintools/roles.json`), hot-reloadable |
| Item identity & anti-dupe | `/itemtrace <uuid>` | Per-stack persistent identity (`admintools:uid` data component), lineage, movement log, duplicate detection |
| Admin item give/remove | `/adminitem give\|remove` | Grants items with `ADMIN_GIVE` identity / removes items with `ADMIN_REMOVE` |

### Item identity system (26.2 only)

- Every stack gets a persistent `admintools:uid` data component on first observation
  (not re-generated on copy; survives moves/restarts; hidden from clients).
- `ItemStackMatchingMixin` exempts the uid from `isSameItemSameComponents` so vanilla
  stacking/splitting/merging is unaffected.
- Split stacks (same uid twice in one inventory) are re-identified same-tick with
  `SPLIT` lineage; merges record `MERGE` (absorbed parent); player↔player moves are
  correlated as `TRANSFER`.
- Persistence: `config/admintools/item_ledger.json` (snapshot) +
  `logs/admintools/item_ledger.jsonl` (events) + `item_duplicates.jsonl`.
- `ItemDuplicateDetector` flags the same uid in ≥2 independent locations (player
  inventories/ender chests) — alert-only, no auto-removal; creative excluded by
  `detect_creative_duplicates` config.
- Mixins (26.2): `ItemStackMatchingMixin`, `PlayerItemMixin` (pickup/drop),
  `ItemEntityMixin` (spawn), `BlockItemPlaceMixin` (place).
- Tests: `:version-26-2:runUnitTests` (dependency-free logic tests) and `@GameTest`
  methods in `com.echubbuck.admintools.test` via `:version-26-2:runGametest`.

## Architecture

- **Common module** (`:common`): MC-agnostic POJOs — roles, permissions, config,
  action logger, heuristic engine data models. No Minecraft imports.
- **Per-version modules**: Minecraft-dependent code — commands, screen handlers,
  screens, event hooks, client init.
- All GUIs use `splitEnvironmentSourceSets()` — `src/main/java` for shared/server,
  `src/client/java` for client-only (screens).
- Permission gate via custom `PermissionManager` layered on vanilla OP levels.

## Key build details

- **Gradle**: 9.5.1 (wrapper generated)
- **Loom**: 1.17.14 for both versions
- **For 1.21.11**: uses `fabric-loom` (legacy obfuscated) with `loom.officialMojangMappings()`
- **For 26.2**: uses `net.fabricmc.fabric-loom` (non-obfuscated); code uses Mojang
  official mapping names directly
- **Java**: 1.21.11 → Java 21, 26.2 → Java 25

## Critical API differences between versions

The code for both versions uses **Mojang official mapping names**. Key classes:

| Concept | Mojang class | Package |
|---|---|---|
| Text/Component | `Component` | `net.minecraft.network.chat` |
| ScreenHandler | `AbstractContainerMenu` | `net.minecraft.world.inventory` |
| MenuType | `MenuType` | `net.minecraft.world.inventory` |
| Identifier | `Identifier` | `net.minecraft.resources` |
| Player | `Player` | `net.minecraft.world.entity.player` |
| Inventory | `Inventory` | `net.minecraft.world.entity.player` |
| ServerPlayer | `ServerPlayer` | `net.minecraft.server.level` |
| Commands | `Commands` | `net.minecraft.commands` |
| CommandSourceStack | `CommandSourceStack` | `net.minecraft.commands` |
| EntityArgument | `EntityArgument` | `net.minecraft.commands.arguments` |
| PermissionSet | `PermissionSet` | `net.minecraft.server.permissions` |
| BuiltInRegistries | `BuiltInRegistries` | `net.minecraft.core.registries` |
| Container/SimpleContainer | `Container`/`SimpleContainer` | `net.minecraft.world` |

### Differences between 1.21.11 and 26.2

| Area | 1.21.11 | 26.2 |
|---|---|---|
| Render class | `GuiGraphics` | `GuiGraphicsExtractor` |
| GuiGraphics.blit pipeline | `RenderPipelines.GUI` | `RenderPipelines.GUI_TEXTURED` |
| Screen render method | `renderBg(GuiGraphics, float, int, int)` | `extractBackground(GuiGraphicsExtractor, int, int, float)` |
| Screen labels method | `renderLabels(GuiGraphics, int, int)` | `extractLabels(GuiGraphicsExtractor, int, int)` |
| AbstractContainerMenu.quickMove | `quickMoveStack(Player, int)` | `quickMoveStack(Player, int)` |
| ServerTickEvents constant | `START_WORLD_TICK` | `START_LEVEL_TICK` |
| ServerTickEvents interface | `StartWorldTick` | `StartLevelTick` |
| Loader requirement | `>=0.16.8` | `>=0.19.3` |
| Java target | 21 | 25 |

## Git workflow

Commit after each logical unit. Use `./gradlew :<module>:build -x test` to verify.

## Dependencies

- `common/`: Gson 2.10.1 (JSON serialization)
- Both version modules: Fabric Loader, Fabric API (version-specific)
- 26.2 uses 4 mixins (item identity system); no third-party mod dependencies
