# AGENTS.md — AdminTools (Fabric Mod)

## Project state

This repo contains only a **planning document** (`Admin Tool Mod Development - 2026-07-13 10.38.md`). No code, build files, or manifests exist yet. Everything you generate should implement the design described there.

## Mod identity

- **Loader:** Fabric
- **Purpose:** Server-authoritative admin tools (no vanilla command replacements)
- **Permission gating:** Custom role system (`/adminrole assign|grant|remove`) layered on top of vanilla OP

## Required features

| Feature | Entry | Notes |
|---|---|---|
| Player inventory viewer | `/invsee <player>` | Double-chest GUI (54 slots). Read-only by default; optional edit mode with logging |
| Ender chest viewer | `/endersee <player>` | Single-chest GUI (27 slots). Read-only with metadata panel |
| X-ray heuristic detector | `/xrayaudit <player>` | Dimension-specific rules. Tracks mining speed, torch ratio, ore exposure rate, chunk updates |

## Architecture constraints (from planning doc)

- All GUIs are server-authoritative (server → client packet sync, never trust client)
- Permission gate on every action (`hasAdminAccess(Player)` + role checks)
- Action logging to JSON file + chat fallback
- Rate limiting on commands
- No chunk/block modification by tools
- Offline players → command fails with friendly message
- Modded items → show basic ID/metadata, not crash

## Starting from scratch

You will need to generate the full Fabric mod project structure:
- `fabric.mod.json`, `gradle.properties`, `build.gradle`
- Mixin config if needed
- Mod initializer class
- Custom commands (`CommandRegistrationCallback`)
- GUI screens (`Screen`/`ScreenHandler` for `/invsee` and `/endersee`)
- Role/permission config (JSON, hot-reloadable)
- Heuristic tracking engine (server-side, throttled to 1-2 ticks/sec per player)
- Networking (`PacketByteBuf` based, server→client sync)
