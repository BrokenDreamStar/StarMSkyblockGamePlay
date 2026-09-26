# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build / Run

- **Build:** `./gradlew build`
- **Run dev server:** `./gradlew runServer` — launches a PaperMC 26.1.2 server with your plugin loaded, with 2GB RAM allocated
- **Clean build:** `./gradlew clean build`

This is a Gradle 9.6.1 project using Kotlin DSL and Java 25 toolchain. Configuration cache, parallel execution, and build caching are enabled in `gradle.properties`.

## Architecture

**StarMSkyblockGamePlay** is a PaperMC plugin for a Skyblock game mode. The single entrypoint is `StarMSkyblockGamePlay extends JavaPlugin`, which registers all listeners on `onEnable` and starts the Vault daily-reset scheduler. There is no command system — all features are purely event-driven.

### Feature Modules (all in `listener/`)

Each listener is independent, receives the plugin instance in its constructor, and reads its own section of `config.yml`.

1. **TrialSpawnerListener** — Right-click a Trial Spawner block with a copper block to reduce its cooldown by a configurable tick amount. Cancels the interaction event so the copper block is consumed instead of placed.

2. **VaultListener** — Right-click a Vault block (regular or ominous) with a copper block to remove yourself from the vault's rewarded-player blacklist. Tracks per-player daily removal counts in `vault-data.yml` (in the plugin data folder), resetting each cycle at a configurable time of day (default 04:00). The `startResetTask` method runs a repeating BukkitRunnable scheduler — `onEnable` calls this once so it starts ticking. Also handles ominous vaults using an `Ominous Trial Key` check on `vault.getKeyItem()`.

3. **SilkTouchCollectListener** — When a player breaks a configurable block type with a Silk Touch tool, the block's full block-entity NBT is stored on the dropped item via `BlockStateMeta#setBlockState` (only `Vault`'s ominous flag is tracked separately in the item's PDC, since block state is deliberately not carried: `BlockDataMeta` would also force the harvested facing instead of the player's placement direction). On `BlockPlaceEvent`, the plugin re-applies the item's block entity data to the placed block via `BlockState.copy(location).update()` — required because vanilla's `updateCustomBlockEntityTag` refuses to apply `block_entity_data` in survival: `SPAWNER` and `TRIAL_SPAWNER` are in the `OP_ONLY_CUSTOM_DATA` set (`onlyOpCanSetNbt`), so non-OP survival players place spawners that fall back to an empty/default config. Block data and block entity data are separate things: `setBlockState` stores no `block_state` component, so the snapshot's block data is always the material default (`VAULT`: `ominous=false`, `facing=north`) while `BlockState#update()` re-places the whole snapshot — `applyBlockEntityData` therefore copies the placed block's own `BlockData` onto the snapshot first and runs before the ominous flag is applied, otherwise silk-touched ominous vaults come back as plain vaults (and lose their placement orientation).

4. **CopperOxidationListener** — When copper blocks (and their oxidizable variants) are wet (waterlogged / adjacent to water), accelerate oxidation in vanilla "random tick" semantics: this Paper version exposes no random-tick event API, so every wet copper block rolls a simulated vanilla random tick at the original per-block frequency (average once per 68.27 s = 1/1365.3 per tick) and, when selected, advances directly to the next oxidation stage with the configurable `copper-oxidation.advance-chance-per-tick` chance (default 14.2%, ≈7 random ticks ≈ 8 min per stage) — `setType` along the `NEXT_OXIDATION` chain map, skipping vanilla pre-oxidation accumulation. Copper golems (no random ticks) share the exact same simulated selection (same frequency, same chance) and advance one stage via `CopperGolem#setWeatheringState` when selected in water. Waxed and fully-oxidized entries are excluded. Wet-copper and golem indices are both event-driven (wet-copper: place / water-flow / break with startup scan + periodic rescan; golems: `EntityAddToWorldEvent`/`EntityRemoveFromWorldEvent` with a one-time startup scan), no full entity iteration, no periodic oxidation timer.

### Configuration (`config.yml`)

All features are toggleable via `enabled` flags. Key config paths:
- `trial-spawner.cooldown-reduction-ticks` — tick reduction per copper block (default 6000 = 5 min)
- `vault.daily-limit` / `vault.blacklist-removal-limit` — per-player daily caps; `<= 0` means unlimited
- `ominous-vault.blacklist-removal-limit` — same as above but for ominous vaults
- `vault.reset-time` / `ominous-vault.reset-time` — `HH:mm` format for daily cycle boundaries
- `silk-touch-collectibles.blocks` — list of `Material` enum names that silk touch can harvest
- `copper-oxidation.advance-chance-per-tick` — chance (%, 0–100) that a wet copper block / in-water copper golem advances directly to the next oxidation stage per (simulated vanilla) random tick, ~68.27 s average; default 14.2 ≈ 8 min/stage
- `copper-oxidation.worlds` — target worlds (empty = all)

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
