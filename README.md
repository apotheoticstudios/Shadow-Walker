# Apotheotic's Shadow Walker

Standalone Forge 1.20.1 mod containing the stealth awareness and sneak attack mechanics extracted from Thu'umcraft.

## Features

- Light, distance, facing, sound, armor, invisibility, and line-of-sight based stealth detection.
- Sneaking players can avoid hostile mob target acquisition while undetected.
- Synced stealth eye indicator with hidden, suspicious, searching, and detected states.
- Detection rises through the awareness cycle instead of snapping straight to detected.
- Synced `apotheotics_shadow_walker:sneak` player attribute for per-player stealth scaling. Players start at 0, and the attribute maximum is 100.
- Shadow Walker armor enchantment, with one level and +20 Sneak per enchanted armor piece.
- Configurable melee, one-handed, dagger, and ranged sneak attack multipliers.
- Sneak attack particles, feedback messages, and attack noise.

## Configuration

The common config is generated at `config/apotheotics_shadow_walker-common.toml`.

Important options:

- `enableStealthSystem`: master switch for stealth awareness, targeting suppression, and sneak attacks.
- `showStealthCrosshair`: toggles the stealth eye overlay.
- `removeInvisibilityOnAttack`: removes vanilla invisibility from players after they damage another entity. Defaults to true.
- `sneakLevel`: global stealth proficiency from 0 to 100. This stacks with the player's `apotheotics_shadow_walker:sneak` attribute, which starts at 0 and is capped at 100.
- `maxScanRange`: upper bound for mob awareness scans.
- `crosshairDetectionRange`: radius, in blocks, where detected mobs can affect the stealth eye. Defaults to 24.
- `armorNoiseMultiplier`: controls how much armor contributes to sneak noise.
- `observerMobMovementMultiplier`: slows idle hostile mobs while they are observing nearby sneaking players. Use 0 to stop wandering, or 1 for vanilla movement.
- `meleeSneakAttackMultiplier`, `oneHandedSneakAttackMultiplier`, `daggerSneakAttackMultiplier`, `rangedSneakAttackMultiplier`: damage multipliers.

## Weapon Classification

- Ranged sneak attacks use Minecraft damage types tagged as projectiles.
- Melee sneak attacks happen when the direct damage entity is the attacking player.
- Daggers use the `apotheotics_shadow_walker:daggers` item tag, with a fallback for item registry paths containing `dagger`.
- One-handed melee weapons are non-heavy `SwordItem`, `AxeItem`, `TridentItem`, or other `TieredItem` instances.
- Heavy melee weapons are detected by registry paths containing `greatsword`, `battleaxe`, `warhammer`, `claymore`, `halberd`, `glaive`, or `scythe`.

The dagger tag lives at `src/main/resources/data/apotheotics_shadow_walker/tags/items/daggers.json`.

To generate dagger tag entries from item ids, resource roots, or mod jars:

```bash
python3 scripts/generate_dagger_tag.py --mods-dir run/mods
```

## Credits

- `sneak_detected` icon: Cloak and Dagger mod

## Development

Open this folder directly in IntelliJ as a Gradle project.

Useful commands:

```bash
./gradlew compileJava
./gradlew build
./gradlew runClient
```

Requires Java 17.
