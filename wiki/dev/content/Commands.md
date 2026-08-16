# Commands

All Villager Retaliation commands use the `/vr` root. Run `/vr`, `/vr party`, `/vr duel`, `/vr admin`, or `/vr admin debug` in game for a short localized summary, and use Tab completion for IDs, selectors, enum values, players, and other suggested arguments.

Notation on this page:

- `<value>` is required.
- `[value]` is optional.
- Entity arguments accept normal Minecraft selectors where the command permits them.
- `/vr admin` requires permission level 2. Player commands do not require operator permission, but they must be run by a player where the action needs one.
- Command names are case-sensitive. In particular, keep camel-case literals such as `forceStart`, `whyAvailable`, `inspectHere`, and `cleanupEncounter` exactly as shown.

## Player Commands

### Parties

| Command | Purpose |
| --- | --- |
| `/vr party create` | Create a player party. |
| `/vr party invite <player>` | Invite an online player. |
| `/vr party accept [player]` | Accept the latest invitation, or the invitation from the named player. |
| `/vr party decline [player]` | Decline the latest invitation, or the invitation from the named player. |
| `/vr party leave` | Leave the current party. |
| `/vr party kick <player>` | Remove a party member; leader permission is checked by the party service. |
| `/vr party promote <player>` | Transfer party leadership to a member. |
| `/vr party disband` | Disband the party. |
| `/vr party alliance request <player>` | Request an alliance with the named player's party. |
| `/vr party alliance accept <player>` | Accept that party's alliance request. |
| `/vr party alliance cancel <player>` | Cancel the outgoing request to that party. |
| `/vr party alliance end <player>` | End the alliance with that party. |

### Player Duels

| Command | Purpose |
| --- | --- |
| `/vr duel challenge <player> [kit] [wager]` | Challenge a player. Omitted kit defaults to bring-your-own and omitted wager defaults to 0. Kit IDs are suggested; wagers must be non-negative. |
| `/vr duel accept [player]` | Accept the latest challenge, or the challenge from an online player. |
| `/vr duel decline [player]` | Decline the latest challenge, or the challenge from an online player. |

## Operator Commands

### Villager Profiles, Skills, And Social State

| Command | Purpose |
| --- | --- |
| `/vr admin villager profile get <villager>` | Show the villager's social profile. |
| `/vr admin villager profile set <villager> <attribute> <value>` | Set a suggested Social Attribute to its bounded value. |
| `/vr admin villager profile reroll <villager>` | Reroll the profile. |
| `/vr admin villager profile export <villager>` | Export profile data for debugging. |
| `/vr admin villager skill get <villager> [skill]` | Show every skill, or one suggested skill. |
| `/vr admin villager skill set <villager> <skill> <value>` | Set a skill to its bounded value. |
| `/vr admin villager skill reroll <villager>` | Reroll skills. |
| `/vr admin villager gender set <villager> <gender>` | Set a suggested stored gender value. |
| `/vr admin villager reputation set <targets> <player> <value>` | Set the named player's reputation on one or more target entities. |
| `/vr admin villager relationship set <first> <second> <stage>` | Set a suggested relationship stage between two entities. |

The `<villager>` argument supports a UUID, a normal entity selector, or an exact custom/preset name. A plain name must resolve to exactly one loaded villager.

### Villager Allegiance

| Command | Purpose |
| --- | --- |
| `/vr admin villager allegiance inspect <entity>` | Show saved allegiance and assignment history. |
| `/vr admin villager allegiance explain <entity>` | Explain the assignment rule and current state. |
| `/vr admin villager allegiance assign <entity> <uuid>` | Assign a village identity directly. |
| `/vr admin villager allegiance unknown <entity>` | Mark allegiance unresolved so normal resolution can retry. |
| `/vr admin villager allegiance unaffiliated <entity>` | Mark the entity as a Wanderer/unaffiliated. |
| `/vr admin villager allegiance merge <source> <target>` | Merge allegiance identities. |
| `/vr admin villager allegiance undoMerge <source>` | Undo the latest eligible merge for the source identity. |
| `/vr admin villager allegiance fork <entity>` | Fork an entity into a new allegiance identity. |
| `/vr admin villager allegiance repair <entity>` | Retry migration or repair a stuck assignment. |
| `/vr admin villager allegiance statistics` | Show assignment statistics. |
| `/vr admin villager allegiance resetAbuse <entity> <player>` | Reset saved abuse state for that player and entity. |

### Tracked Villages

| Command | Purpose |
| --- | --- |
| `/vr admin village inspectHere` | Inspect the tracked village at the operator's position. |
| `/vr admin village renameHere <name>` | Rename the local tracked village; the rest of the line is used as the name. |
| `/vr admin village list` | List tracked villages. |
| `/vr admin village registry inspect [limit]` | Inspect up to 10 registry entries by default, or 1-50 when supplied. |
| `/vr admin village registry pruneOlderThan <ticks>` | Prune eligible registry data older than the non-negative tick age. |
| `/vr admin village registry suggestMerges [radius] [limit]` | Suggest registry-key merges using bounded radius and 1-50 result limits. |
| `/vr admin village registry merge <source_key> <target_key>` | Merge two suggested registry keys. Quote keys when needed. |

### Datapack And Dialogue Diagnostics

| Command | Purpose |
| --- | --- |
| `/vr admin datapack diagnostics` | Show current datapack diagnostics. |
| `/vr admin datapack diagnostics severity <severity>` | Filter by suggested diagnostic severity. |
| `/vr admin datapack diagnostics severity <severity> resource <resource>` | Filter by severity and resource string. |
| `/vr admin datapack diagnostics resource <resource>` | Filter by resource string. |
| `/vr admin dialogue explain <villager> <request> [option]` | Explain dialogue candidate matching for a suggested request and optional option ID. |

### Quests

| Command | Purpose |
| --- | --- |
| `/vr admin quest providers [radius]` | List matching loaded providers in the default or supplied bounded radius. |
| `/vr admin quest start <quest_id> <provider>` | Start a suggested quest when ordinary start rules pass. |
| `/vr admin quest forceStart <quest_id> <provider>` | Force the debug start path while retaining structural/provider validation. |
| `/vr admin quest remove <quest_id>` | Remove the player's quest state. |
| `/vr admin quest inspect <quest_id>` | Inspect saved state, provider binding, stages, objectives, facts, and deferred work. |
| `/vr admin quest rebind <quest_id> <provider>` | Rebind an eligible missing provider after filter validation. |
| `/vr admin quest whyAvailable <quest_id> <provider>` | Explain why a provider can or cannot offer the quest. |
| `/vr admin quest whyHidden <quest_id> [provider]` | Explain why the quest is hidden, optionally against a specific provider. |
| `/vr admin quest objectives <quest_id>` | Show objective state. |
| `/vr admin quest setStage <quest_id> <stage>` | Move debug state to the named stable stage. |
| `/vr admin quest fireTrigger <quest_id> <event>` | Fire a suggested registered quest event for testing. |
| `/vr admin quest actions dryRun <quest_id> <trigger_id>` | Evaluate a trigger's actions without committing them. |
| `/vr admin quest facts <scope_key>` | Show facts for the supplied scope key; the rest of the line is accepted. |

Quest trace commands are:

```text
/vr admin quest trace on
/vr admin quest trace off
/vr admin quest trace show [limit]
/vr admin quest trace clear
/vr admin quest trace capture <quest_id> <provider>
```

`show` defaults to the trace buffer capacity and accepts a bounded positive limit. `capture` records the selected quest/provider evaluation.

### Persistent Scenes And Encounters

| Command | Purpose |
| --- | --- |
| `/vr admin scene list` | List persistent scene runs. |
| `/vr admin scene inspect <scene_id>` | Show the scene's current saved state. |
| `/vr admin scene trace <scene_id>` | Show detailed trace lines. |
| `/vr admin scene retry <scene_id>` | Retry a blocked or failed operation when the runtime permits it. |
| `/vr admin scene cancel <scene_id>` | Cancel the scene. |
| `/vr admin scene resume <scene_id>` | Resume an eligible paused or blocked scene. |
| `/vr admin scene rebind <scene_id> <alias> <target>` | Rebind an actor alias to one entity. |
| `/vr admin scene cleanupEncounter <encounter_id>` | Run encounter cleanup by durable encounter ID. |

Scene and encounter IDs shown by these commands are runtime IDs, not necessarily datapack template resource IDs.

### Debug And Test Tools

These commands mutate test state or can create many entities. Use them in disposable worlds or backups.

| Command | Purpose |
| --- | --- |
| `/vr admin debug duel <villager> [kit] [wager]` | Start a villager duel test. Kits include `byo`, `bring_your_own`, `bare_handed`, `melee`, `ranged`, and `armored`; wager is non-negative. |
| `/vr admin debug hired previews <enabled> [radius]` | Toggle hired-work debug previews with an optional bounded radius. |
| `/vr admin debug hired stressGrid [count]` | Spawn the default role-count stress grid or a bounded count. |
| `/vr admin debug hired inspect <villager>` | Inspect hired-work runtime state. |
| `/vr admin debug raid win` | Force the active debug Player Raid to its win path. |
| `/vr admin debug raid lose` | Force its loss path. |
| `/vr admin debug builder materials <structure>` | Place material chests for a suggested registered builder structure. |
| `/vr admin debug transferVillagerOwnership <villager> <player>` | Transfer supported villager ownership/controller state to exactly one player profile. |

## Troubleshooting

- If a command is missing, confirm the server and client use the same mod version and use `/vr` rather than an older root.
- If `/vr admin` is hidden or rejected, grant permission level 2 or run it from the server console where the command does not require a player position.
- If a named villager is ambiguous, use a UUID or a narrow selector such as `@e[type=minecraft:villager,sort=nearest,limit=1]`.
- Use `/vr admin datapack diagnostics` after `/reload`, then the feature-specific `explain`, `whyAvailable`, `whyHidden`, `inspect`, or `trace` command.
- Mutating quest and scene commands are debugging and repair tools. Back up saves before changing live quest stages, bindings, facts, ownership, or village identities.
