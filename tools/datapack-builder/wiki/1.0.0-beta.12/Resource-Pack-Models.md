# Resource Pack Models

Villager Retaliation supports combat-specific textures plus JSON model overrides for villagers.

## Texture Paths

| Entity | Normal texture | Combat texture |
| --- | --- | --- |
| Villager | `assets/minecraft/textures/entity/villager/villager.png` | `assets/villagerretaliation/textures/entity/villager/villager.png` |
| Wandering trader | `assets/minecraft/textures/entity/wandering_trader.png` | `assets/villagerretaliation/textures/entity/wandering_trader/wandering_trader.png` |

If you retexture villagers, you usually want both the vanilla path and the combat path.

## Combat Model Path

```text
assets/villagerretaliation/models/entity/villager/combat_villager.json
```

## Minimal Combat Model Shape

```json
{
  "texture_width": 64,
  "texture_height": 64,
  "parts": [
    {
      "name": "body",
      "pivot": [0.0, 24.0, 0.0],
      "children": [
        { "name": "head", "pivot": [0.0, -12.0, 0.0], "cubes": [] },
        { "name": "RightArm", "pivot": [-5.0, -10.0, 0.0], "cubes": [] },
        { "name": "LeftArm", "pivot": [5.0, -10.0, 0.0], "cubes": [] },
        { "name": "RightLeg", "pivot": [-2.0, 0.0, 0.0], "cubes": [] },
        { "name": "LeftLeg", "pivot": [2.0, 0.0, 0.0], "cubes": [] }
      ]
    }
  ]
}
```

Required part names:

```text
body
head
RightArm
LeftArm
RightLeg
LeftLeg
```

## Optional Non-Combat Model

By default, non-combat villagers keep the vanilla crossed-arms model. To opt into a custom non-combat model:

```text
assets/villagerretaliation/models/entity/villager/render_options.json
assets/villagerretaliation/models/entity/villager/non_combat_villager.json
```

`render_options.json`:

```json
{
  "non_combat_model": "custom"
}
```

Use `"vanilla"` or omit the file to keep the default non-combat model.

## Practical Advice

- Start from the built-in combat model and change it gradually.
- Keep required part names exactly as documented.
- Treat custom non-combat models as optional polish, not a requirement.
- If your pack only changes textures, you do not need model JSON at all.
