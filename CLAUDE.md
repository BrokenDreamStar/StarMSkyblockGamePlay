# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build / Run

- **Build:** `./gradlew build`
- **Run dev server:** `./gradlew runServer` — launches a PaperMC 26.1.2 server with your plugin loaded, with 2GB RAM allocated
- **Clean build:** `./gradlew clean build`

This is a Gradle 9.6.1 project using Kotlin DSL and Java 25 toolchain. Configuration cache, parallel execution, and build caching are enabled in `gradle.properties`.

## Architecture

**StarMSkyblockGamePlay** is a PaperMC plugin for a Skyblock game mode. The single entrypoint is `StarMSkyblockGamePlay extends JavaPlugin`, which registers all listeners on `onEnable` and starts the Vault daily-reset scheduler. There is no command system — all features are purely event-driven.

### Three Feature Modules (all in `listener/`)

Each listener is independent, receives the plugin instance in its constructor, and reads its own section of `config.yml`.

1. **TrialSpawnerListener** — Right-click a Trial Spawner block with a copper block to reduce its cooldown by a configurable tick amount. Cancels the interaction event so the copper block is consumed instead of placed.

2. **VaultListener** — Right-click a Vault block (regular or ominous) with a copper block to remove yourself from the vault's rewarded-player blacklist. Tracks per-player daily removal counts in `vault-data.yml` (in the plugin data folder), resetting each cycle at a configurable time of day (default 04:00). The `startResetTask` method runs a repeating BukkitRunnable scheduler — `onEnable` calls this once so it starts ticking. Also handles ominous vaults using an `Ominous Trial Key` check on `vault.getKeyItem()`.

3. **SilkTouchCollectListener** — When a player breaks a configurable block type with a Silk Touch tool, the block's full NBT state is serialized into the item's `PersistentDataContainer` (via `NamespacedKey` + YAML string) and the block does not drop its default loot. On `BlockPlaceEvent`, the stored state is deserialized and applied back to the new block. Supports three block types: `CreatureSpawner`, `TrialSpawner`, and `Vault`, each with their own serialized fields.

### Configuration (`config.yml`)

All features are toggleable via `enabled` flags. Key config paths:
- `trial-spawner.cooldown-reduction-ticks` — tick reduction per copper block (default 6000 = 5 min)
- `vault.daily-limit` / `vault.blacklist-removal-limit` — per-player daily caps; `<= 0` means unlimited
- `ominous-vault.blacklist-removal-limit` — same as above but for ominous vaults
- `vault.reset-time` / `ominous-vault.reset-time` — `HH:mm` format for daily cycle boundaries
- `silk-touch-collectibles.blocks` — list of `Material` enum names that silk touch can harvest

### Data Model

Per-player vault data persists in `vault-data.yml` (auto-created in the plugin data folder) with the structure:
```yaml
players:
  <uuid>:
    vault:
      cycle: "2026-07-26"
      count: 5
      removals: 2
    ominous-vault:
      cycle: "2026-07-26"
      removals: 1
last-reset:
  vault: "2026-07-26"
  ominous-vault: "2026-07-26"
```

## Dependencies

- **Paper API** 26.1.2 (compileOnly) — this corresponds to Minecraft 1.21.4+ style API. The server is run on the same version.
- **No other dependencies** — no shadow jar, no external libraries beyond Paper API.
