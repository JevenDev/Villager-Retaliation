# Resource Pack Models

Villager Retaliation uses one villager model and texture layout for both crossed-arms and side-arms rendering.

## Texture Paths

| Entity | Normal texture | Villager Retaliation texture |
| --- | --- | --- |
| Villager | `assets/minecraft/textures/entity/villager/villager.png` | `assets/villagerretaliation/textures/entity/villager/villager.png` |
| Wandering trader | `assets/minecraft/textures/entity/wandering_trader.png` | `assets/villagerretaliation/textures/entity/wandering_trader/wandering_trader.png` |

For Villager Retaliation's side-arm/crossed-arm model, put the 128x128 base, profession, profession level, and biome type textures under the `villagerretaliation` path. The renderer uses those `villagerretaliation` overlays only while the Villager Retaliation model is active.

Only replace files under `assets/minecraft/textures/entity/villager/` when you intentionally want to override the vanilla/base CEM villager textures too. Base Fresh Animations uses 64x64 `minecraft` villager textures, so pairing its `villager.jem` with 128x128 Villager Retaliation textures will look mis-mapped.

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

## Fresh Animations Compatibility

When Entity Model Features is installed and a resource pack provides `assets/minecraft/optifine/cem/villager.jem`, default villagers with empty hands use the vanilla EMF-backed villager model so base Fresh Animations can animate them. That fallback is only enabled when the active top `minecraft:textures/entity/villager/villager.png` is 64x64, matching Fresh Animations' vanilla CEM layout.

Villagers switch back to the Villager Retaliation JSON model whenever they need side arms for held items, weapons, shields, bows, potions, or throwing poses. The side-arm model uses the 128x128 `villagerretaliation` texture layout and should not be paired with base Fresh Animations' vanilla `villager.jem`.

## Practical Advice

- Start from the built-in model and change it gradually.
- Keep required part names exactly as documented.
- If your pack only changes textures, you do not need model JSON at all.
