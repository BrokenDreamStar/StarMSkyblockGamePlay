# Snowball Converter Design

> Convert mobs into spawn eggs via special NBT snowballs with configurable chance.

## Overview

Admin-granted snowballs with a PDC tag (`mob_converter`) can convert hit mobs into spawn eggs. Configurable per-mob and global probability, with a blacklist for excluded entity types.

## Configuration

### config.yml

```yaml
snowball-converter:
  enabled: true
  global-chance: 30
  blacklist:
    - IRON_GOLEM
    - SNOWMAN
    - WITHER
    - ELDER_GUARDIAN
  mob-chances:
    ZOMBIE: 50
    CREEPER: 40
```

### messages.yml

```yaml
snowball-converter:
  converted: "&a成功将生物转换为刷怪蛋！"
  failed: "&7生物抵抗了转换效果..."
  no-egg: "&c该生物没有对应的刷怪蛋"
  give-snowball: "&a已给予 {player} {amount} 个特殊雪球"
```

## Command

```
/starmskyblockgameplay givesnowball <player> [amount] [chance]
```

- `amount`: default 1
- `chance`: optional override stored in PDC; if 0 or absent, uses config

## NBT / PDC

- `NamespacedKey(plugin, "mob_converter")` → `PersistentDataType.INTEGER` (optional custom chance, 0 = use config)
- `NamespacedKey(plugin, "mob_converter_flag")` → `PersistentDataType.BOOLEAN` (true = this is a converter snowball)

## SnowballConverterListener

**Events:**
- `ProjectileHitEvent` — main logic
- `EntityDamageByEntityEvent` — cancel damage from converter snowballs

**Flow:**
1. Check projectile is Snowball with `mob_converter_flag` PDC
2. Check entity is LivingEntity, not player, not in blacklist
3. Check entity has a spawn egg material mapping
4. Roll chance (snowball PDC > mob-chances > global-chance)
5. Success: remove entity, create spawn egg, give to player (drop if full), send message
6. Fail: send failure message

## EntityType → Material mapping

Static map covering all vanilla mobs that have spawn eggs. Used to convert `EntityType` → `Material` (e.g. `ZOMBIE` → `ZOMBIE_SPAWN_EGG`).
