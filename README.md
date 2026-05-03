<div align="center">

<h2><strong>Villagers remember. Villagers fight back.</strong></h2>

</div>

<div align="center">

<a href="https://modrinth.com/mod/Villager-Retaliation/settings/versions?l=neoforge"><img src="https://raw.githubusercontent.com/intergrav/devins-badges/refs/heads/v3/assets/cozy/supported/neoforge_64h.png" alt="Available for NeoForge"></a><br>
<a href="https://modrinth.com/mod/Villager-Retaliation" target="_blank" rel="noopener noreferrer"><img src="https://raw.githubusercontent.com/intergrav/devins-badges/refs/heads/v3/assets/compact-minimal/available/modrinth_46h.png" alt="Available on Modrinth"></a>
<a href="https://github.com/JevenDev/Villager-Retaliation" target="_blank" rel="noopener noreferrer"><img src="https://raw.githubusercontent.com/intergrav/devins-badges/refs/heads/v3/assets/compact-minimal/available/github_46h.png" alt="Available on GitHub"></a>

</div>

**Villager Retaliation** is a Vanilla+ NeoForge mod for Minecraft 1.21.1 that makes villagers and wandering traders less helpless.

Adult villagers can now defend themselves, rally nearby allies, use profession-based combat roles, remember how players treat them, and react differently depending on their reputation toward you.

The goal is not to turn villages into permanent warzones. Retaliation is temporary, reputation is personal, and village behaviour still tries to feel like Minecraft.

- **Attack a villager** and that villager can fight back
- **Kill a villager in public** and nearby villagers may rally against you
- **Build trust** through trading and positive actions
- **Lose trust** through violence, witnessed crimes, and village harm
- **Make villagers despise or fear you** if you push things too far
- **Keep baby villagers harmless**
- **Keep nitwits cowardly by default**, unless they find a usable weapon

![features](https://cdn.modrinth.com/data/cached_images/ec0e4dc78ec1a652eb11b233dd2926f7461fe770.png)

## Villager retaliation

Villagers are no longer completely passive when threatened.

- Adult villagers can retaliate when damaged
- Hitting a villager only angers that villager by default
- Killing an adult villager can anger nearby witnesses
- Witnesses can require **line of sight**, so enclosed attacks do not alert villagers through walls
- Anger expires after a configurable duration
- Creative and spectator players can be ignored
- Trading is blocked while a villager or wandering trader is hostile toward the player
- Hostile villagers and wandering traders can be pacified with emeralds
- Baby villagers never fight back
- Unarmed nitwits keep their fleeing behaviour by default

## Reputation system

Villagers can now remember how each player treats them.

Reputation is tracked **per villager** and **per player**, meaning one villager can trust you while another despises you. Reputation can affect trade prices, pacification, fleeing, aggression, and special behaviours.

### Reputation tiers

Positive reputation:

- **Royalty**
- **Revered**
- **Respected**
- **Trusted**
- **Neutral**

Negative reputation:

- **Suspicious**
- **Hostile**
- **Despised**
- **Feared**

At higher reputation, villagers may treat you more favourably. At lower reputation, villagers may refuse peace, attack on sight, throw harmless items at you, or even visibly shake when they fear you.

## Profession combat roles

Villagers fight in ways that match their profession.

| Profession | Behaviour |
| --- | --- |
| **Weaponsmith** | Uses stronger melee behaviour, usually with a sword |
| **Armorer** | Can fight defensively, gain resistance, and use shields in hard mode |
| **Toolsmith** | Uses tool-based melee behaviour |
| **Mason** | Uses mining-tool style melee behaviour |
| **Butcher** | Uses axe-based melee behaviour |
| **Fletcher** | Uses bows or crossbows |
| **Farmer** | Can defend themselves and heal with bread |
| **Cleric** | Uses potions for defense, attacks, and ally healing |
| **Librarian** | Can fight with book-themed behaviour |
| **Nitwit** | Usually flees unless holding a usable weapon |
| **Unemployed** | Uses basic defensive behaviour when applicable |

## Ranged combat

Fletchers and other villagers holding ranged weapons can use ranged combat.

Supported weapons include:

- **Bows**
- **Crossbows**
- **Tridents**

Crossbows use proper charge, hold, and fire states. Tridents use a thrown attack inspired by drowned behaviour.

## Armorer shields

Armorers can become defensive frontliners.

- Armorers may spawn with an offhand shield in hard mode
- Shield chance is configurable
- Shields can block incoming damage
- Axe hits can disable blocking temporarily
- Armorers use shield blocking and lowered-shield poses

## Cleric support

Clerics have the most advanced support kit.

They can:

- drink defensive potions while threatened
- throw harmful or slowing splash potions at attackers
- avoid bad potion choices against undead or inverted-healing targets
- avoid splashing friendly civilians when attacking
- heal injured villagers and wandering traders
- passively look for injured allies while idle
- support trusted-or-better players depending on reputation

Passive cleric healing has configurable range, health threshold, and line-of-sight rules.

## Hostile reputation behaviour

Villagers who dislike you enough can do more than simply raise prices.

Depending on config and reputation:

- **Hostile** villagers randomly throw eggs or poisonous potatoes at you
- **Despised** villagers attack on sight
- **Feared** villagers visibly shake around you
- Villagers who despise you refuse pacification with emeralds
- Reputation influences retaliation and fleeing behaviour

![keybinds](https://cdn.modrinth.com/data/cached_images/201d5ce49ba16974e3c3b0b562c392e03f38e35f.png)

## Commands

### Set nearby reputation

Requires operator permissions.

```mcfunction
/villagerretaliation setNearbyReputation <value>
```

This sets the reputation value for nearby villagers toward the executing player.

Useful for testing:

- reputation tiers
- trade pricing
- despised behaviour
- feared behaviour
- debug overlay display
- pacification rules

Example:

```mcfunction
/villagerretaliation setNearbyReputation -150
```

## Debug overlay

Villager Retaliation includes an optional client debug overlay for testing reputation.

When enabled, it can show:

- villager reputation tier
- exact reputation number
- reputation above villager heads
- configurable max display distance
- optional sneaking requirement
- optional advanced tooltip requirement

This is mainly intended for development, testing, and balancing.

![compatibility](https://cdn.modrinth.com/data/cached_images/1252c11050b7daf8b8621712b58dd1005e7ba982.png)

## Compatibility

Villager Retaliation is designed to work with vanilla villager systems instead of replacing them.

- Built for **NeoForge 1.21.1**
- Uses vanilla-style entity events and AI memory adjustments
- Uses gossip integration where enabled
- Uses NeoForge item tags for weapon detection where possible
- Does not replace villager entities
- Should work best with mods that respect vanilla villager behaviour and item tags

<div align="center">
  <p><strong><em>Note: If another mod heavily replaces villager AI, trading, or villager entity classes, compatibility may vary.</em></strong></p>
</div>

![roadmap](https://cdn.modrinth.com/data/cached_images/04825ea0e2e5462ffa075e783ca38b0c63a36d34.png)

## Version and loaders

- ✅ **NeoForge 1.21.1** [Active development]
- ⛔ **NeoForge 1.20.1** [Not planned]
- ⛔ **Forge 1.21.1** [Not planned]
- ⛔ **Forge 1.20.1** [Not planned]
- 🚧 **Fabric 1.21.1** [Possible future port]
- ⛔ **Fabric 1.20.1** [Not planned]

## Planned / possible features

- More reputation-driven villager reactions
- More profession-specific combat polish
- More config options for pack makers
- Expanded compatibility testing with villager-related mods
- Additional debug tools for reputation and retaliation
- More passive village defense behaviours

![credits & license](https://cdn.modrinth.com/data/cached_images/5fd3ad80e342e6985dd6ebda1f7afd9c48749fce.png)

## Credits

Created by **jvn** (me!).

## License

This project is licensed under the **[GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html)**.

Feel free to use this mod in modpacks, videos, etc. Just provide a link back to this page if possible :)

For general questions, bug reports, or feature requests, use the **[GitHub issue tracker](https://github.com/JevenDev/Villager-Retaliation/issues)** or contact me on Discord.

<div align="center">

  <p><strong>⚠ <em>This mod ONLY exists on Modrinth and GitHub unless stated otherwise. Any other sites hosting this mod are not official releases.</em> ⚠</strong></p>

</div>
