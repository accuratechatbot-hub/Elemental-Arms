# Elemental Arms (Minecraft Forge 1.20.1)

Four vanilla items become elemental weapons (trident, golden axe, iron sword, stone sword). No new items, just powers.

| Weapon | Element | Passive (while held) |
|---|---|---|
| Trident | Lightning | Immune to lightning |
| Golden Axe | Fire | Immune to fire and lava, you stop burning |
| Iron Sword | Explosions | Immune to explosion damage |

## Controls

- **Sneak + Right-click** with the weapon: use the selected ability (normal right-click still behaves like vanilla, so tridents can still be thrown).
- **R** (rebindable, "Elemental Arms" category): next mode. Hold **Sneak** while pressing to go to the previous mode.
- Hover the item to see all modes, the selected one (">"), and the passive.
- The selected mode is stored on the item, so each weapon remembers its own mode.

## Abilities

### Trident - Lightning
| Mode | Cooldown | What it does |
|---|---|---|
| Smite | 1.5s | Lightning bolt where you look (up to 64 blocks) |
| Chain Lightning | 3s | Zaps the mob you look at, then arcs to up to 5 more nearby mobs |
| Thunderstorm | 20s | 14 real lightning bolts rain on the target area over ~3.5s |
| Lightning Dash | 3s | Dash forward at high speed, shocking everything you pass through (no fall damage) |
| Weather Control | 60s | Toggles a thunderstorm on/off in the Overworld |

### Golden Axe - Fire
| Mode | Cooldown | What it does |
|---|---|---|
| Fireball | 1.25s | Launches a large fireball |
| Flame Breath | 2.5s | Cone of flame in front of you; ignites mobs and nearby ground |
| Fire Pillar | 10s | Column of fire at the target that burns for ~6s |
| Flame Absorb | 4s | Absorbs fire blocks within 10 blocks (and fire on you/pets) into absorption hearts + a bit of healing |

### Iron Sword - Explosions
| Mode | Cooldown | What it does |
|---|---|---|
| Blast | 1.5s | Explosion exactly where you look |
| Rocket Leap | 4s | Explosion at your feet launches you where you look (no self damage, no fall damage) |
| Shockwave Line | 6s | Line of 8 explosions racing across the ground ahead of you |
| Detonation Mark | 5s | Marks a mob (it glows and is tracked) or a spot; it explodes after 3 seconds |

### Stone Sword - Reality (aggressive fire)
Passive while held: immune to fire and explosions, and monsters within 4 blocks of you catch fire.

| Mode | Cooldown | What it does |
|---|---|---|
| Telekinetic Grab | 5s | Lifts the mob you look at, holds it in front of you for 2.5s, then hurls it; it explodes on impact |
| Force Push | 3s | Burning shockwave that throws everything in front of you away |
| God Blast | 30s | Charge for ~1.2s (you are slowed), then a huge hellfire beam (70 blocks) that explodes along its path |
| Inferno Nova | 8s | Rings of hellfire race outward from you, burning and knocking back everything |
| Time Stop | 45s | Freezes all mobs and projectiles within 24 blocks for 5s (damage still lands) |
| Time Shift | 60s | Day becomes midnight, night becomes the next sunrise (Overworld only) |
| Rewind | 20s | Pulls you back to where you were ~5 seconds ago and restores lost health (it only remembers while you hold the Stone Sword, so hold it a few seconds first) |
| Reality Blink | 3s | Warps you up to 48 blocks where you look (stops before walls) |
| Singularity | 25s | Fiery black hole drags everything nearby in for 3s, then detonates |

Your own pets, tamed animals, armor stands and team mates are never hit by abilities.

## Config (`config/elementalarms-common.toml`)

- `blockDamage` (default `true`): `false` = explosions never break blocks and fire abilities never place fire. Fireballs still follow the `mobGriefing` gamerule.
- `damageMultiplier` (default `1.0`)
- `cooldownMultiplier` (default `1.0`, `0` = no cooldowns)
- `durabilityCost` (default `0`): durability lost per ability use

## Building

Requirements: **JDK 17**.

This project does not ship the Gradle wrapper jar. Easiest way to get it:

1. Download the Forge **1.20.1 MDK** from https://files.minecraftforge.net (any 47.x), and copy its `gradlew`, `gradlew.bat` and `gradle/` folder into this project (or run `gradle wrapper --gradle-version 8.1.1` with a local Gradle).
2. Run `./gradlew build` (Windows: `gradlew build`).
3. The mod jar is in `build/libs/elementalarms-1.0.0.jar`. Drop it into the `mods` folder of a Forge 1.20.1 (47.x) install, client and server.

For development: `./gradlew runClient`, or open the folder in IntelliJ / Eclipse as a Gradle project.

The Forge version in `gradle.properties` (`forge_version=47.3.0`) can be changed to any 47.x release.

## Project layout

```
src/main/java/com/example/elementalarms/
  ElementalArms.java        mod entry point
  Config.java               common config
  ability/
    WeaponType.java         weapon -> modes table (edit this to rebalance / add modes)
    Abilities.java          all ability implementations
    AbilityManager.java     mode storage, cooldowns, use entry point
    Targeting.java          raycasts + valid target rules
    Scheduler.java          delayed server tasks
    Fx.java                 particle helpers
  events/ForgeEvents.java   sneak+right-click trigger, passives, ticking
  network/                  "cycle mode" packet
  client/                   keybind + tooltips
```

To add a new mode: write a `static boolean myAbility(ServerPlayer, ServerLevel)` in `Abilities`, add a `new Mode("id", cooldownTicks, Abilities::myAbility)` to the weapon in `WeaponType`, and add `mode.elementalarms.<weapon>.<id>` to `en_us.json`.
