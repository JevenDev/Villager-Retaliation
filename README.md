<div align="center">

<h2><strong>Villagers remember, villagers fight back.</strong></h2>

</div>

<div align="center">

<a href="https://modrinth.com/mod/Villager-Retaliation/settings/versions?l=neoforge"><img src="https://raw.githubusercontent.com/intergrav/devins-badges/refs/heads/v3/assets/cozy/supported/neoforge_64h.png" alt="Available for NeoForge"></a>
<a href="https://modrinth.com/mod/toucan"><img src="https://raw.githubusercontent.com/JevenDev/toucanLib/refs/heads/1.21.1/docs/badges/toucanlib_toucanlib_cozy_64h.png" alt="Requires toucanLib"></a>
<br>
<a href="https://modrinth.com/mod/Villager-Retaliation" target="_blank" rel="noopener noreferrer"><img src="https://raw.githubusercontent.com/intergrav/devins-badges/refs/heads/v3/assets/compact-minimal/available/modrinth_46h.png" alt="Available on Modrinth"></a>
<a href="https://www.curseforge.com/minecraft/mc-mods/villager-retaliation" target="_blank" rel="nofollow"><img src="https://raw.githubusercontent.com/intergrav/devins-badges/refs/heads/v3/assets/compact-minimal/available/curseforge_46h.png" alt="Available on CurseForge"></a>
<a href="https://github.com/JevenDev/Villager-Retaliation" target="_blank" rel="noopener noreferrer"><img src="https://raw.githubusercontent.com/intergrav/devins-badges/refs/heads/v3/assets/compact-minimal/available/github_46h.png" alt="Available on GitHub"></a>

</div>

![villagers chasing a player](https://cdn.modrinth.com/data/cached_images/16269e99f4ef7ac15b6d24f3b523e5fa5778d5f5.png)

Villager Retaliation! is a Vanilla+ NeoForge mod that makes villagers and wandering traders less helpless without turning villages into warzones.

Adult villagers can defend themselves, react to crimes they witness, use profession-based combat roles, and remember how each player treats them through a personal reputation system.

- Attack a villager and that villager can fight back
- Kill a villager in public and nearby villagers may rally
- Player-placed lava and fire can count as aggression for a short attribution window
- Build trust through trade and positive village defense
- Lose trust through attacks, witnessed crimes, and village harm
- Make villagers suspicious, hostile, despised, or feared if you push things too far
- Keep babies defenseless and nitwits cowardly as intended

![features](https://cdn.modrinth.com/data/cached_images/ec0e4dc78ec1a652eb11b233dd2926f7461fe770.png)

![villagers carrying profession based weapons](https://cdn.modrinth.com/data/cached_images/44e64c624e3bad762345ee5f8df5d91c5ef5f6cd.png)

## Main Features

### Retaliation

Villagers are neutral instead of purely passive.

By default, hitting a villager only angers that villager. Killing an adult villager can anger nearby adult villagers if they can witness it. Anger expires after a configurable duration, and creative/spectator players can be ignored.

Villagers and wandering traders can also block trading while hostile. Hostile villagers can be pacified with emeralds, unless your reputation has fallen too low.

### Reputation

Reputation is tracked per villager and per player.

That means one villager can trust you while another despises you. Reputation can affect trade prices, pacification, anger duration, fleeing, despised attack-on-sight behavior, cleric support, and advancement progression.

Default tiers:

| Tier | Default threshold |
| --- | ---: |
| Royalty | 750 |
| Revered | 400 |
| Respected | 250 |
| Trusted | 75 |
| Neutral | 0 |
| Suspicious | -75 |
| Hostile | -100 |
| Despised | -250 |
| Feared | -750 |

Reputation changes are configurable. By default, direct villager hits are punished more than witnessed hits, villager kills are punished heavily, and player-attributed lava/fire damage counts for half the normal reputation penalty.

![villagers retaliating against a player that killed another villager](https://i.imgur.com/6c3vdac.gif)

### Profession Combat

Villagers fight in ways that match their profession.

| Profession | Behavior |
| --- | --- |
| Weaponsmith | Uses stronger melee behavior with swords |
| Armorer | Can fight defensively, gain resistance, and use shields in hard mode |
| Toolsmith | Uses tool-based melee |
| Mason | Uses mining-tool style melee |
| Butcher | Uses axe-based melee |
| Fletcher | Uses bows or crossbows |
| Farmer | Can defend themselves and heal with bread |
| Cleric | Uses potions for defense, attacks, and ally support |
| Librarian | Can fight with book-themed |
| Nitwit | Usually flees unless holding a usable weapon |

Fletchers and other villagers holding ranged weapons can use bows, crossbows, and tridents. Crossbows use charge/hold/fire states, and tridents use a thrown attack.

### Cleric Support

Clerics have the most advanced combat role.

They can drink defensive potions, throw harmful or slowing splash potions, avoid friendly splash damage where possible, heal injured villagers and wandering traders, and support trusted-or-better players.

Passive cleric healing has configurable range, health threshold, and line-of-sight rules.

### Hazard Attribution

Player-placed hazards can count as aggression.

If a player places lava, uses flint and steel, or uses a fire charge, nearby lava/fire damage can be attributed to that player for a short window. This allows "accidental" environmental attacks to still matter for retaliation, reputation, and advancements.

The default attribution window is 2 real-time minutes. Natural lava, old hazards, worldgen hazards, and untracked hazards are not meant to count.

![A player using lava to kill a villager, and being attributed to the kill](https://i.imgur.com/GSgbQr8.gif)

## Reputation Behavior

Higher trust can reduce anger duration, improve trade pricing, and unlock helpful cleric support.

Lower trust can make villagers more dangerous or less willing to deal with you:

- Suspicious villagers apply negative trade pressure
- Hostile villagers can harass you with eggs or poisonous potatoes
- Despised villagers can attack on sight when enabled
- Feared villagers visibly react when you get close
- Despised or feared villagers may refuse emerald pacification
- Babies, nitwits, and non-combat villagers can flee from hated players

## Loot

Villager Retaliation! adds configurable drops for villagers and wandering traders.

- Villagers can drop emeralds and bread
- Adult villagers can roll profession-themed loot
- Combat weapons can drop at configurable rates
- Baby villager loot is disabled by default
- Wandering traders can drop emeralds, invisibility potions, and safe copies of current trade results

Profession loot generally requires player kill credit by default.

## Advancements

The mod includes a full reputation advancement tab.

<details>
<summary><strong>All Advancements (Click to Expand)</strong></summary>

<br>

| Advancement | Type | Criteria | Hidden |
| --- | --- | --- | --- |
| Village Relations | Task (Tab Root) | Automatically granted when any Villager Retaliation advancement is awarded. | No |
| Commonfolk | Task | Interact with any villager, or enter a village. | No |
| I'm Sorry! | Task | Pacify a hostile villager with emeralds. | No |
| A Familiar Face | Task | Reach Trusted reputation with any villager. | No |
| Respect Is Earned | Task | Reach Respected reputation with any villager. | No |
| Friend of the Village | Goal | Reach Trusted with 5 villagers in one village area. | No |
| Local Legend | Challenge | Reach Revered reputation with any villager. | No |
| Crowned by the Village | Challenge | Reach Royalty reputation with any villager. | No |
| Second Chance | Goal | Cure a zombie villager that retains known reputation data for you. | No |
| The Village Remembers | Goal | Move from Suspicious or lower back to Neutral or higher. | No |
| Bad First Impression | Task | Reach Suspicious reputation with any villager. | No |
| Hands Off | Task | Damage a villager. | No |
| The Village Has Eyes | Goal | Harm or kill a villager while at least 3 adult villagers can witness it. | No |
| Marked | Challenge | Reach Feared reputation with any villager. | No |
| Village Enemy | Challenge | Have 5 or more villagers targeting you at the same time. | No |
| Mob Justice | Challenge | Have 8 or more villagers targeting you at the same time. | No |
| Regular Customer | Goal | Complete 10 trades with the same villager. | No |
| Community Support | Goal | Trade with 5 different villagers in one village area. | No |
| Price of Trust | Goal | Reach a positive trust tier with a villager after trading with them. | No |
| Refused Service | Task | Attempt to trade with a villager that blocks interaction due to low reputation. | No |
| Hero, Not Menace | Goal | While distrusted by nearby villagers, gain positive reputation by defending against hostiles. | No |
| An Unwise Decision | Goal | Damage an iron golem associated with a village. | No |
| Peace Offering | Challenge | After being Hostile or worse with a villager, return to Neutral or higher with that villager. | Yes |
| Accidentally, Of Course | Challenge | A villager dies from a player-attributed environmental hazard without direct player damage. | Yes |

</details>

![keybinds](https://cdn.modrinth.com/data/cached_images/201d5ce49ba16974e3c3b0b562c392e03f38e35f.png)

## Commands

Requires operator.

```mcfunction
/villagerretaliation setNearbyReputation <integer>
```

This sets nearby villagers' and wandering traders' reputation toward the executing player. It is mainly useful for testing tiers, trade pricing, despised behavior, feared behavior, pacification, and the debug overlay.

Example:

```mcfunction
/villagerretaliation setNearbyReputation -150
```

## Configuration

Main config file:

- Singleplayer/client: `config/villagerretaliation-common.toml`
- Dedicated server: `<server root>/config/villagerretaliation-common.toml`

Config categories include:

- `general` - master feature toggles
- `balance` - loot and drop rates
- `retaliation` - anger rules, aggro radius, duration, line-of-sight witnesses
- `reputation` - penalties, gains, thresholds, gossip, trade pricing
- `combat` - profession combat toggles, armorer shields, clerics, farmers
- `debugOverlay` - optional reputation display for testing
- `wanderer` - wandering trader drop behavior

## Debug Overlay

There is an optional client-side debug overlay for testing reputation.

When enabled, it can show the villager's reputation tier and/or exact reputation value above their head. It can be limited by distance, sneaking, or advanced tooltips.

This is disabled by default and is mainly intended for testing and balancing.

![compatibility](https://cdn.modrinth.com/data/cached_images/1252c11050b7daf8b8621712b58dd1005e7ba982.png)

## Compatibility

Villager Retaliation! is designed to work with vanilla villager systems rather than replacing villager entities.

- Built for NeoForge 1.21.1
- Uses vanilla-style entity events and AI memory adjustments
- Uses vanilla gossip integration where enabled
- Uses NeoForge item tags for weapon detection where possible
- Does not replace the villager entity type

Compatibility may vary with mods that heavily replace villager AI, trading, combat, or entity classes.

### Resource Pack Textures

Villagers and wandering traders use vanilla texture paths while idle, trading, or otherwise using the vanilla crossed-arms model. When they enter a combat posture, they use Villager Retaliation's combat model and a matching combat texture path.

Resource packs that change villagers should include both the vanilla texture and the combat-state texture:

| Entity | Normal texture | Combat texture |
| --- | --- | --- |
| Villager | `assets/minecraft/textures/entity/villager/villager.png` | `assets/villagerretaliation/textures/entity/villager/villager.png` |
| Wandering Trader | `assets/minecraft/textures/entity/wandering_trader.png` | `assets/villagerretaliation/textures/entity/wandering_trader/wandering_trader.png` |

The combat texture is separate because the combat model has independent animated arms for weapons, shields, bows, potions, and throwing poses. A vanilla villager texture alone does not contain enough arm layout information to support every combat pose cleanly.

### Resource Pack Combat Model

Resource packs can also override the combat model geometry at:

```text
assets/villagerretaliation/models/entity/villager/combat_villager.json
```

The model uses a small JSON format with `texture_width`, `texture_height`, and recursive `parts`. Each part can define `name`, `pivot`, `rotation` in degrees, `cubes`, and `children`. Cubes use `uv`, `origin`, `size`, optional `inflate`, and optional `mirror`.

The animation system requires these part names to remain present: `body`, `head`, `RightArm`, `LeftArm`, `RightLeg`, and `LeftLeg`. Decorative children such as `nose`, `helmet`, and `brim` may be changed or removed, so packs can do things like remove the villager nose or shorten the head without code changes.

By default, idle villagers still use Minecraft's vanilla crossed-arms model. Packs that want their normal, non-combat villagers to use a custom arms-at-side model can opt in with:

```text
assets/villagerretaliation/models/entity/villager/render_options.json
```

```json
{
  "non_combat_model": "custom"
}
```

Then provide the non-combat model at:

```text
assets/villagerretaliation/models/entity/villager/non_combat_villager.json
```

Use `"non_combat_model": "vanilla"` or omit `render_options.json` to keep the vanilla crossed-arms model.

If Entity Model Features (EMF) is installed, Villager Retaliation exposes its combat model through Minecraft's normal entity model layer instead. This lets EMF packs use the standard OptiFine CEM workflow and export a starter `.jem` from EMF's in-game model export tools. Entity Texture Features (ETF) is still optional and useful for random or emissive entity texture packs, but it is not required for the combat model override above.

## Version and Loaders

- NeoForge 1.21.1 - active development
- Forge - not planned anytime soon, don't ask please :)
- Fabric - possible future port, not anytime soon
- Older Minecraft versions - not planned
- Eventually, will skip straight to 26.1/whatever the newest standard will be for modding.

![credits & license](https://cdn.modrinth.com/data/cached_images/5fd3ad80e342e6985dd6ebda1f7afd9c48749fce.png)

## Modpacks

You may use this mod in modpacks, videos, servers, and other projects. A link back to the Modrinth page is appreciated.

## Credits

Created by me :D

## License

This project is licensed under the **[GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html)**.

Feel free to use this mod in modpacks, videos, etc. Just provide a link back to this page if possible :)

Looking to port the mod to your favourite loader/version outside of my scope? Feel free to, and let me know so I can add a sub-section to direct users to it!

For any general queries/unlisted questions, DM me on Twitter (@prodbyjvn) / Discord (ijvn).

<div align="center">

  <p><strong>⚠ <em>This mod ONLY exists on Modrinth & CurseForge as of May 2026. Any sites hosting this mod outside of Modrinth/CurseForge are not official releases.</em> ⚠</strong></p>

</div>
