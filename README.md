# Villager Retaliation!

Villager Retaliation! is a Vanilla+ Minecraft mod that makes villagers and wandering traders respond more dynamically when threatened. Adult villagers can fight back, react to crimes they witness, use profession-based combat behavior, and remember how each player treats them through a villager reputation system.

## Supported Versions

| Minecraft | Loader | Status |
| --- | --- | --- |
| 1.21.1 | NeoForge | Active |
| 1.21.1 | Fabric | Scaffolded only |

Fabric is present only as a template-compatible skeleton. The gameplay systems have not been ported to Fabric yet.

## Features

- Villagers can retaliate when attacked.
- Nearby villagers may rally when they witness a kill.
- Player-placed lava and fire can be attributed as aggression for a short window.
- Reputation changes through trading, village defense, attacks, and witnessed crimes.
- Reputation can affect trade pricing, pacification, anger duration, fleeing, and hostile behavior.
- Profession-based combat lets villagers use role-appropriate weapons and support behavior.
- Wandering traders can participate in retaliation and loot behavior.

## Build

Use Java 21.

```bash
./gradlew build
```

On Windows:

```bat
gradlew.bat build
```

Build outputs stay inside the repository under Gradle `build/` directories.

## Project Layout

```text
common/     Shared resources and no-op common bootstrap
fabric/     Fabric skeleton only
neoforge/   Active NeoForge gameplay implementation
gradle/     Gradle wrapper files
.github/    CI workflows
```

The Java package is:

```text
com.jvn.villagerretaliation
```

The released internal mod id remains:

```text
villagerretaliation
```

## ToucanLib

ToucanLib resolves from the published Modrinth artifact by default:

```gradle
maven.modrinth:toucan:0.1.4-neoforge
```

CI and release builds should use the default published dependency. Do not rely on a local ToucanLib project, local jars, `flatDir`, included builds, or `mavenLocal()` for release builds.

An opt-in local Maven override exists for advanced local development only:

```bash
./gradlew build -PuseLocalToucanLib=true
```

Do not enable `-PuseLocalToucanLib=true` in CI or release workflows.

## Development Notes

- Minecraft: `1.21.1`
- Java: `21`
- Active loader: NeoForge
- Fabric: skeleton only
- Current version: `1.0.0-beta.4`
- Main source module: `neoforge`
- Shared resources module: `common`

## Known TODOs

- Port gameplay systems to Fabric in a future pass.
- Keep Fabric metadata explicit that the module is scaffolded only until the port exists.
