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

Adult villagers can defend themselves, react to crimes they witness, use profession-based combat roles, remember how each player treats them through a personal reputation system, and feel more like actual NPCs through dialogue and interaction.

- Attack a villager and that villager can fight back
- Kill a villager in public and nearby villagers may rally
- Player-placed lava and fire can count as aggression for a short attribution window
- Build trust through trade, village defense, and positive interactions
- Lose trust through attacks, witnessed crimes, and village harm
- Make villagers suspicious, hostile, despised, or feared if you push things too far
- Talk to villagers through a reputation-aware interaction screen
- Get different dialogue depending on reputation, profession, recent events, and local village activity
- Refresh individual villager trade slots when their skills unlock better stock
- Let villagers confront players for opening or stealing from generated village chests
- Give gifts, discover preferences, and see trusted villagers keep favorite items as keepsakes
- Learn village stories, follow villager map hints, and build family or relationship history
- Keep babies defenseless and nitwits cowardly as intended

![features](https://cdn.modrinth.com/data/cached_images/ec0e4dc78ec1a652eb11b233dd2926f7461fe770.png)

![villagers carrying profession based weapons](https://cdn.modrinth.com/data/cached_images/44e64c624e3bad762345ee5f8df5d91c5ef5f6cd.png)

## Main Features

### Retaliation

Villagers are neutral instead of purely passive.

By default, hitting a villager only angers that villager. Killing an adult villager can anger nearby adult villagers if they can witness it. Anger expires after a configurable duration, and creative/spectator players can be ignored.

Villagers and wandering traders can also block trading while hostile. Hostile villagers can be pacified with datapack-defined item payments, unless your reputation has fallen too low.

### Reputation

Reputation is tracked per villager and per player.

That means one villager can trust you while another despises you. Reputation can affect trade prices, pacification, anger duration, fleeing, despised attack-on-sight behavior, cleric support, dialogue responses, interaction availability, and advancement progression.

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

Reputation changes are configurable. By default, direct villager hits are punished more than witnessed hits, villager kills are punished heavily, breaking watched containers causes a large witnessed penalty with extra loss per generated item dropped, watched container opening prompts scale by reputation, and player-attributed lava/fire damage counts for half the normal reputation penalty.

![villagers retaliating against a player that killed another villager](https://i.imgur.com/6c3vdac.gif)

### Dialogue and Interaction

Villagers can feel more like actual NPCs instead of silent trade menus.

The interaction screen gives players a dedicated way to talk, give gifts, recruit, check relationships, and read reputation context. Dialogue can react to personal reputation, profession, recent village events, helpful actions, aggression, local danger, family ties, relationships, and whether this is the first time that villager has met the player.

Trusted villagers may greet you warmly, suspicious villagers may be cold, and hostile villagers may insult or refuse you. The goal is to make each villager feel more personal without breaking the vanilla feel.

Villagers can also share stories about discovered structures and biomes, give unreliable or reputation-aware gift advice, remember family and romantic relationships, and be recruited to follow or help the player when conditions allow.

Villagers can confront players for opening, breaking, or stealing from watched containers, including generated village chests. Breaking generated containers unpacks and counts the dropped loot before applying reputation loss, so smashing fuller village stores is worse than cracking an empty box. Data packs can customize the event dialogue, target specific loot tables, require item payments, and show different responses based on the player's current reputation with the witnessing villager.

Villagers with skill-generated trade pools can also refresh specific trade slots. A random refresh request is stored until the next Minecraft day, then replaces that slot with an eligible skill-trade offer if the villager knows one and does not already offer the same result item. High-reputation Special Orders let players target unlocked skill-trade definitions directly, including earlier-rank catalog items and items the villager already stocks. The refresh dialogue and option replies are data-driven, so packs can customize the tone through normal dialogue and forced-dialogue JSON.

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
| Librarian | Can fight with book-themed melee |
| Nitwit | Usually flees unless holding a usable weapon |

Fletchers and other villagers holding ranged weapons can use bows, crossbows, and tridents. Crossbows use charge/hold/fire states, and tridents use a thrown attack.

When enabled, villagers and wandering traders can also target, retaliate against, or stand ground against hostile mobs, making villages feel more self-protective without replacing vanilla villagers.

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
- Despised or feared villagers may refuse pacification payments
- Babies, nitwits, and non-combat villagers can flee from hated players
- Low-reputation villagers may block trade or refuse normal interaction

## Loot

Villager Retaliation! adds configurable drops for villagers and wandering traders.

- Villagers can drop emeralds and bread
- Adult villagers can roll profession-themed loot
- Combat weapons can drop at configurable rates
- Equipped gear can be given through the villager inventory interaction
- Baby villager loot is disabled by default
- Wandering traders can drop emeralds, invisibility potions, and safe copies of current trade results

Profession loot generally requires player kill credit by default, and profession-specific drops are backed by datapack loot-table rules.

## Advancements

The mod includes a full reputation advancement tab.

<details>
<summary><strong>All Advancements (Click to Expand)</strong></summary>

<br>

| Advancement | Type | Criteria | Hidden |
| --- | --- | --- | --- |
| Village Relations | Task (Tab Root) | Automatically granted when any Villager Retaliation advancement is awarded. | No |
| Commonfolk | Task | Interact with any villager, or enter a village. | No |
| I'm Sorry! | Task | Pacify a hostile villager with a payment item. | No |
| A Familiar Face | Task | Reach Trusted reputation with any villager. | No |
| Respect Is Earned | Task | Reach Respected reputation with any villager. | No |
| Friend of the Village | Goal | Reach Trusted with 5 villagers in one village area. | No |
| Local Legend | Challenge | Reach Revered reputation with any villager. | No |
| Crowned by the Village | Challenge | Reach Royalty reputation with any villager. | No |
| Cover Them in Debris | Challenge | Equip a villager with a full set of netherite armor. | Yes |
| Second Chance | Goal | Cure a zombie villager that retains known reputation data for you. | No |
| The Village Remembers | Goal | Move from Suspicious or lower back to Neutral or higher. | No |
| Bad First Impression | Task | Reach Suspicious reputation with any villager. | No |
| Hands Off | Task | Damage a villager. | No |
| No Rest For The Wicked | Challenge | Break the bed of a sleeping villager. | Yes |
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
| Bait and Betrayal | Challenge | Have a villager follow you, then kill them. | Yes |
| Changed My Mind | Task | Take back a gift from a villager. | No |
| Trusted Directions | Goal | Follow a map given through villager dialogue. | Yes |
| Once Upon a Time | Task | Share a discovered story with a villager. | No |
| Story Keeper | Goal | Share 5 discovered stories with villagers. | No |
| Village Chronicler | Goal | Share 10 discovered stories with villagers. | No |
| Legend Trader | Challenge | Share 25 discovered stories with villagers. | No |

</details>

![keybinds](https://cdn.modrinth.com/data/cached_images/201d5ce49ba16974e3c3b0b562c392e03f38e35f.png)

## Commands

Requires operator.

```mcfunction
/villagerretaliation setNearbyReputation <integer>
/villagerretaliation setNearestRelationship <crush|dating|engaged|married|separated>
```

This sets nearby villagers' and wandering traders' reputation toward the executing player. It is mainly useful for testing tiers, trade pricing, despised behavior, feared behavior, pacification, and the debug overlay.

`setNearestRelationship` is a relationship-system debug command. It applies the chosen romantic stage to the two nearest adult villagers, respecting the social graph's adult, alive, close-family, and exclusive-partner validation rules.

Example:

```mcfunction
/villagerretaliation setNearbyReputation -150
```

Debug-only family testing items are available through commands:

```mcfunction
/give @s villagerretaliation:villager_breeding_stick
/give @s villagerretaliation:villager_maturity_emerald
```

Right-click two adult villagers with the breeding stick to create a biological baby immediately, bypassing vanilla breeding cooldown. Biological births require opposite-gender parents and still respect close-family checks. Sneak-right-click the second adult instead to select any valid adult pair for adoption, then right-click an orphan baby villager. Same-gender adult pairs automatically enter adoption mode instead of creating a biological baby. Right-click a baby villager with the maturity emerald to make them an adult immediately.

## Configuration

Main config file:

- Singleplayer/client: `config/villagerretaliation-common.toml`
- Dedicated server: `<server root>/config/villagerretaliation-common.toml`

Config categories include:

- `general` - master feature toggles
- `balance` - loot and drop rates
- `retaliation` - anger rules, aggro radius, duration, line-of-sight witnesses
- `reputation` - penalties, gains, thresholds, gossip, trade pricing
- `combat` - profession combat toggles, hostile mob targeting/retaliation, weapon pickup, armorer shields, clerics, farmers
- `debugOverlay` - optional reputation display for testing
- `wanderer` - wandering trader drop behavior

Pack creators can tune monster-defense behavior independently with `combat.villagersTargetHostileMobs`, `combat.villagersRetaliateAgainstHostileMobs`, `combat.villagersStandGroundAgainstHostileMobs`, `combat.villagersPickUpGroundWeapons`, and the equivalent wandering trader options. `combat.naturalHostileTargetRadius` still controls the scan distance when proactive hostile mob targeting is enabled.

Baby villagers flee witnessed villager deaths by default through `retaliation.babyVillagersFleeWitnessedDeaths`. Disable it to keep the original nitwit-only alarm behavior for witnessed deaths.

## Debug Overlay

There is an optional client-side debug overlay for testing reputation.

When enabled, it can show the villager's reputation tier and/or exact reputation value above their head. Optional health and armor lines can be shown under the reputation line. It can be limited by distance, sneaking, or advanced tooltips.

This is disabled by default and is mainly intended for testing and balancing.

## Pack Support

Villager Retaliation! has built-in datapack and resource-pack support for creators who want to tune the experience without writing Java.

Datapacks can add or replace villager dialogue, forced dialogue events, chat event lines, notification text, ambient world text, gift preferences, pacification payments, profession loot, story discoveries, and preset villager names. Forced dialogue can cover watched-container events, retaliation-started barks, and nearby player item reactions through `player_item_proximity`. These systems are data-driven so addon packs and modpacks can make villages feel warmer, harsher, funnier, stranger, or more tied to their own worldbuilding.

Resource packs can translate the interaction GUI and reputation UI, replace normal and combat villager textures, customize wandering trader textures, and override the combat-capable villager model used when villagers need independent arms for weapons, shields, bows, potions, and throwing animations.

There is also a local browser-based datapack generator included in the GitHub repo:

```text
tools/datapack-builder/index.html
```

Open it in a browser to create, import, preview, validate, and export Villager Retaliation datapacks. It can generate the current datapack paths for dialogue, forced dialogue, notifications, gifts, pacification, story discovery, preset names, and `pack.mcmeta`. Dialogue, forced-opening, and notification text fields accept one variation per line where the runtime supports `lines`.

The generator and runtime both use strict system folders: dialogue, forced dialogue, and notifications are loaded from their documented roots, and recent versions log warnings for common misplaced sections or ignored fields.

For full pack-author documentation, examples, JSON references, and model notes, use the [GitHub pack docs](https://github.com/JevenDev/Villager-Retaliation/tree/1.21.1/wiki) rather than this Modrinth page.

## Documentation

- [Changelog](CHANGELOG.md)
- [Wiki Home](wiki/Home.md)
- [Pack Format Changes](wiki/Pack-Format-Changes.md)
- [Datapack Generator](wiki/Datapack-Generator.md)
- [JSON Reference](wiki/JSON-Reference.md)
- [Forced Dialogue JSON](wiki/Forced-Dialogue.md)
- [Dialogue JSON](wiki/Dialogue.md)
- [Event Tags](wiki/Event-Tags.md)
- [Notifications JSON](wiki/Notifications.md)

![compatibility](https://cdn.modrinth.com/data/cached_images/1252c11050b7daf8b8621712b58dd1005e7ba982.png)

## Compatibility

Villager Retaliation! is designed to work with vanilla villager systems rather than replacing villager entities.

- Built for NeoForge 1.21.1
- Uses vanilla-style entity events and AI memory adjustments
- Uses vanilla gossip integration where enabled
- Uses NeoForge item tags for weapon detection where possible
- Does not replace the villager entity type

Compatibility may vary with mods that heavily replace villager AI, trading, combat, or entity classes.

### Resource Pack Notes

Texture-only villager packs should generally work, but hostile/combat villagers use separate Villager Retaliation texture paths so they can animate weapons, shields, bows, potions, and throwing poses cleanly.

Resource packs can also override the combat villager model, opt into a custom non-combat model, and use EMF's normal entity model workflow when Entity Model Features is installed. Full texture paths and model notes are in the [GitHub pack docs](https://github.com/JevenDev/Villager-Retaliation/tree/1.21.1/wiki).

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

All Rights Reserved.

Feel free to use this mod in modpacks, videos, etc. Just provide a link back to this page if possible :)

Please don't port this mod without express permission from me.

For any general queries/unlisted questions, DM me on Twitter (@prodbyjvn) / Discord (ijvn).

<div align="center">

  <p><strong>⚠ <em>This mod ONLY exists on Modrinth & CurseForge as of May 2026. Any sites hosting this mod outside of Modrinth/CurseForge are not official releases.</em> ⚠</strong></p>

</div>
