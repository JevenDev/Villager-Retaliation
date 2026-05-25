# Resource Pack Models And Textures

Villager Retaliation uses separate combat textures and an optional JSON model format for combat villagers.

## Texture Paths

Idle villagers and wandering traders use vanilla texture paths. Combat pose renderers use Villager Retaliation texture paths.

| Entity | Normal texture | Combat texture |
| --- | --- | --- |
| Villager | `assets/minecraft/textures/entity/villager/villager.png` | `assets/villagerretaliation/textures/entity/villager/villager.png` |
| Wandering Trader | `assets/minecraft/textures/entity/wandering_trader.png` | `assets/villagerretaliation/textures/entity/wandering_trader/wandering_trader.png` |

Resource packs that retexture villagers should usually provide both the vanilla texture and the combat texture.

## Combat Model Path

Override the combat model at:

```text
assets/villagerretaliation/models/entity/villager/combat_villager.json
```

If the model is missing, invalid, or lacks required parts, the mod falls back to the built-in combat model.

## Model Format

```json
{
  "texture_width": 64,
  "texture_height": 64,
  "parts": [
    {
      "name": "body",
      "pivot": [0.0, 24.0, 0.0],
      "cubes": [
        {
          "uv": [16, 20],
          "origin": [-4.0, -24.0, -3.0],
          "size": [8.0, 12.0, 6.0],
          "inflate": 0.0,
          "mirror": false
        }
      ],
      "children": []
    }
  ]
}
```

Root fields:

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `texture_width` | integer | `64` | Texture width. `textureWidth` also works. |
| `texture_height` | integer | `64` | Texture height. `textureHeight` also works. |
| `parts` | array | required | Top-level model parts. |

Part fields:

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `name` | string | required | Part name. |
| `pivot` | number[3] | `[0, 0, 0]` | Pivot/offset. |
| `rotation` | number[3] | `[0, 0, 0]` | Rotation in degrees. |
| `cubes` | array | none | Cubes attached to this part. |
| `children` | array | none | Child parts. |

Cube fields:

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `uv` | integer[2] | required | Texture UV offset. |
| `origin` | number[3] | required | Box origin. |
| `size` | number[3] | required | Box size. |
| `inflate` | number | `0.0` | Cube deformation. |
| `deformation` | number | `0.0` | Alias fallback for `inflate`. |
| `mirror` | boolean | `false` | Mirrors UVs while adding the cube. |

## Required Parts

These part names must exist for the animation system:

```text
body
head
RightArm
LeftArm
RightLeg
LeftLeg
```

`head`, `RightArm`, `LeftArm`, `RightLeg`, and `LeftLeg` must be children of `body`.

Decorative children such as `nose`, `helmet`, and `brim` can be changed or removed.

## Non-Combat Model Opt-In

By default, non-combat villagers use Minecraft's vanilla crossed-arms model. To use a custom non-combat model, add:

```text
assets/villagerretaliation/models/entity/villager/render_options.json
```

```json
{
  "non_combat_model": "custom"
}
```

Then provide:

```text
assets/villagerretaliation/models/entity/villager/non_combat_villager.json
```

The non-combat model uses the same JSON shape and required parts as the combat model.

Use:

```json
{
  "non_combat_model": "vanilla"
}
```

or omit `render_options.json` to keep vanilla crossed arms.

## EMF Compatibility

If Entity Model Features is installed and no resource-pack JSON override is present for the combat model, Villager Retaliation can load the combat model through the EMF-compatible baked layer. Resource-pack JSON overrides still take priority over the built-in model source.

