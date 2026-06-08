# Resource Pack Models

Villager Retaliation uses one villager model and texture layout for both crossed-arms and side-arms rendering.

## Texture Paths

| Entity | Normal texture | Villager Retaliation texture |
| --- | --- | --- |
| Villager | `assets/minecraft/textures/entity/villager/villager.png` | `assets/villagerretaliation/textures/entity/villager/villager.png` |
| Wandering trader | `assets/minecraft/textures/entity/wandering_trader.png` | `assets/villagerretaliation/textures/entity/wandering_trader/wandering_trader.png` |

For villagers, use the same 128x128 layout at both paths when you want vanilla profession layers and the Villager Retaliation renderer to line up. Villager profession, profession level, and biome type layers still use the vanilla paths under `assets/minecraft/textures/entity/villager/`.

## Model Path

```text
assets/villagerretaliation/models/entity/villager/combat_villager.json
```

This one model must include both crossed arms and side arms. The renderer swaps visibility between `arms` and `RightArm`/`LeftArm`.

## Minimal Model Shape

```json
{
  "texture_width": 128,
  "texture_height": 128,
  "parts": [
    { "name": "body", "cubes": [] },
    { "name": "arms", "pivot": [0.0, 2.0, 0.0], "rotation": [-45.0, 0.0, 0.0], "cubes": [] },
    { "name": "RightArm", "pivot": [-5.0, 2.0, 0.0], "cubes": [] },
    { "name": "LeftArm", "pivot": [5.0, 2.0, 0.0], "cubes": [] },
    { "name": "RightLeg", "pivot": [-2.0, 12.0, 0.0], "cubes": [] },
    { "name": "LeftLeg", "pivot": [2.0, 12.0, 0.0], "cubes": [] },
    { "name": "head", "cubes": [] }
  ]
}
```

Required part names:

```text
body
arms
head
RightArm
LeftArm
RightLeg
LeftLeg
```

## Practical Advice

- Start from the built-in model and change it gradually.
- Keep required part names exactly as documented.
- If your pack only changes textures, you do not need model JSON at all.
