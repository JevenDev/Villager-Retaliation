window.VR_DEVELOPER_WIKI_DATA = {
  "version": "1.0.0-beta.13-pre-release.5",
  "generatedAt": "repository build",
  "pages": [
    {
      "slug": "pack-development",
      "file": "Pack-Development.md",
      "source": "wiki/Pack-Development.md",
      "sourceKind": "wiki",
      "group": "Getting Started",
      "icon": "package-open",
      "title": "Pack Development",
      "description": "Folder layout, namespaces, overrides, reloads, and a safe pack-authoring workflow.",
      "markdown": "# Pack Development\n\nThis page is the setup guide for writing Villager Retaliation addons. Use it before touching any system-specific JSON.\n\n## Pack Types\n\nUse a datapack for behavior and authored text:\n\n```text\n<datapack root>/\n  pack.mcmeta\n  data/\n    villagerretaliation/\n      gifts/\n      currency/\n      item_text/en_us/\n      notifications/\n      natural_job_armor/\n      pacification/\n      profession_loot/\n      villager_names/\n      village_names/\n    my_pack/\n      builder_structures/\n      dialogue/en_us/\n      generated_containers/\n      duel_kits/\n      dialogue_trees/en_us/\n      forced_dialogue/\n      quest_encounters/\n      quest_scenes/\n      quests/\n      sell_prices/\n      skill_trades/\n      story_structures/\n      story_biomes/\n      villager_events/\n      loot_table/\n```\n\nEvery datapack also needs a `pack.mcmeta` at its root. For Minecraft 1.21.1 datapacks:\n\n```json\n{\n  \"pack\": {\n    \"pack_format\": 48,\n    \"description\": \"My Villager Retaliation addon\"\n  }\n}\n```\n\nUse a resource pack for GUI text, textures, and model JSON:\n\n```text\n<resource-pack root>/\n  pack.mcmeta\n  assets/\n    villagerretaliation/\n      lang/en_us.json\n      models/entity/villager/combat_villager.json\n      textures/entity/villager/villager.png\n```\n\n## Namespace Rules\n\nThese systems are intentionally fixed to the mod namespace:\n\n| System | Namespace |\n| --- | --- |\n| Notifications | `villagerretaliation` |\n| Gifts | `villagerretaliation` |\n| Currency | `villagerretaliation` |\n| Natural job armor | `villagerretaliation` |\n| Pacification | `villagerretaliation` |\n| Profession loot rules | `villagerretaliation` |\n| Villager names | `villagerretaliation` |\n| Village names | `villagerretaliation` |\n\nThese systems can live in your own namespace:\n\n- Dialogue\n- Dialogue trees\n- Quests\n- Forced dialogue\n- Skill trades\n- Builder structures\n- Story structures\n- Story biomes\n- Villager event triggers\n- Duel kits\n- Sell prices\n- Persistent quest scenes and encounter templates\n- Generated-container lists\n- Referenced loot tables\n\nExample:\n\n```text\ndata/my_pack/dialogue/en_us/global/lines/rumors.json\ndata/my_pack/quests/lost_civilization/echo_shard.json\ndata/my_pack/skill_trades/cartographer.json\ndata/my_pack/builder_structures/custom_houses.json\ndata/my_pack/loot_table/villager/profession/alchemist/common.json\n```\n\n## Override Rules\n\nMinecraft resolves exact resource paths first. Villager Retaliation then merges the files it finds for that loader.\n\n- A file at the same resource path as a built-in file replaces that built-in file before VR reads it.\n- Inside many systems, a later entry with the same `id` replaces an earlier entry without replacing the whole file.\n- For quests, dialogue trees, and forced dialogue, top-level `replace: true` puts that loader in replacement mode: VR skips built-in resources for that system, then applies add-on resources.\n- For normal dialogue, top-level `replace: true` clears the current dialogue pool, and `replace_sections` can clear only selected sections.\n- Top-level `remove: true` removes one quest, dialogue tree, or forced-dialogue definition by `id`.\n\n| System | Additive by default | Clear everything | Remove one entry |\n| --- | --- | --- | --- |\n| Dialogue | Yes | `replace: true` or `replace_sections` | Replace by same entry `id` |\n| Dialogue trees | Yes | `replace: true` | `remove: true` with `id` |\n| Quests | Yes | `replace: true` | `remove: true` with `id` |\n| Forced dialogue | Yes | `replace: true` | `remove: true` with `id` |\n| Notifications and gifts | Yes | `replace: true` | Replace or remove by entry `id` |\n| Pacification | Yes | Same-path file replacement only | No entry removal |\n| Profession loot | Yes | `replace: true` | `remove: true` with rule `id` |\n| Villager and village names | Yes | `replace: true` | No entry removal |\n| Duel kits and sell prices | One definition per resource path | Replace the same resource path | Disable sell prices with `enabled: false` |\n| Story discovery and generated containers | Yes | Same-path file replacement only | Redefine a story target ID |\n| Villager event triggers | Yes | Replace by trigger `id` | No removal flag |\n\nUse your own file names when you want additive content:\n\n```text\ndata/my_pack/dialogue/en_us/my_pack/lines/rumors.json\ndata/villagerretaliation/notifications/en_us/my_pack/world_text.json\ndata/villagerretaliation/gifts/my_pack_preferences.json\ndata/villagerretaliation/currency/default.json\ndata/villagerretaliation/village_names/my_village_names.json\n```\n\nUse a small control file when you want a complete overhaul:\n\n```json\n{ \"replace\": true }\n```\n\nFor quests, dialogue trees, and forced dialogue, a control-only `replace` file disables the built-ins without registering a dummy quest, tree, or forced-dialogue entry. Put your replacement content in the same file or any other add-on file for that system.\n\n## Suggested Workflow\n\n1. Make one file.\n2. Put one obvious line or rule in it.\n3. Run `/reload`.\n4. Trigger that feature in game.\n5. Only then add more filters or more entries.\n\nExample first test:\n\n```text\ndata/my_pack/dialogue/en_us/my_pack/messages/00_test.json\n```\n\n```json\n{\n  \"id\": \"my_pack.message.test\",\n  \"key\": \"my_pack.message.test\",\n  \"text\": \"Testing.\"\n}\n```\n\nIf the file loads, you know the path and JSON shape are valid before you build something more complex around it.\n\n## Reload And Diagnostic Commands\n\n```mcfunction\n/reload\n/vr admin datapack diagnostics\n/vr admin villager reputation set <targets> <player> <value>\n/vr admin dialogue explain <villager> <request> [option_id]\n```\n\n`datapack diagnostics` reports loading and validation problems. `dialogue explain` reports why a line matched or was rejected.\n\n## Common Mistakes\n\n- Putting `notifications` data inside a dialogue file.\n- Using the wrong namespace for gifts, pacification, or notifications.\n- Forgetting to add stable `id` values to content you want to translate or override later.\n- Copying a built-in file path when you only meant to add one extra line.\n- Adding heavy filters before verifying the unfiltered version works.\n- Using resource-pack format `34` for a Minecraft 1.21.1 datapack. Datapacks use format `48`.\n\n## Example Layout\n\nThis is a clean small addon that touches several systems:\n\n```text\npack.mcmeta\ndata/\n  villagerretaliation/\n    gifts/my_pack_gifts.json\n    notifications/en_us/my_pack_notifications.json\n  my_pack/\n    dialogue/en_us/my_pack/options/00_rumor.json\n    dialogue/en_us/my_pack/lines/00_rumor.json\n    forced_dialogue/my_pack_events.json\n    quests/old_roads/road_ledger.json\n    dialogue_trees/en_us/quests/old_roads/road_ledger.json\n```\n\nThat is usually easier to maintain than one giant file per system.\n",
      "text": "Pack Development This page is the setup guide for writing Villager Retaliation addons. Use it before touching any system specific JSON. Pack Types Use a datapack for behavior and authored text: Every datapack also needs a pack.mcmeta at its root. For Minecraft 1.21.1 datapacks: Use a resource pack for GUI text, textures, and model JSON: Namespace Rules These systems are intentionally fixed to the mod namespace: System Namespace Notifications villagerretaliation Gifts villagerretaliation Currency villagerretaliation Natural job armor villagerretaliation Pacification villagerretaliation Profession loot rules villagerretaliation Villager names villagerretaliation Village names villagerretaliation These systems can live in your own namespace: Dialogue Dialogue trees Quests Forced dialogue Skill trades Builder structures Story structures Story biomes Villager event triggers Duel kits Sell prices Persistent quest scenes and encounter templates Generated container lists Referenced loot tables Example: Override Rules Minecraft resolves exact resource paths first. Villager Retaliation then merges the files it finds for that loader. A file at the same resource path as a built in file replaces that built in file before VR reads it. Inside many systems, a later entry with the same id replaces an earlier entry without replacing the whole file. For quests, dialogue trees, and forced dialogue, top level replace: true puts that loader in replacement mode: VR skips built in resources for that system, then applies add on resources. For normal dialogue, top level replace: true clears the current dialogue pool, and replace sections can clear only selected sections. Top level remove: true removes one quest, dialogue tree, or forced dialogue definition by id. System Additive by default Clear everything Remove one entry Dialogue Yes replace: true or replace sections Replace by same entry id Dialogue trees Yes replace: true remove: true with id Quests Yes replace: true remove: true with id Forced dialogue Yes replace: true remove: true with id Notifications and gifts Yes replace: true Replace or remove by entry id Pacification Yes Same path file replacement only No entry removal Profession loot Yes replace: true remove: true with rule id Villager and village names Yes replace: true No entry removal Duel kits and sell prices One definition per resource path Replace the same resource path Disable sell prices with enabled: false Story discovery and generated containers Yes Same path file replacement only Redefine a story target ID Villager event triggers Yes Replace by trigger id No removal flag Use your own file names when you want additive content: Use a small control file when you want a complete overhaul: For quests, dialogue trees, and forced dialogue, a control only replace file disables the built ins without registering a dummy quest, tree, or forced dialogue entry. Put your replacement content in the same file or any other add on file for that system. Suggested Workflow 1. Make one file. 2. Put one obvious line or rule in it. 3. Run /reload. 4. Trigger that feature in game. 5. Only then add more filters or more entries. Example first test: If the file loads, you know the path and JSON shape are valid before you build something more complex around it. Reload And Diagnostic Commands datapack diagnostics reports loading and validation problems. dialogue explain reports why a line matched or was rejected. Common Mistakes Putting notifications data inside a dialogue file. Using the wrong namespace for gifts, pacification, or notifications. Forgetting to add stable id values to content you want to translate or override later. Copying a built in file path when you only meant to add one extra line. Adding heavy filters before verifying the unfiltered version works. Using resource pack format 34 for a Minecraft 1.21.1 datapack. Datapacks use format 48. Example Layout This is a clean small addon that touches several systems: That is usually easier to maintain than one giant file per system.",
      "headings": [
        {
          "level": 2,
          "title": "Pack Types"
        },
        {
          "level": 2,
          "title": "Namespace Rules"
        },
        {
          "level": 2,
          "title": "Override Rules"
        },
        {
          "level": 2,
          "title": "Suggested Workflow"
        },
        {
          "level": 2,
          "title": "Reload And Diagnostic Commands"
        },
        {
          "level": 2,
          "title": "Common Mistakes"
        },
        {
          "level": 2,
          "title": "Example Layout"
        }
      ],
      "related": [
        "json-reference",
        "datapack-generator",
        "example-packs",
        "pack-format-changes"
      ]
    },
    {
      "slug": "json-reference",
      "file": "JSON-Reference.md",
      "source": "wiki/JSON-Reference.md",
      "sourceKind": "wiki",
      "group": "Getting Started",
      "icon": "braces",
      "title": "JSON Reference",
      "description": "Shared IDs, selectors, conditions, quest facts, actions, weights, and message-key rules.",
      "markdown": "# JSON Reference\n\nThis page covers the shared authoring rules used across Villager Retaliation JSON.\n\n## Stable Ids\n\nGive entries a stable `id` whenever you may want to:\n\n- override that entry later\n- translate it in another locale\n- remove it with a follow-up datapack\n- read cleaner debug output\n\nExample:\n\n```json\n{\n  \"id\": \"my_pack.greeting.rainy_day\",\n  \"request\": \"greeting\",\n  \"text\": \"Rain makes even short roads feel longer.\"\n}\n```\n\n## `text` vs `lines`\n\nUse `text` for one output. Use `lines` when the same rule should randomly say one of several variations.\n\n```json\n{\n  \"id\": \"my_pack.line.variants\",\n  \"request\": \"question\",\n  \"lines\": [\n    \"Quiet roads are usually planning something.\",\n    \"Roads are safer when someone else has already checked them.\"\n  ],\n  \"weight\": 10\n}\n```\n\n## `replace` and `remove`\n\nTop-level `replace: true` clears the previously loaded pool for that system before the file is applied.\n\n```json\n{\n  \"replace\": true,\n  \"notifications\": []\n}\n```\n\nSome systems also support entry-level removal by `id`.\n\n```json\n{\n  \"preferences\": [\n    {\n      \"id\": \"villagerretaliation.default.bad_gift\",\n      \"remove\": true\n    }\n  ]\n}\n```\n\nBuilder structures also support removal by structure id:\n\n```json\n{\n  \"entries\": [\n    {\n      \"structure\": \"minecraft:village/plains/houses/plains_small_house_1\",\n      \"remove\": true\n    }\n  ]\n}\n```\n\n## Arrays and Single Values\n\nMany fields accept one value or several values.\n\n```json\n{\n  \"professions\": [\"minecraft:farmer\", \"minecraft:fletcher\"]\n}\n```\n\nWhen in doubt, prefer arrays. They are clearer and easier to extend later.\n\n## Reputation Filters\n\nThese fields show up in several systems:\n\n| Field | Meaning |\n| --- | --- |\n| `reputation_levels` | One or more named tiers such as `trusted` or `hostile` |\n| `min_reputation` | Lowest numeric or named reputation allowed |\n| `max_reputation` | Highest numeric or named reputation allowed |\n\nNamed tiers commonly used in docs:\n\n```text\nroyalty\nrevered\nrespected\ntrusted\nneutral\nsuspicious\nhostile\ndespised\nfeared\n```\n\nExample:\n\n```json\n{\n  \"id\": \"my_pack.notification.low_trust\",\n  \"trigger\": \"trade.refused\",\n  \"text\": \"Not today.\",\n  \"reputation_levels\": [\"hostile\", \"despised\", \"feared\"]\n}\n```\n\n## Item and Tag Selectors\n\nUse item ids for exact matches:\n\n```json\n{\n  \"items\": [\"minecraft:emerald\"]\n}\n```\n\nUse tags with `#` when any item in the tag should count:\n\n```json\n{\n  \"items\": [\"#minecraft:flowers\"]\n}\n```\n\nThe same pattern is used in sell prices, gifts, pacification, and some forced-dialogue payment selectors.\n\n## Currency\n\nVillager Retaliation's hire payments, payment boxes, wallet deposits, wallet UI, default currency drops, and emerald-default skill-trade costs use:\n\n```text\ndata/villagerretaliation/currency/default.json\n```\n\nBuilt-in default:\n\n```json\n{\n  \"item\": \"minecraft:emerald\",\n  \"name\": \"emerald\",\n  \"plural_name\": \"emeralds\",\n  \"wallet_label\": \"Emeralds\",\n  \"text_color\": \"#55ff55\"\n}\n```\n\nFields:\n\n| Field | Meaning |\n| --- | --- |\n| `item` | Primary currency item. Refunds, wallet deposits, drops, and emerald-default skill trade costs use this item. |\n| `accepted_items` / `items` | Extra item ids accepted as equivalent payment. |\n| `accepted_tags` / `tags` | Item tags accepted as equivalent payment. Prefixing with `#` is optional here. |\n| `name` | Singular display name used in notices. |\n| `plural_name` | Plural display name used in notices. |\n| `wallet_label` | Label shown in the villager interaction wallet line. |\n| `text_color` / `wallet_text_color` / `wallet_color` / `color` | Hex or named color for the villager interaction wallet number. Defaults to `#55ff55`. |\n\nPayment-box recipes and client-side \"hold currency\" checks also use the `villagerretaliation:currency` item tag:\n\n```text\ndata/villagerretaliation/tags/item/currency.json\n```\n\nKeep that tag aligned with your currency item so crafting recipes, payment boxes, and client hints all agree.\n\n## Conditions\n\n`conditions` are the preferred way to express complex logic in newer beta.12 content. A condition array usually means all listed conditions must pass.\n\n```json\n{\n  \"id\": \"my_pack.line.night_storm\",\n  \"request\": \"village_event_report\",\n  \"conditions\": [\n    { \"type\": \"time\", \"value\": \"night\" },\n    { \"type\": \"weather\", \"state\": \"thunder\" }\n  ],\n  \"text\": \"Storm nights make bad fences and worse promises.\"\n}\n```\n\nUse conditions when the older one-off helper flags start to pile up.\n\n### Mood Conditions\n\nUse `mood` conditions for current villager mood gates. Active quests can evaluate these from saved villager mood state when the issuing villager is unloaded.\n\n```json\n{\n  \"conditions\": [\n    { \"type\": \"mood\", \"mood\": \"protective\", \"min_mood_intensity\": 30 }\n  ]\n}\n```\n\nLegacy equipment flags such as `requires_villager_armed`, `requires_villager_unarmed`, `requires_witness_armed`, and `requires_witness_unarmed` are live-context gates for dialogue, notification, loot, gift, forced-dialogue, and pacify resources. They are not quest conditions and are not evaluated from saved active quest state.\n\n### Quest Facts\n\nUse `quest_fact` conditions for durable story flags, branch choices, and counters written by quest or dialogue actions.\n\n```json\n{\n  \"conditions\": [\n    {\n      \"type\": \"quest_fact\",\n      \"scope\": \"quest\",\n      \"quest\": \"my_pack:old_road\",\n      \"tag\": \"my_pack:warned_the_guard\"\n    }\n  ]\n}\n```\n\nScopes:\n\n| Scope | Meaning |\n| --- | --- |\n| `player` | Stored for the current player across the world |\n| `world` | Stored once for the whole save |\n| `quest` | Stored for the current player and a quest id |\n| `villager` | Stored on the current villager id |\n| `village` | Stored on the resolved village area, or the current villager position fallback |\n\nVariables and counters use `key` plus `value`, `min`, or `max`:\n\n```json\n{\n  \"conditions\": [\n    {\n      \"type\": \"quest_fact\",\n      \"scope\": \"quest\",\n      \"quest\": \"my_pack:old_road\",\n      \"key\": \"route\",\n      \"value\": \"river\"\n    },\n    {\n      \"type\": \"quest_fact\",\n      \"scope\": \"player\",\n      \"counter\": \"raiders_defeated\",\n      \"min\": 5\n    }\n  ]\n}\n```\n\nQuest stages are shorthand for `scope: \"quest\"`, `key: \"stage\"`, and a stage value:\n\n```json\n{\n  \"conditions\": [\n    {\n      \"type\": \"quest_stage\",\n      \"quest\": \"my_pack:old_road\",\n      \"stage\": \"warned_guard\"\n    }\n  ]\n}\n```\n\nUse `all_of`, `any_of`, and `not` around `quest_fact` conditions for larger branch logic.\n\nQuest offers can use the same condition shape:\n\n```json\n{\n  \"parent\": \"my_pack:first_chapter\",\n  \"offer\": {\n    \"conditions\": [\n      {\n        \"type\": \"quest_fact\",\n        \"scope\": \"world\",\n        \"tag\": \"my_pack:bridge_repaired\"\n      }\n    ]\n  }\n}\n```\n\n`parent` gates a quest behind a completed parent quest for the current player. `offer.conditions` gates whether the quest can be offered at all. `rules.active.conditions` controls whether an already active quest can currently progress.\n\nBranch locks close unchosen paths:\n\n```json\n{\n  \"rules\": {\n    \"exclusive_group\": \"my_pack:faction_choice\",\n    \"exclusive_on\": \"started\",\n    \"blocks_on_completion\": [\"my_pack:other_outcome\"]\n  }\n}\n```\n\n`exclusive_group` makes sibling quests in the same group mutually exclusive. `exclusive_on` accepts `started` or `completed`. `blocks_on_start`, `blocks_on_completion`, and `blocks` explicitly consume named quests. A locked quest matches quest state `branch_locked` and gets the quest-scoped tag `villagerretaliation:quest_branch_locked` plus variables `blocked_by`, `blocked_on`, and `exclusive_group`.\n\n## Quest Module V2\n\nNew quests should use `schema: \"villagerretaliation:quest/v2\"` under `data/<namespace>/quests/`. A simple playable quest can keep provider filters, availability, stages, objectives, dialogue slots, responses, scenes, rewards, events, and tracker UI in one file.\n\nRequired top-level fields:\n\n| Field | Meaning |\n| --- | --- |\n| `schema` | Must be `villagerretaliation:quest/v2` |\n| `id` | Full quest id, such as `my_pack:bread_delivery` |\n| `provider` | Provider type and filters, usually `villagerretaliation:villager` |\n| `entry_stage` | First stage id |\n| `stages` | Array of stage objects |\n\nCommon optional fields:\n\n| Field | Meaning |\n| --- | --- |\n| `metadata` | `title`, `description`, `title_key`, `description_key`, `questline`, `tags`, `parent`, `show_locked_adventure_hint` |\n| `availability` | Repeat, cooldown, abandonment, locking, completion scope, and active gates |\n| `target` | Structure target, dimension, search radius, discovery radius, and proof item |\n| `events` | Quest-level trigger actions |\n| `rewards` | XP, reputation, gossip, loot, memory event, or reward actions |\n| `ui` | Tracker text, icon, `color`, `outline_color`, progress, placeholders, priority, and hidden flag |\n| `external_scenes` | Resource ids for extracted dialogue tree scenes |\n\nSet `metadata.show_locked_adventure_hint` to `false` when a quest should not appear as a locked preview in the villager `Adventures` menu before its offer requirements are met.\n\nEach stage requires `id` and `objectives`. Stages can also define `complete_when`, `next`, `dialogue`, `responses`, `scenes`, `events`, `entry_actions`, `exit_actions`, `rewards`, `ui`, and `metadata`.\n\nDialogue slots such as `offer`, `reminder`, and `turn_in` can be inline:\n\n```json\n{\n  \"dialogue\": {\n    \"offer\": {\n      \"label\": \"Bread Delivery\",\n      \"request\": \"question\",\n      \"lines\": [\"The bins are low.\"],\n      \"responses\": [\n        {\n          \"id\": \"accept\",\n          \"label\": \"I can help.\",\n          \"scene\": \"start_quest\"\n        }\n      ]\n    }\n  }\n}\n```\n\nOr extracted:\n\n```json\n{\n  \"external_scenes\": [\"my_pack:quests/village_supply/bread_delivery\"],\n  \"dialogue\": {\n    \"offer\": {\n      \"label\": \"Bread Delivery\",\n      \"request\": \"question\",\n      \"external_scene\": {\n        \"tree\": \"my_pack:quests/village_supply/bread_delivery\",\n        \"entry\": \"offer\"\n      }\n    }\n  }\n}\n```\n\nFor responses, use one transition source: direct response fields, a `transition` object, or a transition action. Do not mix direct `next`/`stage`/`scene`/`complete` fields with a transition action on the same response.\n\nValidate standalone quest modules with:\n\n```text\nnode tools/validate-dialogue-data.mjs --quest path/to/quest.json\n```\n\nRegenerate the generated authoring schema and registry metadata with:\n\n```text\n.\\gradlew.bat :neoforge:generateQuestV2Schema\n```\n\n## Shared Actions\n\nDialogue trees, quest triggers, and villager event triggers use the same `actions` shape for most state changes.\n\n```json\n{\n  \"actions\": [\n    { \"type\": \"quest\", \"quest\": \"my_pack:old_road\", \"action\": \"start\" },\n    { \"type\": \"notification\", \"trigger\": \"quest.updated\", \"text\": \"Quest updated: {quest}\" }\n  ]\n}\n```\n\nCommon action types:\n\n| Type | Important fields |\n| --- | --- |\n| `quest` | `quest` or `quest_id`, `action`: `start`, `remind`, `turn_in`, `abandon`, or `block` |\n| `notification` | `trigger`, `text` |\n| `forced_dialogue` | `forced_dialogue` |\n| `experience` | `amount` or `experience` |\n| `reputation` | `amount` or `reputation` |\n| `gossip` | `amount`, `gossip`, or `gossip_reputation` |\n| `memory` | `memory_event`, optional `memory_scope`: `villager`, `village`, or `both` |\n| `loot` | `loot_table` |\n| `tracker` | `flash_tracker` |\n| `draw_weapon` | Optional `duration_ticks`, `duration_seconds`, or `duration_days`; defaults to 10 seconds |\n| `set_tag` | `tag` or `set_tag`, optional `scope`, optional `quest` |\n| `clear_tag` | `tag` or `clear_tag`, optional `scope`, optional `quest` |\n| `set_variable` | `key` or `variable`, `value`, optional `scope`, optional `quest` |\n| `set_stage` | `stage`, optional `quest`. Stores quest-scoped branch state |\n| `counter` | `key` or `counter`, optional `amount`, `by`, or `delta`, optional `scope`, optional `quest` |\n\nQuest facts default to `quest` scope when the action has a quest id or is inside a quest-owned trigger. Otherwise they default to `player` scope.\n\n`draw_weapon` equips the best usable weapon carried by the acting villager for the configured duration. It does not set an attack target, start retaliation, or change reputation. Dialogue-tree response actions can use it when a particular player response should make the villager visibly arm themselves:\n\n```json\n{\n  \"actions\": [\n    { \"type\": \"draw_weapon\", \"duration_seconds\": 10 }\n  ]\n}\n```\n\nMemory actions default `memory_scope` to `both`. `villager` remembers the event only for the acting villager, `village` writes only to the tracked village footprint containing the event, and `both` writes each available bucket. Quest reward shorthand using only `memory_event` also defaults to `both`.\n\nUse `action: \"block\"` when a dialogue choice or trigger should close a path immediately. The target quest becomes `branch_locked` and receives `villagerretaliation:quest_branch_locked`.\n\n```json\n{\n  \"type\": \"quest\",\n  \"quest\": \"my_pack:smuggle_the_relic\",\n  \"action\": \"block\"\n}\n```\n\n```json\n{\n  \"actions\": [\n    {\n      \"type\": \"set_tag\",\n      \"scope\": \"quest\",\n      \"quest\": \"my_pack:old_road\",\n      \"tag\": \"my_pack:warned_the_guard\"\n    },\n    {\n      \"type\": \"set_variable\",\n      \"scope\": \"quest\",\n      \"quest\": \"my_pack:old_road\",\n      \"key\": \"route\",\n      \"value\": \"river\"\n    },\n    {\n      \"type\": \"set_stage\",\n      \"quest\": \"my_pack:old_road\",\n      \"stage\": \"warned_guard\"\n    },\n    {\n      \"type\": \"counter\",\n      \"scope\": \"player\",\n      \"counter\": \"raiders_defeated\",\n      \"amount\": 1\n    }\n  ]\n}\n```\n\n## Weights and Priority\n\n- `weight` changes the random odds between otherwise equivalent matches.\n- `weight: 0` disables normal lines, keyed messages, openings, closings, and pacify lines without removing their data.\n- `priority` is a stronger sort step used on normal dialogue lines before weighted selection.\n\nExample:\n\n```json\n{\n  \"id\": \"my_pack.line.high_priority_warning\",\n  \"request\": \"question\",\n  \"priority\": 20,\n  \"weight\": 1,\n  \"text\": \"You should deal with the raid first.\"\n}\n```\n\nUse `priority` when one line should win reliably. Use `weight` when several matched lines should all stay in rotation.\n\n## Message Keys\n\nWhen several rules should share the same localized text, move the wording into a keyed message and reference it with `text_key`.\n\n```json\n{\n  \"id\": \"my_pack.line.shared_warning\",\n  \"request\": \"question\",\n  \"text_key\": \"my_pack.warning.road_closed\"\n}\n```\n\n```json\n{\n  \"id\": \"my_pack.message.road_closed\",\n  \"key\": \"my_pack.warning.road_closed\",\n  \"text\": \"The road is closed until morning.\"\n}\n```\n\n## Canonical Naming\n\nPrefer the current documented field names even if compatibility aliases still work. For new content, that usually means:\n\n- `trigger` instead of older event aliases\n- `world_text_kind` for notifications\n- `request` on dialogue options and lines\n- `conditions` for complex logic\n\n## Troubleshooting Example\n\nIf a file appears valid but nothing happens, strip it back to a bare minimum:\n\n```json\n{\n  \"id\": \"my_pack.debug\",\n  \"request\": \"question\",\n  \"text\": \"Debug line.\"\n}\n```\n\nIf that works, the problem is in the filters, not the path or loader.\n",
      "text": "JSON Reference This page covers the shared authoring rules used across Villager Retaliation JSON. Stable Ids Give entries a stable id whenever you may want to: override that entry later translate it in another locale remove it with a follow up datapack read cleaner debug output Example: text vs lines Use text for one output. Use lines when the same rule should randomly say one of several variations. replace and remove Top level replace: true clears the previously loaded pool for that system before the file is applied. Some systems also support entry level removal by id. Builder structures also support removal by structure id: Arrays and Single Values Many fields accept one value or several values. When in doubt, prefer arrays. They are clearer and easier to extend later. Reputation Filters These fields show up in several systems: Field Meaning reputation levels One or more named tiers such as trusted or hostile min reputation Lowest numeric or named reputation allowed max reputation Highest numeric or named reputation allowed Named tiers commonly used in docs: Example: Item and Tag Selectors Use item ids for exact matches: Use tags with when any item in the tag should count: The same pattern is used in sell prices, gifts, pacification, and some forced dialogue payment selectors. Currency Villager Retaliation's hire payments, payment boxes, wallet deposits, wallet UI, default currency drops, and emerald default skill trade costs use: Built in default: Fields: Field Meaning item Primary currency item. Refunds, wallet deposits, drops, and emerald default skill trade costs use this item. accepted items / items Extra item ids accepted as equivalent payment. accepted tags / tags Item tags accepted as equivalent payment. Prefixing with is optional here. name Singular display name used in notices. plural name Plural display name used in notices. wallet label Label shown in the villager interaction wallet line. text color / wallet text color / wallet color / color Hex or named color for the villager interaction wallet number. Defaults to 55ff55. Payment box recipes and client side \"hold currency\" checks also use the villagerretaliation:currency item tag: Keep that tag aligned with your currency item so crafting recipes, payment boxes, and client hints all agree. Conditions conditions are the preferred way to express complex logic in newer beta.12 content. A condition array usually means all listed conditions must pass. Use conditions when the older one off helper flags start to pile up. Mood Conditions Use mood conditions for current villager mood gates. Active quests can evaluate these from saved villager mood state when the issuing villager is unloaded. Legacy equipment flags such as requires villager armed, requires villager unarmed, requires witness armed, and requires witness unarmed are live context gates for dialogue, notification, loot, gift, forced dialogue, and pacify resources. They are not quest conditions and are not evaluated from saved active quest state. Quest Facts Use quest fact conditions for durable story flags, branch choices, and counters written by quest or dialogue actions. Scopes: Scope Meaning player Stored for the current player across the world world Stored once for the whole save quest Stored for the current player and a quest id villager Stored on the current villager id village Stored on the resolved village area, or the current villager position fallback Variables and counters use key plus value, min, or max: Quest stages are shorthand for scope: \"quest\", key: \"stage\", and a stage value: Use all of, any of, and not around quest fact conditions for larger branch logic. Quest offers can use the same condition shape: parent gates a quest behind a completed parent quest for the current player. offer.conditions gates whether the quest can be offered at all. rules.active.conditions controls whether an already active quest can currently progress. Branch locks close unchosen paths: exclusive group makes sibling quests in the same group mutually exclusive. exclusive on accepts started or completed. blocks on start, blocks on completion, and blocks explicitly consume named quests. A locked quest matches quest state branch locked and gets the quest scoped tag villagerretaliation:quest branch locked plus variables blocked by, blocked on, and exclusive group. Quest Module V2 New quests should use schema: \"villagerretaliation:quest/v2\" under data/ /quests/. A simple playable quest can keep provider filters, availability, stages, objectives, dialogue slots, responses, scenes, rewards, events, and tracker UI in one file. Required top level fields: Field Meaning schema Must be villagerretaliation:quest/v2 id Full quest id, such as my pack:bread delivery provider Provider type and filters, usually villagerretaliation:villager entry stage First stage id stages Array of stage objects Common optional fields: Field Meaning metadata title, description, title key, description key, questline, tags, parent, show locked adventure hint availability Repeat, cooldown, abandonment, locking, completion scope, and active gates target Structure target, dimension, search radius, discovery radius, and proof item events Quest level trigger actions rewards XP, reputation, gossip, loot, memory event, or reward actions ui Tracker text, icon, color, outline color, progress, placeholders, priority, and hidden flag external scenes Resource ids for extracted dialogue tree scenes Set metadata.show locked adventure hint to false when a quest should not appear as a locked preview in the villager Adventures menu before its offer requirements are met. Each stage requires id and objectives. Stages can also define complete when, next, dialogue, responses, scenes, events, entry actions, exit actions, rewards, ui, and metadata. Dialogue slots such as offer, reminder, and turn in can be inline: Or extracted: For responses, use one transition source: direct response fields, a transition object, or a transition action. Do not mix direct next/stage/scene/complete fields with a transition action on the same response. Validate standalone quest modules with: Regenerate the generated authoring schema and registry metadata with: Shared Actions Dialogue trees, quest triggers, and villager event triggers use the same actions shape for most state changes. Common action types: Type Important fields quest quest or quest id, action: start, remind, turn in, abandon, or block notification trigger, text forced dialogue forced dialogue experience amount or experience reputation amount or reputation gossip amount, gossip, or gossip reputation memory memory event, optional memory scope: villager, village, or both loot loot table tracker flash tracker draw weapon Optional duration ticks, duration seconds, or duration days; defaults to 10 seconds set tag tag or set tag, optional scope, optional quest clear tag tag or clear tag, optional scope, optional quest set variable key or variable, value, optional scope, optional quest set stage stage, optional quest. Stores quest scoped branch state counter key or counter, optional amount, by, or delta, optional scope, optional quest Quest facts default to quest scope when the action has a quest id or is inside a quest owned trigger. Otherwise they default to player scope. draw weapon equips the best usable weapon carried by the acting villager for the configured duration. It does not set an attack target, start retaliation, or change reputation. Dialogue tree response actions can use it when a particular player response should make the villager visibly arm themselves: Memory actions default memory scope to both. villager remembers the event only for the acting villager, village writes only to the tracked village footprint containing the event, and both writes each available bucket. Quest reward shorthand using only memory event also defaults to both. Use action: \"block\" when a dialogue choice or trigger should close a path immediately. The target quest becomes branch locked and receives villagerretaliation:quest branch locked. Weights and Priority weight changes the random odds between otherwise equivalent matches. weight: 0 disables normal lines, keyed messages, openings, closings, and pacify lines without removing their data. priority is a stronger sort step used on normal dialogue lines before weighted selection. Example: Use priority when one line should win reliably. Use weight when several matched lines should all stay in rotation. Message Keys When several rules should share the same localized text, move the wording into a keyed message and reference it with text key. Canonical Naming Prefer the current documented field names even if compatibility aliases still work. For new content, that usually means: trigger instead of older event aliases world text kind for notifications request on dialogue options and lines conditions for complex logic Troubleshooting Example If a file appears valid but nothing happens, strip it back to a bare minimum: If that works, the problem is in the filters, not the path or loader.",
      "headings": [
        {
          "level": 2,
          "title": "Stable Ids"
        },
        {
          "level": 2,
          "title": "`text` vs `lines`"
        },
        {
          "level": 2,
          "title": "`replace` and `remove`"
        },
        {
          "level": 2,
          "title": "Arrays and Single Values"
        },
        {
          "level": 2,
          "title": "Reputation Filters"
        },
        {
          "level": 2,
          "title": "Item and Tag Selectors"
        },
        {
          "level": 2,
          "title": "Currency"
        },
        {
          "level": 2,
          "title": "Conditions"
        },
        {
          "level": 3,
          "title": "Mood Conditions"
        },
        {
          "level": 3,
          "title": "Quest Facts"
        },
        {
          "level": 2,
          "title": "Quest Module V2"
        },
        {
          "level": 2,
          "title": "Shared Actions"
        },
        {
          "level": 2,
          "title": "Weights and Priority"
        },
        {
          "level": 2,
          "title": "Message Keys"
        },
        {
          "level": 2,
          "title": "Canonical Naming"
        },
        {
          "level": 2,
          "title": "Troubleshooting Example"
        }
      ],
      "related": [
        "pack-development",
        "quests",
        "dialogue",
        "forced-dialogue"
      ]
    },
    {
      "slug": "first-quest",
      "file": "First-Quest.md",
      "source": "wiki/First-Quest.md",
      "sourceKind": "wiki",
      "group": "Getting Started",
      "icon": "route",
      "title": "First Quest Guide",
      "description": "Build and test a complete quest module v2 file from the smallest working shape.",
      "markdown": "# First Quest Guide\n\nThis guide walks through the smallest complete quest that feels playable in game.\n\nFor new packs, use one quest module v2 file first. Add external dialogue trees later only when the scene grows large or needs separate ownership.\n\n## What You Are Making\n\nThis example adds a farmer quest named `Bread Delivery`.\n\nThe player can:\n\n1. Talk to a farmer and accept the quest.\n2. Gather 16 bread.\n3. Track the objective in the quest HUD and journal.\n4. Return to the same quest giver and turn it in.\n\n## File: Quest Module V2\n\nCreate:\n\n```text\ndata/my_pack/quests/village_supply/bread_delivery.json\n```\n\n```json\n{\n  \"schema\": \"villagerretaliation:quest/v2\",\n  \"id\": \"my_pack:bread_delivery\",\n  \"metadata\": {\n    \"title\": \"Bread Delivery\",\n    \"description\": \"Bring 16 bread to the village stores.\",\n    \"questline\": \"village_supply\",\n    \"tags\": [\"group.village_supply\"]\n  },\n  \"provider\": {\n    \"type\": \"villagerretaliation:villager\",\n    \"filters\": {\n      \"professions\": [\"minecraft:farmer\"],\n      \"min_villager_level\": \"novice\"\n    }\n  },\n  \"availability\": {\n    \"repeatable\": true,\n    \"completion_cooldown_days\": 1,\n    \"locked_to_villager\": true,\n    \"cross_villager_compatible\": false,\n    \"abandonment\": \"allow_repickup\",\n    \"consume_on_completion\": true\n  },\n  \"entry_stage\": \"gather\",\n  \"stages\": [\n    {\n      \"id\": \"gather\",\n      \"objectives\": [\n        {\n          \"id\": \"bring_bread\",\n          \"type\": \"item_check\",\n          \"item\": \"minecraft:bread\",\n          \"count\": 16,\n          \"tracker\": {\n            \"text\": \"Bring 16 bread back to the quest giver.\",\n            \"complete_text\": \"The bread is packed and ready.\",\n            \"show_progress\": true,\n            \"progress\": 0.75\n          }\n        }\n      ],\n      \"dialogue\": {\n        \"offer\": {\n          \"label\": \"Bread Delivery\",\n          \"request\": \"question\",\n          \"order\": -20,\n          \"show_for_babies\": false,\n          \"lines\": [\n            \"The bins are low. Sixteen bread would quiet a lot of worried stomachs.\"\n          ],\n          \"responses\": [\n            {\n              \"id\": \"accept\",\n              \"label\": \"I can help stock the larder.\",\n              \"scene\": \"start_quest\"\n            },\n            {\n              \"id\": \"decline\",\n              \"label\": \"Another time.\",\n              \"scene\": \"decline\"\n            }\n          ]\n        },\n        \"reminder\": {\n          \"label\": \"About Bread Delivery\",\n          \"request\": \"question\",\n          \"order\": -20,\n          \"show_for_babies\": false,\n          \"lines\": [\n            \"Bread Delivery is still open. The tracker has the count.\"\n          ],\n          \"responses\": [\n            {\n              \"id\": \"leave\",\n              \"label\": \"I'll keep looking.\",\n              \"scene\": \"end\"\n            }\n          ]\n        },\n        \"turn_in\": {\n          \"label\": \"About Bread Delivery\",\n          \"request\": \"question\",\n          \"order\": -20,\n          \"show_for_babies\": false,\n          \"lines\": [\n            \"If that pack smells like fresh bread, you may have saved me an argument.\"\n          ],\n          \"responses\": [\n            {\n              \"id\": \"complete\",\n              \"label\": \"Show what I brought.\",\n              \"scene\": \"complete_quest\"\n            },\n            {\n              \"id\": \"leave\",\n              \"label\": \"Not yet.\",\n              \"scene\": \"end\"\n            }\n          ]\n        }\n      },\n      \"scenes\": [\n        {\n          \"id\": \"start_quest\",\n          \"actions\": [\n            {\n              \"type\": \"quest\",\n              \"action\": \"start\",\n              \"lines\": {\n                \"started\": [\n                  \"Good. Bring the bread back when the count is ready.\"\n                ],\n                \"unavailable\": [\n                  \"The larder is not asking you for bread right now.\"\n                ]\n              }\n            }\n          ]\n        },\n        {\n          \"id\": \"complete_quest\",\n          \"actions\": [\n            {\n              \"type\": \"quest\",\n              \"action\": \"turn_in\",\n              \"lines\": {\n                \"completed\": [\n                  \"Good. A full shelf makes brave talk sound less hollow.\"\n                ],\n                \"missing_objectives\": [\n                  \"Bread Delivery is still short. The tracker has the exact count.\"\n                ],\n                \"unavailable\": [\n                  \"This bread delivery is not ready to close yet.\"\n                ]\n              }\n            }\n          ]\n        },\n        {\n          \"id\": \"decline\",\n          \"text\": \"Then I will keep counting crumbs and pretending it is planning.\"\n        },\n        {\n          \"id\": \"end\",\n          \"text\": \"Keep the bread close until you are ready.\"\n        }\n      ]\n    }\n  ],\n  \"rewards\": {\n    \"experience\": 60,\n    \"reputation\": 5,\n    \"gossip_reputation\": 2\n  },\n  \"ui\": {\n    \"tracker_text\": \"Bring 16 bread.\",\n    \"icon\": \"minecraft:bread\",\n    \"color\": \"#DCEBA6\"\n  }\n}\n```\n\nWhat this file does:\n\n| Section | Meaning |\n| --- | --- |\n| `schema` | Selects quest module v2 |\n| `id` | The stable quest id used by dialogue, commands, saves, and overrides |\n| `metadata` | Title, description, questline, and tags |\n| `provider` | Which villagers can offer the quest |\n| `availability` | Repeat, cooldown, abandonment, and locking behavior |\n| `entry_stage` | The first stage |\n| `stages[].objectives` | What the player must do |\n| `stages[].dialogue` | Offer, reminder, and turn-in Talk menu scenes |\n| `stages[].scenes` | Action scenes reached from response buttons |\n| `rewards` | What the player receives on turn-in |\n| `ui` | Tracker text, icon, and optional title/outline colors |\n\n## Validation And Diagnostics\n\nThe repository validator accepts a quest file directly:\n\n```text\nnode tools/validate-dialogue-data.mjs --quest path/to/data/my_pack/quests/village_supply/bread_delivery.json\n```\n\nRuntime diagnostics are available through:\n\n```text\n/vr admin datapack diagnostics\n/vr admin quest providers\n/vr admin quest whyAvailable my_pack:bread_delivery <provider>\n/vr admin quest inspect my_pack:bread_delivery\n```\n\nThe debug inspector reports saved state, availability, active conditions, issuer data, objective counters, cooldowns, current stage, and branch locks.\n\n## Common Mistakes\n\n| Symptom | Likely cause |\n| --- | --- |\n| Quest never appears in Talk menu | Missing `schema`, wrong path, bad provider filters, or availability gates fail |\n| Quest starts but cannot be turned in | Objective is not complete, turn-in scene is missing, or turn-in action is unavailable |\n| Any villager offers the quest | `provider.filters.professions` is missing or too broad |\n| Quest appears for the wrong story branch | Missing `metadata.parent`, `availability.conditions`, or branch-lock rules |\n| Tracker text is vague | Add `ui.tracker_text` or objective `tracker.text` |\n| Player cannot find the quest giver | Keep `locked_to_villager: true` for personal favors. Use `cross_villager_compatible: true` only when another villager should continue the same quest |\n| Advanced item objective highlights the wrong stack | The client highlights by item id. Explain enchantment, durability, or custom-data requirements in tracker text |\n\n## When To Add More Files\n\nStart with the one quest module file above.\n\nAdd a normal dialogue message file when you want reusable localized text:\n\n```text\ndata/my_pack/dialogue/en_us/quests/village_supply/bread_delivery/messages/00_text.json\n```\n\nAdd an external dialogue tree only when the quest scene is too large for the quest file or another pack should own that scene:\n\n```text\ndata/my_pack/dialogue_trees/en_us/quests/village_supply/bread_delivery.json\n```\n\nAdd forced dialogue only when the quest needs an event-driven interruption, warning, confrontation, or scene outside the Talk menu:\n\n```text\ndata/my_pack/forced_dialogue/quests/village_supply/bread_delivery.json\n```\n\n## Legacy V1 Note\n\nOlder v1 quests still work with a quest file plus a matching dialogue tree. Do not rewrite a working v1 pack just to load it on current builds. Use v2 when creating new quests, when migrating intentionally, or when you want a simple quest to be playable from one file.\n\n## Next Steps\n\n- [Quests](Quests.md) covers stages, transitions, branches, targets, forced/external scenes, diagnostics, and v1 compatibility.\n- [Dialogue Trees](Dialogue-Trees.md) covers extracted authored scenes.\n- [Dialogue And Quests](Dialogue-And-Quests.md) covers file ownership and extraction paths.\n- [Localization](Localization.md) covers replacing inline English with message keys.\n",
      "text": "First Quest Guide This guide walks through the smallest complete quest that feels playable in game. For new packs, use one quest module v2 file first. Add external dialogue trees later only when the scene grows large or needs separate ownership. What You Are Making This example adds a farmer quest named Bread Delivery. The player can: 1. Talk to a farmer and accept the quest. 2. Gather 16 bread. 3. Track the objective in the quest HUD and journal. 4. Return to the same quest giver and turn it in. File: Quest Module V2 Create: What this file does: Section Meaning schema Selects quest module v2 id The stable quest id used by dialogue, commands, saves, and overrides metadata Title, description, questline, and tags provider Which villagers can offer the quest availability Repeat, cooldown, abandonment, and locking behavior entry stage The first stage stages[].objectives What the player must do stages[].dialogue Offer, reminder, and turn in Talk menu scenes stages[].scenes Action scenes reached from response buttons rewards What the player receives on turn in ui Tracker text, icon, and optional title/outline colors Validation And Diagnostics The repository validator accepts a quest file directly: Runtime diagnostics are available through: The debug inspector reports saved state, availability, active conditions, issuer data, objective counters, cooldowns, current stage, and branch locks. Common Mistakes Symptom Likely cause Quest never appears in Talk menu Missing schema, wrong path, bad provider filters, or availability gates fail Quest starts but cannot be turned in Objective is not complete, turn in scene is missing, or turn in action is unavailable Any villager offers the quest provider.filters.professions is missing or too broad Quest appears for the wrong story branch Missing metadata.parent, availability.conditions, or branch lock rules Tracker text is vague Add ui.tracker text or objective tracker.text Player cannot find the quest giver Keep locked to villager: true for personal favors. Use cross villager compatible: true only when another villager should continue the same quest Advanced item objective highlights the wrong stack The client highlights by item id. Explain enchantment, durability, or custom data requirements in tracker text When To Add More Files Start with the one quest module file above. Add a normal dialogue message file when you want reusable localized text: Add an external dialogue tree only when the quest scene is too large for the quest file or another pack should own that scene: Add forced dialogue only when the quest needs an event driven interruption, warning, confrontation, or scene outside the Talk menu: Legacy V1 Note Older v1 quests still work with a quest file plus a matching dialogue tree. Do not rewrite a working v1 pack just to load it on current builds. Use v2 when creating new quests, when migrating intentionally, or when you want a simple quest to be playable from one file. Next Steps Quests Quests.md covers stages, transitions, branches, targets, forced/external scenes, diagnostics, and v1 compatibility. Dialogue Trees Dialogue Trees.md covers extracted authored scenes. Dialogue And Quests Dialogue And Quests.md covers file ownership and extraction paths. Localization Localization.md covers replacing inline English with message keys.",
      "headings": [
        {
          "level": 2,
          "title": "What You Are Making"
        },
        {
          "level": 2,
          "title": "File: Quest Module V2"
        },
        {
          "level": 2,
          "title": "Validation And Diagnostics"
        },
        {
          "level": 2,
          "title": "Common Mistakes"
        },
        {
          "level": 2,
          "title": "When To Add More Files"
        },
        {
          "level": 2,
          "title": "Legacy V1 Note"
        },
        {
          "level": 2,
          "title": "Next Steps"
        }
      ],
      "related": [
        "quests",
        "dialogue-and-quests",
        "quest-scenes"
      ]
    },
    {
      "slug": "datapack-generator",
      "file": "Datapack-Generator.md",
      "source": "wiki/Datapack-Generator.md",
      "sourceKind": "wiki",
      "group": "Getting Started",
      "icon": "hammer",
      "title": "Datapack Generator",
      "description": "Use the bundled browser generator and understand which versioned surfaces it writes.",
      "markdown": "# Datapack Generator\n\nVillager Retaliation ships a local browser-based datapack builder at:\n\n```text\ntools/datapack-builder/index.html\n```\n\nIt is a static page. It does not need a server and it exports a normal datapack zip.\n\n## What It Is Good For\n\nUse the generator when you want to:\n\n- create a starter pack quickly\n- import an existing pack and inspect its structure\n- preview exact output paths before exporting\n- validate common JSON mistakes without hand-editing every file\n\n## Current Target\n\nThe generator keeps structured authoring targets for `1.0.0-beta.11` and `1.0.0-beta.12`. Its quest editor also understands the current quest module v2 surface used by beta.13.\n\n- Use this live developer wiki as the source of truth for hand-authored `1.0.0-beta.13` packs.\n- For Minecraft 1.21.1, set `pack_format` to `48` before export.\n- Choose `1.0.0-beta.12` in the generator for the folderized dialogue, notification, gift, pacification, story, name, loot, and skill-trade surfaces it currently writes.\n- Keep using the `1.0.0-beta.11` snapshot for older packs that have not been manually migrated.\n- The builder does not convert beta.11 packs to beta.12 for you.\n- For beta.13 persistent scenes and encounter orchestration, start from the repository example packs and [Persistent Quest Scenes](Quest-Scenes.md).\n\n## What It Writes\n\n| Builder area | Output root |\n| --- | --- |\n| Quests | `data/<namespace>/quests/` |\n| Skill Trades | `data/<namespace>/skill_trades/` |\n| Dialogue | `data/<namespace>/dialogue/<locale>/` |\n| Forced dialogue | `data/<namespace>/forced_dialogue/` |\n| Imported dialogue trees | `data/<namespace>/dialogue_trees/<locale>/` |\n| Notifications | `data/villagerretaliation/notifications/<locale>/` |\n| Gifts | `data/villagerretaliation/gifts/` |\n| Pacification | `data/villagerretaliation/pacification/` |\n| Story structures | `data/<namespace>/story_structures/` |\n| Story biomes | `data/<namespace>/story_biomes/` |\n| Names | `data/villagerretaliation/villager_names/` |\n\n## Fast Workflow\n\n1. Open `tools/datapack-builder/index.html`.\n2. Set pack name, namespace, locale, and output file slug.\n3. Add one system at a time.\n4. Watch the preview panel to confirm path and JSON.\n5. Export the zip.\n6. Put it in the world's `datapacks` folder and run `/reload`.\n\n## Best Starting Preset\n\nThe `Preset` button is the fastest way to start:\n\n- `Starter Pack` gives you a small editable beta.12 pack.\n- `Dialogue Folder Template` gives you the full folderized template from `example-packs/dialogue-folder-template/`.\n\nThat template already includes examples for quest module v2, dialogue, forced dialogue, notifications, gifts, pacification, profession loot, story discovery, and names.\n\n## Example Use\n\nIf you want one new dialogue option:\n\n1. Open the Dialogue tab.\n2. Pick `Typed folders`.\n3. Create an option with `request: story`.\n4. Create a matching line pointing back to that option id.\n5. Export.\n\nYou should end up with output similar to:\n\n```text\ndata/my_pack/dialogue/en_us/my_pack/options/00_rumor.json\ndata/my_pack/dialogue/en_us/my_pack/lines/00_rumor.json\n```\n\nIf you want one simple quest:\n\n1. Open the Quests tab.\n2. Click `Add Example`.\n3. Edit the quest id, provider filters, objective, dialogue, rewards, and tracker text in the JSON editor.\n4. Keep `Scene mode` on `Inline scenes` unless the scene should live in a separate dialogue tree.\n5. Export.\n\nYou should end up with output similar to:\n\n```text\ndata/my_pack/quests/first_steps.json\n```\n\n## Import Notes\n\nImport works best when your pack already follows the documented folder layout.\n\n- Files under `dialogue/<locale>/` import as dialogue.\n- Files under `forced_dialogue/` import as forced dialogue.\n- Quest module v2 files under `quests/` import as editable Quests tab modules.\n- Skill-trade files under `skill_trades/` import as editable Skill Trades entries and retain their namespace and nested source path.\n- Legacy v1 quest files under `quests/` are preserved as JSON pass-through files with migration suggestions.\n- Files under `dialogue_trees/<locale>/` are recognized and preserved as JSON pass-through files.\n- Files under `notifications/<locale>/` import as notifications.\n\nIf an older handwritten pack mixed several systems into one file, split those files first. The game itself also treats those paths as separate loaders.\n\nLegacy quest and dialogue-tree pass-through means the builder keeps those files in the pack and export zip without overwriting them. Use the migration suggestions as a prompt to run the v1-to-v2 tooling separately.\n\n## Good Safety Checks\n\nBefore exporting, confirm:\n\n- The namespace is correct.\n- The locale is correct.\n- Tags start with `#`.\n- Structure and biome ids are fully namespaced.\n- Stable `id` values are present on entries you may translate or override later.\n- Skill-trade ids, skills, professions, items, rank bounds, and Special Order request fields pass inline validation.\n- Quest modules use `schema: \"villagerretaliation:quest/v2\"`.\n- Response transitions use only one transition source.\n\nThe builder is a convenience layer. It does not register new items, professions, structures, or biomes on its own.\n",
      "text": "Datapack Generator Villager Retaliation ships a local browser based datapack builder at: It is a static page. It does not need a server and it exports a normal datapack zip. What It Is Good For Use the generator when you want to: create a starter pack quickly import an existing pack and inspect its structure preview exact output paths before exporting validate common JSON mistakes without hand editing every file Current Target The generator keeps structured authoring targets for 1.0.0 beta.11 and 1.0.0 beta.12. Its quest editor also understands the current quest module v2 surface used by beta.13. Use this live developer wiki as the source of truth for hand authored 1.0.0 beta.13 packs. For Minecraft 1.21.1, set pack format to 48 before export. Choose 1.0.0 beta.12 in the generator for the folderized dialogue, notification, gift, pacification, story, name, loot, and skill trade surfaces it currently writes. Keep using the 1.0.0 beta.11 snapshot for older packs that have not been manually migrated. The builder does not convert beta.11 packs to beta.12 for you. For beta.13 persistent scenes and encounter orchestration, start from the repository example packs and Persistent Quest Scenes Quest Scenes.md. What It Writes Builder area Output root Quests data/ /quests/ Skill Trades data/ /skill trades/ Dialogue data/ /dialogue/ / Forced dialogue data/ /forced dialogue/ Imported dialogue trees data/ /dialogue trees/ / Notifications data/villagerretaliation/notifications/ / Gifts data/villagerretaliation/gifts/ Pacification data/villagerretaliation/pacification/ Story structures data/ /story structures/ Story biomes data/ /story biomes/ Names data/villagerretaliation/villager names/ Fast Workflow 1. Open tools/datapack builder/index.html. 2. Set pack name, namespace, locale, and output file slug. 3. Add one system at a time. 4. Watch the preview panel to confirm path and JSON. 5. Export the zip. 6. Put it in the world's datapacks folder and run /reload. Best Starting Preset The Preset button is the fastest way to start: Starter Pack gives you a small editable beta.12 pack. Dialogue Folder Template gives you the full folderized template from example packs/dialogue folder template/. That template already includes examples for quest module v2, dialogue, forced dialogue, notifications, gifts, pacification, profession loot, story discovery, and names. Example Use If you want one new dialogue option: 1. Open the Dialogue tab. 2. Pick Typed folders. 3. Create an option with request: story. 4. Create a matching line pointing back to that option id. 5. Export. You should end up with output similar to: If you want one simple quest: 1. Open the Quests tab. 2. Click Add Example. 3. Edit the quest id, provider filters, objective, dialogue, rewards, and tracker text in the JSON editor. 4. Keep Scene mode on Inline scenes unless the scene should live in a separate dialogue tree. 5. Export. You should end up with output similar to: Import Notes Import works best when your pack already follows the documented folder layout. Files under dialogue/ / import as dialogue. Files under forced dialogue/ import as forced dialogue. Quest module v2 files under quests/ import as editable Quests tab modules. Skill trade files under skill trades/ import as editable Skill Trades entries and retain their namespace and nested source path. Legacy v1 quest files under quests/ are preserved as JSON pass through files with migration suggestions. Files under dialogue trees/ / are recognized and preserved as JSON pass through files. Files under notifications/ / import as notifications. If an older handwritten pack mixed several systems into one file, split those files first. The game itself also treats those paths as separate loaders. Legacy quest and dialogue tree pass through means the builder keeps those files in the pack and export zip without overwriting them. Use the migration suggestions as a prompt to run the v1 to v2 tooling separately. Good Safety Checks Before exporting, confirm: The namespace is correct. The locale is correct. Tags start with . Structure and biome ids are fully namespaced. Stable id values are present on entries you may translate or override later. Skill trade ids, skills, professions, items, rank bounds, and Special Order request fields pass inline validation. Quest modules use schema: \"villagerretaliation:quest/v2\". Response transitions use only one transition source. The builder is a convenience layer. It does not register new items, professions, structures, or biomes on its own.",
      "headings": [
        {
          "level": 2,
          "title": "What It Is Good For"
        },
        {
          "level": 2,
          "title": "Current Target"
        },
        {
          "level": 2,
          "title": "What It Writes"
        },
        {
          "level": 2,
          "title": "Fast Workflow"
        },
        {
          "level": 2,
          "title": "Best Starting Preset"
        },
        {
          "level": 2,
          "title": "Example Use"
        },
        {
          "level": 2,
          "title": "Import Notes"
        },
        {
          "level": 2,
          "title": "Good Safety Checks"
        }
      ],
      "related": []
    },
    {
      "slug": "example-packs",
      "file": "Example-Packs.md",
      "source": "wiki/Example-Packs.md",
      "sourceKind": "wiki",
      "group": "Getting Started",
      "icon": "folder-down",
      "title": "Example Packs",
      "description": "Copy complete packs ranging from one focused feature to persistent cinematic encounters.",
      "markdown": "# Example Packs\n\nThese folders are working test packs, not just JSON fragments. Copy the smallest example that covers your system, change its namespace and IDs, then run `/reload`.\n\n## Sell Prices\n\n`example-packs/sell-prices-example/` keeps a legacy beta.13 price beside explicit rates, tag pricing, discrete prices, component matching, durability, a built-in override, and a disabled definition. Its README calls out the fictitious mod and tag IDs used for illustration.\n\n## Custom Duel Kits\n\n`example-packs/custom-duel-kits/` adds an enchanted `duel_examples:champion` kit with temporary equipment for the player and villager. See [Duel Kits](Duel-Kits.md) for every field.\n\n## Persistent Cinematic Gate Ambush\n\n`example-packs/cinematic-gate-ambush/` is the complete beta.13 scene-orchestration example: two named villagers, player/party ownership, a recorded choice branch, movement, dialogue, a persisted wait, controlled encounter scaling and cleanup, quest completion/failure, and provider-unload recovery.\n\n## Repeatable scene run identity\n\n`example-packs/repeatable-scene-run-id/` is a deliberately small repeatable quest. It launches the same scene operation twice in one run (one instance), then demonstrates that a later legitimate run and an unrelated player's run receive different `QUEST_INSTANCE` owners.\n\nThe repo already includes a full starter datapack you can copy from:\n\n```text\nexample-packs/dialogue-folder-template/\n```\n\nThis is the best source of beta.12 dialogue-authoring examples. The beta.13 scene examples above cover the newer quest runtime surface.\n\n## What Is In The Template\n\n| Area | Example path |\n| --- | --- |\n| Dialogue option | `example-packs/dialogue-folder-template/data/villagerretaliation/dialogue/en_us/example_template/options/00_greeting.json` |\n| Dialogue line | `example-packs/dialogue-folder-template/data/villagerretaliation/dialogue/en_us/example_template/lines/00_greeting.json` |\n| Keyed message | `example-packs/dialogue-folder-template/data/villagerretaliation/dialogue/en_us/example_template/messages/00_example.json` |\n| Forced dialogue | `example-packs/dialogue-folder-template/data/villagerretaliation/forced_dialogue/example_template/00_container_theft.json` |\n| Notification | `example-packs/dialogue-folder-template/data/villagerretaliation/notifications/en_us/example_template/00_ambient.json` |\n| Gifts | `example-packs/dialogue-folder-template/data/villagerretaliation/gifts/example_template/00_gifts.json` |\n| Pacification | `example-packs/dialogue-folder-template/data/villagerretaliation/pacification/example_template/00_payments.json` |\n| Profession loot | `example-packs/dialogue-folder-template/data/villagerretaliation/profession_loot/example_template/00_loot.json` |\n| Villager names | `example-packs/dialogue-folder-template/data/villagerretaliation/villager_names/example_template_names.json` |\n\n## Smallest Copyable Pack\n\nIf you want the lightest possible starting point, copy only:\n\n```text\npack.mcmeta\ndata/\n  my_pack/\n    dialogue/en_us/my_pack/options/00_rumor.json\n    dialogue/en_us/my_pack/lines/00_rumor.json\n```\n\nExample option:\n\n```json\n{\n  \"id\": \"my_pack.option.ask_rumor\",\n  \"label\": \"Ask For A Rumor\",\n  \"request\": \"story\"\n}\n```\n\nExample line:\n\n```json\n{\n  \"id\": \"my_pack.line.rumor\",\n  \"request\": \"story\",\n  \"option\": \"my_pack.option.ask_rumor\",\n  \"text\": \"Roads carry stories faster than traders do.\"\n}\n```\n\n## When To Copy The Full Template\n\nCopy the whole `dialogue-folder-template` when you want:\n\n- one file per dialogue request\n- a translator-friendly folder layout\n- examples for beta.12 conditions and filters\n- a reference pack that covers almost every authoring surface\n\n## Minimal `pack.mcmeta`\n\n```json\n{\n  \"pack\": {\n    \"pack_format\": 48,\n    \"description\": \"Villager Retaliation example pack\"\n  }\n}\n```\n\nAdd your own `villagerretaliation.pack_version` marker only if your workflow already expects it. The builder will add it automatically on export for supported versions.\n",
      "text": "Example Packs These folders are working test packs, not just JSON fragments. Copy the smallest example that covers your system, change its namespace and IDs, then run /reload. Sell Prices example packs/sell prices example/ keeps a legacy beta.13 price beside explicit rates, tag pricing, discrete prices, component matching, durability, a built in override, and a disabled definition. Its README calls out the fictitious mod and tag IDs used for illustration. Custom Duel Kits example packs/custom duel kits/ adds an enchanted duel examples:champion kit with temporary equipment for the player and villager. See Duel Kits Duel Kits.md for every field. Persistent Cinematic Gate Ambush example packs/cinematic gate ambush/ is the complete beta.13 scene orchestration example: two named villagers, player/party ownership, a recorded choice branch, movement, dialogue, a persisted wait, controlled encounter scaling and cleanup, quest completion/failure, and provider unload recovery. Repeatable scene run identity example packs/repeatable scene run id/ is a deliberately small repeatable quest. It launches the same scene operation twice in one run (one instance), then demonstrates that a later legitimate run and an unrelated player's run receive different QUEST INSTANCE owners. The repo already includes a full starter datapack you can copy from: This is the best source of beta.12 dialogue authoring examples. The beta.13 scene examples above cover the newer quest runtime surface. What Is In The Template Area Example path Dialogue option example packs/dialogue folder template/data/villagerretaliation/dialogue/en us/example template/options/00 greeting.json Dialogue line example packs/dialogue folder template/data/villagerretaliation/dialogue/en us/example template/lines/00 greeting.json Keyed message example packs/dialogue folder template/data/villagerretaliation/dialogue/en us/example template/messages/00 example.json Forced dialogue example packs/dialogue folder template/data/villagerretaliation/forced dialogue/example template/00 container theft.json Notification example packs/dialogue folder template/data/villagerretaliation/notifications/en us/example template/00 ambient.json Gifts example packs/dialogue folder template/data/villagerretaliation/gifts/example template/00 gifts.json Pacification example packs/dialogue folder template/data/villagerretaliation/pacification/example template/00 payments.json Profession loot example packs/dialogue folder template/data/villagerretaliation/profession loot/example template/00 loot.json Villager names example packs/dialogue folder template/data/villagerretaliation/villager names/example template names.json Smallest Copyable Pack If you want the lightest possible starting point, copy only: Example option: Example line: When To Copy The Full Template Copy the whole dialogue folder template when you want: one file per dialogue request a translator friendly folder layout examples for beta.12 conditions and filters a reference pack that covers almost every authoring surface Minimal pack.mcmeta Add your own villagerretaliation.pack version marker only if your workflow already expects it. The builder will add it automatically on export for supported versions.",
      "headings": [
        {
          "level": 2,
          "title": "Sell Prices"
        },
        {
          "level": 2,
          "title": "Custom Duel Kits"
        },
        {
          "level": 2,
          "title": "Persistent Cinematic Gate Ambush"
        },
        {
          "level": 2,
          "title": "Repeatable scene run identity"
        },
        {
          "level": 2,
          "title": "What Is In The Template"
        },
        {
          "level": 2,
          "title": "Smallest Copyable Pack"
        },
        {
          "level": 2,
          "title": "When To Copy The Full Template"
        },
        {
          "level": 2,
          "title": "Minimal `pack.mcmeta`"
        }
      ],
      "related": []
    },
    {
      "slug": "dialogue",
      "file": "Dialogue.md",
      "source": "wiki/Dialogue.md",
      "sourceKind": "wiki",
      "group": "Dialogue",
      "icon": "message-square-text",
      "title": "Dialogue",
      "description": "Talk options, replies, keyed messages, openings, closings, and pacify lines.",
      "markdown": "# Dialogue\n\nNormal dialogue powers the Talk menu, reusable reply pools, keyed text, openings, closings, and pacify lines.\n\n## Paths\n\nDialogue can live anywhere under:\n\n```text\ndata/<namespace>/dialogue/<locale>/\n```\n\nBeta.12 works best with typed folders:\n\n```text\ndata/my_pack/dialogue/en_us/global/options/00_rumor.json\ndata/my_pack/dialogue/en_us/global/lines/00_rumor.json\ndata/my_pack/dialogue/en_us/global/messages/00_shared.json\ndata/my_pack/dialogue/en_us/professions/farmer/openings/00_openings.json\ndata/my_pack/dialogue/en_us/professions/farmer/closings/00_closings.json\ndata/my_pack/dialogue/en_us/professions/farmer/pacify/00_pacify.json\n```\n\nBundle files still work, but folderized files are easier to translate and override.\n\n## Sections\n\n| Section | Use it for |\n| --- | --- |\n| `options` | Talk menu buttons shown to the player |\n| `lines` | Villager replies and response pools |\n| `messages` | Shared keyed text used by `text_key` or other systems |\n| `openings` | First line when a conversation starts |\n| `closings` | Final line when a conversation ends |\n| `pacify` | Spoken lines used while calming a hostile villager |\n\n## Example: Custom Talk Option\n\n```text\ndata/my_pack/dialogue/en_us/global/options/00_rumor.json\ndata/my_pack/dialogue/en_us/global/lines/00_rumor.json\n```\n\n```json\n{\n  \"id\": \"my_pack.option.ask_rumor\",\n  \"label\": \"Ask For A Rumor\",\n  \"request\": \"story\"\n}\n```\n\nDialogue option IDs and labels are limited to 128 characters so they can be sent safely to clients.\n\n```json\n{\n  \"id\": \"my_pack.line.rumor\",\n  \"request\": \"story\",\n  \"option\": \"my_pack.option.ask_rumor\",\n  \"text\": \"Roads carry stories faster than traders do.\",\n  \"weight\": 10\n}\n```\n\n## Example: Shared Message Text\n\nUse `messages` when several rules should share the same wording.\n\n```json\n{\n  \"id\": \"my_pack.message.rain_warning\",\n  \"key\": \"my_pack.message.rain_warning\",\n  \"lines\": [\n    \"Rain makes bad roads worse.\",\n    \"Rain keeps the careful indoors.\"\n  ]\n}\n```\n\nThen point a line at it:\n\n```json\n{\n  \"id\": \"my_pack.line.rain_warning\",\n  \"request\": \"question\",\n  \"text_key\": \"my_pack.message.rain_warning\"\n}\n```\n\n## Example: Opening\n\n```json\n{\n  \"id\": \"my_pack.opening.trusted_farmer\",\n  \"professions\": [\"minecraft:farmer\"],\n  \"reputation_levels\": [\"trusted\", \"respected\", \"revered\", \"royalty\"],\n  \"text\": \"Good to see you. The fields have been calmer lately.\"\n}\n```\n\n## Example: Closing\n\n```json\n{\n  \"id\": \"my_pack.closing.friendly\",\n  \"dispositions\": [\"friendly\", \"respectful\"],\n  \"text\": \"Travel safe.\"\n}\n```\n\nOpenings and closings can also react when the player displays an ominous banner: worn directly in the head slot, attached to a worn helmet, or applied to a shield in either hand. Use `requires_ominous_banner`, and optionally narrow the speaker's durable village allegiance with `village_allegiance` or `village_allegiances` (`known`, `unknown`, or `unaffiliated`):\n\n```json\n{\n  \"id\": \"my_pack.opening.ominous_resident\",\n  \"requires_ominous_banner\": true,\n  \"village_allegiance\": \"known\",\n  \"reputation_levels\": [\"suspicious\", \"hostile\", \"despised\"],\n  \"text\": \"Do not carry that raider mark through my village.\"\n}\n```\n\nTwo item tags make ominous-symbol recognition extensible:\n\n| Tag | Purpose |\n| --- | --- |\n| `villagerretaliation:ominous_banner_pattern_carriers` | Items whose `banner_patterns` component should be compared with the vanilla ominous design. It contains banners and shields by default. Add compatible modded shields or wearable banner items here. |\n| `villagerretaliation:ominous_banner_equivalents` | Items that always count as displaying the ominous symbol, without requiring banner-pattern components. Add custom insignia, uniforms, masks, or other modded gear here. |\n\nFor other gear-specific dialogue, openings and closings accept the same `player_item`, `player_items`, `player_item_tag`, `player_item_tags`, `player_item_slot`, and `player_item_slots` filters as normal dialogue lines. Slots can be `main_hand`, `off_hand`, `hands`, `armor`, `hotbar`, `inventory`, `accessories`, `equipment`, or `any`. The `accessories` slot reads equipped Curios or Accessories items when either optional mod is installed; `equipment` and `any` include them as well. Item-filtered conversation text can use placeholders such as `{player_item}`, `{player_item_id}`, and `{player_item_slot}`.\n\n```json\n{\n  \"id\": \"my_pack.opening.custom_uniform\",\n  \"player_item_tag\": \"my_pack:village_guard_uniforms\",\n  \"player_item_slots\": [\"armor\"],\n  \"text\": \"I recognize that {player_item}.\"\n}\n```\n\n## Example: Pacify Line\n\nThe items used for pacification live in [Pacification](Pacification.md). The spoken line lives in dialogue.\n\n```json\n{\n  \"id\": \"my_pack.pacify.neutral\",\n  \"professions\": [\"minecraft:toolsmith\"],\n  \"text\": \"Fine. Leave the payment and walk away slower next time.\"\n}\n```\n\n## Example: Profession-Specific Line\n\nFolder paths can communicate ownership clearly:\n\n```text\ndata/my_pack/dialogue/en_us/professions/cartographer/lines/00_map_talk.json\n```\n\n```json\n{\n  \"id\": \"my_pack.line.map_talk\",\n  \"request\": \"question\",\n  \"text\": \"A good map is just a promise written carefully.\"\n}\n```\n\nYou can still include explicit `professions` filters when needed, but the path itself is already a good organizational hint.\n\n## Good Defaults\n\n- Keep one idea per file when possible.\n- Use stable `id` values.\n- Prefer `conditions` once several helper flags are stacking up.\n\nFor request-specific patterns, see [Dialogue Requests](Dialogue-Requests.md).\n\nThe command `/vr admin dialogue explain <villager> <request> [option_id]` reports which request and filters caused a line to match or be rejected.\n",
      "text": "Dialogue Normal dialogue powers the Talk menu, reusable reply pools, keyed text, openings, closings, and pacify lines. Paths Dialogue can live anywhere under: Beta.12 works best with typed folders: Bundle files still work, but folderized files are easier to translate and override. Sections Section Use it for options Talk menu buttons shown to the player lines Villager replies and response pools messages Shared keyed text used by text key or other systems openings First line when a conversation starts closings Final line when a conversation ends pacify Spoken lines used while calming a hostile villager Example: Custom Talk Option Dialogue option IDs and labels are limited to 128 characters so they can be sent safely to clients. Example: Shared Message Text Use messages when several rules should share the same wording. Then point a line at it: Example: Opening Example: Closing Openings and closings can also react when the player displays an ominous banner: worn directly in the head slot, attached to a worn helmet, or applied to a shield in either hand. Use requires ominous banner, and optionally narrow the speaker's durable village allegiance with village allegiance or village allegiances (known, unknown, or unaffiliated): Two item tags make ominous symbol recognition extensible: Tag Purpose villagerretaliation:ominous banner pattern carriers Items whose banner patterns component should be compared with the vanilla ominous design. It contains banners and shields by default. Add compatible modded shields or wearable banner items here. villagerretaliation:ominous banner equivalents Items that always count as displaying the ominous symbol, without requiring banner pattern components. Add custom insignia, uniforms, masks, or other modded gear here. For other gear specific dialogue, openings and closings accept the same player item, player items, player item tag, player item tags, player item slot, and player item slots filters as normal dialogue lines. Slots can be main hand, off hand, hands, armor, hotbar, inventory, accessories, equipment, or any. The accessories slot reads equipped Curios or Accessories items when either optional mod is installed; equipment and any include them as well. Item filtered conversation text can use placeholders such as {player item}, {player item id}, and {player item slot}. Example: Pacify Line The items used for pacification live in Pacification Pacification.md. The spoken line lives in dialogue. Example: Profession Specific Line Folder paths can communicate ownership clearly: You can still include explicit professions filters when needed, but the path itself is already a good organizational hint. Good Defaults Keep one idea per file when possible. Use stable id values. Prefer conditions once several helper flags are stacking up. For request specific patterns, see Dialogue Requests Dialogue Requests.md. The command /vr admin dialogue explain [option id] reports which request and filters caused a line to match or be rejected.",
      "headings": [
        {
          "level": 2,
          "title": "Paths"
        },
        {
          "level": 2,
          "title": "Sections"
        },
        {
          "level": 2,
          "title": "Example: Custom Talk Option"
        },
        {
          "level": 2,
          "title": "Example: Shared Message Text"
        },
        {
          "level": 2,
          "title": "Example: Opening"
        },
        {
          "level": 2,
          "title": "Example: Closing"
        },
        {
          "level": 2,
          "title": "Example: Pacify Line"
        },
        {
          "level": 2,
          "title": "Example: Profession-Specific Line"
        },
        {
          "level": 2,
          "title": "Good Defaults"
        }
      ],
      "related": [
        "dialogue-requests",
        "dialogue-trees",
        "forced-dialogue",
        "localization"
      ]
    },
    {
      "slug": "dialogue-requests",
      "file": "Dialogue-Requests.md",
      "source": "wiki/Dialogue-Requests.md",
      "sourceKind": "wiki",
      "group": "Dialogue",
      "icon": "messages-square",
      "title": "Dialogue Requests",
      "description": "Request families and the option-to-line contract behind the Talk menu.",
      "markdown": "# Dialogue Requests\n\n`request` chooses which dialogue pool a line belongs to. It also tells the runtime what kind of conversation the player is asking for.\n\n## Basic Pattern\n\n```json\n{\n  \"id\": \"my_pack.option.ask_weather\",\n  \"label\": \"Ask About Weather\",\n  \"request\": \"question\"\n}\n```\n\n```json\n{\n  \"id\": \"my_pack.line.weather\",\n  \"request\": \"question\",\n  \"option\": \"my_pack.option.ask_weather\",\n  \"text\": \"Clear skies never last as long as confident people think.\"\n}\n```\n\n## Current Request Families\n\n| Request | Use it for | Example line |\n| --- | --- | --- |\n| `greeting` | hello-style replies | `\"Good to see you.\"` |\n| `question` | custom questions and general talk | `\"Work goes better when nobody panics.\"` |\n| `gift_preferences` | hints about liked and disliked gifts | `\"Useful gifts last longer than flashy ones.\"` |\n| `gift_advice_followup` | talking after advice was tested | `\"So, did the gift land well,\"` |\n| `map_report` | reporting a cartographer map discovery | `\"So the map was honest after all.\"` |\n| `story_hint_report` | reporting a rumor or discovery lead | `\"Then the rumor had a real road under it.\"` |\n| `combat_survival_report` | talking after a villager survives danger | `\"Still standing. That counts.\"` |\n| `gear_report` | talking after giving armor or weapons | `\"The gear helped more than you know.\"` |\n| `recruitment_followup` | talking after following the player | `\"I made it back. That matters.\"` |\n| `cured_recognition` | reacting to a cured villager | `\"{cured_villager} remembers you kindly.\"` |\n| `village_event_report` | recent village news and aftermath | `\"The village is still talking about last night.\"` |\n| `apology` | remembered harm and making amends | `\"Apologies are better when they change what comes next.\"` |\n| `village_defense_report` | thanking or reacting to defense | `\"You stood with us when it mattered.\"` |\n| `story` | rumors, lore, personal stories | `\"Roads keep secrets. Villages keep better ones.\"` |\n| `share_story` | lines tied to discovered structures or biomes | `\"{target_article}. Walk carefully if you go back.\"` |\n| `joke` | lighter one-liners | `\"If the wheat starts gossiping, we have bigger problems.\"` |\n| `insult` | hostile or sharp responses | `\"You bring trouble faster than traders bring wool.\"` |\n\n## Example: Report-Style Request\n\nBuilt-in report requests usually pair well with requirement flags or event memory.\n\n```json\n{\n  \"id\": \"my_pack.line.map_report\",\n  \"request\": \"map_report\",\n  \"text\": \"Good. A map earns its ink when someone returns from the place it promised.\"\n}\n```\n\n## Example: `share_story`\n\n`share_story` is where story discovery data and dialogue meet.\n\n```json\n{\n  \"id\": \"my_pack.line.haunted_keep\",\n  \"request\": \"share_story\",\n  \"option\": \"adult_share_story\",\n  \"story_structure\": \"examplemod:haunted_keep\",\n  \"text\": \"{target_article}. If you found it, leave before dark.\"\n}\n```\n\n## Example: Social Request\n\n```json\n{\n  \"id\": \"my_pack.line.apology\",\n  \"request\": \"apology\",\n  \"player_event_tags\": [\"player_attacked_villager\"],\n  \"text\": \"If you mean that apology, start by not making me need another one.\"\n}\n```\n\nPick the request that matches the player's intent first. Then add filters for profession, mood, reputation, memory, or conditions.\n",
      "text": "Dialogue Requests request chooses which dialogue pool a line belongs to. It also tells the runtime what kind of conversation the player is asking for. Basic Pattern Current Request Families Request Use it for Example line greeting hello style replies \"Good to see you.\" question custom questions and general talk \"Work goes better when nobody panics.\" gift preferences hints about liked and disliked gifts \"Useful gifts last longer than flashy ones.\" gift advice followup talking after advice was tested \"So, did the gift land well,\" map report reporting a cartographer map discovery \"So the map was honest after all.\" story hint report reporting a rumor or discovery lead \"Then the rumor had a real road under it.\" combat survival report talking after a villager survives danger \"Still standing. That counts.\" gear report talking after giving armor or weapons \"The gear helped more than you know.\" recruitment followup talking after following the player \"I made it back. That matters.\" cured recognition reacting to a cured villager \"{cured villager} remembers you kindly.\" village event report recent village news and aftermath \"The village is still talking about last night.\" apology remembered harm and making amends \"Apologies are better when they change what comes next.\" village defense report thanking or reacting to defense \"You stood with us when it mattered.\" story rumors, lore, personal stories \"Roads keep secrets. Villages keep better ones.\" share story lines tied to discovered structures or biomes \"{target article}. Walk carefully if you go back.\" joke lighter one liners \"If the wheat starts gossiping, we have bigger problems.\" insult hostile or sharp responses \"You bring trouble faster than traders bring wool.\" Example: Report Style Request Built in report requests usually pair well with requirement flags or event memory. Example: share story share story is where story discovery data and dialogue meet. Example: Social Request Pick the request that matches the player's intent first. Then add filters for profession, mood, reputation, memory, or conditions.",
      "headings": [
        {
          "level": 2,
          "title": "Basic Pattern"
        },
        {
          "level": 2,
          "title": "Current Request Families"
        },
        {
          "level": 2,
          "title": "Example: Report-Style Request"
        },
        {
          "level": 2,
          "title": "Example: `share_story`"
        },
        {
          "level": 2,
          "title": "Example: Social Request"
        }
      ],
      "related": [
        "dialogue",
        "event-tags",
        "notifications",
        "villager-event-triggers"
      ]
    },
    {
      "slug": "dialogue-trees",
      "file": "Dialogue-Trees.md",
      "source": "wiki/Dialogue-Trees.md",
      "sourceKind": "wiki",
      "group": "Dialogue",
      "icon": "git-fork",
      "title": "Dialogue Trees",
      "description": "Branching authored conversations inside and outside quest modules.",
      "markdown": "# Dialogue Trees\n\nDialogue trees are for authored scenes with branching responses. In current quest module v2 content, trees are optional extracted scenes. A simple playable quest can keep offer, reminder, turn-in, responses, transitions, and actions inside `data/<namespace>/quests/...json`.\n\nUse dialogue trees when a conversation should stay inside its own mini-flow, when it is too large for the quest module, when translators need a separate scene file, or when a datapack should replace only the scene without replacing quest objectives.\n\n## Paths\n\n```text\ndata/<namespace>/dialogue_trees/<locale>/<tree>.json\ndata/<namespace>/dialogue_trees/<locale>/quests/<module>/<quest>.json\n```\n\nUse the `quests/` path for quest-owned extracted scenes. The quest JSON decides whether the quest has a `questline`, `metadata.parent`, or only `group.*` tags.\n\n## Minimal Tree\n\n```json\n{\n  \"id\": \"my_pack:road_ledger\",\n  \"display\": {\n    \"title\": \"Road Ledger\",\n    \"description\": \"A small branching request scene.\"\n  },\n  \"entries\": [\n    {\n      \"id\": \"offer\",\n      \"label\": \"Road Ledger\",\n      \"request\": \"question\",\n      \"conditions\": [\n        { \"type\": \"quest\", \"state\": \"available\" }\n      ],\n      \"start\": \"offer\"\n    }\n  ],\n  \"nodes\": {\n    \"offer\": {\n      \"lines\": [\n        \"I lost a ledger on the old road. If you find it, bring it back.\"\n      ],\n      \"responses\": [\n        { \"id\": \"accept\", \"label\": \"I can look for it.\", \"next\": \"start_quest\" },\n        { \"id\": \"decline\", \"label\": \"Another time.\", \"next\": \"decline\" }\n      ]\n    },\n    \"start_quest\": {\n      \"actions\": [\n        {\n          \"type\": \"quest\",\n          \"action\": \"start\",\n          \"lines\": {\n            \"started\": [\n              \"Good. Search the road and return the ledger if you find it.\"\n            ]\n          }\n        }\n      ],\n      \"end\": true\n    },\n    \"decline\": {\n      \"text\": \"Then the road keeps its paper a little longer.\",\n      \"end\": true\n    }\n  }\n}\n```\n\n## Referencing A Tree From Quest Module V2\n\nIn the quest module:\n\n```json\n{\n  \"external_scenes\": [\"my_pack:quests/old_roads/road_ledger\"],\n  \"stages\": [\n    {\n      \"id\": \"start\",\n      \"objectives\": [],\n      \"dialogue\": {\n        \"offer\": {\n          \"label\": \"Road Ledger\",\n          \"request\": \"question\",\n          \"external_scene\": {\n            \"tree\": \"my_pack:quests/old_roads/road_ledger\",\n            \"entry\": \"offer\"\n          }\n        }\n      }\n    }\n  ]\n}\n```\n\nThe external tree still owns its `entries`, `nodes`, and branch actions. The quest module still owns provider filters, objectives, rewards, stage transitions, and save compatibility.\n\n## What Goes Where\n\n| Part | Purpose |\n| --- | --- |\n| `entries` | Talk menu buttons that open the tree |\n| `nodes` | Villager lines, player responses, and actions |\n| `responses` | Buttons shown inside the scene |\n| `actions` | State changes such as starting a quest, giving XP, writing facts, or forcing another scene |\n\n## Replacing Or Removing Built-Ins\n\nAt the top of a dialogue-tree file:\n\n```json\n{ \"replace\": true }\n```\n\nputs the dialogue-tree loader in replacement mode. VR skips built-in tree resources, then loads add-on tree files normally. In non-default locales, replacement mode also clears inherited fallback trees before applying that locale's add-on trees. Use this for total conversation overhauls.\n\n```json\n{\n  \"id\": \"villagerretaliation:bread_delivery\",\n  \"remove\": true\n}\n```\n\nremoves one tree by `id`. If `id` is omitted, the tree id is inferred from the file path. This remains useful for legacy v1 quest scenes and for v2 modules that deliberately reference extracted trees.\n\n## Example: Non-Quest Branch\n\nTrees are not just for quests. This is a simple lore branch:\n\n```json\n{\n  \"id\": \"my_pack:village_history\",\n  \"entries\": [\n    {\n      \"id\": \"history\",\n      \"label\": \"Ask About The Village\",\n      \"request\": \"story\",\n      \"start\": \"history\"\n    }\n  ],\n  \"nodes\": {\n    \"history\": {\n      \"lines\": [\n        \"This place was smaller once. Safer too, depending on who you ask.\"\n      ],\n      \"responses\": [\n        { \"id\": \"leave\", \"label\": \"Thanks.\", \"end\": true }\n      ]\n    }\n  }\n}\n```\n\n## Use Trees When\n\n- the player needs several responses in a row\n- a quest scene is too large or too shared to keep inline\n- you want scene-only overrides without replacing quest objective logic\n- you want actions attached directly to branches\n- the conversation should not fall back to the normal Talk menu until it ends\n\nUse [Quests](Quests.md) for one-file quest modules. Use normal [Dialogue](Dialogue.md) when one option and one reply are enough.\n",
      "text": "Dialogue Trees Dialogue trees are for authored scenes with branching responses. In current quest module v2 content, trees are optional extracted scenes. A simple playable quest can keep offer, reminder, turn in, responses, transitions, and actions inside data/ /quests/...json. Use dialogue trees when a conversation should stay inside its own mini flow, when it is too large for the quest module, when translators need a separate scene file, or when a datapack should replace only the scene without replacing quest objectives. Paths Use the quests/ path for quest owned extracted scenes. The quest JSON decides whether the quest has a questline, metadata.parent, or only group. tags. Minimal Tree Referencing A Tree From Quest Module V2 In the quest module: The external tree still owns its entries, nodes, and branch actions. The quest module still owns provider filters, objectives, rewards, stage transitions, and save compatibility. What Goes Where Part Purpose entries Talk menu buttons that open the tree nodes Villager lines, player responses, and actions responses Buttons shown inside the scene actions State changes such as starting a quest, giving XP, writing facts, or forcing another scene Replacing Or Removing Built Ins At the top of a dialogue tree file: puts the dialogue tree loader in replacement mode. VR skips built in tree resources, then loads add on tree files normally. In non default locales, replacement mode also clears inherited fallback trees before applying that locale's add on trees. Use this for total conversation overhauls. removes one tree by id. If id is omitted, the tree id is inferred from the file path. This remains useful for legacy v1 quest scenes and for v2 modules that deliberately reference extracted trees. Example: Non Quest Branch Trees are not just for quests. This is a simple lore branch: Use Trees When the player needs several responses in a row a quest scene is too large or too shared to keep inline you want scene only overrides without replacing quest objective logic you want actions attached directly to branches the conversation should not fall back to the normal Talk menu until it ends Use Quests Quests.md for one file quest modules. Use normal Dialogue Dialogue.md when one option and one reply are enough.",
      "headings": [
        {
          "level": 2,
          "title": "Paths"
        },
        {
          "level": 2,
          "title": "Minimal Tree"
        },
        {
          "level": 2,
          "title": "Referencing A Tree From Quest Module V2"
        },
        {
          "level": 2,
          "title": "What Goes Where"
        },
        {
          "level": 2,
          "title": "Replacing Or Removing Built-Ins"
        },
        {
          "level": 2,
          "title": "Example: Non-Quest Branch"
        },
        {
          "level": 2,
          "title": "Use Trees When"
        }
      ],
      "related": [
        "dialogue-and-quests",
        "quests",
        "quest-scenes"
      ]
    },
    {
      "slug": "forced-dialogue",
      "file": "Forced-Dialogue.md",
      "source": "wiki/Forced-Dialogue.md",
      "sourceKind": "wiki",
      "group": "Dialogue",
      "icon": "message-square-warning",
      "title": "Forced Dialogue",
      "description": "Event-driven confrontations, payment choices, reactions, and chat barks.",
      "markdown": "# Forced Dialogue\n\nForced dialogue is for event-driven villager reactions that should interrupt the normal flow. Use it for crimes, confrontations, authored quest interruptions, and chat barks.\n\n## Path\n\n```text\ndata/<namespace>/forced_dialogue/<file>.json\n```\n\nFor larger packs, split forced dialogue by event so authors can find the rule they are changing:\n\n```text\ndata/my_pack/forced_dialogue/events/container_theft.json\ndata/my_pack/forced_dialogue/events/container_opened.json\ndata/my_pack/forced_dialogue/events/retaliation_started.json\ndata/my_pack/forced_dialogue/events/retaliation_disengaged.json\ndata/my_pack/forced_dialogue/quests/lost_civilization.json\n```\n\nThe built-in files follow this pattern under `forced_dialogue/events/`.\n\n## Output Modes\n\n| Mode | Use it for |\n| --- | --- |\n| `forced_dialogue` | Locked scenes with player response buttons |\n| `chat` | One-shot nearby villager speech without opening a conversation |\n\n## Example: Locked Theft Scene\n\n```json\n{\n  \"entries\": [\n    {\n      \"id\": \"my_pack.container_theft.warning\",\n      \"trigger\": \"container_theft\",\n      \"output\": {\n        \"mode\": \"forced_dialogue\"\n      },\n      \"line\": \"Hands off that {container}. I saw what you took.\",\n      \"witness_radius\": 10,\n      \"requires_line_of_sight\": true,\n      \"initiate_dialogue\": true,\n      \"options\": [\n        {\n          \"id\": \"apologize\",\n          \"label\": \"Apologize\",\n          \"response\": \"Then prove it next time before the village has to ask.\",\n          \"reputation\": 2,\n          \"end_conversation\": true\n        },\n        {\n          \"id\": \"talk_back\",\n          \"label\": \"Talk back\",\n          \"response\": \"Wrong answer.\",\n          \"reputation\": -6,\n          \"aggro\": true,\n          \"end_conversation\": true\n        }\n      ]\n    }\n  ]\n}\n```\n\n## Example: Payment Option\n\nForced-dialogue options can take items directly from the player.\n\n```json\n{\n  \"id\": \"offer_payment\",\n  \"label\": \"Offer payment\",\n  \"response\": \"Payment does not make it yours, but it can make things right.\",\n  \"take_items\": {\n    \"items\": [\"minecraft:emerald\"],\n    \"count\": 8,\n    \"destination\": \"villager_inventory\",\n    \"failure_response\": \"Do not offer emeralds you do not have.\"\n  },\n  \"end_conversation\": true\n}\n```\n\n`take_items` removes matching items from the player's inventory. Its default `destination` is `discard`, which is best for fees, bribes, and abstract payments. `take_stolen_items` or `return_stolen_items` returns the exact stolen stacks. Its default `destination` is `villager_inventory_then_source_container`.\n\nDestination values:\n\n| Value | Result |\n| --- | --- |\n| `discard` | Remove the items without placing them anywhere. |\n| `villager_inventory` | Put the items in the speaking villager's inventory. |\n| `villager_inventory_then_source_container` | Try villager inventory first, then the source container. |\n| `source_container` | Put the items back into the watched container. |\n| `drop_at_villager` | Drop leftovers at the villager. |\n| `drop_at_container` | Drop leftovers at the watched container position. |\n\nUse `overflow_destination` when the main destination might not fit. With `require_space: true`, the option fails if neither destination can accept the full stack. With `require_space: false`, the option can still succeed after partial placement.\n\n## Locale-Friendly Text\n\nForced dialogue can keep inline English as fallback text while using datapack message keys as the translation surface. The easiest form is `message_prefix`, which generates keys from the entry and option structure:\n\n```json\n{\n  \"id\": \"my_pack.container_theft.warning\",\n  \"message_prefix\": \"forced.my_pack.container_theft.warning\",\n  \"trigger\": \"container_theft\",\n  \"line\": \"Hands off that {container}. I saw what you took.\",\n  \"options\": [\n    {\n      \"id\": \"apologize\",\n      \"label\": \"Apologize\",\n      \"response\": \"Then prove it next time before the village has to ask.\"\n    }\n  ]\n}\n```\n\nThat example looks up these message keys first, then falls back to the inline text if a key is missing:\n\n| Text | Generated key |\n| --- | --- |\n| entry line | `forced.my_pack.container_theft.warning.line` |\n| option label | `forced.my_pack.container_theft.warning.option.apologize.label` |\n| option response | `forced.my_pack.container_theft.warning.option.apologize.response` |\n| leave label | `forced.my_pack.container_theft.warning.leave.label` |\n| payment success | `forced.my_pack.container_theft.warning.option.apologize.take_items.success` |\n| stolen-item return failure | `forced.my_pack.container_theft.warning.option.apologize.take_stolen_items.failure` |\n\nSupported key fields:\n\n| Place | Key fields |\n| --- | --- |\n| entry line | `line_key`, `line_keys`, `text_key`, `text_keys` |\n| option label | `label_key` |\n| option response | `response_key`, `response_keys` |\n| payment or stolen-item success | `success_response_key`, `success_response_keys` |\n| payment or stolen-item failure | `failure_response_key`, `failure_response_keys` |\n\nWhen a key is present, the keyed message is used first and the inline text is only a fallback. Explicit key fields win over `message_prefix`. Use explicit keys when several filtered options should share one label but use separate response prefixes.\n\n## Example: Chat Bark\n\n```json\n{\n  \"entries\": [\n    {\n      \"id\": \"my_pack.retaliation.chat\",\n      \"trigger\": \"retaliation_started\",\n      \"output\": {\n        \"mode\": \"chat\",\n        \"radius\": 18,\n        \"look_at_player\": true\n      },\n      \"lines\": [\n        \"You picked the wrong village.\",\n        \"Run while you still remember how.\"\n      ],\n      \"chance\": 0.5\n    }\n  ]\n}\n```\n\n`output.look_at_player: true` asks the speaking villager to face the triggering player for the chat bark. This is best-effort: active vanilla work, hired-role work, combat, panic, sleep, trading, conversations, and player movement orders keep control of the villager's attention.\n\n## When To Use Forced Dialogue Instead Of Normal Dialogue\n\nUse forced dialogue when:\n\n- the villager should react immediately to an event\n- the player must answer before returning to normal interaction\n- you need event-specific buttons such as apology, payment, or escalation\n- you want a reactive bark tied to a trigger instead of a Talk menu request\n\nRetaliation chat barks can also use `low_guts_pursuit_abandoned`, `low_guts_counter_completed`, `retaliation_target_escaped`, and `retaliation_search_expired`. These triggers communicate why a villager deliberately stopped fighting without opening a locked conversation.\n\nUse normal [Dialogue](Dialogue.md) when the player chooses to ask something on purpose.\n\n## Held Item Proximity\n\nUse `trigger: \"player_item_proximity\"` for lines that fire when a nearby player is holding, wearing, or carrying a matching item. Add `output.mode: \"chat\"` for a bark instead of a locked conversation.\n\n```json\n{\n  \"id\": \"my_pack.aimed_sword_warning\",\n  \"trigger\": \"player_item_proximity\",\n  \"output\": {\n    \"mode\": \"chat\",\n    \"look_at_player\": true\n  },\n  \"line\": \"Lower {held_item}, {player}.\",\n  \"witness_radius\": 8,\n  \"chance\": 1.0,\n  \"requires_player_aiming_at_witness\": true,\n  \"player_items\": [\"minecraft:diamond_sword\"],\n  \"player_item_slots\": [\"hands\"],\n  \"draw_weapon\": true,\n  \"draw_weapon_duration_seconds\": 10\n}\n```\n\n`draw_weapon: true` makes the matching villager visibly equip the best usable weapon they carry for 10 seconds by default. Set `draw_weapon_duration_ticks`, `draw_weapon_duration_seconds`, or `draw_weapon_duration_days` to change that window. Drawing a weapon does not assign a target or start retaliation.\n\n`requires_player_aiming_at_witness: true` requires the player's unobstructed server-side sight ray to hit this villager before another living entity. It can be combined with `player_items` and `player_item_slots` to author weapon-specific warnings.\n\n`requires_held_trade_item: true` makes the entry match only adult, non-nitwit villagers the player can currently trade with, and only when the player's main hand or off hand matches one of that villager's active trade cost items. It uses vanilla's `ShowTradesToPlayer` item check, extended to also consider the off hand, so counts and components do not need to match. Out-of-stock offers are ignored. You can also use `requires_trade_item` or `requires_matching_trade_item` as aliases.\n\nTrade-cost entries can use `{held_item}`, `{trade_cost}`, `{trade_cost_count}`, `{trade_result}`, `{trade_result_stack}`, and `{trade_offer_index}`. Trade-level filters use villager levels 1 through 5. `min_villager_trade_level` and `max_villager_trade_level` are accepted aliases for `min_trade_level` and `max_trade_level`.\n\n## Replacing Or Removing Built-Ins\n\nUse top-level `replace: true` when a pack wants to replace the built-in forced-dialogue set instead of adding to it:\n\n```json\n{ \"replace\": true }\n```\n\nWhen any forced-dialogue resource in the reload uses `replace: true`, VR skips its built-in forced-dialogue resources before add-on content is applied. This makes total conversion packs predictable even when the add-on namespace sorts before `villagerretaliation`.\n\nUse `remove: true` with an `id` to remove one definition:\n\n```json\n{\n  \"id\": \"player_aiming_sword_neutral\",\n  \"remove\": true\n}\n```\n\nInside an `entries` array, `remove: true` removes that entry. Without an explicit `id`, the fallback id is inferred from the file path and entry index.\n",
      "text": "Forced Dialogue Forced dialogue is for event driven villager reactions that should interrupt the normal flow. Use it for crimes, confrontations, authored quest interruptions, and chat barks. Path For larger packs, split forced dialogue by event so authors can find the rule they are changing: The built in files follow this pattern under forced dialogue/events/. Output Modes Mode Use it for forced dialogue Locked scenes with player response buttons chat One shot nearby villager speech without opening a conversation Example: Locked Theft Scene Example: Payment Option Forced dialogue options can take items directly from the player. take items removes matching items from the player's inventory. Its default destination is discard, which is best for fees, bribes, and abstract payments. take stolen items or return stolen items returns the exact stolen stacks. Its default destination is villager inventory then source container. Destination values: Value Result discard Remove the items without placing them anywhere. villager inventory Put the items in the speaking villager's inventory. villager inventory then source container Try villager inventory first, then the source container. source container Put the items back into the watched container. drop at villager Drop leftovers at the villager. drop at container Drop leftovers at the watched container position. Use overflow destination when the main destination might not fit. With require space: true, the option fails if neither destination can accept the full stack. With require space: false, the option can still succeed after partial placement. Locale Friendly Text Forced dialogue can keep inline English as fallback text while using datapack message keys as the translation surface. The easiest form is message prefix, which generates keys from the entry and option structure: That example looks up these message keys first, then falls back to the inline text if a key is missing: Text Generated key entry line forced.my pack.container theft.warning.line option label forced.my pack.container theft.warning.option.apologize.label option response forced.my pack.container theft.warning.option.apologize.response leave label forced.my pack.container theft.warning.leave.label payment success forced.my pack.container theft.warning.option.apologize.take items.success stolen item return failure forced.my pack.container theft.warning.option.apologize.take stolen items.failure Supported key fields: Place Key fields entry line line key, line keys, text key, text keys option label label key option response response key, response keys payment or stolen item success success response key, success response keys payment or stolen item failure failure response key, failure response keys When a key is present, the keyed message is used first and the inline text is only a fallback. Explicit key fields win over message prefix. Use explicit keys when several filtered options should share one label but use separate response prefixes. Example: Chat Bark output.look at player: true asks the speaking villager to face the triggering player for the chat bark. This is best effort: active vanilla work, hired role work, combat, panic, sleep, trading, conversations, and player movement orders keep control of the villager's attention. When To Use Forced Dialogue Instead Of Normal Dialogue Use forced dialogue when: the villager should react immediately to an event the player must answer before returning to normal interaction you need event specific buttons such as apology, payment, or escalation you want a reactive bark tied to a trigger instead of a Talk menu request Retaliation chat barks can also use low guts pursuit abandoned, low guts counter completed, retaliation target escaped, and retaliation search expired. These triggers communicate why a villager deliberately stopped fighting without opening a locked conversation. Use normal Dialogue Dialogue.md when the player chooses to ask something on purpose. Held Item Proximity Use trigger: \"player item proximity\" for lines that fire when a nearby player is holding, wearing, or carrying a matching item. Add output.mode: \"chat\" for a bark instead of a locked conversation. draw weapon: true makes the matching villager visibly equip the best usable weapon they carry for 10 seconds by default. Set draw weapon duration ticks, draw weapon duration seconds, or draw weapon duration days to change that window. Drawing a weapon does not assign a target or start retaliation. requires player aiming at witness: true requires the player's unobstructed server side sight ray to hit this villager before another living entity. It can be combined with player items and player item slots to author weapon specific warnings. requires held trade item: true makes the entry match only adult, non nitwit villagers the player can currently trade with, and only when the player's main hand or off hand matches one of that villager's active trade cost items. It uses vanilla's ShowTradesToPlayer item check, extended to also consider the off hand, so counts and components do not need to match. Out of stock offers are ignored. You can also use requires trade item or requires matching trade item as aliases. Trade cost entries can use {held item}, {trade cost}, {trade cost count}, {trade result}, {trade result stack}, and {trade offer index}. Trade level filters use villager levels 1 through 5. min villager trade level and max villager trade level are accepted aliases for min trade level and max trade level. Replacing Or Removing Built Ins Use top level replace: true when a pack wants to replace the built in forced dialogue set instead of adding to it: When any forced dialogue resource in the reload uses replace: true, VR skips its built in forced dialogue resources before add on content is applied. This makes total conversion packs predictable even when the add on namespace sorts before villagerretaliation. Use remove: true with an id to remove one definition: Inside an entries array, remove: true removes that entry. Without an explicit id, the fallback id is inferred from the file path and entry index.",
      "headings": [
        {
          "level": 2,
          "title": "Path"
        },
        {
          "level": 2,
          "title": "Output Modes"
        },
        {
          "level": 2,
          "title": "Example: Locked Theft Scene"
        },
        {
          "level": 2,
          "title": "Example: Payment Option"
        },
        {
          "level": 2,
          "title": "Locale-Friendly Text"
        },
        {
          "level": 2,
          "title": "Example: Chat Bark"
        },
        {
          "level": 2,
          "title": "When To Use Forced Dialogue Instead Of Normal Dialogue"
        },
        {
          "level": 2,
          "title": "Held Item Proximity"
        },
        {
          "level": 2,
          "title": "Replacing Or Removing Built-Ins"
        }
      ],
      "related": [
        "dialogue",
        "notification-triggers",
        "villager-event-triggers",
        "generated-containers"
      ]
    },
    {
      "slug": "notifications",
      "file": "Notifications.md",
      "source": "wiki/Notifications.md",
      "sourceKind": "wiki",
      "group": "Dialogue",
      "icon": "bell",
      "title": "Notifications",
      "description": "HUD notices and floating world text with locale-aware overrides.",
      "markdown": "# Notifications\n\nNotifications cover two related outputs:\n\n- HUD notices such as quest updates and gift results\n- floating world text above villagers, such as ambient murmurs or refusals\n\n## Path\n\nNotification files always live in the `villagerretaliation` namespace and use locale folders:\n\n```text\ndata/villagerretaliation/notifications/en_us/my_pack_notifications.json\ndata/villagerretaliation/notifications/fr_fr/my_pack_notifications.json\n```\n\n## Minimal File\n\n```json\n{\n  \"notifications\": [\n    {\n      \"id\": \"my_pack.quest.started\",\n      \"trigger\": \"quest.started\",\n      \"text\": \"Quest started: {quest}\",\n      \"kind\": \"quest\",\n      \"color\": \"#FFD166\"\n    }\n  ]\n}\n```\n\n## Common Uses\n\n### Ambient World Text\n\n```json\n{\n  \"id\": \"my_pack.ambient.trusted_farmer\",\n  \"trigger\": \"ambient.murmur\",\n  \"text\": \"Good harvest follows good neighbors\",\n  \"world_text_kind\": \"murmur\",\n  \"professions\": [\"minecraft:farmer\"],\n  \"reputation_levels\": [\"trusted\", \"respected\", \"revered\", \"royalty\"]\n}\n```\n\n### Quest HUD Notice\n\n```json\n{\n  \"id\": \"my_pack.quest.completed\",\n  \"trigger\": \"quest.completed\",\n  \"text\": \"{quest} complete.\",\n  \"kind\": \"quest\",\n  \"color\": \"#FFE29A\"\n}\n```\n\n### Trade Refusal Flavor\n\n```json\n{\n  \"id\": \"my_pack.trade.refused.hostile\",\n  \"trigger\": \"trade.refused\",\n  \"text\": \"Not today.\",\n  \"world_text_kind\": \"negative\",\n  \"reputation_levels\": [\"hostile\", \"despised\", \"feared\"]\n}\n```\n\n## Main Fields\n\n| Field | Use |\n| --- | --- |\n| `trigger` | What event causes the notice to be considered |\n| `text` or `lines` | The actual output |\n| `kind` | HUD icon family such as `quest` or `gift_liked` |\n| `world_text_kind` | Floating-text style such as `murmur`, `negative`, or `alert` |\n| `professions` | Villager profession filter |\n| `reputation_levels` | Trust-tier filter |\n| `weight` | Relative selection weight |\n| `chance` | Additional random gate from `0.0` to `1.0` |\n\n## Translation Rule\n\nNotification files behave like dialogue locale overlays:\n\n- `en_us` is the fallback\n- matching `id` values in another locale replace the fallback entry for that player\n\nUse the same `id` in both files when translating an existing notification.\n\n## Good Practice\n\n- Give every notification a stable `id`.\n- Use `text` for one line and `lines` when several equal variants share the same rule.\n- Keep GUI translations in a resource pack, not here.\n\nFor trigger selection, see [Notification Triggers](Notification-Triggers.md).\n",
      "text": "Notifications Notifications cover two related outputs: HUD notices such as quest updates and gift results floating world text above villagers, such as ambient murmurs or refusals Path Notification files always live in the villagerretaliation namespace and use locale folders: Minimal File Common Uses Ambient World Text Quest HUD Notice Trade Refusal Flavor Main Fields Field Use trigger What event causes the notice to be considered text or lines The actual output kind HUD icon family such as quest or gift liked world text kind Floating text style such as murmur, negative, or alert professions Villager profession filter reputation levels Trust tier filter weight Relative selection weight chance Additional random gate from 0.0 to 1.0 Translation Rule Notification files behave like dialogue locale overlays: en us is the fallback matching id values in another locale replace the fallback entry for that player Use the same id in both files when translating an existing notification. Good Practice Give every notification a stable id. Use text for one line and lines when several equal variants share the same rule. Keep GUI translations in a resource pack, not here. For trigger selection, see Notification Triggers Notification Triggers.md.",
      "headings": [
        {
          "level": 2,
          "title": "Path"
        },
        {
          "level": 2,
          "title": "Minimal File"
        },
        {
          "level": 2,
          "title": "Common Uses"
        },
        {
          "level": 3,
          "title": "Ambient World Text"
        },
        {
          "level": 3,
          "title": "Quest HUD Notice"
        },
        {
          "level": 3,
          "title": "Trade Refusal Flavor"
        },
        {
          "level": 2,
          "title": "Main Fields"
        },
        {
          "level": 2,
          "title": "Translation Rule"
        },
        {
          "level": 2,
          "title": "Good Practice"
        }
      ],
      "related": [
        "notification-triggers",
        "localization",
        "event-tags"
      ]
    },
    {
      "slug": "notification-triggers",
      "file": "Notification-Triggers.md",
      "source": "wiki/Notification-Triggers.md",
      "sourceKind": "wiki",
      "group": "Dialogue",
      "icon": "zap",
      "title": "Notification Triggers",
      "description": "The built-in notification triggers and when each family is emitted.",
      "markdown": "# Notification Triggers\n\n`trigger` decides when a notification entry is eligible to appear. The entry still has to pass its filters, `chance`, and weight.\n\n## High-Value Trigger Groups\n\n| Area | Common triggers |\n| --- | --- |\n| Quests | `quest.started`, `quest.updated`, `quest.location_reached`, `quest.completed`, `quest.abandoned`, `quest.expired` |\n| Gifts | `gift.liked`, `gift.neutral`, `gift.disliked`, `gift.received_item` |\n| Discoveries | `dialogue.map.found`, `dialogue.rumor.found` |\n| Recruiting | `recruitment.follow_start`, `recruitment.follow_stop`, `recruitment.hired`, `recruitment.fired`, `recruitment.follower_death` |\n| Reputation shifts | `reputation.tier.<tier>.improved`, `reputation.tier.<tier>.worsened` |\n| Ambient world text | `ambient.murmur` |\n| Trading | `trade.completed`, `trade.refused` |\n\n## Example: Quest Trigger\n\n```json\n{\n  \"id\": \"my_pack.quest.started\",\n  \"trigger\": \"quest.started\",\n  \"text\": \"Quest started: {quest}\",\n  \"kind\": \"quest\"\n}\n```\n\n## Example: Discovery Trigger\n\n```json\n{\n  \"id\": \"my_pack.rumor.found\",\n  \"trigger\": \"dialogue.rumor.found\",\n  \"text\": \"Found rumored place: {target}\",\n  \"kind\": \"map_discovery\",\n  \"color\": \"#55AAFF\"\n}\n```\n\n## Example: Ambient Trigger\n\n```json\n{\n  \"id\": \"my_pack.ambient.revered\",\n  \"trigger\": \"ambient.murmur\",\n  \"lines\": [\n    \"There they are\",\n    \"Good omen\"\n  ],\n  \"world_text_kind\": \"murmur\",\n  \"reputation_levels\": [\"revered\"]\n}\n```\n\n## Example: Reputation Trigger\n\n```json\n{\n  \"id\": \"my_pack.rep.trusted.improved\",\n  \"trigger\": \"reputation.tier.trusted.improved\",\n  \"text\": \"You feel yourself gaining {villager_possessive} trust.\",\n  \"color\": \"green\"\n}\n```\n\n## Choosing A Trigger\n\n- Use a `quest.*` trigger when the line is about state change in a quest.\n- Use `ambient.murmur` when the line is just floating flavor text.\n- Use a `gift.*` trigger when the player just gave or received an item.\n- Use `trade.refused` or `trade.completed` when the line should be tied to the trade UI.\n\nIf you already know what happened in code or through another system, choose the smallest matching trigger and then do the nuance with filters.\n",
      "text": "Notification Triggers trigger decides when a notification entry is eligible to appear. The entry still has to pass its filters, chance, and weight. High Value Trigger Groups Area Common triggers Quests quest.started, quest.updated, quest.location reached, quest.completed, quest.abandoned, quest.expired Gifts gift.liked, gift.neutral, gift.disliked, gift.received item Discoveries dialogue.map.found, dialogue.rumor.found Recruiting recruitment.follow start, recruitment.follow stop, recruitment.hired, recruitment.fired, recruitment.follower death Reputation shifts reputation.tier. .improved, reputation.tier. .worsened Ambient world text ambient.murmur Trading trade.completed, trade.refused Example: Quest Trigger Example: Discovery Trigger Example: Ambient Trigger Example: Reputation Trigger Choosing A Trigger Use a quest. trigger when the line is about state change in a quest. Use ambient.murmur when the line is just floating flavor text. Use a gift. trigger when the player just gave or received an item. Use trade.refused or trade.completed when the line should be tied to the trade UI. If you already know what happened in code or through another system, choose the smallest matching trigger and then do the nuance with filters.",
      "headings": [
        {
          "level": 2,
          "title": "High-Value Trigger Groups"
        },
        {
          "level": 2,
          "title": "Example: Quest Trigger"
        },
        {
          "level": 2,
          "title": "Example: Discovery Trigger"
        },
        {
          "level": 2,
          "title": "Example: Ambient Trigger"
        },
        {
          "level": 2,
          "title": "Example: Reputation Trigger"
        },
        {
          "level": 2,
          "title": "Choosing A Trigger"
        }
      ],
      "related": []
    },
    {
      "slug": "event-tags",
      "file": "Event-Tags.md",
      "source": "wiki/Event-Tags.md",
      "sourceKind": "wiki",
      "group": "Dialogue",
      "icon": "tags",
      "title": "Event Tags",
      "description": "Durable villager and player event memory used by dialogue filters.",
      "markdown": "# Event Tags\n\nEvent tags are short-lived village memories. Dialogue can react to them with `event_tags` and `player_event_tags`.\n\n## Which Field To Use\n\n- `event_tags`: the villager remembers something happened nearby or in the village.\n- `player_event_tags`: the current player is specifically tied to that memory.\n\nExample:\n\n```json\n{\n  \"id\": \"my_pack.line.raid_thanks\",\n  \"request\": \"village_defense_report\",\n  \"event_tags\": [\"raid\"],\n  \"player_event_tags\": [\"player_defended_raid\"],\n  \"text\": \"You stood with us when the banners came over the hill.\"\n}\n```\n\n## Common Tag Areas\n\n| Area | Tags |\n| --- | --- |\n| Family and village life | `baby_born` |\n| Child harm | `baby_villager_attacked` |\n| Weather and danger | `thunderstorm`, `sandstorm`, `snowstorm`, `village_fire`, `night_attack` |\n| Raids and combat | `raid`, `villager_attacked`, `villager_death`, `villager_retaliation_started`, `iron_golem_defeated_mob`, `golem_killed` |\n| Player crimes | `player_attacked_villager`, `player_killed_villager`, `player_container_theft` |\n| Player help | `player_defended_village`, `player_defended_raid`, `player_cured_villager` |\n| Social changes | `reputation_changed`, `player_gave_loved_gift`, `player_gave_liked_gift`, `player_gave_neutral_gift`, `player_gave_disliked_gift`, `player_gave_hated_gift` |\n\nThe parser also accepts `golem_created` and `nearby_hostile_mob`, but those are reserved-style values unless your pack or code writes them.\n\n## Example: Family Life\n\n```json\n{\n  \"id\": \"my_pack.line.new_baby\",\n  \"request\": \"question\",\n  \"event_tags\": [\"baby_born\"],\n  \"text\": \"There is a new little voice in the village today.\"\n}\n```\n\n## Example: Crime Memory\n\n```json\n{\n  \"id\": \"my_pack.line.theft_memory\",\n  \"request\": \"apology\",\n  \"player_event_tags\": [\"player_container_theft\"],\n  \"text\": \"Village stores are not souvenirs.\"\n}\n```\n\n## Example: Defense Memory\n\n```json\n{\n  \"id\": \"my_pack.line.raid_defense\",\n  \"request\": \"village_defense_report\",\n  \"player_event_tags\": [\"player_defended_raid\"],\n  \"text\": \"The village still talks about the way you fought that raid.\"\n}\n```\n\nEvent tags are strongest when paired with normal filters like reputation, disposition, profession, or conditions.\n",
      "text": "Event Tags Event tags are short lived village memories. Dialogue can react to them with event tags and player event tags. Which Field To Use event tags: the villager remembers something happened nearby or in the village. player event tags: the current player is specifically tied to that memory. Example: Common Tag Areas Area Tags Family and village life baby born Child harm baby villager attacked Weather and danger thunderstorm, sandstorm, snowstorm, village fire, night attack Raids and combat raid, villager attacked, villager death, villager retaliation started, iron golem defeated mob, golem killed Player crimes player attacked villager, player killed villager, player container theft Player help player defended village, player defended raid, player cured villager Social changes reputation changed, player gave loved gift, player gave liked gift, player gave neutral gift, player gave disliked gift, player gave hated gift The parser also accepts golem created and nearby hostile mob, but those are reserved style values unless your pack or code writes them. Example: Family Life Example: Crime Memory Example: Defense Memory Event tags are strongest when paired with normal filters like reputation, disposition, profession, or conditions.",
      "headings": [
        {
          "level": 2,
          "title": "Which Field To Use"
        },
        {
          "level": 2,
          "title": "Common Tag Areas"
        },
        {
          "level": 2,
          "title": "Example: Family Life"
        },
        {
          "level": 2,
          "title": "Example: Crime Memory"
        },
        {
          "level": 2,
          "title": "Example: Defense Memory"
        }
      ],
      "related": []
    },
    {
      "slug": "villager-event-triggers",
      "file": "Villager-Event-Triggers.md",
      "source": "wiki/Villager-Event-Triggers.md",
      "sourceKind": "wiki",
      "group": "Dialogue",
      "icon": "radio",
      "title": "Villager Event Triggers",
      "description": "Run dialogue, notifications, or event-tag changes when a villager event matches authored filters.",
      "markdown": "# Villager Event Triggers\n\nVillager event triggers run actions when the village memory system records an event. Use them when a remembered event should start a quest action, show a notice, write another fact, or launch a persistent scene.\n\nThis system reacts to memory records. It does not add new Minecraft or NeoForge event listeners.\n\n## Path And ID\n\n```text\ndata/<namespace>/villager_events/<path>.json\n```\n\nThe file path becomes the trigger ID unless the JSON supplies `id`.\n\nFor example:\n\n```text\ndata/my_pack/villager_events/raid_thanks.json\n```\n\ncreates `my_pack:raid_thanks`.\n\n## Minimal Example\n\n```json\n{\n  \"memory\": \"villagerretaliation:player_defended_raid\",\n  \"scope\": \"player\",\n  \"cooldown\": 24000,\n  \"actions\": [\n    {\n      \"type\": \"notification\",\n      \"trigger\": \"quest.updated\",\n      \"text\": \"The village remembers what {player} did during the raid.\"\n    }\n  ]\n}\n```\n\nWhen the named memory is written, this trigger can notify the involved player. Its cooldown is tracked separately for each player.\n\n## Main Fields\n\n| Field | Default | Meaning |\n| --- | --- | --- |\n| `id` | File path ID | Stable namespaced trigger ID. |\n| `listen` | `memory_written` | Event family to listen to. Memory writes are the current supported family. |\n| `memory`, `tag`, or `tags` | Any memory | One or more memory tags. Use a narrow list for predictable behavior. |\n| `scope` | `village` | Where cooldown and one-time state are tracked. |\n| `conditions` | None | Normal dialogue conditions that must all pass. |\n| `actions` | None | One or more shared actions. At least one is required. |\n| `cooldown` | `0` | Delay in ticks before the same trigger can run again in the selected scope. |\n| `repeatable` | `true` | Set to `false` to run once in each selected scope. |\n| `once` or `run_once` | `false` | Compatibility aliases that invert `repeatable`. |\n\nAvailable scopes:\n\n| Scope | State is tracked for |\n| --- | --- |\n| `player` | The player attached to the memory event. |\n| `source_villager` | The villager that wrote the memory. `source` and `villager` are accepted aliases. |\n| `village` | The resolved village area. This is the default. |\n\n## Conditions Need Live Context\n\nConditions need both the source villager and player to be loaded. If either is missing, a trigger with conditions does not run.\n\nActions have different context needs. Notifications, tracker flashes, positive experience grants, and memory actions can still run when their required target is available. Provider-bound actions such as forced dialogue and some quest actions need the player and villager loaded.\n\nUse [JSON Reference](JSON-Reference.md#shared-actions) for the shared action fields.\n\n## Placeholders\n\nAction text can use:\n\n```text\n{memory}\n{memory_tag}\n{event}\n{event_x}\n{event_y}\n{event_z}\n{event_dimension}\n{villager}\n{villager_profession}\n{player}\n```\n\nVillager and player placeholders are available only when those entities can be resolved.\n\n## Quest Fact Example\n\n```json\n{\n  \"id\": \"my_pack:remember_first_defense\",\n  \"tags\": [\"villagerretaliation:player_defended_village\"],\n  \"scope\": \"player\",\n  \"repeatable\": false,\n  \"actions\": [\n    {\n      \"type\": \"set_tag\",\n      \"scope\": \"player\",\n      \"tag\": \"my_pack:first_village_defense\"\n    },\n    {\n      \"type\": \"notification\",\n      \"trigger\": \"quest.updated\",\n      \"text\": \"A village now knows you as a defender.\"\n    }\n  ]\n}\n```\n\n## Avoid Trigger Loops\n\nA memory action can write another memory, which can run another trigger. Keep the chain short and do not create two triggers that write each other's memory tags.\n",
      "text": "Villager Event Triggers Villager event triggers run actions when the village memory system records an event. Use them when a remembered event should start a quest action, show a notice, write another fact, or launch a persistent scene. This system reacts to memory records. It does not add new Minecraft or NeoForge event listeners. Path And ID The file path becomes the trigger ID unless the JSON supplies id. For example: creates my pack:raid thanks. Minimal Example When the named memory is written, this trigger can notify the involved player. Its cooldown is tracked separately for each player. Main Fields Field Default Meaning id File path ID Stable namespaced trigger ID. listen memory written Event family to listen to. Memory writes are the current supported family. memory, tag, or tags Any memory One or more memory tags. Use a narrow list for predictable behavior. scope village Where cooldown and one time state are tracked. conditions None Normal dialogue conditions that must all pass. actions None One or more shared actions. At least one is required. cooldown 0 Delay in ticks before the same trigger can run again in the selected scope. repeatable true Set to false to run once in each selected scope. once or run once false Compatibility aliases that invert repeatable. Available scopes: Scope State is tracked for player The player attached to the memory event. source villager The villager that wrote the memory. source and villager are accepted aliases. village The resolved village area. This is the default. Conditions Need Live Context Conditions need both the source villager and player to be loaded. If either is missing, a trigger with conditions does not run. Actions have different context needs. Notifications, tracker flashes, positive experience grants, and memory actions can still run when their required target is available. Provider bound actions such as forced dialogue and some quest actions need the player and villager loaded. Use JSON Reference JSON Reference.md shared actions for the shared action fields. Placeholders Action text can use: Villager and player placeholders are available only when those entities can be resolved. Quest Fact Example Avoid Trigger Loops A memory action can write another memory, which can run another trigger. Keep the chain short and do not create two triggers that write each other's memory tags.",
      "headings": [
        {
          "level": 2,
          "title": "Path And ID"
        },
        {
          "level": 2,
          "title": "Minimal Example"
        },
        {
          "level": 2,
          "title": "Main Fields"
        },
        {
          "level": 2,
          "title": "Conditions Need Live Context"
        },
        {
          "level": 2,
          "title": "Placeholders"
        },
        {
          "level": 2,
          "title": "Quest Fact Example"
        },
        {
          "level": 2,
          "title": "Avoid Trigger Loops"
        }
      ],
      "related": [
        "forced-dialogue",
        "notifications",
        "event-tags"
      ]
    },
    {
      "slug": "localization",
      "file": "Localization.md",
      "source": "wiki/Localization.md",
      "sourceKind": "wiki",
      "group": "Dialogue",
      "icon": "languages",
      "title": "Localization",
      "description": "Datapack locale overlays, message keys, GUI translations, and resource-pack language files.",
      "markdown": "# Localization\n\nVillager Retaliation uses two different localization layers.\n\n## 1. Datapack Locale Files\n\nUse datapack locale folders for authored speech and notification text:\n\n```text\ndata/my_pack/dialogue/en_us/global/messages/00_weather.json\ndata/my_pack/dialogue/fr_fr/global/messages/00_weather.json\ndata/villagerretaliation/notifications/en_us/my_pack_notifications.json\ndata/villagerretaliation/notifications/fr_fr/my_pack_notifications.json\n```\n\nExample translated message:\n\n```json\n{\n  \"id\": \"my_pack.message.weather\",\n  \"key\": \"my_pack.message.weather\",\n  \"text\": \"Rain keeps the fields honest.\"\n}\n```\n\n```json\n{\n  \"id\": \"my_pack.message.weather\",\n  \"key\": \"my_pack.message.weather\",\n  \"text\": \"La pluie garde les champs honnetes.\"\n}\n```\n\nUse the same `id` so the locale-specific entry replaces the fallback.\n\n## Item Counts In Dialogue\n\nGenerated item phrases such as `two emeralds` are controlled by locale-scoped datapack files:\n\n```text\ndata/<namespace>/item_text/en_us/items.json\ndata/<namespace>/item_text/fr_fr/items.json\n```\n\nThe built-in English definition uses numeric counts, but a locale can define any count categories it needs. Forms are tested in order and the final form is the fallback:\n\n```json\n{\n  \"forms\": [\n    { \"id\": \"one\", \"count_pattern\": \"1\", \"format\": \"{item}\" },\n    { \"id\": \"few\", \"count_pattern\": \"(,:2|3|4)\", \"format\": \"{count} {item}\" },\n    { \"id\": \"other\", \"format\": \"{count} {item}\" }\n  ],\n  \"currency\": {\n    \"one\": \"emerald\",\n    \"few\": \"emeralds\",\n    \"other\": \"emeralds\"\n  },\n  \"items\": {\n    \"minecraft:bread\": {\n      \"one\": \"bread\",\n      \"few\": \"loaves of bread\",\n      \"other\": \"loaves of bread\"\n    }\n  },\n  \"rules\": [\n    {\n      \"forms\": [\"few\", \"other\"],\n      \"pattern\": \"(,i)(.*[^aeiou])y$\",\n      \"replacement\": \"$1ies\"\n    }\n  ]\n}\n```\n\n- `count_pattern` is a regular expression matched against the integer count.\n- `format` supports `{count}` and `{item}`. A locale may omit `{count}` or spell out particular values with additional forms.\n- `currency` provides form-specific names for the configured currency item.\n- `items` provides form-specific names by item ID and is the preferred way to translate irregular or uncountable nouns.\n- `rules` are ordered regular-expression fallbacks. The first matching rule for the selected form wins.\n\nFiles merge in resource order. The requested locale inherits `en_us`, then overrides the sections and entries it supplies. Run `/reload` after changing these files.\n\n## 2. Resource-Pack Language Files\n\nUse a resource pack for GUI and generated labels:\n\n```text\nassets/villagerretaliation/lang/en_us.json\nassets/villagerretaliation/lang/fr_fr.json\n```\n\nThis is where buttons, profile labels, relationship rows, reputation text, mood names, and profession labels belong.\n\nExample:\n\n```json\n{\n  \"villagerretaliation.gui.root.talk\": \"Parler\",\n  \"villagerretaliation.gui.root.trade\": \"Commercer\",\n  \"villagerretaliation.reputation.value_format\": \"Reputation : %s\"\n}\n```\n\n## When To Use `text_key`\n\nIf several filtered dialogue rules should share one translated line, keep the logic in `lines` and the wording in `messages`:\n\n```json\n{\n  \"id\": \"my_pack.line.weather_rain\",\n  \"request\": \"question\",\n  \"text_key\": \"my_pack.message.weather\"\n}\n```\n\nThat lets translators touch one keyed message instead of copying every filter block.\n\n## Quests\n\nQuest JSON can keep its objectives, rewards, and rules in one file while moving player-facing words into locale message files:\n\n```json\n{\n  \"display\": {\n    \"title\": \"Bread Delivery\",\n    \"title_key\": \"quest.village_supply.bread_delivery.title\"\n  },\n  \"dialogue\": {\n    \"start\": [\"Bring me 16 bread.\"],\n    \"start_key\": \"quest.village_supply.bread_delivery.dialogue.start\"\n  }\n}\n```\n\nTranslate the matching keys under the locale:\n\n```text\ndata/my_pack/dialogue/fr_fr/quests/messages/00_quest_text.json\n```\n\nQuest key fields include `title_key`, `description_key`, tracker `text_key`, objective `text_key` / `complete_text_key`, dialogue `<stage>_key`, and expiration `text_key`.\n\n## Forced Dialogue\n\nForced dialogue supports the same message catalog:\n\n```json\n{\n  \"message_prefix\": \"forced.my_pack.theft.warning\",\n  \"line\": \"Hands off that {container}.\",\n  \"options\": [\n    {\n      \"label\": \"Apologize\",\n      \"response\": \"Then prove it.\"\n    }\n  ]\n}\n```\n\nWith `message_prefix`, VR looks for `.line`, `.option.<id>.label`, `.option.<id>.response`, `.option.<id>.take_items.success`, and matching failure or stolen-item keys. Explicit fields such as `line_key`, `label_key`, and `response_key` still work and override the generated keys.\n\nTranslate entry lines, option labels, option responses, and payment/stolen-item success or failure responses with message entries in `data/<namespace>/dialogue/<locale>/.../messages/*.json`.\n\n## Profession Names\n\nVanilla professions use Minecraft's own language keys:\n\n```json\n{\n  \"entity.minecraft.villager.farmer\": \"Farmer\"\n}\n```\n\nCustom professions follow the same pattern with namespace and dotted path:\n\n```json\n{\n  \"entity.minecraft.villager.my_mod.crystal_smith\": \"Crystal Smith\"\n}\n```\n\n## Rule Of Thumb\n\n- Dialogue, notifications, and authored lines: datapack locale folders\n- Quest titles, quest tracker text, quest lifecycle dialogue, and forced-dialogue labels/responses: datapack locale folders with message keys\n- UI labels, profile text, family rows, profession names: resource-pack language files\n",
      "text": "Localization Villager Retaliation uses two different localization layers. 1. Datapack Locale Files Use datapack locale folders for authored speech and notification text: Example translated message: Use the same id so the locale specific entry replaces the fallback. Item Counts In Dialogue Generated item phrases such as two emeralds are controlled by locale scoped datapack files: The built in English definition uses numeric counts, but a locale can define any count categories it needs. Forms are tested in order and the final form is the fallback: count pattern is a regular expression matched against the integer count. format supports {count} and {item}. A locale may omit {count} or spell out particular values with additional forms. currency provides form specific names for the configured currency item. items provides form specific names by item ID and is the preferred way to translate irregular or uncountable nouns. rules are ordered regular expression fallbacks. The first matching rule for the selected form wins. Files merge in resource order. The requested locale inherits en us, then overrides the sections and entries it supplies. Run /reload after changing these files. 2. Resource Pack Language Files Use a resource pack for GUI and generated labels: This is where buttons, profile labels, relationship rows, reputation text, mood names, and profession labels belong. Example: When To Use text key If several filtered dialogue rules should share one translated line, keep the logic in lines and the wording in messages: That lets translators touch one keyed message instead of copying every filter block. Quests Quest JSON can keep its objectives, rewards, and rules in one file while moving player facing words into locale message files: Translate the matching keys under the locale: Quest key fields include title key, description key, tracker text key, objective text key / complete text key, dialogue key, and expiration text key. Forced Dialogue Forced dialogue supports the same message catalog: With message prefix, VR looks for .line, .option. .label, .option. .response, .option. .take items.success, and matching failure or stolen item keys. Explicit fields such as line key, label key, and response key still work and override the generated keys. Translate entry lines, option labels, option responses, and payment/stolen item success or failure responses with message entries in data/ /dialogue/ /.../messages/ .json. Profession Names Vanilla professions use Minecraft's own language keys: Custom professions follow the same pattern with namespace and dotted path: Rule Of Thumb Dialogue, notifications, and authored lines: datapack locale folders Quest titles, quest tracker text, quest lifecycle dialogue, and forced dialogue labels/responses: datapack locale folders with message keys UI labels, profile text, family rows, profession names: resource pack language files",
      "headings": [
        {
          "level": 2,
          "title": "1. Datapack Locale Files"
        },
        {
          "level": 2,
          "title": "Item Counts In Dialogue"
        },
        {
          "level": 2,
          "title": "2. Resource-Pack Language Files"
        },
        {
          "level": 2,
          "title": "When To Use `text_key`"
        },
        {
          "level": 2,
          "title": "Quests"
        },
        {
          "level": 2,
          "title": "Forced Dialogue"
        },
        {
          "level": 2,
          "title": "Profession Names"
        },
        {
          "level": 2,
          "title": "Rule Of Thumb"
        }
      ],
      "related": []
    },
    {
      "slug": "quests",
      "file": "Quests.md",
      "source": "wiki/Quests.md",
      "sourceKind": "wiki",
      "group": "Quests & Scenes",
      "icon": "scroll-text",
      "title": "Quests",
      "description": "Quest module v2 providers, stages, objectives, transitions, branches, rewards, and diagnostics.",
      "markdown": "# Quests\n\nQuest module v2 is the preferred shape for new quest datapacks. A v2 module can define the provider, availability, lifecycle, stages, objectives, dialogue, responses, transitions, events, rewards, and tracker UI in one file.\n\nLegacy v1 quest files are still supported. Keep existing v1 packs working, but use v2 for new simple quests and for migrations where you want dialogue and quest state to live together.\n\nEach quest run receives a saved unique ID before its first actions run. A solo run belongs to one player. A party run uses one shared ID for the party. Persistent scenes use this saved ID so a reload resumes the same scene instead of starting a duplicate.\n\n## Paths\n\n```text\ndata/<namespace>/quests/<quest>.json\ndata/<namespace>/quests/<module>/<quest>.json\n```\n\nThe folder path is for organization and overrides. It does not create a questline by itself.\n\n## One-File Quest\n\nThis is a complete playable quest. It needs no external dialogue tree.\n\n```json\n{\n  \"schema\": \"villagerretaliation:quest/v2\",\n  \"id\": \"my_pack:bread_delivery\",\n  \"metadata\": {\n    \"title\": \"Bread Delivery\",\n    \"description\": \"Bring 16 bread to the village stores.\",\n    \"questline\": \"village_supply\",\n    \"tags\": [\"group.village_supply\"]\n  },\n  \"provider\": {\n    \"type\": \"villagerretaliation:villager\",\n    \"filters\": {\n      \"professions\": [\"minecraft:farmer\"],\n      \"min_villager_level\": \"novice\"\n    }\n  },\n  \"availability\": {\n    \"repeatable\": true,\n    \"completion_cooldown_days\": 1,\n    \"locked_to_villager\": true,\n    \"cross_villager_compatible\": false,\n    \"abandonment\": \"allow_repickup\",\n    \"consume_on_completion\": true\n  },\n  \"entry_stage\": \"gather\",\n  \"stages\": [\n    {\n      \"id\": \"gather\",\n      \"objectives\": [\n        {\n          \"id\": \"bring_bread\",\n          \"type\": \"item_check\",\n          \"item\": \"minecraft:bread\",\n          \"count\": 16,\n          \"tracker\": {\n            \"text\": \"Bring 16 bread back to the quest giver.\",\n            \"complete_text\": \"The bread is packed and ready.\",\n            \"show_progress\": true,\n            \"progress\": 0.75\n          }\n        }\n      ],\n      \"dialogue\": {\n        \"offer\": {\n          \"label\": \"Bread Delivery\",\n          \"request\": \"question\",\n          \"order\": -20,\n          \"show_for_babies\": false,\n          \"lines\": [\n            \"The bins are low. Sixteen bread would quiet a lot of worried stomachs.\"\n          ],\n          \"responses\": [\n            {\n              \"id\": \"accept\",\n              \"label\": \"I can help stock the larder.\",\n              \"scene\": \"start_quest\"\n            },\n            {\n              \"id\": \"decline\",\n              \"label\": \"Another time.\",\n              \"scene\": \"decline\"\n            }\n          ]\n        },\n        \"reminder\": {\n          \"label\": \"About Bread Delivery\",\n          \"request\": \"question\",\n          \"order\": -20,\n          \"show_for_babies\": false,\n          \"lines\": [\n            \"Bread Delivery is still open. The tracker has the count.\"\n          ],\n          \"responses\": [\n            {\n              \"id\": \"leave\",\n              \"label\": \"I'll keep looking.\",\n              \"scene\": \"end\"\n            }\n          ]\n        },\n        \"turn_in\": {\n          \"label\": \"About Bread Delivery\",\n          \"request\": \"question\",\n          \"order\": -20,\n          \"show_for_babies\": false,\n          \"lines\": [\n            \"If that pack smells like fresh bread, you may have saved me an argument.\"\n          ],\n          \"responses\": [\n            {\n              \"id\": \"complete\",\n              \"label\": \"Show what I brought.\",\n              \"scene\": \"complete_quest\"\n            },\n            {\n              \"id\": \"leave\",\n              \"label\": \"Not yet.\",\n              \"scene\": \"end\"\n            }\n          ]\n        }\n      },\n      \"scenes\": [\n        {\n          \"id\": \"start_quest\",\n          \"actions\": [\n            {\n              \"type\": \"quest\",\n              \"action\": \"start\",\n              \"lines\": {\n                \"started\": [\n                  \"Good. Bring the bread back when the count is ready.\"\n                ],\n                \"unavailable\": [\n                  \"The larder is not asking you for bread right now.\"\n                ]\n              }\n            }\n          ]\n        },\n        {\n          \"id\": \"complete_quest\",\n          \"actions\": [\n            {\n              \"type\": \"quest\",\n              \"action\": \"turn_in\",\n              \"lines\": {\n                \"completed\": [\n                  \"Good. A full shelf makes brave talk sound less hollow.\"\n                ],\n                \"missing_objectives\": [\n                  \"Bread Delivery is still short. The tracker has the exact count.\"\n                ],\n                \"unavailable\": [\n                  \"This bread delivery is not ready to close yet.\"\n                ]\n              }\n            }\n          ]\n        },\n        {\n          \"id\": \"decline\",\n          \"text\": \"Then I will keep counting crumbs and pretending it is planning.\"\n        },\n        {\n          \"id\": \"end\",\n          \"text\": \"Keep the bread close until you are ready.\"\n        }\n      ]\n    }\n  ],\n  \"rewards\": {\n    \"experience\": 60,\n    \"reputation\": 5,\n    \"gossip_reputation\": 2\n  },\n  \"ui\": {\n    \"tracker_text\": \"Bring 16 bread.\",\n    \"icon\": \"minecraft:bread\",\n    \"color\": \"#DCEBA6\"\n  }\n}\n```\n\nValidate standalone quest examples with:\n\n```text\nnode tools/validate-dialogue-data.mjs --quest path/to/quest.json\n```\n\n## Main Parts\n\n| Section | Purpose |\n| --- | --- |\n| `schema` | Must be `villagerretaliation:quest/v2` for v2 modules |\n| `id` | Stable quest resource id used by saves, commands, dialogue, and overrides |\n| `metadata` | Player-facing title, description, questline, tags, and legacy parent convenience |\n| `provider` | Who can offer or own the quest |\n| `availability` | Ordered prerequisites, repeat limits, abandonment, cooldowns, locking, and active gates |\n| `target` | Optional world target such as a structure search |\n| `entry_stage` | Authoritative first stage id. A later stage named `started` does not override it |\n| `stages` | Objectives, stage-local dialogue, responses, scenes, events, and UI |\n| `events` | Quest-level triggers that run while the quest exists |\n| `rewards` | XP, reputation, gossip, loot, memory events, or reward actions |\n| `ui` | Tracker text, icon, progress, placeholders, title and outline colors, and priority |\n| `external_scenes` | Optional external dialogue scene resources used by this module |\n\n## Quest Tag Taxonomy\n\n`metadata.tags` classifies quests for journal filters, quest-board pools, authoring tools, diagnostics, and content tests. Tags describe a quest; they do not replace authoritative fields such as `availability.repeatable`, `completion_scope`, `ui.hidden`, `ui.priority`, provider filters, or objective definitions.\n\n## Journal Presentation\n\nRoot `ui` fields are live journal data, not authoring-only hints:\n\n```json\n\"ui\": {\n  \"icon\": \"minecraft:filled_map\",\n  \"color\": \"#d4a35a\",\n  \"outline_color\": \"#201408\",\n  \"priority\": 25,\n  \"hidden\": false,\n  \"tracker_text\": \"Survey the old road.\"\n}\n```\n\n`priority` sorts otherwise equivalent journal entries, `color` tints their titles, `outline_color` opts into a ToucanLib 1px title outline, and `hidden` suppresses the quest from the journal and HUD. When omitted, title text defaults to unoutlined black. Both authored colors accept `#RRGGBB` or named Minecraft chat colors. The synchronized journal also carries `metadata.questline` and `metadata.tags`; press `/` in the journal to search titles, descriptions, objectives, questlines, and tags.\n\nActive quests with `availability.expiration.after_ticks` show a live remaining-time countdown. A located structure or location objective publishes its saved dimension and coordinates as a waypoint in both the selected journal entry and tracked HUD, including live distance while the player is in the same dimension. Completed journal-history entries show how long ago that run completed.\n\n## Quest Pools\n\nQuest pools turn quest tags or explicit quest IDs into bounded rotating offers. Put pool resources under `data/<namespace>/quest_pools/`. A pool only controls quests that match one of its selectors; quests not claimed by any pool keep their normal availability.\n\n```json\n{\n  \"schema\": \"villagerretaliation:quest_pool/v1\",\n  \"id\": \"my_pack:daily_commissions\",\n  \"scope\": \"village\",\n  \"refresh_days\": 1,\n  \"max_offers\": 3,\n  \"anti_repeat_rotations\": 2,\n  \"any_tags\": [\"pool.daily\", \"pool.commission\"],\n  \"exclude_tags\": [\"difficulty.extreme\"],\n  \"weights\": {\n    \"my_pack:urgent_repairs\": 4\n  }\n}\n```\n\nSelection is deterministic for the pool, scope key, and refresh epoch. `scope` accepts `player`, `village`, `provider`, or `world`. `quests`, `any_tags`, and `all_tags` select candidates; `exclude_quests` and `exclude_tags` remove candidates. Selection is weighted without replacement, and `anti_repeat_rotations` avoids recent selections when the pool has enough alternatives.\n\n## Generic Criterion Objectives\n\nUse a `criterion` objective when another gameplay system should advance a quest without adding a dedicated objective type. The namespaced criterion identifies the event, `match` contains exact string/number/boolean filters, and the normal `item`, entity selectors, location, dimension, and `count` fields add optional constraints.\n\n```json\n{\n  \"id\": \"forge_blades\",\n  \"type\": \"criterion\",\n  \"criterion\": \"villagerretaliation:crafted\",\n  \"item\": \"minecraft:iron_sword\",\n  \"match\": {\n    \"item\": \"minecraft:iron_sword\"\n  },\n  \"count\": 3\n}\n```\n\nBuilt-in criteria are `villagerretaliation:crafted`, `villagerretaliation:smelted`, `villagerretaliation:entity_interacted`, `villagerretaliation:damage_dealt`, and `villagerretaliation:dimension_changed`. Their match keys include `item`, `entity`, `damage_type`, `from`, and `to` as appropriate.\n\nOther mods can publish their own namespaced criteria through `QuestCriterionApi.trigger`. Supplying an item or entity lets datapacks reuse the standard item, entity type/tag, dimension, and location matching rules. Match data is an open string map, so integrations can evolve independently of this mod's quest schema.\n\nBuilt-in quests use these families:\n\n| Family | Values | Use |\n| --- | --- | --- |\n| `group.*` | Questline or umbrella grouping | Catalog organization and related-content searches |\n| `role.*` | `story`, `side`, `request`, `tutorial` | Journal sections, offer priority, and onboarding |\n| `activity.*` | `gather`, `deliver`, `combat`, `explore`, `build`, `trade`, `social`, `choice` | Multi-select gameplay filters and player preferences |\n| `destination.*` | `village`, `overworld`, `nether`, `end`, plus optional `remote` | Travel planning and dimension filters |\n| `tier.*` | `early`, `mid`, `late`, `endgame` | World-progression recommendations |\n| `difficulty.*` | `easy`, `normal`, `hard`, `extreme` | Danger and complexity guidance |\n| `commitment.*` | `quick`, `standard`, `expedition` | Expected time and travel commitment |\n| `theme.*` | `community`, `defense`, `mystery`, `craftsmanship`, `exploration` | Curated collections and thematic search |\n| `feature.*` | `branching`, `scene`, `encounter`, `recoverable` | Special presentation, tooling, and regression-test coverage |\n| `party.*` | `recommended`, `challenge` | Advisory group-play guidance without changing completion scope |\n| `pool.*` | `commission`, `daily`, `quest_board` | Eligibility for rotating or curated offer pools |\n\nUse the full prefix in JSON:\n\n```json\n\"tags\": [\n  \"group.village_supply\",\n  \"role.request\",\n  \"activity.deliver\",\n  \"activity.gather\",\n  \"destination.village\",\n  \"tier.early\",\n  \"difficulty.easy\",\n  \"commitment.quick\",\n  \"theme.community\",\n  \"pool.daily\",\n  \"pool.quest_board\"\n]\n```\n\nFor built-in content:\n\n- use exactly one `group.*`, `role.*`, `tier.*`, `difficulty.*`, and `commitment.*` tag\n- use exactly one primary destination; add `destination.remote` when travel is a material part of the quest\n- use one or more `activity.*` and `theme.*` tags when multiple descriptions apply\n- use `feature.*`, `party.*`, and `pool.*` only when the corresponding capability or curation rule applies\n- keep `role.tutorial`, `activity.social`, and `feature.recoverable` available for future content instead of applying them to unrelated quests\n\nDatapacks may add their own tags. Avoid redefining the documented built-in prefixes with different meanings, and keep mechanics in their dedicated fields so metadata cannot drift from runtime behavior.\n\n## Dialogue And Scenes\n\nStage `dialogue` slots normally use these names:\n\n| Slot | When it appears |\n| --- | --- |\n| `offer` | Quest can be started |\n| `reminder` | Quest is active but not ready |\n| `turn_in` | Objectives are complete |\n| `already_completed` | Player already completed a non-repeatable quest |\n| `unavailable` | Provider is known but availability gates fail |\n| `inactive` | Accepted quest is paused by active conditions |\n| `missing_target`, `missing_proof`, `locate_failed` | Target/proof helper states |\n\nInline scenes stay inside the quest module. Use `external` or `external_scene` only when the scene is large, shared, localized separately, or deliberately owned by another datapack resource.\n\n## Objective Composition And Bonuses\n\nStages use `all` completion by default. Set `completion.mode` to `any` when one predicate is enough, or `at_least` with `count` for k-of-n goals. Both objective references and condition predicates in `complete_when` participate in the count.\n\n```json\n{\n  \"complete_when\": [\"secure_gate\", \"rescue_smith\", \"recover_ledger\"],\n  \"completion\": { \"mode\": \"at_least\", \"count\": 2 },\n  \"bonuses\": [\n    {\n      \"id\": \"save_everyone\",\n      \"when\": [\"secure_gate\", \"rescue_smith\", \"recover_ledger\"],\n      \"mode\": \"all\",\n      \"actions\": [\n        { \"type\": \"experience\", \"amount\": 100 },\n        { \"type\": \"reputation\", \"amount\": 5 }\n      ]\n    }\n  ]\n}\n```\n\nBonus outcomes are one-shot per quest run and stage. Their claimed IDs are saved before actions run, so reconnects and reloads cannot duplicate rewards. A bonus supports the same `all`, `any`, and `at_least` modes and can use any normal action type.\n\n## Transition Rules\n\nKeep each response to one transition source. Pick one of:\n\n- direct response fields such as `next`, `stage`, `scene`, `complete`, `abandon`, or `fail`\n- a `transition` object with `stage`, `scene`, `response`, `complete`, `abandon`, or `fail`\n- a transition action such as `quest_transition`\n\nDo not combine direct transition fields with a transition action on the same response. Put side effects, such as `set_variable`, `notification`, or `reputation`, in `actions`, then put the single stage or scene move in `transition`.\n\n`fail` and `abandon` are different terminal outcomes. Failure records `FAILED`, runs only `lifecycle.on_fail`, stores a normalized failure code and time, and never grants completion rewards or increments completion/abandonment counts. Voluntary abandonment records `ABANDONED` (or `CONSUMED` when authored that way) and runs only `lifecycle.on_abandon`.\n\n## Prerequisites And Restart Rules\n\nUse `prerequisite_cooldown` (or its `_ticks`, `_seconds`, and `_days` forms) when a follow-up quest should wait after its prerequisites are completed. The cooldown is checked against every prerequisite, so the quest unlocks only after the most recently completed prerequisite has aged past the configured duration.\n\nPut every required quest in `availability.prerequisites`. The list is ordered for journal/debug presentation and every entry must be completed. `metadata.parent` remains a singular compatibility and organization field for older content.\n\nQuest module v2 also carries the full runtime rule set used by v1. Put active-state gates, expiration policy, and branch exclusion under `availability`:\n\n```json\n\"availability\": {\n  \"active\": {\n    \"conditions\": [{ \"type\": \"quest_fact\", \"tag\": \"my_pack:road_open\" }],\n    \"hide_when_unmet\": false,\n    \"pause_progress_when_unmet\": true\n  },\n  \"expiration\": {\n    \"after_days\": 3,\n    \"consume\": false,\n    \"allow_repickup\": true,\n    \"notify\": true\n  },\n  \"branch\": {\n    \"exclusive_group\": \"my_pack:route_choice\",\n    \"exclusive_on\": \"completed\",\n    \"blocks_on_completion\": [\"my_pack:other_route\"]\n  }\n}\n```\n\n`complete_when` may mix objective references with condition predicates. Objective UI also accepts `tracker_complete_text` and `tracker_complete_text_key`, and reward memory events retain `memory_scope`. The v1-to-v2 migration tool preserves these rules, stage tracker steps, and objective completion copy.\n\n## Definition Revisions And Live-Save Migration\n\nIncrement `metadata.revision` whenever an update changes stage or objective identity for a quest that players may already have active. The migration policy is applied once when a save first sees the newer revision and the result is persisted for audit/debug output.\n\n```json\n\"metadata\": {\n  \"revision\": 3,\n  \"migration\": {\n    \"active_policy\": \"keep\",\n    \"stage_aliases\": {\n      \"find_ruins\": \"survey_ruins\"\n    },\n    \"objective_aliases\": {\n      \"find_ruins.old_map\": \"survey_ruins.map_fragment\"\n    }\n  }\n}\n```\n\nPolicies are:\n\n- `keep`: remap aliases, retain progress, and safely move to the entry stage only if the saved stage no longer exists\n- `reset_stage`: remap aliases, then clear objective and bonus progress owned by the current stage\n- `restart`: keep the quest run and provider binding but clear objective, target, and bonus progress and return to the entry stage\n- `fail`: terminate the active quest with a revision-specific failure code and run the normal failure integrations\n\nLegacy saves without a stored revision adopt the current revision without destructive migration. A datapack rollback never re-applies an older revision over a newer persisted one. For v1 files, put `revision` and `migration` at the quest root; the v1-to-v2 migration tool moves them into `metadata`.\n\n```json\n{\n\"availability\": {\n  \"prerequisites\": [\n    \"my_pack:first_steps\",\n    \"my_pack:earn_their_trust\",\n    \"my_pack:find_the_map\"\n  ]\n}\n}\n```\n\nFailed quests can restart only when `repeatable` is true. `max_starts`, `max_completions`, provider locking, and completion scope still apply. Failure does not consume the quest by itself. Abandoned quests continue to follow `abandonment`, abandonment cooldown, and `consume_on_abandonment`.\n\n## Missing Providers And Rebind\n\nActive progress remains in the journal using the saved provider name, profession, location, and UUID when the live villager is gone. The journal's **Abandon quest** action works without the live provider. If abandonment or expiration has an authored lifecycle hook, the runtime persists that event instead of dropping its provider-bound actions. It replays the event once when the original provider is live again, or immediately after an operator supplies a compatible replacement. Turning in through another matching provider is allowed only with `cross_villager_compatible: true`. The runtime never chooses a nearby villager automatically.\n\nOperators can explicitly repair a missing binding with:\n\n```text\n/vr admin quest rebind <quest_id> <provider>\n```\n\nThe command refuses a rebind while the current provider is live, verifies the provider type and authored filters, retains the previous snapshot in save history, and reports the accepted or rejected audit result. A terminal quest can be rebound only while it has deferred lifecycle work. The rebind consumes that work after one dispatch without reopening the quest. The debug inspector lists pending lifecycle events alongside provider history.\n\n## Branch Example\n\nThis module records a route choice, moves to the chosen stage, and completes from either branch.\n\n```json\n{\n  \"schema\": \"villagerretaliation:quest/v2\",\n  \"id\": \"my_pack:choose_supply_route\",\n  \"metadata\": {\n    \"title\": \"Choose Supply Route\",\n    \"description\": \"Choose how the village will move supplies.\",\n    \"questline\": \"village_supply\",\n    \"tags\": [\"group.village_supply\"]\n  },\n  \"provider\": {\n    \"type\": \"villagerretaliation:villager\",\n    \"filters\": {\n      \"professions\": [\"minecraft:cartographer\"]\n    }\n  },\n  \"availability\": {\n    \"repeatable\": false,\n    \"max_completions\": 1,\n    \"locked_to_villager\": true\n  },\n  \"entry_stage\": \"choose_route\",\n  \"stages\": [\n    {\n      \"id\": \"choose_route\",\n      \"objectives\": [\n        {\n          \"id\": \"choose_route\",\n          \"type\": \"choice\",\n          \"choices\": [\"river\", \"ridge\"],\n          \"tracker\": {\n            \"text\": \"Choose a supply route.\",\n            \"complete_text\": \"Route chosen: {objective_choice_value}.\"\n          }\n        }\n      ],\n      \"dialogue\": {\n        \"offer\": {\n          \"label\": \"Choose Supply Route\",\n          \"request\": \"question\",\n          \"lines\": [\n            \"The village needs a safer supply route. River or ridge,\"\n          ],\n          \"responses\": [\n            {\n              \"id\": \"river\",\n              \"label\": \"Use the river.\",\n              \"actions\": [\n                {\n                  \"type\": \"set_variable\",\n                  \"scope\": \"quest\",\n                  \"key\": \"choice\",\n                  \"value\": \"river\"\n                }\n              ],\n              \"transition\": {\n                \"stage\": \"river_route\"\n              }\n            },\n            {\n              \"id\": \"ridge\",\n              \"label\": \"Use the ridge.\",\n              \"actions\": [\n                {\n                  \"type\": \"set_variable\",\n                  \"scope\": \"quest\",\n                  \"key\": \"choice\",\n                  \"value\": \"ridge\"\n                }\n              ],\n              \"transition\": {\n                \"stage\": \"ridge_route\"\n              }\n            }\n          ]\n        }\n      }\n    },\n    {\n      \"id\": \"river_route\",\n      \"objectives\": [],\n      \"dialogue\": {\n        \"turn_in\": {\n          \"label\": \"River Route\",\n          \"request\": \"question\",\n          \"lines\": [\"The river road will move quietly.\"],\n          \"responses\": [\n            {\n              \"id\": \"complete\",\n              \"label\": \"Mark the river route.\",\n              \"complete\": true\n            }\n          ]\n        }\n      }\n    },\n    {\n      \"id\": \"ridge_route\",\n      \"objectives\": [],\n      \"dialogue\": {\n        \"turn_in\": {\n          \"label\": \"Ridge Route\",\n          \"request\": \"question\",\n          \"lines\": [\"The ridge road will keep watch over the valley.\"],\n          \"responses\": [\n            {\n              \"id\": \"complete\",\n              \"label\": \"Mark the ridge route.\",\n              \"complete\": true\n            }\n          ]\n        }\n      }\n    }\n  ],\n  \"ui\": {\n    \"tracker_text\": \"Choose a route.\",\n    \"icon\": \"minecraft:map\"\n  }\n}\n```\n\n## Structure Target Example\n\nRoot `target` fields define a structure search, discovery radius, and proof item. Stages can combine a visit objective with a proof-item objective.\n\n```json\n{\n  \"schema\": \"villagerretaliation:quest/v2\",\n  \"id\": \"my_pack:trail_marker\",\n  \"metadata\": {\n    \"title\": \"Trail Marker\",\n    \"description\": \"Find nearby Trail Ruins and return with a brush.\",\n    \"tags\": [\"group.old_roads\"]\n  },\n  \"provider\": {\n    \"type\": \"villagerretaliation:villager\",\n    \"filters\": {\n      \"professions\": [\"minecraft:cartographer\", \"minecraft:mason\"]\n    }\n  },\n  \"availability\": {\n    \"repeatable\": false,\n    \"max_completions\": 1,\n    \"locked_to_villager\": true\n  },\n  \"target\": {\n    \"structure\": \"minecraft:trail_ruins\",\n    \"dimension\": \"minecraft:overworld\",\n    \"search_radius\": 192,\n    \"discovery_radius\": 96,\n    \"proof_item\": \"minecraft:brush\"\n  },\n  \"entry_stage\": \"survey\",\n  \"stages\": [\n    {\n      \"id\": \"survey\",\n      \"objectives\": [\n        {\n          \"id\": \"visit_ruins\",\n          \"type\": \"structure_visit\",\n          \"structure\": \"minecraft:trail_ruins\",\n          \"tracker\": {\n            \"text\": \"Find the Trail Ruins near {target_x}, {target_z}.\",\n            \"complete_text\": \"You found the old road.\"\n          }\n        },\n        {\n          \"id\": \"bring_brush\",\n          \"type\": \"item_check\",\n          \"item\": \"minecraft:brush\",\n          \"count\": 1,\n          \"tracker\": {\n            \"text\": \"Bring a brush back from the ruins.\",\n            \"complete_text\": \"The brush is ready.\"\n          }\n        }\n      ],\n      \"complete_when\": [\"visit_ruins\", \"bring_brush\"],\n      \"dialogue\": {\n        \"offer\": {\n          \"label\": \"Trail Marker\",\n          \"request\": \"question\",\n          \"lines\": [\"The old road left a mark under the dust.\"],\n          \"responses\": [\n            {\n              \"id\": \"accept\",\n              \"label\": \"Mark the ruins.\",\n              \"scene\": \"start_quest\"\n            }\n          ]\n        },\n        \"turn_in\": {\n          \"label\": \"Trail Marker\",\n          \"request\": \"question\",\n          \"lines\": [\"You found the mark and brought a brush.\"],\n          \"responses\": [\n            {\n              \"id\": \"complete\",\n              \"label\": \"Hand over the notes.\",\n              \"complete\": true\n            }\n          ]\n        }\n      },\n      \"scenes\": [\n        {\n          \"id\": \"start_quest\",\n          \"actions\": [\n            {\n              \"type\": \"quest\",\n              \"action\": \"start\",\n              \"lines\": {\n                \"started\": [\"The ruins should be near {target_x}, {target_z}.\"],\n                \"locate_failed\": [\"The old road is hiding from the map today.\"]\n              }\n            }\n          ]\n        }\n      ]\n    }\n  ],\n  \"rewards\": {\n    \"experience\": 80,\n    \"reputation\": 6\n  },\n  \"ui\": {\n    \"tracker_text\": \"Find the Trail Ruins.\",\n    \"icon\": \"minecraft:brush\"\n  }\n}\n```\n\n## Forced Or External Scene Example\n\nUse external scenes when another file owns a long conversation. Use `forced_dialogue` actions when the quest needs an event-driven locked scene. These actions need live player and provider context. If the quest giver is unloaded, the runtime records diagnostics and waits until it can safely run the live action.\n\n```json\n{\n  \"schema\": \"villagerretaliation:quest/v2\",\n  \"id\": \"my_pack:storm_warning\",\n  \"metadata\": {\n    \"title\": \"Storm Warning\",\n    \"description\": \"Ask a cleric about a storm omen.\",\n    \"questline\": \"lost_civilization\",\n    \"tags\": [\"group.lost_civilization\"]\n  },\n  \"provider\": {\n    \"type\": \"villagerretaliation:villager\",\n    \"filters\": {\n      \"professions\": [\"minecraft:cleric\"]\n    }\n  },\n  \"availability\": {\n    \"repeatable\": false,\n    \"max_completions\": 1,\n    \"locked_to_villager\": true\n  },\n  \"external_scenes\": [\"my_pack:quests/storm_warning\"],\n  \"entry_stage\": \"ask\",\n  \"stages\": [\n    {\n      \"id\": \"ask\",\n      \"objectives\": [\n        {\n          \"id\": \"hear_warning\",\n          \"type\": \"choice\",\n          \"choices\": [\"heard\"],\n          \"tracker\": {\n            \"text\": \"Hear the storm warning.\",\n            \"complete_text\": \"The warning is clear.\"\n          }\n        }\n      ],\n      \"dialogue\": {\n        \"offer\": {\n          \"label\": \"Storm Warning\",\n          \"request\": \"question\",\n          \"external_scene\": {\n            \"tree\": \"my_pack:quests/storm_warning\",\n            \"entry\": \"offer\"\n          }\n        },\n        \"turn_in\": {\n          \"label\": \"Storm Warning\",\n          \"request\": \"question\",\n          \"lines\": [\"The storm warning is clear now.\"],\n          \"responses\": [\n            {\n              \"id\": \"complete\",\n              \"label\": \"I understand the omen.\",\n              \"complete\": true\n            }\n          ]\n        }\n      }\n    }\n  ],\n  \"events\": [\n    {\n      \"id\": \"storm_reminder\",\n      \"event\": \"near_provider\",\n      \"radius\": 10,\n      \"cooldown_seconds\": 120,\n      \"conditions\": [\n        { \"type\": \"weather\", \"state\": \"thunder\" }\n      ],\n      \"actions\": [\n        {\n          \"type\": \"forced_dialogue\",\n          \"forced_dialogue\": \"my_pack.quest.storm_warning.reminder\"\n        }\n      ]\n    }\n  ],\n  \"ui\": {\n    \"tracker_text\": \"Hear the storm warning.\",\n    \"icon\": \"minecraft:lightning_rod\"\n  }\n}\n```\n\nThe external scene above can live in `data/my_pack/dialogue_trees/en_us/quests/storm_warning.json`:\n\n```json\n{\n  \"id\": \"my_pack:quests/storm_warning\",\n  \"metadata\": {\n    \"quest\": \"my_pack:storm_warning\",\n    \"questline\": \"lost_civilization\"\n  },\n  \"entries\": [\n    {\n      \"id\": \"offer\",\n      \"label\": \"Storm Warning\",\n      \"request\": \"question\",\n      \"start\": \"offer\"\n    }\n  ],\n  \"nodes\": {\n    \"offer\": {\n      \"lines\": [\n        \"Thunder is not the omen. The silence after it is.\"\n      ],\n      \"responses\": [\n        {\n          \"id\": \"heard\",\n          \"label\": \"I will listen for it.\",\n          \"actions\": [\n            {\n              \"type\": \"set_variable\",\n              \"scope\": \"quest\",\n              \"key\": \"choice\",\n              \"value\": \"heard\"\n            }\n          ],\n          \"end\": true\n        }\n      ]\n    }\n  }\n}\n```\n\nThe forced quest scene above can live in `data/my_pack/forced_dialogue/quests/lost_civilization/storm_warning.json`:\n\n```json\n{\n  \"metadata\": {\n    \"quest\": \"my_pack:storm_warning\",\n    \"questline\": \"lost_civilization\"\n  },\n  \"entries\": [\n    {\n      \"id\": \"my_pack.quest.storm_warning.reminder\",\n      \"trigger\": \"quest\",\n      \"output\": {\n        \"mode\": \"forced_dialogue\"\n      },\n      \"lines\": [\n        \"Storms make old warnings easier to hear. Stay close to shelter.\"\n      ],\n      \"requires_line_of_sight\": true,\n      \"force_camera_towards_villager\": true,\n      \"options\": [\n        {\n          \"id\": \"my_pack.quest.storm_warning.ok\",\n          \"label\": \"I understand.\",\n          \"response\": \"Then keep the warning near your feet.\",\n          \"end_conversation\": true\n        }\n      ]\n    }\n  ]\n}\n```\n\n## Localization\n\nInline text is a fallback. Use `*_key` fields when you want datapack-localized text:\n\n```json\n{\n  \"metadata\": {\n    \"title\": \"Bread Delivery\",\n    \"title_key\": \"quest.my_pack.bread_delivery.title\",\n    \"description\": \"Bring 16 bread.\",\n    \"description_key\": \"quest.my_pack.bread_delivery.description\"\n  },\n  \"ui\": {\n    \"tracker_text\": \"Bring 16 bread.\",\n    \"tracker_text_key\": \"quest.my_pack.bread_delivery.tracker\"\n  }\n}\n```\n\nDialogue slots and scenes also accept `text_key`, `label_key`, and keyed lines where the generated schema lists them. Put keyed text in normal dialogue message files under `data/<namespace>/dialogue/<locale>/.../messages/*.json`.\n\n## Capabilities And Live Context\n\nConditions and actions come from the generated quest registries. The datapack builder reads `tools/datapack-builder/quest-registry-metadata.json`. The Node validator and Java schema generator use the same runtime metadata.\n\nSome registry entries need live entities:\n\n- provider-live conditions, such as villager equipment or live mood checks, need the quest giver loaded\n- player-live actions, such as notifications, forced dialogue, loot, XP, and reputation changes, need a player context\n- provider-live actions, such as forced dialogue and gossip, need the issuing villager loaded\n\nPrefer saved-state conditions for active quest gates that must continue while the villager is unloaded. Use live-context actions from events only when the event is expected to run near the player and provider.\n\n## Diagnostics And Trace Commands\n\nAvailable diagnostic commands:\n\n```text\n/vr admin datapack diagnostics\n/vr admin quest providers [radius]\n/vr admin quest whyAvailable <quest_id> <provider>\n/vr admin quest whyHidden <quest_id> [provider]\n/vr admin quest inspect <quest_id>\n/vr admin quest rebind <quest_id> <provider>\n/vr admin quest objectives <quest_id>\n/vr admin quest trace on\n/vr admin quest trace show [limit]\n/vr admin quest trace capture <quest_id> <provider>\n/vr admin quest fireTrigger <quest_id> <event>\n/vr admin quest actions dryRun <quest_id> <trigger_id>\n```\n\nUse `inspect` for saved state, issuer context, target context, repeat rules, objective counters, current stage, and fact values. Use `trace` for indexed trigger dispatch, condition traces, action diagnostics, and bounded recent events.\n\n## Legacy V1 Compatibility\n\nV1 quest JSON remains supported when the file has no `schema: \"villagerretaliation:quest/v2\"`. V1 fields such as `display`, `offer`, top-level `objectives`, `rules`, `tracker`, `triggers`, and separate dialogue trees still load through the compatibility adapter.\n\nLegacy override rules still apply:\n\n- a higher-priority datapack can replace a built-in quest by writing the same quest id\n- a v2 module can replace a v1 quest with the same id\n- old dialogue tree resources under `data/<namespace>/dialogue_trees/<locale>/quests/...` still work\n- `remove` and `replace` on dialogue trees still remove or replace legacy/extracted scenes\n\nDo not delete v1 resources just because v2 exists. Migrate intentionally, validate the generated v2 file, and keep any external dialogue tree only when it is still needed.\n\n## Extraction Guidance\n\nStart with one v2 quest file. Extract only when the file becomes hard to maintain:\n\n```text\ndata/<namespace>/quests/<module>/<quest>.json\ndata/<namespace>/dialogue_trees/<locale>/quests/<module>/<quest>.json\ndata/<namespace>/dialogue/<locale>/quests/<module>/<quest>/messages/*.json\ndata/<namespace>/forced_dialogue/quests/<module>/<quest>.json\n```\n\nUse the quest module for quest state, stages, objective readiness, rewards, transitions, and short scenes. Use external dialogue trees for long authored branches or shared localization. Use forced dialogue only for event-driven locked scenes outside the normal Talk flow.\n",
      "text": "Quests Quest module v2 is the preferred shape for new quest datapacks. A v2 module can define the provider, availability, lifecycle, stages, objectives, dialogue, responses, transitions, events, rewards, and tracker UI in one file. Legacy v1 quest files are still supported. Keep existing v1 packs working, but use v2 for new simple quests and for migrations where you want dialogue and quest state to live together. Each quest run receives a saved unique ID before its first actions run. A solo run belongs to one player. A party run uses one shared ID for the party. Persistent scenes use this saved ID so a reload resumes the same scene instead of starting a duplicate. Paths The folder path is for organization and overrides. It does not create a questline by itself. One File Quest This is a complete playable quest. It needs no external dialogue tree. Validate standalone quest examples with: Main Parts Section Purpose schema Must be villagerretaliation:quest/v2 for v2 modules id Stable quest resource id used by saves, commands, dialogue, and overrides metadata Player facing title, description, questline, tags, and legacy parent convenience provider Who can offer or own the quest availability Ordered prerequisites, repeat limits, abandonment, cooldowns, locking, and active gates target Optional world target such as a structure search entry stage Authoritative first stage id. A later stage named started does not override it stages Objectives, stage local dialogue, responses, scenes, events, and UI events Quest level triggers that run while the quest exists rewards XP, reputation, gossip, loot, memory events, or reward actions ui Tracker text, icon, progress, placeholders, title and outline colors, and priority external scenes Optional external dialogue scene resources used by this module Quest Tag Taxonomy metadata.tags classifies quests for journal filters, quest board pools, authoring tools, diagnostics, and content tests. Tags describe a quest; they do not replace authoritative fields such as availability.repeatable, completion scope, ui.hidden, ui.priority, provider filters, or objective definitions. Journal Presentation Root ui fields are live journal data, not authoring only hints: priority sorts otherwise equivalent journal entries, color tints their titles, outline color opts into a ToucanLib 1px title outline, and hidden suppresses the quest from the journal and HUD. When omitted, title text defaults to unoutlined black. Both authored colors accept RRGGBB or named Minecraft chat colors. The synchronized journal also carries metadata.questline and metadata.tags; press / in the journal to search titles, descriptions, objectives, questlines, and tags. Active quests with availability.expiration.after ticks show a live remaining time countdown. A located structure or location objective publishes its saved dimension and coordinates as a waypoint in both the selected journal entry and tracked HUD, including live distance while the player is in the same dimension. Completed journal history entries show how long ago that run completed. Quest Pools Quest pools turn quest tags or explicit quest IDs into bounded rotating offers. Put pool resources under data/ /quest pools/. A pool only controls quests that match one of its selectors; quests not claimed by any pool keep their normal availability. Selection is deterministic for the pool, scope key, and refresh epoch. scope accepts player, village, provider, or world. quests, any tags, and all tags select candidates; exclude quests and exclude tags remove candidates. Selection is weighted without replacement, and anti repeat rotations avoids recent selections when the pool has enough alternatives. Generic Criterion Objectives Use a criterion objective when another gameplay system should advance a quest without adding a dedicated objective type. The namespaced criterion identifies the event, match contains exact string/number/boolean filters, and the normal item, entity selectors, location, dimension, and count fields add optional constraints. Built in criteria are villagerretaliation:crafted, villagerretaliation:smelted, villagerretaliation:entity interacted, villagerretaliation:damage dealt, and villagerretaliation:dimension changed. Their match keys include item, entity, damage type, from, and to as appropriate. Other mods can publish their own namespaced criteria through QuestCriterionApi.trigger. Supplying an item or entity lets datapacks reuse the standard item, entity type/tag, dimension, and location matching rules. Match data is an open string map, so integrations can evolve independently of this mod's quest schema. Built in quests use these families: Family Values Use group. Questline or umbrella grouping Catalog organization and related content searches role. story, side, request, tutorial Journal sections, offer priority, and onboarding activity. gather, deliver, combat, explore, build, trade, social, choice Multi select gameplay filters and player preferences destination. village, overworld, nether, end, plus optional remote Travel planning and dimension filters tier. early, mid, late, endgame World progression recommendations difficulty. easy, normal, hard, extreme Danger and complexity guidance commitment. quick, standard, expedition Expected time and travel commitment theme. community, defense, mystery, craftsmanship, exploration Curated collections and thematic search feature. branching, scene, encounter, recoverable Special presentation, tooling, and regression test coverage party. recommended, challenge Advisory group play guidance without changing completion scope pool. commission, daily, quest board Eligibility for rotating or curated offer pools Use the full prefix in JSON: For built in content: use exactly one group. , role. , tier. , difficulty. , and commitment. tag use exactly one primary destination; add destination.remote when travel is a material part of the quest use one or more activity. and theme. tags when multiple descriptions apply use feature. , party. , and pool. only when the corresponding capability or curation rule applies keep role.tutorial, activity.social, and feature.recoverable available for future content instead of applying them to unrelated quests Datapacks may add their own tags. Avoid redefining the documented built in prefixes with different meanings, and keep mechanics in their dedicated fields so metadata cannot drift from runtime behavior. Dialogue And Scenes Stage dialogue slots normally use these names: Slot When it appears offer Quest can be started reminder Quest is active but not ready turn in Objectives are complete already completed Player already completed a non repeatable quest unavailable Provider is known but availability gates fail inactive Accepted quest is paused by active conditions missing target, missing proof, locate failed Target/proof helper states Inline scenes stay inside the quest module. Use external or external scene only when the scene is large, shared, localized separately, or deliberately owned by another datapack resource. Objective Composition And Bonuses Stages use all completion by default. Set completion.mode to any when one predicate is enough, or at least with count for k of n goals. Both objective references and condition predicates in complete when participate in the count. Bonus outcomes are one shot per quest run and stage. Their claimed IDs are saved before actions run, so reconnects and reloads cannot duplicate rewards. A bonus supports the same all, any, and at least modes and can use any normal action type. Transition Rules Keep each response to one transition source. Pick one of: direct response fields such as next, stage, scene, complete, abandon, or fail a transition object with stage, scene, response, complete, abandon, or fail a transition action such as quest transition Do not combine direct transition fields with a transition action on the same response. Put side effects, such as set variable, notification, or reputation, in actions, then put the single stage or scene move in transition. fail and abandon are different terminal outcomes. Failure records FAILED, runs only lifecycle.on fail, stores a normalized failure code and time, and never grants completion rewards or increments completion/abandonment counts. Voluntary abandonment records ABANDONED (or CONSUMED when authored that way) and runs only lifecycle.on abandon. Prerequisites And Restart Rules Use prerequisite cooldown (or its ticks, seconds, and days forms) when a follow up quest should wait after its prerequisites are completed. The cooldown is checked against every prerequisite, so the quest unlocks only after the most recently completed prerequisite has aged past the configured duration. Put every required quest in availability.prerequisites. The list is ordered for journal/debug presentation and every entry must be completed. metadata.parent remains a singular compatibility and organization field for older content. Quest module v2 also carries the full runtime rule set used by v1. Put active state gates, expiration policy, and branch exclusion under availability: complete when may mix objective references with condition predicates. Objective UI also accepts tracker complete text and tracker complete text key, and reward memory events retain memory scope. The v1 to v2 migration tool preserves these rules, stage tracker steps, and objective completion copy. Definition Revisions And Live Save Migration Increment metadata.revision whenever an update changes stage or objective identity for a quest that players may already have active. The migration policy is applied once when a save first sees the newer revision and the result is persisted for audit/debug output. Policies are: keep: remap aliases, retain progress, and safely move to the entry stage only if the saved stage no longer exists reset stage: remap aliases, then clear objective and bonus progress owned by the current stage restart: keep the quest run and provider binding but clear objective, target, and bonus progress and return to the entry stage fail: terminate the active quest with a revision specific failure code and run the normal failure integrations Legacy saves without a stored revision adopt the current revision without destructive migration. A datapack rollback never re applies an older revision over a newer persisted one. For v1 files, put revision and migration at the quest root; the v1 to v2 migration tool moves them into metadata. Failed quests can restart only when repeatable is true. max starts, max completions, provider locking, and completion scope still apply. Failure does not consume the quest by itself. Abandoned quests continue to follow abandonment, abandonment cooldown, and consume on abandonment. Missing Providers And Rebind Active progress remains in the journal using the saved provider name, profession, location, and UUID when the live villager is gone. The journal's Abandon quest action works without the live provider. If abandonment or expiration has an authored lifecycle hook, the runtime persists that event instead of dropping its provider bound actions. It replays the event once when the original provider is live again, or immediately after an operator supplies a compatible replacement. Turning in through another matching provider is allowed only with cross villager compatible: true. The runtime never chooses a nearby villager automatically. Operators can explicitly repair a missing binding with: The command refuses a rebind while the current provider is live, verifies the provider type and authored filters, retains the previous snapshot in save history, and reports the accepted or rejected audit result. A terminal quest can be rebound only while it has deferred lifecycle work. The rebind consumes that work after one dispatch without reopening the quest. The debug inspector lists pending lifecycle events alongside provider history. Branch Example This module records a route choice, moves to the chosen stage, and completes from either branch. Structure Target Example Root target fields define a structure search, discovery radius, and proof item. Stages can combine a visit objective with a proof item objective. Forced Or External Scene Example Use external scenes when another file owns a long conversation. Use forced dialogue actions when the quest needs an event driven locked scene. These actions need live player and provider context. If the quest giver is unloaded, the runtime records diagnostics and waits until it can safely run the live action. The external scene above can live in data/my pack/dialogue trees/en us/quests/storm warning.json: The forced quest scene above can live in data/my pack/forced dialogue/quests/lost civilization/storm warning.json: Localization Inline text is a fallback. Use key fields when you want datapack localized text: Dialogue slots and scenes also accept text key, label key, and keyed lines where the generated schema lists them. Put keyed text in normal dialogue message files under data/ /dialogue/ /.../messages/ .json. Capabilities And Live Context Conditions and actions come from the generated quest registries. The datapack builder reads tools/datapack builder/quest registry metadata.json. The Node validator and Java schema generator use the same runtime metadata. Some registry entries need live entities: provider live conditions, such as villager equipment or live mood checks, need the quest giver loaded player live actions, such as notifications, forced dialogue, loot, XP, and reputation changes, need a player context provider live actions, such as forced dialogue and gossip, need the issuing villager loaded Prefer saved state conditions for active quest gates that must continue while the villager is unloaded. Use live context actions from events only when the event is expected to run near the player and provider. Diagnostics And Trace Commands Available diagnostic commands: Use inspect for saved state, issuer context, target context, repeat rules, objective counters, current stage, and fact values. Use trace for indexed trigger dispatch, condition traces, action diagnostics, and bounded recent events. Legacy V1 Compatibility V1 quest JSON remains supported when the file has no schema: \"villagerretaliation:quest/v2\". V1 fields such as display, offer, top level objectives, rules, tracker, triggers, and separate dialogue trees still load through the compatibility adapter. Legacy override rules still apply: a higher priority datapack can replace a built in quest by writing the same quest id a v2 module can replace a v1 quest with the same id old dialogue tree resources under data/ /dialogue trees/ /quests/... still work remove and replace on dialogue trees still remove or replace legacy/extracted scenes Do not delete v1 resources just because v2 exists. Migrate intentionally, validate the generated v2 file, and keep any external dialogue tree only when it is still needed. Extraction Guidance Start with one v2 quest file. Extract only when the file becomes hard to maintain: Use the quest module for quest state, stages, objective readiness, rewards, transitions, and short scenes. Use external dialogue trees for long authored branches or shared localization. Use forced dialogue only for event driven locked scenes outside the normal Talk flow.",
      "headings": [
        {
          "level": 2,
          "title": "Paths"
        },
        {
          "level": 2,
          "title": "One-File Quest"
        },
        {
          "level": 2,
          "title": "Main Parts"
        },
        {
          "level": 2,
          "title": "Quest Tag Taxonomy"
        },
        {
          "level": 2,
          "title": "Journal Presentation"
        },
        {
          "level": 2,
          "title": "Quest Pools"
        },
        {
          "level": 2,
          "title": "Generic Criterion Objectives"
        },
        {
          "level": 2,
          "title": "Dialogue And Scenes"
        },
        {
          "level": 2,
          "title": "Objective Composition And Bonuses"
        },
        {
          "level": 2,
          "title": "Transition Rules"
        },
        {
          "level": 2,
          "title": "Prerequisites And Restart Rules"
        },
        {
          "level": 2,
          "title": "Definition Revisions And Live-Save Migration"
        },
        {
          "level": 2,
          "title": "Missing Providers And Rebind"
        },
        {
          "level": 2,
          "title": "Branch Example"
        },
        {
          "level": 2,
          "title": "Structure Target Example"
        },
        {
          "level": 2,
          "title": "Forced Or External Scene Example"
        },
        {
          "level": 2,
          "title": "Localization"
        },
        {
          "level": 2,
          "title": "Capabilities And Live Context"
        },
        {
          "level": 2,
          "title": "Diagnostics And Trace Commands"
        },
        {
          "level": 2,
          "title": "Legacy V1 Compatibility"
        },
        {
          "level": 2,
          "title": "Extraction Guidance"
        }
      ],
      "related": [
        "first-quest",
        "dialogue-and-quests",
        "quest-scenes",
        "quest-scene-runtime"
      ]
    },
    {
      "slug": "dialogue-and-quests",
      "file": "Dialogue-And-Quests.md",
      "source": "wiki/Dialogue-And-Quests.md",
      "sourceKind": "wiki",
      "group": "Quests & Scenes",
      "icon": "workflow",
      "title": "Dialogue And Quests",
      "description": "Choose clean ownership boundaries between quests, dialogue, trees, and forced scenes.",
      "markdown": "# Dialogue And Quests\n\nQuest module v2 makes one-file quests the default starting point. Keep the quest's stages, objectives, short offer/reminder/turn-in dialogue, responses, transitions, events, rewards, and tracker UI together in:\n\n```text\ndata/<namespace>/quests/<module>/<quest>.json\n```\n\nExtract extra files only when the module benefits from separate ownership, long authored scenes, shared localization, or event-driven forced dialogue.\n\n## Recommended Module Layout\n\n```text\ndata/<namespace>/quests/<module>/<quest>.json\ndata/<namespace>/dialogue_trees/<locale>/quests/<module>/<quest>.json\ndata/<namespace>/dialogue/<locale>/quests/<module>/<quest>/messages/*.json\ndata/<namespace>/forced_dialogue/quests/<module>/<quest>.json\n```\n\nOnly create the files the module actually needs. A simple playable v2 quest does not need a dialogue tree.\n\n## What Each File Does\n\n| File | Job |\n| --- | --- |\n| Quest module v2 | Provider, availability, lifecycle, stages, objectives, inline scenes, responses, transitions, events, rewards, and tracker UI |\n| Dialogue tree | Optional extracted branch scene referenced by `external` or `external_scene` |\n| Normal dialogue messages | Optional reusable localized text referenced by `text_key`, `label_key`, or metadata key fields |\n| Forced dialogue | Optional locked event scene triggered by a `forced_dialogue` action |\n\n## One-File Ownership\n\nFor most small quests, keep the whole playable flow in the quest module:\n\n```json\n{\n  \"schema\": \"villagerretaliation:quest/v2\",\n  \"id\": \"my_pack:road_ledger\",\n  \"metadata\": {\n    \"title\": \"Road Ledger\",\n    \"tags\": [\"group.old_roads\"]\n  },\n  \"provider\": {\n    \"type\": \"villagerretaliation:villager\",\n    \"filters\": {\n      \"professions\": [\"minecraft:cartographer\"]\n    }\n  },\n  \"entry_stage\": \"start\",\n  \"stages\": [\n    {\n      \"id\": \"start\",\n      \"objectives\": [],\n      \"dialogue\": {\n        \"offer\": {\n          \"label\": \"Road Ledger\",\n          \"request\": \"question\",\n          \"lines\": [\"Paper survives rain worse than stone does.\"],\n          \"responses\": [\n            {\n              \"id\": \"complete\",\n              \"label\": \"Mark that down.\",\n              \"complete\": true\n            }\n          ]\n        }\n      }\n    }\n  ]\n}\n```\n\n## Extracted Scene Ownership\n\nUse `external_scene` when a scene belongs in a dialogue tree:\n\n```json\n{\n  \"external_scenes\": [\"my_pack:quests/old_roads/road_ledger\"],\n  \"stages\": [\n    {\n      \"id\": \"start\",\n      \"objectives\": [],\n      \"dialogue\": {\n        \"offer\": {\n          \"label\": \"Road Ledger\",\n          \"request\": \"question\",\n          \"external_scene\": {\n            \"tree\": \"my_pack:quests/old_roads/road_ledger\",\n            \"entry\": \"offer\"\n          }\n        }\n      }\n    }\n  ]\n}\n```\n\nThen put the authored branch under:\n\n```text\ndata/my_pack/dialogue_trees/en_us/quests/old_roads/road_ledger.json\n```\n\nThis is useful when the tree is long, when translators should work in a separate file, or when another datapack should be able to replace only the scene without replacing the quest's objective logic.\n\n## Message Ownership\n\nUse normal dialogue message files for shared localized text:\n\n```json\n{\n  \"id\": \"my_pack.message.road_ledger_hint\",\n  \"key\": \"quest.my_pack.road_ledger.hint\",\n  \"text\": \"Paper survives rain worse than stone does.\"\n}\n```\n\nReference that key from quest module fields such as `metadata.title_key`, `metadata.description_key`, `ui.tracker_text_key`, dialogue `text_key`, and response `label_key`.\n\n## Forced Dialogue Ownership\n\nAdd forced dialogue only when the quest needs:\n\n- a locked event scene\n- an interruption during progress\n- a trigger-based confrontation\n- authored quest chatter outside the Talk menu\n\nTrigger it from a quest event:\n\n```json\n{\n  \"events\": [\n    {\n      \"id\": \"storm_reminder\",\n      \"event\": \"near_provider\",\n      \"radius\": 10,\n      \"cooldown_seconds\": 120,\n      \"actions\": [\n        {\n          \"type\": \"forced_dialogue\",\n          \"forced_dialogue\": \"my_pack.quest.road_ledger.storm_warning\"\n        }\n      ]\n    }\n  ]\n}\n```\n\n`forced_dialogue` is a live-context action. It needs the player and provider loaded. If the issuer is unloaded, the runtime records diagnostics instead of pretending the action succeeded.\n\n## Do Not Duplicate Gates\n\nDo not repeat quest offer requirements in several files.\n\nIf the quest module already says the quest is only for farmers, keep the dialogue scene focused on scene ownership:\n\n```json\n{\n  \"provider\": {\n    \"type\": \"villagerretaliation:villager\",\n    \"filters\": {\n      \"professions\": [\"minecraft:farmer\"]\n    }\n  }\n}\n```\n\nIn legacy dialogue trees, use the quest condition for state:\n\n```json\n{ \"type\": \"quest\", \"state\": \"available\" }\n```\n\nThe quest system resolves provider filters, parent locks, cooldowns, branch locks, and completion limits.\n\n## Legacy Layout\n\nV1 quests still use a quest JSON file plus a dialogue tree:\n\n```text\ndata/<namespace>/quests/<module>/<quest>.json\ndata/<namespace>/dialogue_trees/<locale>/quests/<module>/<quest>.json\n```\n\nThat layout remains supported. New v2 modules should start as one quest file and extract only when needed.\n",
      "text": "Dialogue And Quests Quest module v2 makes one file quests the default starting point. Keep the quest's stages, objectives, short offer/reminder/turn in dialogue, responses, transitions, events, rewards, and tracker UI together in: Extract extra files only when the module benefits from separate ownership, long authored scenes, shared localization, or event driven forced dialogue. Recommended Module Layout Only create the files the module actually needs. A simple playable v2 quest does not need a dialogue tree. What Each File Does File Job Quest module v2 Provider, availability, lifecycle, stages, objectives, inline scenes, responses, transitions, events, rewards, and tracker UI Dialogue tree Optional extracted branch scene referenced by external or external scene Normal dialogue messages Optional reusable localized text referenced by text key, label key, or metadata key fields Forced dialogue Optional locked event scene triggered by a forced dialogue action One File Ownership For most small quests, keep the whole playable flow in the quest module: Extracted Scene Ownership Use external scene when a scene belongs in a dialogue tree: Then put the authored branch under: This is useful when the tree is long, when translators should work in a separate file, or when another datapack should be able to replace only the scene without replacing the quest's objective logic. Message Ownership Use normal dialogue message files for shared localized text: Reference that key from quest module fields such as metadata.title key, metadata.description key, ui.tracker text key, dialogue text key, and response label key. Forced Dialogue Ownership Add forced dialogue only when the quest needs: a locked event scene an interruption during progress a trigger based confrontation authored quest chatter outside the Talk menu Trigger it from a quest event: forced dialogue is a live context action. It needs the player and provider loaded. If the issuer is unloaded, the runtime records diagnostics instead of pretending the action succeeded. Do Not Duplicate Gates Do not repeat quest offer requirements in several files. If the quest module already says the quest is only for farmers, keep the dialogue scene focused on scene ownership: In legacy dialogue trees, use the quest condition for state: The quest system resolves provider filters, parent locks, cooldowns, branch locks, and completion limits. Legacy Layout V1 quests still use a quest JSON file plus a dialogue tree: That layout remains supported. New v2 modules should start as one quest file and extract only when needed.",
      "headings": [
        {
          "level": 2,
          "title": "Recommended Module Layout"
        },
        {
          "level": 2,
          "title": "What Each File Does"
        },
        {
          "level": 2,
          "title": "One-File Ownership"
        },
        {
          "level": 2,
          "title": "Extracted Scene Ownership"
        },
        {
          "level": 2,
          "title": "Message Ownership"
        },
        {
          "level": 2,
          "title": "Forced Dialogue Ownership"
        },
        {
          "level": 2,
          "title": "Do Not Duplicate Gates"
        },
        {
          "level": 2,
          "title": "Legacy Layout"
        }
      ],
      "related": [
        "quests",
        "dialogue-trees",
        "forced-dialogue"
      ]
    },
    {
      "slug": "quest-scenes",
      "file": "Quest-Scenes.md",
      "source": "wiki/Quest-Scenes.md",
      "sourceKind": "wiki",
      "group": "Quests & Scenes",
      "icon": "clapperboard",
      "title": "Persistent Quest Scenes",
      "description": "Persistent actors, scene steps, encounters, recovery, scheduling, and extension hooks.",
      "markdown": "# Persistent Quest Scenes\n\nPersistent quest scenes coordinate long quest sequences that must resume after a save, reload, disconnect, or unloaded chunk. A scene can move named actors, wait, show dialogue, start a controlled fight, branch, update a quest, and clean up what it created.\n\nUse an inline quest scene for a short conversation or a few immediate actions. Use a persistent scene when the sequence waits on time, movement, players, actors, or combat.\n\nScene files use:\n\n```text\ndata/<namespace>/quest_scenes/<path>.json\n```\n\nEncounter files use:\n\n```text\ndata/<namespace>/quest_encounters/<path>.json\n```\n\n## Terms Used On This Page\n\n| Term | Plain meaning |\n| --- | --- |\n| Scene | A saved sequence of named steps. |\n| Step | One unit of work, such as waiting, moving an actor, or starting a fight. |\n| Actor | A named player, villager, entity, or position used by scene steps. |\n| Binding | The saved link between an actor name and its actual player, entity, or position. |\n| Encounter | A controlled group of enemies or allies created and tracked by a scene. |\n| Operation ID | An author-chosen name that prevents the same quest action from starting the same scene twice. |\n| Receipt | A saved record that an action was prepared or completed. It helps prevent duplicate rewards and messages after reload. |\n| Cleanup | Removing or restoring entities, encounters, and temporary blocks owned by the scene. |\n| Reconcile | Compare saved scene state with the loaded world and safely continue or report a problem. |\n\n## Smallest Complete Scene\n\nThis scene waits one second, then completes:\n\n```json\n{\n  \"schema\": \"villagerretaliation:scene/v1\",\n  \"id\": \"my_pack:short_pause\",\n  \"definition_version\": 1,\n  \"ownership\": \"quest_instance\",\n  \"entry_step\": \"pause\",\n  \"actors\": [],\n  \"steps\": [\n    {\n      \"id\": \"pause\",\n      \"type\": \"villagerretaliation:wait_ticks\",\n      \"data\": {\n        \"ticks\": 20\n      },\n      \"next\": \"done\"\n    },\n    {\n      \"id\": \"done\",\n      \"type\": \"villagerretaliation:scene_complete\"\n    }\n  ]\n}\n```\n\nStart it from a quest action:\n\n```json\n{\n  \"type\": \"start_scene\",\n  \"scene\": \"my_pack:short_pause\",\n  \"operation_id\": \"short_pause_v1\",\n  \"wait_for_result\": true\n}\n```\n\nKeep `operation_id` stable after release. Set `wait_for_result` to `true` when the quest must pause until the scene succeeds. Use `false` when the quest can continue immediately.\n\n## Starting a scene\n\nUse the registered safe quest action `start_scene`:\n\n```json\n{\n  \"type\": \"start_scene\",\n  \"scene\": \"example:gate_ambush\",\n  \"operation_id\": \"gate_ambush_v1\",\n  \"wait_for_result\": true\n}\n```\n\nThe runtime combines the operation ID with the owning player, party, quest run, or world. Repeating that combination returns the existing scene instead of creating another one. Scene work continues over server ticks, even when the calling quest waits for its result.\n\nFor solo quests, the run identity includes the player UUID, quest ID, and persisted start count. Shared party quests use one durable shared instance ID before stage actions or `STARTED` triggers run. `world` ownership is an explicit global singleton for the dimension/scene/quest/operation combination.\n\nWhen `wait_for_result` is true, the enclosing dialogue, stage, or lifecycle sequence is persisted and resumes only after scene success. Failure and cancellation remain distinct and do not run success actions. Scene `action_batch` cannot suspend and rejects a waiting scene launch during compilation.\n\n## Scene format\n\nEvery step ID is authored and persistence-critical. Keep IDs stable when editing a live pack.\n\n```json\n{\n  \"schema\": \"villagerretaliation:scene/v1\",\n  \"id\": \"example:gate_ambush\",\n  \"definition_version\": 1,\n  \"metadata\": { \"title\": \"Ambush at the Gate\" },\n  \"ownership\": \"party\",\n  \"entry_step\": \"move_captain\",\n  \"timeout_ticks\": 2400,\n  \"failure_policy\": \"block_for_repair\",\n  \"cancellation_policy\": \"cancel_scene\",\n  \"cleanup_policy\": \"all_owned\",\n  \"actors\": [],\n  \"steps\": []\n}\n```\n\nOwnership is `player`, `party`, `quest_instance`, or `world`. Failure/cancellation policies are `fail_scene`, `cancel_scene`, `block_for_repair`, and `run_failure_step`. Cleanup is `none`, `owned_entities`, `encounters`, `all_owned`, or `preserve_world`.\n\n`quest_transition` uses one typed target: `target_stage` (or `target: \"stage\"` plus `target_stage`), `target: \"complete\"`, `target: \"fail\"`, or `target: \"abandon\"`. Mixed targets are compile errors.\n\nOverall timeouts wake at the earlier of the current step wake and the absolute scene deadline, including while blocked. Terminal state remains visible until cleanup reaches `COMPLETE`. Missing definitions show a durable cleanup diagnostic and bounded retry time.\n\nThe compiler rejects missing references, duplicate actor or step IDs, missing capabilities, unknown types/templates, unreachable paths, invalid failure paths, and immediate unbounded cycles. Datapack reload compares the canonical definition hash and stable step IDs. Compatible edits continue. Incompatible edits leave the readable instance blocked for repair.\n\n## Actors and replacement\n\nAn actor declaration has an `alias`, registered `type`, required `capabilities`, `required`, `binding_source`, optional `binding`, replacement/missing/death policies, optional string `filters`, and optional `timeout_ticks`.\n\nBuilt-in actor types are:\n\n| Type | Typical capabilities |\n| --- | --- |\n| `villagerretaliation:player` | live entity, living, dialogue target |\n| `villagerretaliation:villager` | live entity, living, navigation, dialogue |\n| `villagerretaliation:living_entity` | live entity and living |\n| `villagerretaliation:hostile_encounter_group` | persistent encounter membership |\n| `villagerretaliation:position` | stable dimension and block position |\n\nBinding sources are `owner_player`, `party_member`, `quest_provider`, `uuid`, `marker`, `encounter`, `owned_spawn`, and `unbound`. Replacement policies are `fixed`, `operator_rebindable`, `compatible_replacement`, `respawn_if_owned`, and `optional`. Missing policies are `block`, `fail`, `skip`, and `wait_until_timeout`. Death policies are `fail`, `block`, `apply_missing_policy`, `respawn_if_owned`, and `continue_with_snapshot`.\n\nBindings persist UUID/target identity, source, last dimension and position, display snapshot, generation, live/snapshot state, and full replacement history. A fixed narrative actor is never proximity-replaced. Provider actors reuse quest-provider identity. A quest-provider rebind updates scene actors only when they explicitly use `compatible_replacement`, and appends both binding history and an audit entry. Use `/vr admin scene rebind <scene-uuid> <alias> <entity>` for an `operator_rebindable` repair.\n\n## Built-in steps\n\nEach step has `id`, `type`, optional `actors`, a `data` object, `next`, named `transitions`, and optional `failure_step`.\n\n| Step | Important data |\n| --- | --- |\n| `wait_ticks` | `ticks`. Persists an absolute wake time |\n| `wait_condition` | registered `conditions`, `timeout_ticks`, `poll_ticks` |\n| `move_actor` | `actor`, `target_actor` or `dimension`/`x`/`y`/`z`, `speed`, `arrival_distance`, `timeout_ticks`, `path_failure_policy`, explicit `allow_teleport` |\n| `face_actor` / `face_position` | source `actor` and target actor or position |\n| `dialogue` | `text`, speaker aliases in `actors`, `offline_policy` (`wait`, `fail`, or `skip`), `offline_poll_ticks`. One delivery receipt per participant |\n| `action_batch` | allowlisted `actions`, each with a stable `id`. Arbitrary commands are rejected |\n| `quest_transition` | safe quest action fields such as `target_stage`, completion, or failure |\n| `scene_branch` | ordered `branches` containing registered `conditions` and a transition name, plus `default_transition`. The chosen name is persisted |\n| `scene_complete` / `scene_fail` | durable terminal result |\n| `start_encounter` | `template`, anchor actor or coordinates, optional `offset_x`/`offset_y`/`offset_z`, optional `surface_anchor`, and persisted difficulty inputs |\n| `wait_encounter` / `cancel_encounter` / `cleanup_encounter` | `encounter_step` naming the start step. Omitted only when the scene owns exactly one encounter |\n\nMovement never force-loads a chunk. It waits for the actor/destination chunk, resumes navigation when available, and only teleports when both `path_failure_policy: \"teleport\"` and `allow_teleport: true` are authored.\n\nEncounter offsets are applied to an actor or coordinate anchor before that anchor is persisted. Set `surface_anchor: true` to replace the resulting Y coordinate with the motion-blocking surface height. This is useful for portable quests that need a fixed destination some distance from a dynamically located villager without hard-coding world coordinates.\n\n## Encounters\n\n```json\n{\n  \"schema\": \"villagerretaliation:encounter/v1\",\n  \"id\": \"example:gate_ambush\",\n  \"version\": 1,\n  \"controller\": \"villagerretaliation:controlled\",\n  \"members\": [\n    { \"entity\": \"minecraft:zombie\", \"count\": 3 },\n    {\n      \"entity\": \"minecraft:pillager\",\n      \"count\": 1,\n      \"custom_name\": \"Gate Captain\",\n      \"name_visible\": true,\n      \"glowing\": true,\n      \"persistent\": true,\n      \"health\": 40,\n      \"movement_speed\": 0.35,\n      \"attack_damage\": 8,\n      \"armor\": 10,\n      \"knockback_resistance\": 0.3,\n      \"boss\": true,\n      \"boss_bar_color\": \"purple\",\n      \"boss_bar_overlay\": \"notched_10\",\n      \"equipment\": {\n        \"mainhand\": {\n          \"item\": \"minecraft:crossbow\",\n          \"enchantments\": { \"minecraft:quick_charge\": 2 },\n          \"drop_chance\": 0.05\n        },\n        \"head\": { \"item\": \"minecraft:iron_helmet\" }\n      }\n    }\n  ],\n  \"spawn_mode\": \"group\",\n  \"spawn_points\": [\n    { \"id\": \"west_gate\", \"marker\": \"gate\", \"offset_x\": -8, \"weight\": 2 },\n    { \"id\": \"east_gate\", \"x\": 120, \"y\": 64, \"z\": -32, \"dimension\": \"minecraft:overworld\" }\n  ],\n  \"spawn_selection\": \"weighted\",\n  \"extra_per_player\": 1,\n  \"max_party_size\": 4,\n  \"placement_attempts\": 16,\n  \"spawn_radius\": 8,\n  \"area\": {\n    \"radius\": 32,\n    \"vertical_radius\": 16,\n    \"leave_behavior\": \"warn\",\n    \"leave_timeout_ticks\": 200,\n    \"mob_behavior\": \"return\"\n  },\n  \"respawn_policy\": \"missing_if_loaded\",\n  \"cleanup_policy\": \"remove_survivors\",\n  \"completion_condition\": \"all_defeated\"\n}\n```\n\nTemplates are allowlists, not command containers. Party-size and difficulty inputs are captured when the encounter starts. Owned entities carry durable encounter identity. Reload reconciles UUIDs and tags before bounded safe-placement attempts. Unrelated nearby mobs never count. Cleanup removes, retains, or releases surviving owned mobs according to the template and scene policy.\n\nThe optional `spawn_points` array supplies 1-64 named positions. Each point has a stable `id` and exactly one source: `actor`, `marker`, or complete `x`/`y`/`z` coordinates. `actor` and `marker` both name an actor alias declared by the scene. The two spellings let a template communicate whether it expects a live/snapshotted actor or a position actor bound from a marker. Actor and marker sources may add bounded `offset_x`, `offset_y`, and `offset_z` values. Explicit coordinates may set `dimension`. Otherwise they use the encounter anchor dimension. Every point must resolve into that same dimension. Missing actors, unknown or incompatible dimensions, incomplete coordinates, duplicate IDs, empty lists, and weights outside 1-10000 reject the start with a focused diagnostic.\n\n`spawn_selection` defaults to `random` and may be `random`, `sequential`, `weighted`, `nearest_player`, `farthest_player`, or `one_group_per_point`. Weighted selection uses each point's optional `weight` (default 1). Distance modes compare the points with online captured participants and wait when no suitable participant is online. Group selection assigns each member definition to a point in authored order. Party-scaling extras stay with the first group. Resolved absolute points, every member's selected point ID, and the sequential cursor are saved before placement, so reloads and unloaded chunks wait without rerolling. Recovery checks only the bounded anchor and authored-point neighborhoods and never force-loads chunks. Authored points cannot be combined with `spawn_mode: \"near_player\"`.\n\n### Mid-fight phases\n\n`phases` is an ordered array of up to 64 durable phase definitions. Every phase has a stable `id`, one `trigger`, and 1-32 allowlisted actions. Trigger shapes are:\n\n| Trigger type | Required field | Fires when |\n|---|---|---|\n| `wave_started` | `wave` | The named authored or shorthand wave has durably started. |\n| `wave_completed` | `wave` | Every enemy through the named wave has been defeated. |\n| `remaining_percentage` | `percentage` (0-100) | Remaining enemies are at or below the threshold. |\n| `elapsed_time` | `ticks` (1-1,728,000) | The durable time since first encounter reconciliation reaches the threshold. |\n| `elite_defeated` | `member` | The referenced stable member ID has been defeated. The member must have count 1, must not receive party-scaling copies, and must be named, enhanced, or designated as a boss. |\n\nMembers only need an `id` when another encounter feature references them. IDs are unique across the encounter's waves. A phase action is `notification` or `dialogue` with bounded `text`, `fact` with either a namespaced `tag` or `key`/`value`, or `transition` with a target scene step. Fact scope is `player`, `quest`, or `world`. Player and quest facts apply to each captured participant, and quest scope requires a linked quest scene. Transitions are checked against the scene when `start_encounter` prepares. At most one transition may appear in a non-repeatable phase.\n\nPhases fire once by default. Setting `repeatable: true` also requires `repeat_interval_ticks` from 1-12,000 and `max_fires` from 2-64. Repeatable phases cannot transition the scene. The encounter saves its start time, defeated member IDs, fire counts, and absolute repeat deadlines. Each phase run and action also receives a stable scene operation receipt. Idempotent facts and transitions resume safely, while participant messages reserve their receipt before delivery so a reload can never send them twice.\n\n### Completion objectives\n\n`completion_objectives` replaces the legacy `completion_condition` when an encounter needs more than a simple enemy clear. It contains a `mode` of `all` (the default) or `any` and 1-32 objectives with unique stable IDs. `all` completes after every objective completes and fails as soon as one objective fails. `any` completes after the first success and fails only when every objective has failed. The two completion fields are mutually exclusive.\n\n| Objective type | Fields | Meaning |\n| --- | --- | --- |\n| `all_defeated` | none | Every encounter-owned enemy has been defeated. |\n| `all_gone` | none | Every owned enemy is defeated or durably missing. |\n| `survive_duration` | `duration_ticks` | The encounter remains active for the requested duration. |\n| `protect_actor` | `actor`, `duration_ticks` | The bound scene actor survives for the duration. Its death fails the objective. |\n| `prevent_entry` | `point`, `duration_ticks`, optional radii | No living encounter-owned enemy enters the named point's area for the duration. A breach fails the objective. |\n| `escort_actor` | `actor`, `point`, optional radii | The live bound actor reaches the named point. The actor's death fails the objective. |\n| `destroy_targets` | `actors` | Every listed bound scene actor dies. |\n| `defeat_leader` | `member` | The encounter member with that stable ID is defeated. |\n| `retrieve_item` | `item`, optional `count` | Captured participants collectively carry the item count. Items are inspected, not consumed. |\n| `hold_areas` | `points`, `duration_ticks`, optional radii | Every named point is continuously occupied by at least one captured participant for the duration. Leaving any area resets the timer. |\n\nDurations are 1-1,728,000 ticks. Horizontal `radius` and `vertical_radius` default to 4 and are bounded to 1-64. Point references use resolved `spawn_points`. Actor references are checked against the owning scene at encounter preparation, item IDs are checked against the item registry, and leader IDs must name an authored member. Runtime evaluation uses only captured participants, bound actor UUIDs, resolved points, and encounter-owned entity UUIDs. It never performs an unbounded world scan.\n\n```json\n{\n\"completion_objectives\": {\n  \"mode\": \"all\",\n  \"objectives\": [\n    { \"id\": \"hold_gate\", \"type\": \"prevent_entry\", \"point\": \"west_gate\", \"duration_ticks\": 600, \"radius\": 5 },\n    { \"id\": \"stop_captain\", \"type\": \"defeat_leader\", \"member\": \"raider_captain\" }\n  ]\n}\n}\n```\n\nCompleted and failed objective IDs, continuous-hold timestamps, destroyed actor aliases, and the custom-completion flag are saved with the encounter. The quest tracker reports custom-objective progress, and scene inspection includes the completed, failed, and active-timer sets.\n\n### Friendly participants\n\nThe optional `allies` array declares 1-32 controlled friendly definitions, capped at 64 resulting entities. Each ally has a stable `id` and exactly one source. `entity` creates 1-16 living entities using the same safe equipment, presentation, and combat-attribute allowlists as hostile members. `actor` captures one live scene actor by UUID and rejects entity-only fields such as count, equipment, or attributes.\n\n```json\n{\n\"allies\": [\n  {\n    \"id\": \"village_guard\",\n    \"entity\": \"minecraft:iron_golem\",\n    \"revivable\": true,\n    \"revive_delay_ticks\": 100,\n    \"replacement_policy\": \"missing_if_loaded\",\n    \"cleanup_policy\": \"preserve\",\n    \"affects_completion\": true\n  },\n  {\n    \"id\": \"watch_captain\",\n    \"actor\": \"watch_captain\",\n    \"invulnerable\": true,\n    \"cleanup_policy\": \"preserve\"\n  }\n]\n}\n```\n\n`required_survival` fails the encounter when the ally dies or is confirmed missing in a loaded chunk. It is mutually exclusive with `revivable`, which recreates the ally after `revive_delay_ticks` (default 100, maximum 12,000). `replacement_policy` is `never` by default or `missing_if_loaded`. Replacement never treats an unloaded chunk as proof of loss. Bound allies retain their captured entity type for revival or replacement without silently changing the owning scene's actor binding.\n\n`invulnerable` is applied only while the encounter owns the ally. Preservation restores the entity's prior invulnerability value. `cleanup_policy` is `remove` or `preserve`, independent of hostile cleanup. Entity-defined allies default to removal, while bound scene actors safely default to preservation. `affects_completion` makes victory wait while that ally has a recoverable death or missing/replacement state and fails clearly when recovery is impossible. Allies are never added to hostile kill counts. Enemy and ally UUIDs are stored in separate ledgers.\n\nLoaded ally mobs and encounter-owned hostile mobs receive direct, encounter-local targets. Same-side targets are cleared, but no scoreboard team, global targeting rule, nearby unrelated entity, or participant team membership is changed. Ally identities include definition/index keys, entity UUID and type, last loaded location, generation, recovery deadline, source kind, cleanup policy, and invulnerability restoration state.\n\n### Failure and retry policies\n\nThe optional `failure` object controls participant and protected-actor death without embedding commands:\n\n```json\n{\n\"failure\": {\n  \"on_player_death\": \"reset_wave\",\n  \"on_protected_actor_death\": \"branch_scene\",\n  \"branch_step\": \"failed\",\n  \"retry_delay_ticks\": 200,\n  \"max_attempts\": 3,\n  \"retain_defeated\": false\n}\n}\n```\n\n`on_player_death` applies to captured participants. `on_protected_actor_death` applies to actors referenced by `protect_actor` objectives and bound allies with `required_survival`. Each action is one of:\n\n| Action | Behavior |\n| --- | --- |\n| `fail` | Fails immediately. This is the default for both triggers. |\n| `reset_wave` | Waits for the retry deadline, retires the current wave's non-retained owned mobs, and respawns that wave. |\n| `restart_encounter` | Waits, retires non-retained hostile progress from the whole encounter, and reconciles again from the first remaining wave. |\n| `pause` | Pauses until the retry deadline, then resumes the same entities and progress. |\n| `branch_scene` | Records a scene-transition receipt, chooses `branch_step`, and terminates the failed encounter. |\n\n`retry_delay_ticks` defaults to 200 and is bounded to 0-12,000. `max_attempts` includes the initial attempt, defaults to 3, and is bounded to 1-16. Once exhausted, a retry action becomes a normal failure. `retain_defeated` keeps defeated UUID credits while retiring living owned mobs. Otherwise the affected scope's defeat progress is cleared. `branch_step` is required exactly when either trigger uses `branch_scene` and is validated against the owning scene before start.\n\nThe encounter saves its attempt count, absolute retry deadline, pending action, cause, and protected actor alias. Retry removal increments the durable spawn generation: an unloaded retired mob that later returns is discarded before it can rejoin the fight, while tracked hostiles from an earlier timer wave remain valid. Wave hook IDs, phase fire counts, and scene operation receipts are never cleared, so retries cannot replay dialogue, notifications, facts, or transitions. Objective state is reevaluated for the new attempt, and cleanup remains idempotent.\n\n### Deterministic encounter variants\n\nAn encounter resource may be a bounded selector instead of defining `members` or `waves`:\n\n```json\n{\n  \"schema\": \"villagerretaliation:encounter/v1\",\n  \"id\": \"my_pack:roadblock_variants\",\n  \"variants\": [\n    { \"id\": \"zombie_roadblock\", \"weight\": 3, \"template\": \"my_pack:zombie_roadblock\" },\n    { \"id\": \"skeleton_ambush\", \"weight\": 2, \"template\": \"my_pack:skeleton_ambush\" }\n  ]\n}\n```\n\nSelector resources may contain only `schema`, `id`, optional `version` and `controller`, and `variants`. A `start_encounter` step may author the same `variants` array directly instead of `template` or `encounter_template`. Arrays contain 1-32 entries. IDs are stable and unique, weights are integers from 1-10,000, and templates are namespaced encounter IDs. Every referenced template must exist. Selectors may reference other selectors, but reload validation rejects direct or indirect recursion and chains deeper than 32.\n\nSelection uses a deterministic seed derived from the durable scene ID and encounter operation ID. The start step records the seed, selected variant ID, source template, and final concrete template before spawning. The encounter copies those values into its own save state. Reloads and retries therefore reuse the decision and cannot reroll enemies or duplicate creation receipts.\n\nTo branch after creation, give the `start_encounter` step a transition named for a variant ID:\n\n```json\n{\n  \"id\": \"start_roadblock\",\n  \"type\": \"villagerretaliation:start_encounter\",\n  \"data\": { \"template\": \"my_pack:roadblock_variants\", \"x\": 120, \"y\": 64, \"z\": -40 },\n  \"transitions\": {\n    \"zombie_roadblock\": \"warn_about_zombies\",\n    \"skeleton_ambush\": \"raise_shields\"\n  }\n}\n```\n\nIf no matching transition is authored, normal `next`/success routing is unchanged. Quest tracker text can use `{encounter_variant}` and `{encounter_template}`. Both resolve to empty text before an encounter exists. Scene inspection reports the source template, selected variant, resolved template, and seed.\n\n### Environmental setup and restoration\n\nConcrete encounter templates may add bounded, command-free environmental presentation and temporary world setup:\n\n```json\n{\n\"environment\": {\n  \"cues\": [\n    { \"id\": \"alarm\", \"type\": \"sound\", \"sound\": \"minecraft:block.bell.use\", \"volume\": 1.0, \"pitch\": 0.8 },\n    { \"id\": \"gate_column\", \"type\": \"glowing_column\", \"particle\": \"minecraft:end_rod\", \"offset_y\": 1, \"count\": 32, \"height\": 8 }\n  ],\n  \"temporary_blocks\": [\n    { \"id\": \"gate_light\", \"block\": \"minecraft:light\", \"offset_y\": 3 }\n  ]\n}\n}\n```\n\n`cues` contains at most 32 stable IDs. `sound` and `music` use registered sound IDs and are sent only to online encounter participants in the encounter dimension. `particles` and `glowing_column` use registered simple particle types. Counts are 1-128 and columns are 1-64 blocks high. Offsets are relative to the durable encounter anchor and bounded to 64 blocks per axis. Cue IDs are persisted before delivery, so reloads never replay one-time presentation.\n\n`temporary_blocks` contains at most 64 entries and initially allowlists `barrier`, `light`, `structure_void`, and `glass`. A block may replace only a replaceable state in an already loaded chunk. Before mutation, the encounter saves the exact original state, intended placed state, dimension, position, and ownership status. Setup never force-loads chunks.\n\nCleanup restores a block only while the world still contains the exact state placed by that encounter. If a player or another system changes it, cleanup records the block as preserved and never overwrites the edit. Prepared, applied, restored, and preserved decisions survive reloads. Cleanup remains pending while a required chunk is unloaded and the server maintenance pass resumes it after the chunk returns. Completion, failure, cancellation, explicit cleanup, and operator cleanup all converge on the same idempotent restoration path.\n\nThis first environmental pass intentionally does not mutate global weather or world time. Those presentation types require participant-scoped client state and conflict arbitration before they can be safe alongside overlapping encounters.\n\n### Navigation guidance\n\nConcrete templates can guide captured participants to the durable anchor for fixed-coordinate and authored-location encounters:\n\n```json\n{\n\"guidance\": {\n  \"coordinate_message\": \"Find {location}. It is {distance}m {direction}.\",\n  \"arrival_message\": \"You reached {coordinates}.\",\n  \"discovery_radius\": 64,\n  \"arrival_radius\": 8,\n  \"distance_tracker\": true,\n  \"compass_target\": true,\n  \"directional_particles\": true,\n  \"hud_marker\": true,\n  \"exact_coordinates\": \"after_discovery\",\n  \"update_interval_ticks\": 20\n}\n}\n```\n\nGuidance is participant-only and dimension-aware. `discovery_radius` is 1-512 blocks, `arrival_radius` is 1-64 and cannot exceed discovery range, and live presentation updates every 10-200 ticks. `distance_tracker` exposes a rounded block distance, `compass_target` exposes an eight-way compass bearing, `hud_marker` renders the enabled distance/bearing through Minecraft's temporary action-bar HUD, and `directional_particles` sends a short end-rod trail only to that participant. Cross-dimension guidance identifies the target dimension without calculating a misleading distance or bearing.\n\n`exact_coordinates` is `always`, `after_discovery` (default), or `never`. Messages support `{location}`, `{coordinates}`, `{x}`, `{y}`, `{z}`, `{dimension}`, `{distance}`, and `{direction}`. Hidden coordinates resolve to `undiscovered` or `,`. Each participant's initial message, discovery, arrival, next update deadline, and cleanup acknowledgement are persisted. A participant who was offline receives their own initial guidance after returning, while one-time discovery and arrival messages never replay after reload.\n\nQuest tracker text can use `{encounter_distance}`, `{encounter_direction}`, `{encounter_coordinates}`, `{encounter_dimension}`, `{encounter_discovered}`, and `{encounter_arrived}`. Values respect the exact-coordinate policy and are empty when a feature is disabled or the target is in another dimension. Completion, failure, cancellation, and cleanup stop updates and remove the temporary HUD marker. The legacy fixed-mode `location_message` keeps its old one-time behavior when `guidance` is omitted. It cannot be combined with `guidance.coordinate_message`.\n\n### Rewards and mob drops\n\nConcrete encounter templates can grant bounded rewards and control drops without commands:\n\n```json\n{\n\"rewards\": {\n  \"waves\": [\n    { \"id\": \"scout_supplies\", \"wave\": \"scouts\", \"item\": \"minecraft:arrow\", \"count\": 4 }\n  ],\n  \"phases\": [\n    { \"id\": \"captain_token\", \"phase\": \"captain_falls\", \"item\": \"minecraft:iron_nugget\" }\n  ],\n  \"completion\": [\n    { \"id\": \"village_medal\", \"item\": \"minecraft:emerald\", \"trophy_name\": \"Village Medal\" },\n    { \"id\": \"bonus_cache\", \"loot_table\": \"example:encounters/gate_cache\" }\n  ],\n  \"trophies\": [\n    { \"id\": \"captain_badge\", \"member\": \"gate_captain\", \"item\": \"minecraft:gold_nugget\", \"name\": \"Captain Badge\" }\n  ],\n  \"drop_policy\": \"trophy_only\"\n}\n}\n```\n\n`waves`, `phases`, and `completion` each contain at most 32 rewards, with at most 64 triggered rewards total. IDs are unique across every reward and trophy. A reward has exactly one registered `item` (count 1-64) or registered `loot_table`. `trophy_name` is an optional bounded custom name for direct item rewards. Wave and phase targets must reference authored IDs. Repeatable phase rewards use the phase fire ordinal, so each bounded fire is independently receipt-guarded.\n\nEvery eligible reward reserves a durable scene operation receipt per captured participant before delivery. Item and loot grants use the existing item/loot receipt kinds. Loot rolls use a stable encounter/reward/player seed. Reconciliation, reload, retry, and maintenance reuse the receipt and never grant it twice. Offline participants remain pending, and successfully completed encounters retain completion eligibility through cleanup so their rewards can be delivered after they reconnect. Failed or cancelled encounters do not create new pending grants. A persisted ambiguous `prepared` receipt is treated as consumed rather than risking a duplicate.\n\n`drop_policy` defaults to `normal` and preserves vanilla drops plus authored equipment `drop_chance`. `suppress` removes all item drops. `authored_only` removes vanilla loot and deterministically rolls only authored equipment with a positive `drop_chance`. It is rejected when no such equipment exists. `trophy_only` removes vanilla and equipment drops, requires `trophies`, and drops matching trophies once per durable hostile spawn index. Trophy claims persist separately from hostile death progress, so wave reset, encounter restart, reload, and repeated drop callbacks cannot farm them. Cleanup/discard operations do not produce encounter drops.\n\nThe optional `area` is a cylinder centered on the encounter's durable anchor. `radius` is required and limited to 256 blocks. `vertical_radius` defaults to the radius and is limited to 128. `leave_behavior` is `ignore` (the backward-compatible default), `warn`, `pause`, or `fail`. A failing participant has `leave_timeout_ticks` (default 200, maximum 12000) to return. Warnings and absolute deadlines are saved, messages go only to the affected participant, offline players do not start or advance a new leave decision, and returning clears that excursion's state.\n\n`mob_behavior` is `ignore`, `return`, or `teleport`. `return` asks loaded owned mobs to navigate back without changing unrelated entities. `teleport` waits for the persisted `mob_timeout_ticks` deadline (default 200, maximum 12000) before returning a loaded mob to the anchor. Area checks never force-load the anchor, a participant, or an owned mob's chunk. Omitting `area` preserves encounter/v1 behavior exactly.\n\nEvery mob runs its normal vanilla spawn initialization first, so mobs such as pillagers receive their usual equipment. A member's optional `equipment` object then overrides individual `mainhand`, `offhand`, `head`, `chest`, `legs`, `feet`, or `body` slots. Each slot accepts `item`, optional `count`, an `enchantments` object mapping namespaced enchantment IDs to levels, and `drop_chance` from `0.0` to `1.0`.\n\n### Elite and boss members\n\nMember presentation is an allowlist: `custom_name` (1-128 characters), `name_visible`, `glowing`, and `persistent`. The last option calls the mob's normal persistence mechanism. It does not inject NBT. `name_visible: true` requires a custom name. Omitting every field retains vanilla encounter/v1 presentation and despawn behavior.\n\nSafe combat attributes can use the short fields below or their exact namespaced IDs inside `attributes`, but not both for the same attribute:\n\n| Short field | Attribute ID | Bounds |\n| --- | --- | --- |\n| `health` | `minecraft:max_health` | 1-2048 |\n| `movement_speed` | `minecraft:movement_speed` | 0-4 |\n| `attack_damage` | `minecraft:attack_damage` | 0-2048 |\n| `armor` | `minecraft:armor` | 0-30 |\n| `knockback_resistance` | `minecraft:knockback_resistance` | 0-1 |\n\nAttributes are applied after vanilla spawn initialization and before authored equipment. When maximum health is changed, current health is then set to the resulting maximum. If the selected entity is not living or does not own an authored attribute, spawning fails with a focused diagnostic instead of silently ignoring the field.\n\nSet `boss: true` for a participant-only health bar owned by that spawned member. `boss_bar_color` is `pink`, `blue`, `red`, `green`, `yellow`, `purple`, or `white`. `boss_bar_overlay` is `progress`, `notched_6`, `notched_10`, `notched_12`, or `notched_20`. The designation is stored on the owned entity, so the bar reconstructs after reload or chunk return and disappears on death, failure, cancellation, release, or cleanup. Boss-bar presentation without `boss: true` is rejected.\n\n### Spawn modes\n\n| `spawn_mode` | Behavior |\n| --- | --- |\n| `group` | Spawns one raid-like group around the authored anchor. This is the backward-compatible default. |\n| `near_player` | Captures an online participant's current position when `start_encounter` runs and spawns within three blocks. An explicit anchor is not required. |\n| `fixed` | Spawns at the step's `dimension`, `x`, `y`, and `z` coordinates and tells participants where to go. |\n| `raid_waves` | Spawns either `wave_count` copies of `members` or an explicit `waves` array, retaining authored identity and progress across saves. |\n\nFor `fixed`, customize the message with `location_message`. `{x}`, `{y}`, `{z}`, and `{dimension}` are replaced at runtime. If omitted, the player receives a default “Go to the encounter” coordinate message.\n\nFor `raid_waves`, `wave_interval_ticks` controls the delay between waves. `wave_trigger` is `all_defeated` (the default raid-style behavior) or `timer`. A timer-triggered wave waits only for its interval. An all-defeated wave starts its interval after every mob in the previous wave has been defeated. Raid waves show a participant-only boss bar by default. Set `\"boss_bar\": false` to disable it. The bar is restored after a reload and removed when the encounter ends or is cleaned up.\n\nThe legacy `members` plus `wave_count` shape remains shorthand for identical waves. For distinct waves, omit those shorthand fields and author `waves` with 1-32 entries. Every wave requires a stable lowercase `id` and its own `members`. It may set `delay_ticks` (0-12000), `trigger`, `boss_bar_title`, and wave-level `equipment` defaults that individual members override. The current wave index and ID, its absolute delay deadline, started-wave IDs, and fired hook IDs are persisted. Changing or removing an active wave ID fails safely rather than silently substituting a different definition.\n\n`extra_per_player` is deterministic for both forms: after party size is captured at encounter creation, that many copies of each wave's first member are added for every additional participant up to `max_party_size`. Explicit waves may also use bounded, participant-only `scene_actions` of type `notification` or `dialogue`, plus a single `dialogue_hook`. Every hook needs a stable ID and text of at most 512 characters. Hook IDs are recorded before delivery and are never fired again after reload.\n\n```json\n{\n  \"schema\": \"villagerretaliation:encounter/v1\",\n  \"id\": \"example:three_wave_raid\",\n  \"members\": [{ \"entity\": \"minecraft:pillager\", \"count\": 3 }],\n  \"spawn_mode\": \"raid_waves\",\n  \"wave_count\": 3,\n  \"wave_interval_ticks\": 100,\n  \"wave_trigger\": \"all_defeated\",\n  \"boss_bar\": true,\n  \"spawn_radius\": 12\n}\n```\n\nDistinct composition example:\n\n```json\n{\n  \"schema\": \"villagerretaliation:encounter/v1\",\n  \"id\": \"example:gate_defense\",\n  \"spawn_mode\": \"raid_waves\",\n  \"waves\": [\n    {\n      \"id\": \"scouts\",\n      \"members\": [{ \"entity\": \"minecraft:zombie\", \"count\": 3 }],\n      \"boss_bar_title\": \"Gate Defense - Scouts\"\n    },\n    {\n      \"id\": \"captain\",\n      \"members\": [{ \"entity\": \"minecraft:pillager\" }],\n      \"delay_ticks\": 100,\n      \"trigger\": \"all_defeated\",\n      \"boss_bar_title\": \"Gate Defense - Captain\",\n      \"equipment\": { \"mainhand\": { \"item\": \"minecraft:crossbow\" } },\n      \"dialogue_hook\": { \"id\": \"captain_arrives\", \"text\": \"Their captain is here!\" }\n    }\n  ]\n}\n```\n\n## Persistence and recovery\n\nScene SavedData is version 2. Version 0 gains the explicit version/instance/encounter/audit containers. Version 1 gains durable receipts, pending operations, cleanup, and result fields. Quest and fact SavedData keep their independent migrations.\n\nInstances persist identity, definition version/hash, quest/player/party ownership, participants, lifecycle state, current step, casts, step records, retry/failure data, cleanup, pending operations, receipts, and results. Step states are `pending`, `prepared`, `running`, `applied`, `completed`, `failed`, and `skipped`. Scene states are `pending`, `running`, `waiting`, `blocked`, `completed`, `failed`, `cancelled`, and `cleaning_up`.\n\nMinecraft and separate SavedData writes are not a transaction, so the runtime does not promise universal exactly-once effects. It provides stable operation IDs, durable intent, prepared/applied/completed receipts, idempotent state-setting, and world reconciliation. Item/loot, XP, reputation/gossip, counter, quest-transition, dialogue, and encounter operations use receipts. A reload that finds a prepared operation without enough evidence blocks with a precise diagnostic instead of guessing and risking duplication or loss.\n\n## Scheduling and ownership\n\nThe server scheduler has a fixed work budget per tick, a wake-time queue, and fair owner buckets. It does not scan all entities, force-load chunks, or repeatedly synchronize an unchanged journal. Player reconnect and provider return wake blocked work. Quest completion, failure, abandonment, and expiration apply the scene's cancellation/cleanup policy. Party membership changes preserve the scene's captured identity and encounter scaling, preventing duplicate scenes or rewards.\n\nJournal status exposes actor waits, party waits, active encounters, failures, blocks, and operator-repair requirements. Operator commands include `inspect`, `trace`, `list`, `rebind`, `retry`, `cancel`, `cleanupEncounter`, and `resume` under `/vr admin scene`. Every mutation appends an audit record with scene/actor identity, before/after state, reason, game time, and operator identity. Bindings and receipts remain historical.\n\n## Extensions For Java Mod Authors\n\nOther mods can register actor types, scene steps, encounter controllers, providers, objectives, actions, conditions, and trigger events through `VillagerRetaliationRegistries` during mod registration.\n\nEach registered type supplies its ID, accepted JSON, validation, runtime behavior, debug text, recovery behavior, and any schema metadata needed by authoring tools. Built-in types use the same registration path.\n\nRegistration rejects invalid IDs, duplicate IDs, and conflicting aliases. Registration closes before datapack files are compiled, so late registration fails with a clear error. Install step executors through `SceneStepExecutors` using the matching registered step description.\n\n## Performance and migration guidance\n\n- Keep waits event-driven or use sensible `poll_ticks`. Do not build one-tick polling loops.\n- Prefer marker/position targets over broad entity searches.\n- Keep encounters small and placement attempts bounded.\n- Never renumber or derive step IDs from array positions. Raise `definition_version` for intentional semantic changes.\n- Preserve the old resource while active instances drain if a step graph cannot remain compatible.\n- Back up world data before removing an extension that owns active actor or step types.\n\nThe complete two-villager, player/party, branch, movement, dialogue, wait, controlled ambush, cleanup, provider-unload, and quest-result example is in `example-packs/cinematic-gate-ambush/`.\n",
      "text": "Persistent Quest Scenes Persistent quest scenes coordinate long quest sequences that must resume after a save, reload, disconnect, or unloaded chunk. A scene can move named actors, wait, show dialogue, start a controlled fight, branch, update a quest, and clean up what it created. Use an inline quest scene for a short conversation or a few immediate actions. Use a persistent scene when the sequence waits on time, movement, players, actors, or combat. Scene files use: Encounter files use: Terms Used On This Page Term Plain meaning Scene A saved sequence of named steps. Step One unit of work, such as waiting, moving an actor, or starting a fight. Actor A named player, villager, entity, or position used by scene steps. Binding The saved link between an actor name and its actual player, entity, or position. Encounter A controlled group of enemies or allies created and tracked by a scene. Operation ID An author chosen name that prevents the same quest action from starting the same scene twice. Receipt A saved record that an action was prepared or completed. It helps prevent duplicate rewards and messages after reload. Cleanup Removing or restoring entities, encounters, and temporary blocks owned by the scene. Reconcile Compare saved scene state with the loaded world and safely continue or report a problem. Smallest Complete Scene This scene waits one second, then completes: Start it from a quest action: Keep operation id stable after release. Set wait for result to true when the quest must pause until the scene succeeds. Use false when the quest can continue immediately. Starting a scene Use the registered safe quest action start scene: The runtime combines the operation ID with the owning player, party, quest run, or world. Repeating that combination returns the existing scene instead of creating another one. Scene work continues over server ticks, even when the calling quest waits for its result. For solo quests, the run identity includes the player UUID, quest ID, and persisted start count. Shared party quests use one durable shared instance ID before stage actions or STARTED triggers run. world ownership is an explicit global singleton for the dimension/scene/quest/operation combination. When wait for result is true, the enclosing dialogue, stage, or lifecycle sequence is persisted and resumes only after scene success. Failure and cancellation remain distinct and do not run success actions. Scene action batch cannot suspend and rejects a waiting scene launch during compilation. Scene format Every step ID is authored and persistence critical. Keep IDs stable when editing a live pack. Ownership is player, party, quest instance, or world. Failure/cancellation policies are fail scene, cancel scene, block for repair, and run failure step. Cleanup is none, owned entities, encounters, all owned, or preserve world. quest transition uses one typed target: target stage (or target: \"stage\" plus target stage), target: \"complete\", target: \"fail\", or target: \"abandon\". Mixed targets are compile errors. Overall timeouts wake at the earlier of the current step wake and the absolute scene deadline, including while blocked. Terminal state remains visible until cleanup reaches COMPLETE. Missing definitions show a durable cleanup diagnostic and bounded retry time. The compiler rejects missing references, duplicate actor or step IDs, missing capabilities, unknown types/templates, unreachable paths, invalid failure paths, and immediate unbounded cycles. Datapack reload compares the canonical definition hash and stable step IDs. Compatible edits continue. Incompatible edits leave the readable instance blocked for repair. Actors and replacement An actor declaration has an alias, registered type, required capabilities, required, binding source, optional binding, replacement/missing/death policies, optional string filters, and optional timeout ticks. Built in actor types are: Type Typical capabilities villagerretaliation:player live entity, living, dialogue target villagerretaliation:villager live entity, living, navigation, dialogue villagerretaliation:living entity live entity and living villagerretaliation:hostile encounter group persistent encounter membership villagerretaliation:position stable dimension and block position Binding sources are owner player, party member, quest provider, uuid, marker, encounter, owned spawn, and unbound. Replacement policies are fixed, operator rebindable, compatible replacement, respawn if owned, and optional. Missing policies are block, fail, skip, and wait until timeout. Death policies are fail, block, apply missing policy, respawn if owned, and continue with snapshot. Bindings persist UUID/target identity, source, last dimension and position, display snapshot, generation, live/snapshot state, and full replacement history. A fixed narrative actor is never proximity replaced. Provider actors reuse quest provider identity. A quest provider rebind updates scene actors only when they explicitly use compatible replacement, and appends both binding history and an audit entry. Use /vr admin scene rebind for an operator rebindable repair. Built in steps Each step has id, type, optional actors, a data object, next, named transitions, and optional failure step. Step Important data wait ticks ticks. Persists an absolute wake time wait condition registered conditions, timeout ticks, poll ticks move actor actor, target actor or dimension/x/y/z, speed, arrival distance, timeout ticks, path failure policy, explicit allow teleport face actor / face position source actor and target actor or position dialogue text, speaker aliases in actors, offline policy (wait, fail, or skip), offline poll ticks. One delivery receipt per participant action batch allowlisted actions, each with a stable id. Arbitrary commands are rejected quest transition safe quest action fields such as target stage, completion, or failure scene branch ordered branches containing registered conditions and a transition name, plus default transition. The chosen name is persisted scene complete / scene fail durable terminal result start encounter template, anchor actor or coordinates, optional offset x/offset y/offset z, optional surface anchor, and persisted difficulty inputs wait encounter / cancel encounter / cleanup encounter encounter step naming the start step. Omitted only when the scene owns exactly one encounter Movement never force loads a chunk. It waits for the actor/destination chunk, resumes navigation when available, and only teleports when both path failure policy: \"teleport\" and allow teleport: true are authored. Encounter offsets are applied to an actor or coordinate anchor before that anchor is persisted. Set surface anchor: true to replace the resulting Y coordinate with the motion blocking surface height. This is useful for portable quests that need a fixed destination some distance from a dynamically located villager without hard coding world coordinates. Encounters Templates are allowlists, not command containers. Party size and difficulty inputs are captured when the encounter starts. Owned entities carry durable encounter identity. Reload reconciles UUIDs and tags before bounded safe placement attempts. Unrelated nearby mobs never count. Cleanup removes, retains, or releases surviving owned mobs according to the template and scene policy. The optional spawn points array supplies 1 64 named positions. Each point has a stable id and exactly one source: actor, marker, or complete x/y/z coordinates. actor and marker both name an actor alias declared by the scene. The two spellings let a template communicate whether it expects a live/snapshotted actor or a position actor bound from a marker. Actor and marker sources may add bounded offset x, offset y, and offset z values. Explicit coordinates may set dimension. Otherwise they use the encounter anchor dimension. Every point must resolve into that same dimension. Missing actors, unknown or incompatible dimensions, incomplete coordinates, duplicate IDs, empty lists, and weights outside 1 10000 reject the start with a focused diagnostic. spawn selection defaults to random and may be random, sequential, weighted, nearest player, farthest player, or one group per point. Weighted selection uses each point's optional weight (default 1). Distance modes compare the points with online captured participants and wait when no suitable participant is online. Group selection assigns each member definition to a point in authored order. Party scaling extras stay with the first group. Resolved absolute points, every member's selected point ID, and the sequential cursor are saved before placement, so reloads and unloaded chunks wait without rerolling. Recovery checks only the bounded anchor and authored point neighborhoods and never force loads chunks. Authored points cannot be combined with spawn mode: \"near player\". Mid fight phases phases is an ordered array of up to 64 durable phase definitions. Every phase has a stable id, one trigger, and 1 32 allowlisted actions. Trigger shapes are: Trigger type Required field Fires when wave started wave The named authored or shorthand wave has durably started. wave completed wave Every enemy through the named wave has been defeated. remaining percentage percentage (0 100) Remaining enemies are at or below the threshold. elapsed time ticks (1 1,728,000) The durable time since first encounter reconciliation reaches the threshold. elite defeated member The referenced stable member ID has been defeated. The member must have count 1, must not receive party scaling copies, and must be named, enhanced, or designated as a boss. Members only need an id when another encounter feature references them. IDs are unique across the encounter's waves. A phase action is notification or dialogue with bounded text, fact with either a namespaced tag or key/value, or transition with a target scene step. Fact scope is player, quest, or world. Player and quest facts apply to each captured participant, and quest scope requires a linked quest scene. Transitions are checked against the scene when start encounter prepares. At most one transition may appear in a non repeatable phase. Phases fire once by default. Setting repeatable: true also requires repeat interval ticks from 1 12,000 and max fires from 2 64. Repeatable phases cannot transition the scene. The encounter saves its start time, defeated member IDs, fire counts, and absolute repeat deadlines. Each phase run and action also receives a stable scene operation receipt. Idempotent facts and transitions resume safely, while participant messages reserve their receipt before delivery so a reload can never send them twice. Completion objectives completion objectives replaces the legacy completion condition when an encounter needs more than a simple enemy clear. It contains a mode of all (the default) or any and 1 32 objectives with unique stable IDs. all completes after every objective completes and fails as soon as one objective fails. any completes after the first success and fails only when every objective has failed. The two completion fields are mutually exclusive. Objective type Fields Meaning all defeated none Every encounter owned enemy has been defeated. all gone none Every owned enemy is defeated or durably missing. survive duration duration ticks The encounter remains active for the requested duration. protect actor actor, duration ticks The bound scene actor survives for the duration. Its death fails the objective. prevent entry point, duration ticks, optional radii No living encounter owned enemy enters the named point's area for the duration. A breach fails the objective. escort actor actor, point, optional radii The live bound actor reaches the named point. The actor's death fails the objective. destroy targets actors Every listed bound scene actor dies. defeat leader member The encounter member with that stable ID is defeated. retrieve item item, optional count Captured participants collectively carry the item count. Items are inspected, not consumed. hold areas points, duration ticks, optional radii Every named point is continuously occupied by at least one captured participant for the duration. Leaving any area resets the timer. Durations are 1 1,728,000 ticks. Horizontal radius and vertical radius default to 4 and are bounded to 1 64. Point references use resolved spawn points. Actor references are checked against the owning scene at encounter preparation, item IDs are checked against the item registry, and leader IDs must name an authored member. Runtime evaluation uses only captured participants, bound actor UUIDs, resolved points, and encounter owned entity UUIDs. It never performs an unbounded world scan. Completed and failed objective IDs, continuous hold timestamps, destroyed actor aliases, and the custom completion flag are saved with the encounter. The quest tracker reports custom objective progress, and scene inspection includes the completed, failed, and active timer sets. Friendly participants The optional allies array declares 1 32 controlled friendly definitions, capped at 64 resulting entities. Each ally has a stable id and exactly one source. entity creates 1 16 living entities using the same safe equipment, presentation, and combat attribute allowlists as hostile members. actor captures one live scene actor by UUID and rejects entity only fields such as count, equipment, or attributes. required survival fails the encounter when the ally dies or is confirmed missing in a loaded chunk. It is mutually exclusive with revivable, which recreates the ally after revive delay ticks (default 100, maximum 12,000). replacement policy is never by default or missing if loaded. Replacement never treats an unloaded chunk as proof of loss. Bound allies retain their captured entity type for revival or replacement without silently changing the owning scene's actor binding. invulnerable is applied only while the encounter owns the ally. Preservation restores the entity's prior invulnerability value. cleanup policy is remove or preserve, independent of hostile cleanup. Entity defined allies default to removal, while bound scene actors safely default to preservation. affects completion makes victory wait while that ally has a recoverable death or missing/replacement state and fails clearly when recovery is impossible. Allies are never added to hostile kill counts. Enemy and ally UUIDs are stored in separate ledgers. Loaded ally mobs and encounter owned hostile mobs receive direct, encounter local targets. Same side targets are cleared, but no scoreboard team, global targeting rule, nearby unrelated entity, or participant team membership is changed. Ally identities include definition/index keys, entity UUID and type, last loaded location, generation, recovery deadline, source kind, cleanup policy, and invulnerability restoration state. Failure and retry policies The optional failure object controls participant and protected actor death without embedding commands: on player death applies to captured participants. on protected actor death applies to actors referenced by protect actor objectives and bound allies with required survival. Each action is one of: Action Behavior fail Fails immediately. This is the default for both triggers. reset wave Waits for the retry deadline, retires the current wave's non retained owned mobs, and respawns that wave. restart encounter Waits, retires non retained hostile progress from the whole encounter, and reconciles again from the first remaining wave. pause Pauses until the retry deadline, then resumes the same entities and progress. branch scene Records a scene transition receipt, chooses branch step, and terminates the failed encounter. retry delay ticks defaults to 200 and is bounded to 0 12,000. max attempts includes the initial attempt, defaults to 3, and is bounded to 1 16. Once exhausted, a retry action becomes a normal failure. retain defeated keeps defeated UUID credits while retiring living owned mobs. Otherwise the affected scope's defeat progress is cleared. branch step is required exactly when either trigger uses branch scene and is validated against the owning scene before start. The encounter saves its attempt count, absolute retry deadline, pending action, cause, and protected actor alias. Retry removal increments the durable spawn generation: an unloaded retired mob that later returns is discarded before it can rejoin the fight, while tracked hostiles from an earlier timer wave remain valid. Wave hook IDs, phase fire counts, and scene operation receipts are never cleared, so retries cannot replay dialogue, notifications, facts, or transitions. Objective state is reevaluated for the new attempt, and cleanup remains idempotent. Deterministic encounter variants An encounter resource may be a bounded selector instead of defining members or waves: Selector resources may contain only schema, id, optional version and controller, and variants. A start encounter step may author the same variants array directly instead of template or encounter template. Arrays contain 1 32 entries. IDs are stable and unique, weights are integers from 1 10,000, and templates are namespaced encounter IDs. Every referenced template must exist. Selectors may reference other selectors, but reload validation rejects direct or indirect recursion and chains deeper than 32. Selection uses a deterministic seed derived from the durable scene ID and encounter operation ID. The start step records the seed, selected variant ID, source template, and final concrete template before spawning. The encounter copies those values into its own save state. Reloads and retries therefore reuse the decision and cannot reroll enemies or duplicate creation receipts. To branch after creation, give the start encounter step a transition named for a variant ID: If no matching transition is authored, normal next/success routing is unchanged. Quest tracker text can use {encounter variant} and {encounter template}. Both resolve to empty text before an encounter exists. Scene inspection reports the source template, selected variant, resolved template, and seed. Environmental setup and restoration Concrete encounter templates may add bounded, command free environmental presentation and temporary world setup: cues contains at most 32 stable IDs. sound and music use registered sound IDs and are sent only to online encounter participants in the encounter dimension. particles and glowing column use registered simple particle types. Counts are 1 128 and columns are 1 64 blocks high. Offsets are relative to the durable encounter anchor and bounded to 64 blocks per axis. Cue IDs are persisted before delivery, so reloads never replay one time presentation. temporary blocks contains at most 64 entries and initially allowlists barrier, light, structure void, and glass. A block may replace only a replaceable state in an already loaded chunk. Before mutation, the encounter saves the exact original state, intended placed state, dimension, position, and ownership status. Setup never force loads chunks. Cleanup restores a block only while the world still contains the exact state placed by that encounter. If a player or another system changes it, cleanup records the block as preserved and never overwrites the edit. Prepared, applied, restored, and preserved decisions survive reloads. Cleanup remains pending while a required chunk is unloaded and the server maintenance pass resumes it after the chunk returns. Completion, failure, cancellation, explicit cleanup, and operator cleanup all converge on the same idempotent restoration path. This first environmental pass intentionally does not mutate global weather or world time. Those presentation types require participant scoped client state and conflict arbitration before they can be safe alongside overlapping encounters. Navigation guidance Concrete templates can guide captured participants to the durable anchor for fixed coordinate and authored location encounters: Guidance is participant only and dimension aware. discovery radius is 1 512 blocks, arrival radius is 1 64 and cannot exceed discovery range, and live presentation updates every 10 200 ticks. distance tracker exposes a rounded block distance, compass target exposes an eight way compass bearing, hud marker renders the enabled distance/bearing through Minecraft's temporary action bar HUD, and directional particles sends a short end rod trail only to that participant. Cross dimension guidance identifies the target dimension without calculating a misleading distance or bearing. exact coordinates is always, after discovery (default), or never. Messages support {location}, {coordinates}, {x}, {y}, {z}, {dimension}, {distance}, and {direction}. Hidden coordinates resolve to undiscovered or ,. Each participant's initial message, discovery, arrival, next update deadline, and cleanup acknowledgement are persisted. A participant who was offline receives their own initial guidance after returning, while one time discovery and arrival messages never replay after reload. Quest tracker text can use {encounter distance}, {encounter direction}, {encounter coordinates}, {encounter dimension}, {encounter discovered}, and {encounter arrived}. Values respect the exact coordinate policy and are empty when a feature is disabled or the target is in another dimension. Completion, failure, cancellation, and cleanup stop updates and remove the temporary HUD marker. The legacy fixed mode location message keeps its old one time behavior when guidance is omitted. It cannot be combined with guidance.coordinate message. Rewards and mob drops Concrete encounter templates can grant bounded rewards and control drops without commands: waves, phases, and completion each contain at most 32 rewards, with at most 64 triggered rewards total. IDs are unique across every reward and trophy. A reward has exactly one registered item (count 1 64) or registered loot table. trophy name is an optional bounded custom name for direct item rewards. Wave and phase targets must reference authored IDs. Repeatable phase rewards use the phase fire ordinal, so each bounded fire is independently receipt guarded. Every eligible reward reserves a durable scene operation receipt per captured participant before delivery. Item and loot grants use the existing item/loot receipt kinds. Loot rolls use a stable encounter/reward/player seed. Reconciliation, reload, retry, and maintenance reuse the receipt and never grant it twice. Offline participants remain pending, and successfully completed encounters retain completion eligibility through cleanup so their rewards can be delivered after they reconnect. Failed or cancelled encounters do not create new pending grants. A persisted ambiguous prepared receipt is treated as consumed rather than risking a duplicate. drop policy defaults to normal and preserves vanilla drops plus authored equipment drop chance. suppress removes all item drops. authored only removes vanilla loot and deterministically rolls only authored equipment with a positive drop chance. It is rejected when no such equipment exists. trophy only removes vanilla and equipment drops, requires trophies, and drops matching trophies once per durable hostile spawn index. Trophy claims persist separately from hostile death progress, so wave reset, encounter restart, reload, and repeated drop callbacks cannot farm them. Cleanup/discard operations do not produce encounter drops. The optional area is a cylinder centered on the encounter's durable anchor. radius is required and limited to 256 blocks. vertical radius defaults to the radius and is limited to 128. leave behavior is ignore (the backward compatible default), warn, pause, or fail. A failing participant has leave timeout ticks (default 200, maximum 12000) to return. Warnings and absolute deadlines are saved, messages go only to the affected participant, offline players do not start or advance a new leave decision, and returning clears that excursion's state. mob behavior is ignore, return, or teleport. return asks loaded owned mobs to navigate back without changing unrelated entities. teleport waits for the persisted mob timeout ticks deadline (default 200, maximum 12000) before returning a loaded mob to the anchor. Area checks never force load the anchor, a participant, or an owned mob's chunk. Omitting area preserves encounter/v1 behavior exactly. Every mob runs its normal vanilla spawn initialization first, so mobs such as pillagers receive their usual equipment. A member's optional equipment object then overrides individual mainhand, offhand, head, chest, legs, feet, or body slots. Each slot accepts item, optional count, an enchantments object mapping namespaced enchantment IDs to levels, and drop chance from 0.0 to 1.0. Elite and boss members Member presentation is an allowlist: custom name (1 128 characters), name visible, glowing, and persistent. The last option calls the mob's normal persistence mechanism. It does not inject NBT. name visible: true requires a custom name. Omitting every field retains vanilla encounter/v1 presentation and despawn behavior. Safe combat attributes can use the short fields below or their exact namespaced IDs inside attributes, but not both for the same attribute: Short field Attribute ID Bounds health minecraft:max health 1 2048 movement speed minecraft:movement speed 0 4 attack damage minecraft:attack damage 0 2048 armor minecraft:armor 0 30 knockback resistance minecraft:knockback resistance 0 1 Attributes are applied after vanilla spawn initialization and before authored equipment. When maximum health is changed, current health is then set to the resulting maximum. If the selected entity is not living or does not own an authored attribute, spawning fails with a focused diagnostic instead of silently ignoring the field. Set boss: true for a participant only health bar owned by that spawned member. boss bar color is pink, blue, red, green, yellow, purple, or white. boss bar overlay is progress, notched 6, notched 10, notched 12, or notched 20. The designation is stored on the owned entity, so the bar reconstructs after reload or chunk return and disappears on death, failure, cancellation, release, or cleanup. Boss bar presentation without boss: true is rejected. Spawn modes spawn mode Behavior group Spawns one raid like group around the authored anchor. This is the backward compatible default. near player Captures an online participant's current position when start encounter runs and spawns within three blocks. An explicit anchor is not required. fixed Spawns at the step's dimension, x, y, and z coordinates and tells participants where to go. raid waves Spawns either wave count copies of members or an explicit waves array, retaining authored identity and progress across saves. For fixed, customize the message with location message. {x}, {y}, {z}, and {dimension} are replaced at runtime. If omitted, the player receives a default “Go to the encounter” coordinate message. For raid waves, wave interval ticks controls the delay between waves. wave trigger is all defeated (the default raid style behavior) or timer. A timer triggered wave waits only for its interval. An all defeated wave starts its interval after every mob in the previous wave has been defeated. Raid waves show a participant only boss bar by default. Set \"boss bar\": false to disable it. The bar is restored after a reload and removed when the encounter ends or is cleaned up. The legacy members plus wave count shape remains shorthand for identical waves. For distinct waves, omit those shorthand fields and author waves with 1 32 entries. Every wave requires a stable lowercase id and its own members. It may set delay ticks (0 12000), trigger, boss bar title, and wave level equipment defaults that individual members override. The current wave index and ID, its absolute delay deadline, started wave IDs, and fired hook IDs are persisted. Changing or removing an active wave ID fails safely rather than silently substituting a different definition. extra per player is deterministic for both forms: after party size is captured at encounter creation, that many copies of each wave's first member are added for every additional participant up to max party size. Explicit waves may also use bounded, participant only scene actions of type notification or dialogue, plus a single dialogue hook. Every hook needs a stable ID and text of at most 512 characters. Hook IDs are recorded before delivery and are never fired again after reload. Distinct composition example: Persistence and recovery Scene SavedData is version 2. Version 0 gains the explicit version/instance/encounter/audit containers. Version 1 gains durable receipts, pending operations, cleanup, and result fields. Quest and fact SavedData keep their independent migrations. Instances persist identity, definition version/hash, quest/player/party ownership, participants, lifecycle state, current step, casts, step records, retry/failure data, cleanup, pending operations, receipts, and results. Step states are pending, prepared, running, applied, completed, failed, and skipped. Scene states are pending, running, waiting, blocked, completed, failed, cancelled, and cleaning up. Minecraft and separate SavedData writes are not a transaction, so the runtime does not promise universal exactly once effects. It provides stable operation IDs, durable intent, prepared/applied/completed receipts, idempotent state setting, and world reconciliation. Item/loot, XP, reputation/gossip, counter, quest transition, dialogue, and encounter operations use receipts. A reload that finds a prepared operation without enough evidence blocks with a precise diagnostic instead of guessing and risking duplication or loss. Scheduling and ownership The server scheduler has a fixed work budget per tick, a wake time queue, and fair owner buckets. It does not scan all entities, force load chunks, or repeatedly synchronize an unchanged journal. Player reconnect and provider return wake blocked work. Quest completion, failure, abandonment, and expiration apply the scene's cancellation/cleanup policy. Party membership changes preserve the scene's captured identity and encounter scaling, preventing duplicate scenes or rewards. Journal status exposes actor waits, party waits, active encounters, failures, blocks, and operator repair requirements. Operator commands include inspect, trace, list, rebind, retry, cancel, cleanupEncounter, and resume under /vr admin scene. Every mutation appends an audit record with scene/actor identity, before/after state, reason, game time, and operator identity. Bindings and receipts remain historical. Extensions For Java Mod Authors Other mods can register actor types, scene steps, encounter controllers, providers, objectives, actions, conditions, and trigger events through VillagerRetaliationRegistries during mod registration. Each registered type supplies its ID, accepted JSON, validation, runtime behavior, debug text, recovery behavior, and any schema metadata needed by authoring tools. Built in types use the same registration path. Registration rejects invalid IDs, duplicate IDs, and conflicting aliases. Registration closes before datapack files are compiled, so late registration fails with a clear error. Install step executors through SceneStepExecutors using the matching registered step description. Performance and migration guidance Keep waits event driven or use sensible poll ticks. Do not build one tick polling loops. Prefer marker/position targets over broad entity searches. Keep encounters small and placement attempts bounded. Never renumber or derive step IDs from array positions. Raise definition version for intentional semantic changes. Preserve the old resource while active instances drain if a step graph cannot remain compatible. Back up world data before removing an extension that owns active actor or step types. The complete two villager, player/party, branch, movement, dialogue, wait, controlled ambush, cleanup, provider unload, and quest result example is in example packs/cinematic gate ambush/.",
      "headings": [
        {
          "level": 2,
          "title": "Terms Used On This Page"
        },
        {
          "level": 2,
          "title": "Smallest Complete Scene"
        },
        {
          "level": 2,
          "title": "Starting a scene"
        },
        {
          "level": 2,
          "title": "Scene format"
        },
        {
          "level": 2,
          "title": "Actors and replacement"
        },
        {
          "level": 2,
          "title": "Built-in steps"
        },
        {
          "level": 2,
          "title": "Encounters"
        },
        {
          "level": 3,
          "title": "Mid-fight phases"
        },
        {
          "level": 3,
          "title": "Completion objectives"
        },
        {
          "level": 3,
          "title": "Friendly participants"
        },
        {
          "level": 3,
          "title": "Failure and retry policies"
        },
        {
          "level": 3,
          "title": "Deterministic encounter variants"
        },
        {
          "level": 3,
          "title": "Environmental setup and restoration"
        },
        {
          "level": 3,
          "title": "Navigation guidance"
        },
        {
          "level": 3,
          "title": "Rewards and mob drops"
        },
        {
          "level": 3,
          "title": "Elite and boss members"
        },
        {
          "level": 3,
          "title": "Spawn modes"
        },
        {
          "level": 2,
          "title": "Persistence and recovery"
        },
        {
          "level": 2,
          "title": "Scheduling and ownership"
        },
        {
          "level": 2,
          "title": "Extensions For Java Mod Authors"
        },
        {
          "level": 2,
          "title": "Performance and migration guidance"
        }
      ],
      "related": [
        "quests",
        "quest-scene-runtime",
        "quest-runtime-roadmap"
      ]
    },
    {
      "slug": "quest-runtime-roadmap",
      "file": "Quest-Runtime-Roadmap.md",
      "source": "wiki/Quest-Runtime-Roadmap.md",
      "sourceKind": "wiki",
      "group": "Quests & Scenes",
      "icon": "milestone",
      "title": "Quest Runtime Roadmap",
      "description": "Current quest runtime status plus the stabilization and compatibility work planned around beta.13.",
      "markdown": "# Quest Runtime Roadmap\n\nThe persistent quest scene runtime is already implemented in the beta.13 pre-release. Typed actors, resumable steps, controlled encounters, recovery, and extension descriptors are part of the current development surface. The remaining roadmap is focused on release hardening and long-term compatibility. It does not describe a replacement runtime.\n\n## Current development baseline\n\nThe following work is complete in the beta.13 development branch:\n\n- Typed player, villager, living-entity, encounter-group, and position actors\n- Persistent actor bindings, replacement policies, snapshots, and replacement history\n- Resumable scene steps with stable IDs and durable operation receipts\n- Safe movement, dialogue, waits, branches, quest transitions, and allowlisted action batches\n- Controlled encounters with waves, objectives, allies, rewards, cleanup, and recovery\n- Save migrations, blocked-repair states, operator commands, and audit history\n- Registered actor, step, encounter, provider, objective, action, condition, and trigger descriptors\n- Exported schema metadata used by the runtime and browser authoring tools\n\n## Beta.13 release work\n\nBefore beta.13 leaves pre-release, development will concentrate on these outcomes:\n\n- Keep `villagerretaliation:scene/v1` and `villagerretaliation:encounter/v1` stable for datapack authors\n- Continue regression coverage for reloads, unloaded chunks, offline participants, provider replacement, operator repair, cleanup, and reward delivery\n- Use the built-in quest scenes and encounters as compatibility cases for parser, compiler, persistence, and runtime changes\n- Gate SavedData changes with migration tests so existing readable scene state is preserved\n- Keep diagnostics, exported descriptors, and browser authoring tools aligned with the runtime\n- Fix save-safety and duplicate-side-effect defects before adding another step family\n\n## Compatibility after beta.13\n\nThe first stable release will preserve authored actor aliases, step IDs, encounter member IDs, and operation IDs as persistence-critical data. Compatible additions will remain additive. A change that cannot safely load existing definitions or scene state will use a new schema version instead of silently changing version 1 behavior.\n\nExisting scene instances will continue to fail closed when recovery cannot be proven safe. Operator repair, cancellation, and cleanup will remain explicit actions with audit records.\n\n## Not currently scheduled\n\nThere is no scheduled `scene/v2`, no arbitrary command step, and no replacement for the current state-machine boundary. Unbounded entity searches and forced chunk loading are also outside the plan. New actor types, step types, or encounter systems will be added to this roadmap only after they have an implementation target and a release target.\n\n## Roadmap updates\n\nThis page will change when work is assigned to a release, when compatibility commitments change, or when a planned item ships. Ideas without an implementation target will not be listed as future features.\n",
      "text": "Quest Runtime Roadmap The persistent quest scene runtime is already implemented in the beta.13 pre release. Typed actors, resumable steps, controlled encounters, recovery, and extension descriptors are part of the current development surface. The remaining roadmap is focused on release hardening and long term compatibility. It does not describe a replacement runtime. Current development baseline The following work is complete in the beta.13 development branch: Typed player, villager, living entity, encounter group, and position actors Persistent actor bindings, replacement policies, snapshots, and replacement history Resumable scene steps with stable IDs and durable operation receipts Safe movement, dialogue, waits, branches, quest transitions, and allowlisted action batches Controlled encounters with waves, objectives, allies, rewards, cleanup, and recovery Save migrations, blocked repair states, operator commands, and audit history Registered actor, step, encounter, provider, objective, action, condition, and trigger descriptors Exported schema metadata used by the runtime and browser authoring tools Beta.13 release work Before beta.13 leaves pre release, development will concentrate on these outcomes: Keep villagerretaliation:scene/v1 and villagerretaliation:encounter/v1 stable for datapack authors Continue regression coverage for reloads, unloaded chunks, offline participants, provider replacement, operator repair, cleanup, and reward delivery Use the built in quest scenes and encounters as compatibility cases for parser, compiler, persistence, and runtime changes Gate SavedData changes with migration tests so existing readable scene state is preserved Keep diagnostics, exported descriptors, and browser authoring tools aligned with the runtime Fix save safety and duplicate side effect defects before adding another step family Compatibility after beta.13 The first stable release will preserve authored actor aliases, step IDs, encounter member IDs, and operation IDs as persistence critical data. Compatible additions will remain additive. A change that cannot safely load existing definitions or scene state will use a new schema version instead of silently changing version 1 behavior. Existing scene instances will continue to fail closed when recovery cannot be proven safe. Operator repair, cancellation, and cleanup will remain explicit actions with audit records. Not currently scheduled There is no scheduled scene/v2, no arbitrary command step, and no replacement for the current state machine boundary. Unbounded entity searches and forced chunk loading are also outside the plan. New actor types, step types, or encounter systems will be added to this roadmap only after they have an implementation target and a release target. Roadmap updates This page will change when work is assigned to a release, when compatibility commitments change, or when a planned item ships. Ideas without an implementation target will not be listed as future features.",
      "headings": [
        {
          "level": 2,
          "title": "Current development baseline"
        },
        {
          "level": 2,
          "title": "Beta.13 release work"
        },
        {
          "level": 2,
          "title": "Compatibility after beta.13"
        },
        {
          "level": 2,
          "title": "Not currently scheduled"
        },
        {
          "level": 2,
          "title": "Roadmap updates"
        }
      ],
      "related": [
        "quest-scenes",
        "quest-scene-runtime"
      ]
    },
    {
      "slug": "gifts",
      "file": "Gifts.md",
      "source": "wiki/Gifts.md",
      "sourceKind": "wiki",
      "group": "Economy & Progression",
      "icon": "gift",
      "title": "Gifts",
      "description": "Gift preferences, reactions, trust caps, and high-reputation reward rolls.",
      "markdown": "# Gifts\n\nGift files define two things:\n\n- which items villagers like or dislike\n- which items high-trust villagers may give back as rewards\n\n## Path\n\n```text\ndata/villagerretaliation/gifts/<file>.json\n```\n\n## Minimal Preference Example\n\n```json\n{\n  \"preferences\": [\n    {\n      \"id\": \"my_pack.librarian.favorite_book\",\n      \"professions\": [\"minecraft:librarian\"],\n      \"reaction\": \"loved\",\n      \"items\": [\"minecraft:enchanted_book\", \"minecraft:name_tag\"],\n      \"response_key\": \"my_pack.gift.librarian.favorite_book\",\n      \"priority\": 20\n    }\n  ]\n}\n```\n\n## Minimal Reward Example\n\n```json\n{\n  \"rewards\": [\n    {\n      \"id\": \"my_pack.librarian.reward\",\n      \"professions\": [\"minecraft:librarian\"],\n      \"reputation_levels\": [\"revered\", \"royalty\"],\n      \"item\": \"minecraft:book\",\n      \"min_count\": 2,\n      \"max_count\": 5,\n      \"weight\": 10\n    }\n  ]\n}\n```\n\n## Reactions\n\nCurrent reaction values:\n\n```text\nloved\nliked\nneutral\ndisliked\nhated\n```\n\nPick the reaction first, then tune specifics with `reputation_per_item`, profession filters, and `priority`.\n\n## Reputation Limits\n\nPositive gift reputation is tracked separately for each player-villager relationship. By default, the first stack of an item earns its full value, additional stacks of the same item on that Minecraft day earn 10%, and total positive gift reputation is capped at 120 per day. Disliked and hated gift penalties are not reduced or counted against that cap.\n\nServers can tune this behavior with `gifts.repeatedGiftReputationMultiplier`, `gifts.dailyGiftReputationCap`, and `gifts.giftRequestCooldownTicks`. Item repetition is based on the item ID, so changing stack components does not create a new first gift.\n\n## Example: Shared Response Text\n\nGift files stay language-neutral by using a response key:\n\n```json\n{\n  \"id\": \"my_pack.gift_message.favorite_book\",\n  \"key\": \"my_pack.gift.librarian.favorite_book\",\n  \"text\": \"{gift_item}, This belongs near a reading lamp, not forgotten in a chest.\"\n}\n```\n\nThat message lives in normal dialogue under `messages/`.\n\n## Add, Override, Remove\n\n- Add a new file to add more gift rules.\n- Reuse an existing `id` to replace one rule.\n- Use `\"remove\": true` with an `id` to remove one rule.\n- Use top-level `replace: true` only when you want to rebuild the entire gift table.\n\n## Good Uses\n\n- profession-specific favorites\n- one universally hated prank gift\n- special rewards for high-trust villagers\n- modded items or item tags as custom gift content\n",
      "text": "Gifts Gift files define two things: which items villagers like or dislike which items high trust villagers may give back as rewards Path Minimal Preference Example Minimal Reward Example Reactions Current reaction values: Pick the reaction first, then tune specifics with reputation per item, profession filters, and priority. Reputation Limits Positive gift reputation is tracked separately for each player villager relationship. By default, the first stack of an item earns its full value, additional stacks of the same item on that Minecraft day earn 10%, and total positive gift reputation is capped at 120 per day. Disliked and hated gift penalties are not reduced or counted against that cap. Servers can tune this behavior with gifts.repeatedGiftReputationMultiplier, gifts.dailyGiftReputationCap, and gifts.giftRequestCooldownTicks. Item repetition is based on the item ID, so changing stack components does not create a new first gift. Example: Shared Response Text Gift files stay language neutral by using a response key: That message lives in normal dialogue under messages/. Add, Override, Remove Add a new file to add more gift rules. Reuse an existing id to replace one rule. Use \"remove\": true with an id to remove one rule. Use top level replace: true only when you want to rebuild the entire gift table. Good Uses profession specific favorites one universally hated prank gift special rewards for high trust villagers modded items or item tags as custom gift content",
      "headings": [
        {
          "level": 2,
          "title": "Path"
        },
        {
          "level": 2,
          "title": "Minimal Preference Example"
        },
        {
          "level": 2,
          "title": "Minimal Reward Example"
        },
        {
          "level": 2,
          "title": "Reactions"
        },
        {
          "level": 2,
          "title": "Reputation Limits"
        },
        {
          "level": 2,
          "title": "Example: Shared Response Text"
        },
        {
          "level": 2,
          "title": "Add, Override, Remove"
        },
        {
          "level": 2,
          "title": "Good Uses"
        }
      ],
      "related": [
        "json-reference",
        "localization",
        "profession-loot",
        "currency-and-item-text"
      ]
    },
    {
      "slug": "pacification",
      "file": "Pacification.md",
      "source": "wiki/Pacification.md",
      "sourceKind": "wiki",
      "group": "Economy & Progression",
      "icon": "hand-coins",
      "title": "Pacification",
      "description": "Items and profession-specific costs that calm hostile villagers.",
      "markdown": "# Pacification\n\nPacification files choose which held items can calm a villager or wandering trader that is hostile toward the player. A successful payment consumes the required count and clears that hostility.\n\nThese files choose the payment. The spoken success, failure, and refusal lines belong in normal dialogue under a `pacify/` folder.\n\n## Path\n\nPacification data is fixed to the `villagerretaliation` namespace:\n\n```text\ndata/villagerretaliation/pacification/<file>.json\n```\n\n## Simple Example\n\n```json\n{\n  \"payments\": [\n    {\n      \"items\": [\"minecraft:emerald\"],\n      \"count\": 8,\n      \"priority\": 10\n    }\n  ]\n}\n```\n\nA hostile villager can accept eight emeralds. The player right-clicks while holding the payment in the used hand or off hand.\n\n## Modded Currency Example\n\n```json\n{\n  \"payments\": [\n    {\n      \"item\": \"numismatic-overhaul:gold_coin\",\n      \"count\": 12,\n      \"name\": \"gold coin\",\n      \"plural_name\": \"gold coins\",\n      \"priority\": 20\n    }\n  ]\n}\n```\n\n`name` and `plural_name` control the item wording used by pacification dialogue. They do not rename the item itself.\n\n## Item Tag Example\n\n```json\n{\n  \"payments\": [\n    {\n      \"tags\": [\"#c:ingots/iron\"],\n      \"count\": 4\n    }\n  ]\n}\n```\n\nA leading `#` is optional in `tag` and `tags`.\n\n## Profession-Specific Example\n\n```json\n{\n  \"payments\": [\n    {\n      \"professions\": [\"minecraft:toolsmith\"],\n      \"items\": [\"minecraft:iron_ingot\"],\n      \"min_count\": 2,\n      \"max_count\": 4,\n      \"priority\": 30\n    }\n  ]\n}\n```\n\nThe required count is chosen inclusively from 2 through 4 for each offer.\n\n## How A Rule Is Chosen\n\n1. The held item, profession, and optional armed state must match.\n2. If any matching rule names a profession, general rules are ignored.\n3. The highest `priority` wins.\n4. If priorities tie, the rule loaded first wins.\n\nThis means a profession-specific payment can safely override a general payment without giving it a higher priority.\n\n## Main Fields\n\n| Field | Default | Meaning |\n| --- | --- | --- |\n| `item` or `items` | None | One or more exact item IDs. |\n| `tag` or `tags` | None | One or more item tags. |\n| `count` | None | Exact payment count. |\n| `min_count` | `1` | Lowest randomized payment count when `count` is absent. |\n| `max_count` | `min_count` | Highest randomized payment count. |\n| `professions` | Any | Restrict the rule to one or more villager professions. |\n| `priority` | `0` | Higher values win between otherwise eligible rules. |\n| `name` | Item display name | Singular wording for dialogue. |\n| `plural_name` | Singular wording | Plural wording for dialogue. |\n| `requires_villager_armed` | `false` | Match only villagers with a usable weapon. |\n| `requires_villager_unarmed` | `false` | Match only villagers without a usable weapon. |\n\nPayment counts are clamped from 1 through 64.\n\nPacification entries do not use explicit IDs, `replace`, or `remove`. All files are combined. To replace a lower-priority file, override the same namespace and file path.\n\n## Reputation Can Still Refuse Payment\n\nA valid payment does not guarantee success. The server can block pacification when the player's reputation is too low. In that case, the item is not consumed and the matching pacify refusal line is shown.\n\n## Dialogue Example\n\nPlace a line under:\n\n```text\ndata/my_pack/dialogue/en_us/my_pack/pacify/00_toolsmith.json\n```\n\n```json\n{\n  \"id\": \"my_pack.pacify.toolsmith\",\n  \"professions\": [\"minecraft:toolsmith\"],\n  \"text\": \"Fine. Leave the {payment_cost} {payment_items} and walk away.\"\n}\n```\n",
      "text": "Pacification Pacification files choose which held items can calm a villager or wandering trader that is hostile toward the player. A successful payment consumes the required count and clears that hostility. These files choose the payment. The spoken success, failure, and refusal lines belong in normal dialogue under a pacify/ folder. Path Pacification data is fixed to the villagerretaliation namespace: Simple Example A hostile villager can accept eight emeralds. The player right clicks while holding the payment in the used hand or off hand. Modded Currency Example name and plural name control the item wording used by pacification dialogue. They do not rename the item itself. Item Tag Example A leading is optional in tag and tags. Profession Specific Example The required count is chosen inclusively from 2 through 4 for each offer. How A Rule Is Chosen 1. The held item, profession, and optional armed state must match. 2. If any matching rule names a profession, general rules are ignored. 3. The highest priority wins. 4. If priorities tie, the rule loaded first wins. This means a profession specific payment can safely override a general payment without giving it a higher priority. Main Fields Field Default Meaning item or items None One or more exact item IDs. tag or tags None One or more item tags. count None Exact payment count. min count 1 Lowest randomized payment count when count is absent. max count min count Highest randomized payment count. professions Any Restrict the rule to one or more villager professions. priority 0 Higher values win between otherwise eligible rules. name Item display name Singular wording for dialogue. plural name Singular wording Plural wording for dialogue. requires villager armed false Match only villagers with a usable weapon. requires villager unarmed false Match only villagers without a usable weapon. Payment counts are clamped from 1 through 64. Pacification entries do not use explicit IDs, replace, or remove. All files are combined. To replace a lower priority file, override the same namespace and file path. Reputation Can Still Refuse Payment A valid payment does not guarantee success. The server can block pacification when the player's reputation is too low. In that case, the item is not consumed and the matching pacify refusal line is shown. Dialogue Example Place a line under:",
      "headings": [
        {
          "level": 2,
          "title": "Path"
        },
        {
          "level": 2,
          "title": "Simple Example"
        },
        {
          "level": 2,
          "title": "Modded Currency Example"
        },
        {
          "level": 2,
          "title": "Item Tag Example"
        },
        {
          "level": 2,
          "title": "Profession-Specific Example"
        },
        {
          "level": 2,
          "title": "How A Rule Is Chosen"
        },
        {
          "level": 2,
          "title": "Main Fields"
        },
        {
          "level": 2,
          "title": "Reputation Can Still Refuse Payment"
        },
        {
          "level": 2,
          "title": "Dialogue Example"
        }
      ],
      "related": [
        "forced-dialogue",
        "gifts",
        "json-reference",
        "currency-and-item-text"
      ]
    },
    {
      "slug": "profession-loot",
      "file": "Profession-Loot.md",
      "source": "wiki/Profession-Loot.md",
      "sourceKind": "wiki",
      "group": "Economy & Progression",
      "icon": "package",
      "title": "Profession Loot",
      "description": "Profession-aware drop rules backed by normal Minecraft loot tables.",
      "markdown": "# Profession Loot\n\nProfession loot adds datapack loot-table rolls when a villager dies. Rules can target vanilla or modded professions, and several matching rules can roll from the same death.\n\nBy default, `balance.requirePlayerKillForProfessionLoot` requires a player-caused kill before profession loot runs.\n\n## Paths\n\nThe rule file must use the `villagerretaliation` namespace:\n\n```text\ndata/villagerretaliation/profession_loot/<file>.json\n```\n\nThe referenced loot table can use any namespace:\n\n```text\ndata/<namespace>/loot_table/<path>.json\n```\n\nA rule value such as `my_pack:villager/profession/alchemist/common` points to:\n\n```text\ndata/my_pack/loot_table/villager/profession/alchemist/common.json\n```\n\n## Complete Example\n\nCreate the rule file:\n\n```text\ndata/villagerretaliation/profession_loot/my_pack_alchemist.json\n```\n\n```json\n{\n  \"tables\": [\n    {\n      \"id\": \"my_pack.alchemist.common\",\n      \"professions\": [\"examplemod:alchemist\"],\n      \"loot_table\": \"my_pack:villager/profession/alchemist/common\",\n      \"chance\": \"always\"\n    }\n  ]\n}\n```\n\nThen create the loot table:\n\n```text\ndata/my_pack/loot_table/villager/profession/alchemist/common.json\n```\n\n```json\n{\n  \"type\": \"minecraft:entity\",\n  \"pools\": [\n    {\n      \"rolls\": 1,\n      \"entries\": [\n        {\n          \"type\": \"minecraft:item\",\n          \"name\": \"minecraft:amethyst_shard\"\n        }\n      ]\n    }\n  ]\n}\n```\n\nWhen an alchemist villager dies and the server's player-kill requirement passes, the rule always rolls this loot table.\n\n## Rule Fields\n\n| Field | Required | Meaning |\n| --- | --- | --- |\n| `id` | Recommended | Stable rule ID used for replacement and removal. |\n| `professions` | No | One or more villager profession IDs. An omitted or empty list matches every profession. Vanilla IDs can omit `minecraft:`. |\n| `loot_table` | Yes | Namespaced Minecraft loot table ID. |\n| `chance` | No | `always`, `rare`, `very_rare`, or a number from `0.0` to `1.0`. The default is `always`. |\n| `requires_villager_armed` | No | Match only villagers with a usable weapon. |\n| `requires_villager_unarmed` | No | Match only villagers without a usable weapon. |\n| `remove` | No | Remove an earlier rule with the same `id`. |\n\n`rare` and `very_rare` use the server's configured rare-drop chances. A numeric chance is clamped to the range from 0 to 1.\n\nEvery matching rule rolls independently. Use this to separate common, rare, and very rare drops for one profession.\n\n## Add, Replace, Or Remove\n\nFiles are combined in resource load order.\n\nReuse an `id` to replace an earlier rule:\n\n```json\n{\n  \"tables\": [\n    {\n      \"id\": \"villagerretaliation.profession_loot.farmer.rare\",\n      \"professions\": [\"minecraft:farmer\"],\n      \"loot_table\": \"my_pack:villager/profession/farmer/rare\",\n      \"chance\": 0.2\n    }\n  ]\n}\n```\n\nRemove one rule:\n\n```json\n{\n  \"tables\": [\n    {\n      \"id\": \"villagerretaliation.profession_loot.farmer.rare\",\n      \"remove\": true\n    }\n  ]\n}\n```\n\nClear every rule loaded before the current file:\n\n```json\n{\n  \"replace\": true,\n  \"tables\": []\n}\n```\n\nGive every rule an explicit ID. Rules without one receive a generated ID based on file path and array position, which is harder to override safely.\n\n## Loot Table Context\n\nProfession loot uses Minecraft's entity loot context. The table can inspect the dead villager, death position, damage source, attacking entity, direct attacking entity, and last player damage when available. Player luck is included for player kills.\n\nKeep the loot table type as `minecraft:entity` unless a specific integration requires another supported shape.\n",
      "text": "Profession Loot Profession loot adds datapack loot table rolls when a villager dies. Rules can target vanilla or modded professions, and several matching rules can roll from the same death. By default, balance.requirePlayerKillForProfessionLoot requires a player caused kill before profession loot runs. Paths The rule file must use the villagerretaliation namespace: The referenced loot table can use any namespace: A rule value such as my pack:villager/profession/alchemist/common points to: Complete Example Create the rule file: Then create the loot table: When an alchemist villager dies and the server's player kill requirement passes, the rule always rolls this loot table. Rule Fields Field Required Meaning id Recommended Stable rule ID used for replacement and removal. professions No One or more villager profession IDs. An omitted or empty list matches every profession. Vanilla IDs can omit minecraft:. loot table Yes Namespaced Minecraft loot table ID. chance No always, rare, very rare, or a number from 0.0 to 1.0. The default is always. requires villager armed No Match only villagers with a usable weapon. requires villager unarmed No Match only villagers without a usable weapon. remove No Remove an earlier rule with the same id. rare and very rare use the server's configured rare drop chances. A numeric chance is clamped to the range from 0 to 1. Every matching rule rolls independently. Use this to separate common, rare, and very rare drops for one profession. Add, Replace, Or Remove Files are combined in resource load order. Reuse an id to replace an earlier rule: Remove one rule: Clear every rule loaded before the current file: Give every rule an explicit ID. Rules without one receive a generated ID based on file path and array position, which is harder to override safely. Loot Table Context Profession loot uses Minecraft's entity loot context. The table can inspect the dead villager, death position, damage source, attacking entity, direct attacking entity, and last player damage when available. Player luck is included for player kills. Keep the loot table type as minecraft:entity unless a specific integration requires another supported shape.",
      "headings": [
        {
          "level": 2,
          "title": "Paths"
        },
        {
          "level": 2,
          "title": "Complete Example"
        },
        {
          "level": 2,
          "title": "Rule Fields"
        },
        {
          "level": 2,
          "title": "Add, Replace, Or Remove"
        },
        {
          "level": 2,
          "title": "Loot Table Context"
        }
      ],
      "related": []
    },
    {
      "slug": "currency-and-item-text",
      "file": "Currency-And-Item-Text.md",
      "source": "wiki/Currency-And-Item-Text.md",
      "sourceKind": "wiki",
      "group": "Economy & Progression",
      "icon": "coins",
      "title": "Currency And Item Text",
      "description": "Customize currency icons, item names, and item count wording used by authored text.",
      "markdown": "# Currency And Item Text\n\nCurrency data chooses the item used by Villager Retaliation payment systems. Item-text data controls how item counts are written in dialogue and notices.\n\nUse both when replacing emeralds with another currency or when a locale needs plural forms that English rules cannot produce correctly.\n\n## Currency Path\n\n```text\ndata/villagerretaliation/currency/default.json\n```\n\nCurrency is fixed to the `villagerretaliation` namespace. Override `default.json` in a higher-priority datapack so there is one clear final definition.\n\n## Currency Example\n\n```json\n{\n  \"item\": \"examplemod:copper_coin\",\n  \"accepted_items\": [\n    \"examplemod:copper_coin\",\n    \"examplemod:silver_coin\"\n  ],\n  \"accepted_tags\": [\n    \"examplemod:coins\"\n  ],\n  \"name\": \"copper coin\",\n  \"plural_name\": \"copper coins\",\n  \"wallet_label\": \"Coins\",\n  \"icon_sprite\": \"examplemod:item/copper_coin\",\n  \"text_color\": \"#D9824A\"\n}\n```\n\n`item` is the primary currency. Systems that create a payment, refund, wallet withdrawal, or currency drop create this item.\n\n`accepted_items` and `accepted_tags` add items that can be used as payment. They do not change which item the mod creates when paying the player. The primary item is always accepted even if it is omitted from `accepted_items`.\n\n## Currency Fields\n\n| Field | Required | Meaning |\n| --- | --- | --- |\n| `item` | Yes | Registered primary item ID. An invalid item makes the file unusable. |\n| `accepted_items` or `items` | No | Extra registered item IDs accepted as payment. |\n| `accepted_tags` or `tags` | No | Item tags accepted as payment. A leading `#` is optional. |\n| `name` | No | Singular name used in notices. Defaults to the item's display name. |\n| `plural_name` | No | Plural name. Defaults to the singular name plus `s`. |\n| `wallet_label` | No | Label in the villager interaction wallet row. |\n| `icon_sprite` | No | GUI sprite ID. `textures/` and `.png` are optional in the value. |\n| `text_color` | No | Named color or hex RGB color for the wallet amount. |\n\n## Keep The Currency Tag In Sync\n\nRecipes, payment boxes, and client hints also use:\n\n```text\ndata/villagerretaliation/tags/item/currency.json\n```\n\nIf the primary or accepted currency items should work in those places, override or extend that item tag too.\n\n```json\n{\n  \"replace\": false,\n  \"values\": [\n    \"examplemod:copper_coin\",\n    \"examplemod:silver_coin\"\n  ]\n}\n```\n\nThe currency definition and item tag serve different code paths.\n\n## Item Text Path\n\n```text\ndata/villagerretaliation/item_text/<locale>/<file>.json\n```\n\n`en_us` is the fallback. A player's locale is loaded on top of it.\n\n## Simple Item Name Example\n\nUse explicit forms for items with irregular or uncountable names:\n\n```json\n{\n  \"items\": {\n    \"examplemod:copper_coin\": {\n      \"one\": \"copper coin\",\n      \"other\": \"copper coins\"\n    },\n    \"minecraft:bread\": {\n      \"one\": \"bread\",\n      \"other\": \"bread\"\n    }\n  }\n}\n```\n\nThis changes text such as `{held_item}` or a counted payment name. It does not rename the item stack in normal Minecraft tooltips.\n\n## Count Forms\n\nEnglish uses `one` for a count of 1 and `other` for everything else:\n\n```json\n{\n  \"forms\": [\n    {\n      \"id\": \"one\",\n      \"count_pattern\": \"1\",\n      \"format\": \"{item}\"\n    },\n    {\n      \"id\": \"other\",\n      \"format\": \"{count} {item}\"\n    }\n  ]\n}\n```\n\n`count_pattern` is a regular expression matched against the number. The final form without a pattern acts as the fallback.\n\nLocales with more forms can define them in order:\n\n```json\n{\n  \"forms\": [\n    {\n      \"id\": \"one\",\n      \"count_pattern\": \"1\",\n      \"format\": \"{item}\"\n    },\n    {\n      \"id\": \"few\",\n      \"count_pattern\": \"[2-4]\",\n      \"format\": \"{count} {item}\"\n    },\n    {\n      \"id\": \"other\",\n      \"format\": \"{count} {item}\"\n    }\n  ]\n}\n```\n\nEach item and currency name can then supply values for `one`, `few`, and `other`.\n\nCurrency wording for the locale uses the same form IDs:\n\n```json\n{\n  \"currency\": {\n    \"one\": \"copper coin\",\n    \"few\": \"copper coins\",\n    \"other\": \"copper coins\"\n  }\n}\n```\n\n## Automatic Word Rules\n\n`rules` are regular-expression replacements used only when an item does not define an explicit name for the selected form. They are useful for broad language rules, but explicit item entries are safer for exceptions.\n\n```json\n{\n  \"rules\": [\n    {\n      \"forms\": [\"other\"],\n      \"pattern\": \"(?i)(.*[^aeiou])y$\",\n      \"replacement\": \"$1ies\"\n    }\n  ]\n}\n```\n\nInvalid regular expressions are ignored.\n",
      "text": "Currency And Item Text Currency data chooses the item used by Villager Retaliation payment systems. Item text data controls how item counts are written in dialogue and notices. Use both when replacing emeralds with another currency or when a locale needs plural forms that English rules cannot produce correctly. Currency Path Currency is fixed to the villagerretaliation namespace. Override default.json in a higher priority datapack so there is one clear final definition. Currency Example item is the primary currency. Systems that create a payment, refund, wallet withdrawal, or currency drop create this item. accepted items and accepted tags add items that can be used as payment. They do not change which item the mod creates when paying the player. The primary item is always accepted even if it is omitted from accepted items. Currency Fields Field Required Meaning item Yes Registered primary item ID. An invalid item makes the file unusable. accepted items or items No Extra registered item IDs accepted as payment. accepted tags or tags No Item tags accepted as payment. A leading is optional. name No Singular name used in notices. Defaults to the item's display name. plural name No Plural name. Defaults to the singular name plus s. wallet label No Label in the villager interaction wallet row. icon sprite No GUI sprite ID. textures/ and .png are optional in the value. text color No Named color or hex RGB color for the wallet amount. Keep The Currency Tag In Sync Recipes, payment boxes, and client hints also use: If the primary or accepted currency items should work in those places, override or extend that item tag too. The currency definition and item tag serve different code paths. Item Text Path en us is the fallback. A player's locale is loaded on top of it. Simple Item Name Example Use explicit forms for items with irregular or uncountable names: This changes text such as {held item} or a counted payment name. It does not rename the item stack in normal Minecraft tooltips. Count Forms English uses one for a count of 1 and other for everything else: count pattern is a regular expression matched against the number. The final form without a pattern acts as the fallback. Locales with more forms can define them in order: Each item and currency name can then supply values for one, few, and other. Currency wording for the locale uses the same form IDs: Automatic Word Rules rules are regular expression replacements used only when an item does not define an explicit name for the selected form. They are useful for broad language rules, but explicit item entries are safer for exceptions. Invalid regular expressions are ignored.",
      "headings": [
        {
          "level": 2,
          "title": "Currency Path"
        },
        {
          "level": 2,
          "title": "Currency Example"
        },
        {
          "level": 2,
          "title": "Currency Fields"
        },
        {
          "level": 2,
          "title": "Keep The Currency Tag In Sync"
        },
        {
          "level": 2,
          "title": "Item Text Path"
        },
        {
          "level": 2,
          "title": "Simple Item Name Example"
        },
        {
          "level": 2,
          "title": "Count Forms"
        },
        {
          "level": 2,
          "title": "Automatic Word Rules"
        }
      ],
      "related": [
        "pacification",
        "sell-box-and-daily-market",
        "localization"
      ]
    },
    {
      "slug": "duel-kits",
      "file": "Duel-Kits.md",
      "source": "wiki/Duel-Kits.md",
      "sourceKind": "wiki",
      "group": "Economy & Progression",
      "icon": "swords",
      "title": "Duel Kits",
      "description": "Define equipment, supplies, and bring-your-own rules used by villager duels.",
      "markdown": "# Duel Kits\n\nWhen Curios or Accessories is installed, assigned duel kits temporarily clear equipped accessory and cosmetic slots so their combat effects cannot bypass the kit. The exact slot contents and render toggles are restored after the duel, including through disconnect or crash recovery. Bring-your-own-loadout duels leave accessory gear active and preserve normal durability changes.\n\nDuel kits control the temporary equipment offered when a player challenges a villager or another player to a duel. A kit can use vanilla or modded items.\n\n## Path And ID\n\n```text\ndata/<namespace>/duel_kits/<path>.json\n```\n\nThe file path becomes the kit ID. For example:\n\n```text\ndata/my_pack/duel_kits/champion.json\n```\n\ncreates `my_pack:champion`.\n\nA higher-priority datapack can replace a kit by using the same namespace and path. There is no separate `id` field.\n\n## Small Melee Kit\n\n```json\n{\n  \"name\": \"iron practice gear\",\n  \"description\": \"Iron swords and shields.\",\n  \"sort_order\": 50,\n  \"combat_style\": \"melee\",\n  \"player\": {\n    \"inventory\": [\n      {\n        \"slot\": 0,\n        \"stack\": {\n          \"id\": \"minecraft:iron_sword\"\n        }\n      }\n    ],\n    \"equipment\": {\n      \"offhand\": {\n        \"id\": \"minecraft:shield\"\n      }\n    }\n  },\n  \"villager\": {\n    \"equipment\": {\n      \"mainhand\": {\n        \"id\": \"minecraft:iron_sword\"\n      },\n      \"offhand\": {\n        \"id\": \"minecraft:shield\"\n      }\n    }\n  }\n}\n```\n\nThis kit puts an iron sword in the player's first inventory slot. It equips the villager with a sword and gives both sides a shield.\n\n## Main Fields\n\n| Field | Required | Meaning |\n| --- | --- | --- |\n| `name` | Yes | Short name shown for the selected kit. Maximum 128 characters. |\n| `description` | Yes | Explanation shown in the kit list. Maximum 512 characters. |\n| `sort_order` | No | Lower values appear first. The default is `100`. The kit ID breaks ties. |\n| `combat_style` | No | `melee` or `ranged`. The default is `melee`. This controls the skill trained by the villager. |\n| `bring_your_own` | No | When `true`, both sides keep their current gear. Do not include `player` or `villager` item sections. |\n| `player` | No | Temporary items assigned to the player. |\n| `villager` | No | Temporary items assigned to the villager. |\n\nEach participant can have:\n\n| Field | Meaning |\n| --- | --- |\n| `inventory` | Items placed in numbered inventory slots. Each slot can be used only once. Valid slots are `0` through `255`. |\n| `equipment` | Items equipped in `mainhand`, `offhand`, `feet`, `legs`, `chest`, or `head`. |\n\n`stack` uses Minecraft 1.21.1 item stack JSON. A count belongs beside the item ID:\n\n```json\n{\n  \"slot\": 1,\n  \"stack\": {\n    \"id\": \"minecraft:arrow\",\n    \"count\": 64\n  }\n}\n```\n\nComponents can add enchantments or modded item data:\n\n```json\n{\n  \"id\": \"minecraft:diamond_sword\",\n  \"components\": {\n    \"minecraft:enchantments\": {\n      \"levels\": {\n        \"minecraft:sharpness\": 3\n      }\n    }\n  }\n}\n```\n\nInvalid kit files are skipped and identified in the server log. The complete enchanted example is in `example-packs/custom-duel-kits/`.\n",
      "text": "Duel Kits When Curios or Accessories is installed, assigned duel kits temporarily clear equipped accessory and cosmetic slots so their combat effects cannot bypass the kit. The exact slot contents and render toggles are restored after the duel, including through disconnect or crash recovery. Bring your own loadout duels leave accessory gear active and preserve normal durability changes. Duel kits control the temporary equipment offered when a player challenges a villager or another player to a duel. A kit can use vanilla or modded items. Path And ID The file path becomes the kit ID. For example: creates my pack:champion. A higher priority datapack can replace a kit by using the same namespace and path. There is no separate id field. Small Melee Kit This kit puts an iron sword in the player's first inventory slot. It equips the villager with a sword and gives both sides a shield. Main Fields Field Required Meaning name Yes Short name shown for the selected kit. Maximum 128 characters. description Yes Explanation shown in the kit list. Maximum 512 characters. sort order No Lower values appear first. The default is 100. The kit ID breaks ties. combat style No melee or ranged. The default is melee. This controls the skill trained by the villager. bring your own No When true, both sides keep their current gear. Do not include player or villager item sections. player No Temporary items assigned to the player. villager No Temporary items assigned to the villager. Each participant can have: Field Meaning inventory Items placed in numbered inventory slots. Each slot can be used only once. Valid slots are 0 through 255. equipment Items equipped in mainhand, offhand, feet, legs, chest, or head. stack uses Minecraft 1.21.1 item stack JSON. A count belongs beside the item ID: Components can add enchantments or modded item data: Invalid kit files are skipped and identified in the server log. The complete enchanted example is in example packs/custom duel kits/.",
      "headings": [
        {
          "level": 2,
          "title": "Path And ID"
        },
        {
          "level": 2,
          "title": "Small Melee Kit"
        },
        {
          "level": 2,
          "title": "Main Fields"
        }
      ],
      "related": [
        "player-raids",
        "natural-job-armor",
        "example-packs"
      ]
    },
    {
      "slug": "skill-trades",
      "file": "Skill-Trades.md",
      "source": "wiki/Skill-Trades.md",
      "sourceKind": "wiki",
      "group": "Economy & Progression",
      "icon": "badge-percent",
      "title": "Skill Trades",
      "description": "Skill-gated trades, rank bands, refresh cycles, and targetable Special Orders.",
      "markdown": "# Skill Trades\n\nSkill trades are extra merchant offers gated by a villager's Skills rather than only by vanilla trade level.\n\n## Path\n\n```text\ndata/<namespace>/skill_trades/<file>.json\n```\n\n## Minimal Entry\n\n```json\n{\n  \"entries\": [\n    {\n      \"id\": \"my_pack:cartographer_basic_map\",\n      \"professions\": [\"minecraft:cartographer\"],\n      \"skills\": [\"villagerretaliation:cartography\"],\n      \"min_rank\": \"novice\",\n      \"max_rank\": \"apprentice\",\n      \"villager_level\": 1,\n      \"chance\": 0.8,\n      \"weight\": 12,\n      \"cost\": { \"item\": \"minecraft:emerald\", \"count\": 8 },\n      \"result\": { \"item\": \"minecraft:map\", \"count\": 1 },\n      \"max_uses\": { \"base\": 4 },\n      \"xp\": 4,\n      \"price_multiplier\": 0.05,\n      \"quality_scaling\": true\n    }\n  ]\n}\n```\n\n## Common Usage Areas\n\n### Low-Skill Extra Stock\n\nUse `min_rank` plus `max_rank` to keep a trade in the low tier only.\n\n```json\n{\n\"min_rank\": \"novice\",\n\"max_rank\": \"apprentice\"\n}\n```\n\n### High-Skill Specialty Offer\n\n```json\n{\n  \"id\": \"my_pack:farmer_master_hoe\",\n  \"professions\": [\"minecraft:farmer\"],\n  \"skills\": [\"villagerretaliation:farming\"],\n  \"min_rank\": \"master\",\n  \"villager_level\": 5,\n  \"cost\": { \"item\": \"minecraft:emerald\", \"count\": 18 },\n  \"result\": {\n    \"item\": \"minecraft:diamond_hoe\",\n    \"count\": 1\n  },\n  \"conditions\": {\n    \"config_flags\": [\"skillTradeAllowHighTierEquipment\"]\n  }\n}\n```\n\n### Targetable Special Order\n\nBeta.12 lets high-reputation players request specific skill trades directly.\n\n```json\n{\n\"request\": {\n  \"targetable\": true,\n  \"display_priority\": 20,\n  \"min_reputation\": \"respected\",\n  \"wait_days\": 2,\n  \"cooldown_days\": 3\n}\n}\n```\n\n### Wandering Trader Entry\n\nUse the wandering trader profession id:\n\n```json\n{\n  \"id\": \"my_pack:wandering_trader_shell\",\n  \"professions\": [\"minecraft:wandering_trader\"],\n  \"skills\": [\"villagerretaliation:trading\"],\n  \"min_rank\": \"master\",\n  \"chance\": 0.3,\n  \"cost\": { \"item\": \"minecraft:emerald\", \"count\": 15 },\n  \"result\": { \"item\": \"minecraft:nautilus_shell\", \"count\": 1 }\n}\n```\n\n## Main Fields\n\n| Field | Use |\n| --- | --- |\n| `professions` | Which villagers can roll the entry |\n| `skills` | Which skill ids gate it |\n| `min_rank` / `max_rank` | Skill rank band |\n| `villager_level` | Vanilla trade level gate |\n| `cost` | Input item and count |\n| `result` | Output item and count |\n| `chance` / `weight` | Selection tuning |\n| `quality_scaling` | Rank-based improvements |\n| `request` | Special Order metadata |\n\n## Refresh Cycles\n\nEach villager keeps a persistent cycle for its current profession. Random trade refreshes use weighted sampling without replacement: a larger `weight` makes an entry more likely to appear earlier, but every currently eligible definition is exposed at most once before the cycle resets. The last fulfilled definition is held across the boundary so a multi-entry pool cannot immediately repeat it.\n\nDisplayed results, definitions already reserved by another pending slot, entries that no longer match, and offers that cannot currently be constructed are skipped without stalling the cycle. `chance` still applies to initial natural trade generation. Requested refresh cycles consider every otherwise eligible definition.\n\nOn a datapack reload, loaded villagers reconcile lazily the next time their trade state is used. Newly added definitions join the current remainder, removed or invalid definitions leave it, and valid pending requests keep their accepted definition and deterministic offer seed. A malformed or removed pending request is canceled independently so its slot and active-order reservation are cleared.\n\nIf a canceled Special Order was prepaid, the villager stores a refund claim for the player who paid. That player receives the items the next time they interact with the same villager. Inventory overflow is dropped safely at the player, and a delivered claim cannot be paid twice.\n\n## Authoring and Validation\n\nThe datapack builder has a **Skill Trades** section that imports and exports arbitrary namespaces and nested `skill_trades` paths without flattening them. Its JSON editor preserves supported advanced fields such as conditions, quality scaling, enchantments, and complete `request` metadata.\n\nValidate a file offline with:\n\n```text\nnode tools/validate-dialogue-data.mjs --skill-trade path/to/trades.json\n```\n\nThe authoritative generated schema is `tools/datapack-builder/skill-trades.schema.json`. Runtime diagnostics identify the resource, entry index or id, field path, reason, and corrective guidance. Invalid siblings are skipped individually. Valid entries in the same file continue loading. Duplicate ids resolve deterministically in resource load order, while `remove` deletes the definition currently associated with that id and root-level `replace` clears definitions loaded earlier.\n\n## Best Practice\n\nModel skill trades in bands:\n\n- low tier for novice or apprentice villagers\n- mid tier for skilled or expert villagers\n- rare high tier for expert or master villagers\n\nThat gives progression without flooding early villagers with endgame stock.\n\nLoaded offers are not rebuilt immediately after a datapack reload. Newly generated offers and later refresh cycles use the updated definitions.\n",
      "text": "Skill Trades Skill trades are extra merchant offers gated by a villager's Skills rather than only by vanilla trade level. Path Minimal Entry Common Usage Areas Low Skill Extra Stock Use min rank plus max rank to keep a trade in the low tier only. High Skill Specialty Offer Targetable Special Order Beta.12 lets high reputation players request specific skill trades directly. Wandering Trader Entry Use the wandering trader profession id: Main Fields Field Use professions Which villagers can roll the entry skills Which skill ids gate it min rank / max rank Skill rank band villager level Vanilla trade level gate cost Input item and count result Output item and count chance / weight Selection tuning quality scaling Rank based improvements request Special Order metadata Refresh Cycles Each villager keeps a persistent cycle for its current profession. Random trade refreshes use weighted sampling without replacement: a larger weight makes an entry more likely to appear earlier, but every currently eligible definition is exposed at most once before the cycle resets. The last fulfilled definition is held across the boundary so a multi entry pool cannot immediately repeat it. Displayed results, definitions already reserved by another pending slot, entries that no longer match, and offers that cannot currently be constructed are skipped without stalling the cycle. chance still applies to initial natural trade generation. Requested refresh cycles consider every otherwise eligible definition. On a datapack reload, loaded villagers reconcile lazily the next time their trade state is used. Newly added definitions join the current remainder, removed or invalid definitions leave it, and valid pending requests keep their accepted definition and deterministic offer seed. A malformed or removed pending request is canceled independently so its slot and active order reservation are cleared. If a canceled Special Order was prepaid, the villager stores a refund claim for the player who paid. That player receives the items the next time they interact with the same villager. Inventory overflow is dropped safely at the player, and a delivered claim cannot be paid twice. Authoring and Validation The datapack builder has a Skill Trades section that imports and exports arbitrary namespaces and nested skill trades paths without flattening them. Its JSON editor preserves supported advanced fields such as conditions, quality scaling, enchantments, and complete request metadata. Validate a file offline with: The authoritative generated schema is tools/datapack builder/skill trades.schema.json. Runtime diagnostics identify the resource, entry index or id, field path, reason, and corrective guidance. Invalid siblings are skipped individually. Valid entries in the same file continue loading. Duplicate ids resolve deterministically in resource load order, while remove deletes the definition currently associated with that id and root level replace clears definitions loaded earlier. Best Practice Model skill trades in bands: low tier for novice or apprentice villagers mid tier for skilled or expert villagers rare high tier for expert or master villagers That gives progression without flooding early villagers with endgame stock. Loaded offers are not rebuilt immediately after a datapack reload. Newly generated offers and later refresh cycles use the updated definitions.",
      "headings": [
        {
          "level": 2,
          "title": "Path"
        },
        {
          "level": 2,
          "title": "Minimal Entry"
        },
        {
          "level": 2,
          "title": "Common Usage Areas"
        },
        {
          "level": 3,
          "title": "Low-Skill Extra Stock"
        },
        {
          "level": 3,
          "title": "High-Skill Specialty Offer"
        },
        {
          "level": 3,
          "title": "Targetable Special Order"
        },
        {
          "level": 3,
          "title": "Wandering Trader Entry"
        },
        {
          "level": 2,
          "title": "Main Fields"
        },
        {
          "level": 2,
          "title": "Refresh Cycles"
        },
        {
          "level": 2,
          "title": "Authoring and Validation"
        },
        {
          "level": 2,
          "title": "Best Practice"
        }
      ],
      "related": [
        "datapack-generator",
        "json-reference",
        "example-packs"
      ]
    },
    {
      "slug": "sell-box-and-daily-market",
      "file": "Sell-Box-And-Daily-Market.md",
      "source": "wiki/Sell-Box-And-Daily-Market.md",
      "sourceKind": "wiki",
      "group": "Economy & Progression",
      "icon": "store",
      "title": "Sell Box And Daily Market",
      "description": "Add, override, group, or disable daily market sell-price definitions.",
      "markdown": "# Sell Box And Daily Market\n\nThe Sell Box is a one-slot village market. Put a saleable stack in the slot and press **Sell**; the box uses that village's price for the day and sells the whole stack in one transaction.\n\nIf you insert a second valid stack while the slot is occupied, the first stack sells and the new one becomes pending. The box keeps the exact balance, including fractions. **Collect** gives the player as many whole currency items as will fit and leaves the remainder in the box.\n\nAutomation follows the same rules as the screen. Hoppers and other item handlers insert through the top or sides and extract whole currency items from the bottom. Assigned output couriers can deliver sale items, and supply couriers can collect the finished currency. None of these routes bypasses the stack checks described below.\n\n## Where Price Files Go\n\nPut sell-price files here:\n\n```text\ndata/<namespace>/sell_prices/<path>.json\n```\n\nThe namespace and path become the definition ID. A file at `data/my_pack/sell_prices/coal.json` creates `my_pack:coal`.\n\n## Use Explicit Rates For New Packs\n\nNew definitions should put prices in a `rates` array. Each entry is one exchange-rate family, so its `item_count` always stays paired with its `currency_count`.\n\n```json\n{\n  \"item\": \"minecraft:coal\",\n  \"rates\": [\n    {\n      \"item_count\": 15,\n      \"currency_count\": 1\n    }\n  ],\n  \"market_group\": \"villagerretaliation:fuel\"\n}\n```\n\nBefore demand and supply adjustments, 15 coal are worth one currency item. A stack of 30 starts from a value of two.\n\nEither side can be a fixed positive integer or an inclusive `min` and `max` range:\n\n```json\n{\n  \"item\": \"#minecraft:logs\",\n  \"rates\": [\n    {\n      \"item_count\": {\n        \"min\": 5,\n        \"max\": 8\n      },\n      \"currency_count\": 1\n    }\n  ],\n  \"market_group\": \"villagerretaliation:logs\"\n}\n```\n\nHere, each village can choose from the generated rates of 5, 6, 7, or 8 logs per currency. The choice is stable for that village and day; reloading does not reroll it.\n\nA currency range works the same way:\n\n```json\n{\n  \"item\": \"minecraft:diamond\",\n  \"rates\": [\n    {\n      \"item_count\": 1,\n      \"currency_count\": {\n        \"min\": 10,\n        \"max\": 15\n      }\n    }\n  ]\n}\n```\n\nWhen both sides of one rate are ranges, every combination belongs to that one family. Equivalent fractions are collapsed before the daily choice is made.\n\n## Discrete Prices Stay Discrete\n\nUse separate rate entries when only a few exact prices should be possible:\n\n```json\n{\n  \"item\": \"#vr-food-sellbox:cocktails\",\n  \"rates\": [\n    {\n      \"item_count\": 1,\n      \"currency_count\": 10\n    },\n    {\n      \"item_count\": 1,\n      \"currency_count\": 15\n    },\n    {\n      \"item_count\": 1,\n      \"currency_count\": 25\n    }\n  ],\n  \"market_group\": \"vfs:cocktails\"\n}\n```\n\nThat produces 10, 15, or 25 currency per cocktail. It does not quietly fill in 11 through 24. There is no index pairing between separate item-count and currency-count arrays; the rate object is the association.\n\nDifferent shapes can live beside each other:\n\n```json\n{\n  \"item\": \"minecraft:iron_ingot\",\n  \"rates\": [\n    {\n      \"item_count\": 5,\n      \"currency_count\": 1\n    },\n    {\n      \"item_count\": 10,\n      \"currency_count\": 3\n    },\n    {\n      \"item_count\": 20,\n      \"currency_count\": 8\n    }\n  ]\n}\n```\n\n## Existing beta.13 Files Still Work\n\nThe older top-level pair remains supported:\n\n```json\n{\n  \"item\": \"minecraft:charcoal\",\n  \"item_count\": {\n    \"min\": 12,\n    \"max\": 20\n  },\n  \"currency_count\": 1\n}\n```\n\nIt loads as one rate with the same two counts. Keep old packs as they are if they are working, but prefer `rates` when writing something new. A file cannot use `rates` and the legacy top-level pricing fields together; the server reports the conflict and skips that definition.\n\n## Items And Tags\n\n`item` accepts a registered item ID or a `#`-prefixed item tag. One tag definition applies to every current member, including modded members, so a large tag does not need one JSON file per item.\n\nTags are resolved again on `/reload`. If `market_group` is omitted, an item definition defaults to the item ID and a tag definition defaults to the tag ID without `#`. Unknown or empty tags are reported and skipped.\n\n## Matching Item Components\n\nUse `components` when two stacks of the same item should have different prices. Values are decoded with the registered Minecraft 1.21.1 data-component codec, including component types added by other mods.\n\nFor example, a level-six Miner's Star from Kaleidoscope Tavern can have its own price (for pink lol):\n\n```json\n{\n  \"item\": \"kaleidoscope_tavern:miners_star\",\n  \"components\": {\n    \"kaleidoscope_tavern:brew_level\": 6\n  },\n  \"priority\": 10,\n  \"rates\": [\n    {\n      \"item_count\": 1,\n      \"currency_count\": 15\n    }\n  ],\n  \"market_group\": \"kaleidoscope_tavern:miners_stars\"\n}\n```\n\nExact values use the component's normal codec representation. Numeric component values can use inclusive ranges:\n\n```json\n{\n  \"item\": \"example:graded_gem\",\n  \"components\": {\n    \"example:quality\": {\n      \"min\": 3,\n      \"max\": 6\n    }\n  },\n  \"rates\": [\n    {\n      \"item_count\": 1,\n      \"currency_count\": 8\n    }\n  ]\n}\n```\n\nA range can have only `min` or only `max`. Ranges are rejected for components whose decoded value is not numeric.\n\nFor NBT-like custom item data, use the vanilla `minecraft:custom_data` component:\n\n```json\n{\n  \"item\": \"example:keepsake\",\n  \"components\": {\n    \"minecraft:custom_data\": {\n      \"maker\": \"ada\",\n      \"edition\": 3\n    }\n  },\n  \"rates\": [\n    {\n      \"item_count\": 1,\n      \"currency_count\": 4\n    }\n  ]\n}\n```\n\nCustom-data objects use subset matching. The stack must contain the required keys and values, but unrelated custom data does not prevent a match.\n\nInvalid component IDs, malformed codec values, and nonnumeric range mistakes produce a datapack diagnostic and skip the bad definition instead of breaking the reload.\n\n## Exact Component Shorthand\n\nFor a specific item with exact component values, vanilla item syntax is also accepted:\n\n```json\n{\n  \"item\": \"kaleidoscope_tavern:miners_star[kaleidoscope_tavern:brew_level=6]\",\n  \"rates\": [\n    {\n      \"item_count\": 1,\n      \"currency_count\": 15\n    }\n  ]\n}\n```\n\nMinecraft's own item parser handles this form. Use the structured `components` object for ranges, custom-data predicates, and tag selectors.\n\n## Remaining Durability\n\n`durability` means durability remaining, not raw damage:\n\n```json\n{\n  \"item\": \"#example:valuable_pickaxes\",\n  \"components\": {\n    \"example:quality\": {\n      \"min\": 3\n    }\n  },\n  \"durability\": {\n    \"min\": 250\n  },\n  \"rates\": [\n    {\n      \"item_count\": 1,\n      \"currency_count\": 5\n    },\n    {\n      \"item_count\": 1,\n      \"currency_count\": 8\n    },\n    {\n      \"item_count\": 1,\n      \"currency_count\": 12\n    }\n  ],\n  \"market_group\": \"example:tools\"\n}\n```\n\n`min: 250` means at least 250 durability remains. `max: 25` means 25 or less remains. You can provide both bounds. Non-damageable items never match a durability predicate, and negative, reversed, or impossible conditions are reported during reload.\n\n## Overlapping Definitions And Priority\n\nSeveral definitions can target the same base item. This is useful for a broad fallback plus a component-specific price:\n\n```json\n{\n  \"item\": \"kaleidoscope_tavern:miners_star\",\n  \"rates\": [\n    {\n      \"item_count\": 1,\n      \"currency_count\": 5\n    }\n  ]\n}\n```\n\n```json\n{\n  \"item\": \"kaleidoscope_tavern:miners_star\",\n  \"components\": {\n    \"kaleidoscope_tavern:brew_level\": 6\n  },\n  \"priority\": 10,\n  \"rates\": [\n    {\n      \"item_count\": 1,\n      \"currency_count\": 15\n    }\n  ]\n}\n```\n\nThe server first gathers item and tag selectors that include the stack, then checks components and durability. The matching definition with the highest `priority` wins. The default priority is `0`; having more predicates does not raise it automatically.\n\nEqual-priority ties are deterministic: the later-sorting definition ID wins, matching the old beta.13 conflict behavior. If the predicates can overlap, reload diagnostics ask you to set an explicit priority rather than leaving that tie accidental.\n\n## Fields\n\n| Field | Required | Meaning |\n| --- | --- | --- |\n| `item` | Yes | Registered item ID, exact item-component shorthand, or `#`-prefixed item tag. |\n| `rates` | New files | One or more associated `item_count` and `currency_count` families. |\n| `item_count` + `currency_count` | Legacy alternative | Backward-compatible top-level pair, treated as one rate. Do not combine it with `rates`. |\n| `components` | No | Exact codec-backed values or numeric min/max predicates. |\n| `durability` | No | Remaining-durability `min`, `max`, or both. |\n| `priority` | No | Explicit winner among matching definitions. Defaults to `0`. |\n| `market_group` | No | Demand and supply group shared by related definitions. |\n| `enabled` | No | Set to `false` to disable a lower pack's resource at the same path. |\n\nCount values must be positive. `item_count` cannot exceed 256, each count range can contain at most 256 values, a definition can contain at most 256 rates, and total generated combinations are capped. These limits keep a malformed pack from creating an enormous candidate list.\n\n## The Village Market Still Works The Same Way\n\nPredicates decide whether a stack is eligible and which definition wins. After that selection, the existing market does the rest:\n\n- resolve the local village\n- choose the village's daily demand band\n- select one candidate base rate deterministically\n- apply that village's current supply pressure\n- calculate the exact payout\n- record the completed sale's pressure\n\n`market_group` is still the key that makes related goods share demand and supply pressure. Different villages keep separate pressure. Adding a component predicate does not create a second economy.\n\nThe configured primary currency and accepted currency tags remain unsaleable, even if a sell-price file targets them.\n\n## Add, Replace, Or Disable\n\nA new resource path adds a definition. Reusing the same namespace and path in a higher-priority datapack replaces that resource. To disable a lower pack's file, replace the same path with:\n\n```json\n{\n  \"enabled\": false\n}\n```\n\nInvalid ranges, selectors, components, and priorities are reported during reload. A bad entry is skipped; it does not crash the server.\n\n## Built-In Price Basis\n\nBuilt-in definitions are based on direct Minecraft 1.21.1 villager and wandering-trader offers. They do not copy live trade demand, reputation discounts, mod-added offers, or secondary recipe inputs. The village market applies its own daily demand and supply pressure after choosing the matching definition.\n",
      "text": "Sell Box And Daily Market The Sell Box is a one slot village market. Put a saleable stack in the slot and press Sell ; the box uses that village's price for the day and sells the whole stack in one transaction. If you insert a second valid stack while the slot is occupied, the first stack sells and the new one becomes pending. The box keeps the exact balance, including fractions. Collect gives the player as many whole currency items as will fit and leaves the remainder in the box. Automation follows the same rules as the screen. Hoppers and other item handlers insert through the top or sides and extract whole currency items from the bottom. Assigned output couriers can deliver sale items, and supply couriers can collect the finished currency. None of these routes bypasses the stack checks described below. Where Price Files Go Put sell price files here: The namespace and path become the definition ID. A file at data/my pack/sell prices/coal.json creates my pack:coal. Use Explicit Rates For New Packs New definitions should put prices in a rates array. Each entry is one exchange rate family, so its item count always stays paired with its currency count. Before demand and supply adjustments, 15 coal are worth one currency item. A stack of 30 starts from a value of two. Either side can be a fixed positive integer or an inclusive min and max range: Here, each village can choose from the generated rates of 5, 6, 7, or 8 logs per currency. The choice is stable for that village and day; reloading does not reroll it. A currency range works the same way: When both sides of one rate are ranges, every combination belongs to that one family. Equivalent fractions are collapsed before the daily choice is made. Discrete Prices Stay Discrete Use separate rate entries when only a few exact prices should be possible: That produces 10, 15, or 25 currency per cocktail. It does not quietly fill in 11 through 24. There is no index pairing between separate item count and currency count arrays; the rate object is the association. Different shapes can live beside each other: Existing beta.13 Files Still Work The older top level pair remains supported: It loads as one rate with the same two counts. Keep old packs as they are if they are working, but prefer rates when writing something new. A file cannot use rates and the legacy top level pricing fields together; the server reports the conflict and skips that definition. Items And Tags item accepts a registered item ID or a prefixed item tag. One tag definition applies to every current member, including modded members, so a large tag does not need one JSON file per item. Tags are resolved again on /reload. If market group is omitted, an item definition defaults to the item ID and a tag definition defaults to the tag ID without . Unknown or empty tags are reported and skipped. Matching Item Components Use components when two stacks of the same item should have different prices. Values are decoded with the registered Minecraft 1.21.1 data component codec, including component types added by other mods. For example, a level six Miner's Star from Kaleidoscope Tavern can have its own price (for pink lol): Exact values use the component's normal codec representation. Numeric component values can use inclusive ranges: A range can have only min or only max. Ranges are rejected for components whose decoded value is not numeric. For NBT like custom item data, use the vanilla minecraft:custom data component: Custom data objects use subset matching. The stack must contain the required keys and values, but unrelated custom data does not prevent a match. Invalid component IDs, malformed codec values, and nonnumeric range mistakes produce a datapack diagnostic and skip the bad definition instead of breaking the reload. Exact Component Shorthand For a specific item with exact component values, vanilla item syntax is also accepted: Minecraft's own item parser handles this form. Use the structured components object for ranges, custom data predicates, and tag selectors. Remaining Durability durability means durability remaining, not raw damage: min: 250 means at least 250 durability remains. max: 25 means 25 or less remains. You can provide both bounds. Non damageable items never match a durability predicate, and negative, reversed, or impossible conditions are reported during reload. Overlapping Definitions And Priority Several definitions can target the same base item. This is useful for a broad fallback plus a component specific price: The server first gathers item and tag selectors that include the stack, then checks components and durability. The matching definition with the highest priority wins. The default priority is 0; having more predicates does not raise it automatically. Equal priority ties are deterministic: the later sorting definition ID wins, matching the old beta.13 conflict behavior. If the predicates can overlap, reload diagnostics ask you to set an explicit priority rather than leaving that tie accidental. Fields Field Required Meaning item Yes Registered item ID, exact item component shorthand, or prefixed item tag. rates New files One or more associated item count and currency count families. item count + currency count Legacy alternative Backward compatible top level pair, treated as one rate. Do not combine it with rates. components No Exact codec backed values or numeric min/max predicates. durability No Remaining durability min, max, or both. priority No Explicit winner among matching definitions. Defaults to 0. market group No Demand and supply group shared by related definitions. enabled No Set to false to disable a lower pack's resource at the same path. Count values must be positive. item count cannot exceed 256, each count range can contain at most 256 values, a definition can contain at most 256 rates, and total generated combinations are capped. These limits keep a malformed pack from creating an enormous candidate list. The Village Market Still Works The Same Way Predicates decide whether a stack is eligible and which definition wins. After that selection, the existing market does the rest: resolve the local village choose the village's daily demand band select one candidate base rate deterministically apply that village's current supply pressure calculate the exact payout record the completed sale's pressure market group is still the key that makes related goods share demand and supply pressure. Different villages keep separate pressure. Adding a component predicate does not create a second economy. The configured primary currency and accepted currency tags remain unsaleable, even if a sell price file targets them. Add, Replace, Or Disable A new resource path adds a definition. Reusing the same namespace and path in a higher priority datapack replaces that resource. To disable a lower pack's file, replace the same path with: Invalid ranges, selectors, components, and priorities are reported during reload. A bad entry is skipped; it does not crash the server. Built In Price Basis Built in definitions are based on direct Minecraft 1.21.1 villager and wandering trader offers. They do not copy live trade demand, reputation discounts, mod added offers, or secondary recipe inputs. The village market applies its own daily demand and supply pressure after choosing the matching definition.",
      "headings": [
        {
          "level": 2,
          "title": "Where Price Files Go"
        },
        {
          "level": 2,
          "title": "Use Explicit Rates For New Packs"
        },
        {
          "level": 2,
          "title": "Discrete Prices Stay Discrete"
        },
        {
          "level": 2,
          "title": "Existing beta.13 Files Still Work"
        },
        {
          "level": 2,
          "title": "Items And Tags"
        },
        {
          "level": 2,
          "title": "Matching Item Components"
        },
        {
          "level": 2,
          "title": "Exact Component Shorthand"
        },
        {
          "level": 2,
          "title": "Remaining Durability"
        },
        {
          "level": 2,
          "title": "Overlapping Definitions And Priority"
        },
        {
          "level": 2,
          "title": "Fields"
        },
        {
          "level": 2,
          "title": "The Village Market Still Works The Same Way"
        },
        {
          "level": 2,
          "title": "Add, Replace, Or Disable"
        },
        {
          "level": 2,
          "title": "Built-In Price Basis"
        }
      ],
      "related": [
        "json-reference",
        "example-packs"
      ]
    },
    {
      "slug": "builder-structures",
      "file": "Builder-Structures.md",
      "source": "wiki/Builder-Structures.md",
      "sourceKind": "wiki",
      "group": "Economy & Progression",
      "icon": "building-2",
      "title": "Builder Structures",
      "description": "Add structure templates and currency costs to the hired-builder menu.",
      "markdown": "# Builder Structures\n\nBuilder structure files control which structure templates hired builders can offer in the builder menu and how much extra currency each structure adds to the job price.\n\nUse this for modpack packs that add custom village houses, modded houses, or any other structure template you want builders to construct.\n\n## Paths\n\n```text\ndata/<namespace>/builder_structures/<file>.json\n```\n\nExamples:\n\n```text\ndata/my_pack/builder_structures/custom_village_houses.json\ndata/villagerretaliation/builder_structures/vanilla_village_houses.json\n```\n\nFiles can live in any namespace. The built-in vanilla village houses are declared at:\n\n```text\ndata/villagerretaliation/builder_structures/vanilla_village_houses.json\n```\n\n## Add One Structure\n\n```json\n{\n  \"entries\": [\n    {\n      \"structure\": \"examplemod:village/houses/carpenter_house\",\n      \"category\": \"Modded Village\",\n      \"label\": \"Carpenter House\",\n      \"base_cost\": 18\n    }\n  ]\n}\n```\n\n`structure` must point at a real Minecraft structure template id. For modded structures, use the full `modid:path` id.\n\n## Add Several Structures\n\nUse `structures` when several entries share the same category and cost. Labels are generated from the template path.\n\n```json\n{\n  \"entries\": [\n    {\n      \"category\": \"Modded Village\",\n      \"base_cost\": 16,\n      \"structures\": [\n        \"examplemod:village/houses/small_house_1\",\n        \"examplemod:village/houses/small_house_2\",\n        \"examplemod:village/houses/fisher_house\"\n      ]\n    }\n  ]\n}\n```\n\n## Fields\n\n| Field | Type | Default | Meaning |\n| --- | --- | --- | --- |\n| `structure` | string | none | One structure template id. Alias: `id`, `structure_id`, `structureId`. |\n| `structures` | string or array | none | Several structure template ids using shared category and cost. |\n| `category` | string | `Structures` | Builder menu category. |\n| `label` | string | generated | Display label for a single structure entry. Ignored for multi-structure entries. |\n| `base_cost` | integer | `0` | Extra currency added to this structure's build price. Aliases: `baseCost`, `cost`. |\n| `remove` | boolean | `false` | Removes the listed structure id or ids from the builder menu. |\n| `enabled` | boolean | `true` | Set `false` to remove the listed structure id or ids. |\n\nThe final job price is:\n\n```text\nbuilder base cost config + entry base_cost + configured per-64-block cost\n```\n\nBlueprint creation uses the configured blueprint percentage of that final job price.\n\n## Remove A Built-In Structure\n\n```json\n{\n  \"entries\": [\n    {\n      \"structure\": \"minecraft:village/plains/houses/plains_small_house_1\",\n      \"remove\": true\n    }\n  ]\n}\n```\n\n## Replace The Whole List\n\n`replace: true` clears previously loaded builder structures before this file is applied.\n\n```json\n{\n  \"replace\": true,\n  \"entries\": [\n    {\n      \"structure\": \"examplemod:village/houses/starter_home\",\n      \"category\": \"Starter Village\",\n      \"label\": \"Starter Home\",\n      \"base_cost\": 10\n    }\n  ]\n}\n```\n\nTo guarantee a full built-in replacement, override the built-in file path:\n\n```text\ndata/villagerretaliation/builder_structures/vanilla_village_houses.json\n```\n\n## Reload Behavior\n\nBuilder structure files are loaded on server start and `/reload`. The server syncs the current builder catalog to connected clients after reload, so newly added structures appear in the builder menu without a relog.\n\nThe server still validates every preview, confirmation, blueprint placement, and job start against the server-loaded catalog.\n\n## Practical Notes\n\n- Keep custom structure templates small enough for the builder max-block config.\n- Builders copy block entity data for placed blocks, but generated loot table tags are stripped from built containers.\n- If a structure appears in the menu but preview says unavailable, confirm the template id exists and the structure is not over the builder max-block limit.\n- Use `/vr admin datapack diagnostics` after `/reload` to see skipped or duplicate datapack entries.\n",
      "text": "Builder Structures Builder structure files control which structure templates hired builders can offer in the builder menu and how much extra currency each structure adds to the job price. Use this for modpack packs that add custom village houses, modded houses, or any other structure template you want builders to construct. Paths Examples: Files can live in any namespace. The built in vanilla village houses are declared at: Add One Structure structure must point at a real Minecraft structure template id. For modded structures, use the full modid:path id. Add Several Structures Use structures when several entries share the same category and cost. Labels are generated from the template path. Fields Field Type Default Meaning structure string none One structure template id. Alias: id, structure id, structureId. structures string or array none Several structure template ids using shared category and cost. category string Structures Builder menu category. label string generated Display label for a single structure entry. Ignored for multi structure entries. base cost integer 0 Extra currency added to this structure's build price. Aliases: baseCost, cost. remove boolean false Removes the listed structure id or ids from the builder menu. enabled boolean true Set false to remove the listed structure id or ids. The final job price is: Blueprint creation uses the configured blueprint percentage of that final job price. Remove A Built In Structure Replace The Whole List replace: true clears previously loaded builder structures before this file is applied. To guarantee a full built in replacement, override the built in file path: Reload Behavior Builder structure files are loaded on server start and /reload. The server syncs the current builder catalog to connected clients after reload, so newly added structures appear in the builder menu without a relog. The server still validates every preview, confirmation, blueprint placement, and job start against the server loaded catalog. Practical Notes Keep custom structure templates small enough for the builder max block config. Builders copy block entity data for placed blocks, but generated loot table tags are stripped from built containers. If a structure appears in the menu but preview says unavailable, confirm the template id exists and the structure is not over the builder max block limit. Use /vr admin datapack diagnostics after /reload to see skipped or duplicate datapack entries.",
      "headings": [
        {
          "level": 2,
          "title": "Paths"
        },
        {
          "level": 2,
          "title": "Add One Structure"
        },
        {
          "level": 2,
          "title": "Add Several Structures"
        },
        {
          "level": 2,
          "title": "Fields"
        },
        {
          "level": 2,
          "title": "Remove A Built-In Structure"
        },
        {
          "level": 2,
          "title": "Replace The Whole List"
        },
        {
          "level": 2,
          "title": "Reload Behavior"
        },
        {
          "level": 2,
          "title": "Practical Notes"
        }
      ],
      "related": [
        "json-reference",
        "example-packs"
      ]
    },
    {
      "slug": "natural-job-armor",
      "file": "Natural-Job-Armor.md",
      "source": "wiki/Natural-Job-Armor.md",
      "sourceKind": "wiki",
      "group": "Economy & Progression",
      "icon": "shield",
      "title": "Natural Job Armor",
      "description": "Difficulty-aware armor profiles for newly spawned villagers.",
      "markdown": "# Natural Job Armor\n\nNatural job armor controls the armor fresh villagers can receive when they naturally enter the world and later resolve to a configured profession.\n\n## Paths\n\n```text\ndata/villagerretaliation/natural_job_armor/<file>.json\n```\n\n## Example\n\n```json\n{\n  \"profiles\": [\n    {\n      \"id\": \"my_pack.guard_smiths\",\n      \"professions\": [\"armorer\", \"toolsmith\", \"weaponsmith\"],\n      \"chance\": {\n        \"peaceful\": 0.25,\n        \"easy\": 0.40,\n        \"normal\": 0.60,\n        \"hard\": 0.80\n      },\n      \"next_piece_chance\": {\n        \"peaceful\": 0.50,\n        \"easy\": 0.60,\n        \"normal\": 0.75,\n        \"hard\": 0.90\n      },\n      \"mixed_gear_chance\": {\n        \"peaceful\": 0.05,\n        \"easy\": 0.10,\n        \"normal\": 0.20,\n        \"hard\": 0.30\n      },\n      \"enchant_chance\": {\n        \"peaceful\": 0.01,\n        \"easy\": 0.03,\n        \"normal\": 0.08,\n        \"hard\": 0.16\n      },\n      \"armor_sets\": [\n        {\n          \"id\": \"iron\",\n          \"material\": \"iron\",\n          \"weight\": 95\n        },\n        {\n          \"id\": \"diamond\",\n          \"material\": \"diamond\",\n          \"weight_by_difficulty\": {\n            \"peaceful\": 0,\n            \"easy\": 1,\n            \"normal\": 2,\n            \"hard\": 5\n          }\n        }\n      ]\n    }\n  ]\n}\n```\n\n## Fields\n\n| Field | Meaning |\n| --- | --- |\n| `replace` | Clears previously loaded natural job armor profiles before this file applies. |\n| `profiles` / `armor_profiles` | Array of profile entries. |\n| `id` | Stable profile id. Later entries with the same id replace earlier ones. |\n| `remove` | Removes an earlier profile with the same `id`. |\n| `profession` / `professions` | One or more villager professions. Vanilla professions can omit `minecraft:`. |\n| `chance` / `armor_chance` | Chance that a matching fresh villager receives any armor. Number or per-difficulty object. |\n| `next_piece_chance` | Chance to continue adding another armor piece after each piece except the helmet. |\n| `mixed_gear_chance` | Per-piece chance to choose a different eligible armor set instead of the profile's base armor set. |\n| `enchant_chance` | Per-piece chance to apply vanilla mob-spawn equipment enchantments. |\n| `armor_sets` / `materials` | Weighted armor set entries. |\n\nChance objects support `peaceful`, `easy`, `normal`, and `hard`, with values from `0.0` to `1.0`.\n\nModded professions are supported by using their full registry id:\n\n```json\n{\n\"professions\": [\"examplemod:guard\", \"examplemod:archer\"]\n}\n```\n\nModded villager entities can use these rules when they are villager-like entities that expose normal villager data through Minecraft's `VillagerDataHolder` contract.\n\n## Armor Sets\n\nUse a vanilla material shorthand:\n\n```json\n{\n  \"material\": \"chainmail\",\n  \"weight\": 30\n}\n```\n\nSupported material shorthands are `leather`, `chainmail`, `iron`, and `diamond`.\n\nOr provide explicit item ids:\n\n```json\n{\n  \"id\": \"modded_guard_set\",\n  \"weight\": 10,\n  \"items\": {\n    \"feet\": \"examplemod:guard_boots\",\n    \"legs\": \"examplemod:guard_leggings\",\n    \"chest\": \"examplemod:guard_chestplate\",\n    \"head\": \"examplemod:guard_helmet\"\n  }\n}\n```\n\nArmor set weights can be a single `weight` or a per-difficulty `weight_by_difficulty` object.\n\nWhen `mixed_gear_chance` passes, the piece rerolls from the same `armor_sets` list, excluding the base set when another weighted set is available.\n\nThese profiles apply when a fresh villager enters the world and later resolves its profession. Existing villagers keep their current equipment.\n",
      "text": "Natural Job Armor Natural job armor controls the armor fresh villagers can receive when they naturally enter the world and later resolve to a configured profession. Paths Example Fields Field Meaning replace Clears previously loaded natural job armor profiles before this file applies. profiles / armor profiles Array of profile entries. id Stable profile id. Later entries with the same id replace earlier ones. remove Removes an earlier profile with the same id. profession / professions One or more villager professions. Vanilla professions can omit minecraft:. chance / armor chance Chance that a matching fresh villager receives any armor. Number or per difficulty object. next piece chance Chance to continue adding another armor piece after each piece except the helmet. mixed gear chance Per piece chance to choose a different eligible armor set instead of the profile's base armor set. enchant chance Per piece chance to apply vanilla mob spawn equipment enchantments. armor sets / materials Weighted armor set entries. Chance objects support peaceful, easy, normal, and hard, with values from 0.0 to 1.0. Modded professions are supported by using their full registry id: Modded villager entities can use these rules when they are villager like entities that expose normal villager data through Minecraft's VillagerDataHolder contract. Armor Sets Use a vanilla material shorthand: Supported material shorthands are leather, chainmail, iron, and diamond. Or provide explicit item ids: Armor set weights can be a single weight or a per difficulty weight by difficulty object. When mixed gear chance passes, the piece rerolls from the same armor sets list, excluding the base set when another weighted set is available. These profiles apply when a fresh villager enters the world and later resolves its profession. Existing villagers keep their current equipment.",
      "headings": [
        {
          "level": 2,
          "title": "Paths"
        },
        {
          "level": 2,
          "title": "Example"
        },
        {
          "level": 2,
          "title": "Fields"
        },
        {
          "level": 2,
          "title": "Armor Sets"
        }
      ],
      "related": [
        "player-raids",
        "json-reference"
      ]
    },
    {
      "slug": "story-discovery",
      "file": "Story-Discovery.md",
      "source": "wiki/Story-Discovery.md",
      "sourceKind": "wiki",
      "group": "World & Identity",
      "icon": "map",
      "title": "Story Discovery",
      "description": "Structure and biome pools used by story-sharing dialogue.",
      "markdown": "# Story Discovery\n\nStory discovery files list structures and biomes that villagers can turn into discovery leads. The `share_story` request uses the player's recorded discovery context to choose matching dialogue.\n\nAdding a structure or biome here does not add it to world generation. The target must already be registered by Minecraft or another mod.\n\n## Paths\n\n```text\ndata/<namespace>/story_structures/<file>.json\ndata/<namespace>/story_biomes/<file>.json\n```\n\nBoth systems can use any namespace.\n\n## Structure Example\n\n```json\n{\n  \"radius\": 128,\n  \"entries\": [\n    {\n      \"structure\": \"examplemod:haunted_keep\",\n      \"name\": \"Haunted Keep\"\n    },\n    {\n      \"structures\": [\n        \"examplemod:ruined_watchtower\",\n        \"examplemod:ruined_gate\"\n      ],\n      \"radius\": 96\n    }\n  ]\n}\n```\n\nThe root `radius` is the default for entries in that file. An entry can override it. If neither supplies a radius, the default is 96 blocks.\n\n`structure` accepts one ID. `structures` accepts one or more IDs. When several IDs share one `name`, they also share that display name.\n\n## Biome Example\n\n```json\n{\n  \"entries\": [\n    {\n      \"biome\": \"examplemod:crystal_marsh\",\n      \"name\": \"Crystal Marsh\"\n    },\n    {\n      \"biomes\": [\n        \"examplemod:ashen_fen\",\n        \"examplemod:smoke_bog\"\n      ]\n    }\n  ]\n}\n```\n\nBiomes do not use a radius field. Discovery follows the player's current biome.\n\n## Dialogue Example\n\n```json\n{\n  \"id\": \"my_pack.story.haunted_keep\",\n  \"request\": \"share_story\",\n  \"option\": \"adult_share_story\",\n  \"story_structure\": \"examplemod:haunted_keep\",\n  \"text\": \"{target_article}. We do not say its name after sundown.\"\n}\n```\n\n`story_structure` restricts the line to one structure. Use `story_structures` for several. Biome lines use `story_biome` or `story_biomes`.\n\nWithout a story target filter, a `share_story` line can match any current story target.\n\n## Main Fields\n\n| Field | Meaning |\n| --- | --- |\n| `structure` or `structures` | One or more registered structure IDs. |\n| `biome` or `biomes` | One or more registered biome IDs. |\n| `name` | Player-facing target name used by story text. |\n| `radius` | Structure discovery radius in blocks. The minimum is 1. |\n\nIf `name` is omitted, the mod turns the resource path into readable text. For example, `examplemod:haunted_keep` becomes `Haunted Keep`. Supply `name` when capitalization, punctuation, or translation matters.\n\n## Loading And Overrides\n\nEvery valid target ID is stored once. When later data defines the same target, the later definition replaces its name and structure radius.\n\nThere is no `replace` or `remove` field. Use the exact same namespace and file path to replace a lower-priority resource, or redefine the same target ID in a later-loading file.\n",
      "text": "Story Discovery Story discovery files list structures and biomes that villagers can turn into discovery leads. The share story request uses the player's recorded discovery context to choose matching dialogue. Adding a structure or biome here does not add it to world generation. The target must already be registered by Minecraft or another mod. Paths Both systems can use any namespace. Structure Example The root radius is the default for entries in that file. An entry can override it. If neither supplies a radius, the default is 96 blocks. structure accepts one ID. structures accepts one or more IDs. When several IDs share one name, they also share that display name. Biome Example Biomes do not use a radius field. Discovery follows the player's current biome. Dialogue Example story structure restricts the line to one structure. Use story structures for several. Biome lines use story biome or story biomes. Without a story target filter, a share story line can match any current story target. Main Fields Field Meaning structure or structures One or more registered structure IDs. biome or biomes One or more registered biome IDs. name Player facing target name used by story text. radius Structure discovery radius in blocks. The minimum is 1. If name is omitted, the mod turns the resource path into readable text. For example, examplemod:haunted keep becomes Haunted Keep. Supply name when capitalization, punctuation, or translation matters. Loading And Overrides Every valid target ID is stored once. When later data defines the same target, the later definition replaces its name and structure radius. There is no replace or remove field. Use the exact same namespace and file path to replace a lower priority resource, or redefine the same target ID in a later loading file.",
      "headings": [
        {
          "level": 2,
          "title": "Paths"
        },
        {
          "level": 2,
          "title": "Structure Example"
        },
        {
          "level": 2,
          "title": "Biome Example"
        },
        {
          "level": 2,
          "title": "Dialogue Example"
        },
        {
          "level": 2,
          "title": "Main Fields"
        },
        {
          "level": 2,
          "title": "Loading And Overrides"
        }
      ],
      "related": [
        "dialogue-requests",
        "tracked-villages"
      ]
    },
    {
      "slug": "villager-names",
      "file": "Villager-Names.md",
      "source": "wiki/Villager-Names.md",
      "sourceKind": "wiki",
      "group": "World & Identity",
      "icon": "user-round",
      "title": "Villager Names",
      "description": "Add to or replace the preset villager-name pool.",
      "markdown": "# Villager Names\n\nVillager Retaliation can assign preset names to villagers that do not already have custom names.\n\n## Path\n\n```text\ndata/villagerretaliation/villager_names/<file>.json\n```\n\n## Example\n\n```json\n{\n  \"male_names\": [\n    \"Bram\",\n    \"Edric\"\n  ],\n  \"female_names\": [\n    \"Cora\",\n    \"Mira\"\n  ]\n}\n```\n\n## Replace Example\n\n```json\n{\n  \"replace\": true,\n  \"male_names\": [\"Alden\"],\n  \"female_names\": [\"Lyra\"]\n}\n```\n\n## How It Works\n\n- Villagers with a normal generated identity can receive a preset name.\n- The assigned name is persistent once chosen.\n- Changing the pool later does not rename villagers that already stored identity data.\n- Villagers with explicit Minecraft custom names keep those names.\n\nAdditive files are the safest way to expand the pool. Use `replace: true` only when you want to rebuild the entire preset list.\n",
      "text": "Villager Names Villager Retaliation can assign preset names to villagers that do not already have custom names. Path Example Replace Example How It Works Villagers with a normal generated identity can receive a preset name. The assigned name is persistent once chosen. Changing the pool later does not rename villagers that already stored identity data. Villagers with explicit Minecraft custom names keep those names. Additive files are the safest way to expand the pool. Use replace: true only when you want to rebuild the entire preset list.",
      "headings": [
        {
          "level": 2,
          "title": "Path"
        },
        {
          "level": 2,
          "title": "Example"
        },
        {
          "level": 2,
          "title": "Replace Example"
        },
        {
          "level": 2,
          "title": "How It Works"
        }
      ],
      "related": []
    },
    {
      "slug": "village-names",
      "file": "Village-Names.md",
      "source": "wiki/Village-Names.md",
      "sourceKind": "wiki",
      "group": "World & Identity",
      "icon": "landmark",
      "title": "Village Names",
      "description": "Customize generated village names and naming fragments.",
      "markdown": "# Village Names\n\nVillager Retaliation builds a persistent name for each newly discovered village by combining one prefix with one suffix. Existing villages keep their stored names when datapacks change.\n\n## Path\n\n```text\ndata/villagerretaliation/village_names/<file>.json\n```\n\n## Example\n\n```json\n{\n  \"prefixes\": [\n    \"Copper\",\n    \"Juniper\"\n  ],\n  \"suffixes\": [\n    \"bridge\",\n    \"hollow\"\n  ]\n}\n```\n\nThis example adds `Copperbridge`, `Copperhollow`, `Juniperbridge`, and `Juniperhollow` to the possible generated names.\n\n## Replace Example\n\n```json\n{\n  \"replace\": true,\n  \"prefixes\": [\"Sun\"],\n  \"suffixes\": [\"haven\", \"wick\"]\n}\n```\n\n## How It Works\n\n- Files are additive by default and are read in resource-location order.\n- If any add-on file uses `replace: true`, built-in village-name files are skipped. The replacement file clears prefixes and suffixes loaded from earlier add-on files before adding its values.\n- Prefixes and suffixes are joined directly, without an automatic space.\n- Selection is deterministic from the village identity, while duplicate names already used in the world are skipped.\n- Generated names are persisted. Reloading or changing the pool affects only villages that receive a name afterward.\n- Generated results longer than 32 characters, or containing formatting or control codes, are skipped.\n- If either final pool is empty, VR uses a stable emergency name based on the village identity so village creation still succeeds.\n\nAdditive files are the safest way to expand the pool. Use `replace: true` only when you want to rebuild both halves of every future generated village name.\n",
      "text": "Village Names Villager Retaliation builds a persistent name for each newly discovered village by combining one prefix with one suffix. Existing villages keep their stored names when datapacks change. Path Example This example adds Copperbridge, Copperhollow, Juniperbridge, and Juniperhollow to the possible generated names. Replace Example How It Works Files are additive by default and are read in resource location order. If any add on file uses replace: true, built in village name files are skipped. The replacement file clears prefixes and suffixes loaded from earlier add on files before adding its values. Prefixes and suffixes are joined directly, without an automatic space. Selection is deterministic from the village identity, while duplicate names already used in the world are skipped. Generated names are persisted. Reloading or changing the pool affects only villages that receive a name afterward. Generated results longer than 32 characters, or containing formatting or control codes, are skipped. If either final pool is empty, VR uses a stable emergency name based on the village identity so village creation still succeeds. Additive files are the safest way to expand the pool. Use replace: true only when you want to rebuild both halves of every future generated village name.",
      "headings": [
        {
          "level": 2,
          "title": "Path"
        },
        {
          "level": 2,
          "title": "Example"
        },
        {
          "level": 2,
          "title": "Replace Example"
        },
        {
          "level": 2,
          "title": "How It Works"
        }
      ],
      "related": [
        "tracked-villages",
        "villager-names"
      ]
    },
    {
      "slug": "player-raids",
      "file": "Player-Raids.md",
      "source": "wiki/Player-Raids.md",
      "sourceKind": "wiki",
      "group": "World & Identity",
      "icon": "flag",
      "title": "Player Raids",
      "description": "Configure datapack loadouts used by villagers defending against player sieges.",
      "markdown": "# Player Raids\n\nPlayer Raids turn a tracked village into a continuous siege with the player and their snapshotted party as the raiders.\n\n## Starting a raid\n\nWear a helmet with an attached banner, stand inside a tracked village footprint, and begin using a goat horn. A raid cannot start during a vanilla raid, while that village or any participant is already in another Player Raid, or while a successfully defended village is on cooldown.\n\nThe initiating player's current party is snapshotted. Later party changes do not change either side. Recruited villagers whose recorded home is the target village permanently leave the party, confront the initiating player in chained forced dialogue, and defend their home. Every snapshotted defender sets every raider player's reputation to at most `-250`. Values already below `-250` lose another `250`.\n\n## Siege rules\n\n- The red ten-segment bar fills during the default 10-second preparation period.\n- Once active, it displays combat-capable defenders remaining. Babies and nitwits are snapshotted separately as noncombatants. Defectors remain on the appropriate side of that split, and iron golems do not count.\n- Adults other than nitwits fill empty equipment slots from the `player_raid_loadouts` datapack catalog. Equipment persists and uses normal low mob-equipment drop chances.\n- Babies and nitwits seek hiding places until the armed defense is defeated. Capable villagers and aligned iron golems engage raiders, including when the villager's reputation tier is Feared.\n- Golems arrive in batches at activation and the 75%, 50%, and 25% defender thresholds. The fixed budget is calculated once, and dead golems are not replaced.\n- When every combat-capable defender is dead or converted, the raid enters its mercy stage. Births and visiting villagers after declaration are not added, and villagers snapshotted as babies remain mercy candidates even if they mature during the raid.\n- A living raider player can empty-hand right-click each unresolved baby or nitwit and choose **Spare**, **Kill**, or **Say nothing**. Spare leaves the villager alive and sets their reputation toward every snapshotted raider player to exactly `-1000`. Kill closes the menu so the player must attack manually, and Say nothing closes it without a response. Either unresolved choice can be reconsidered later.\n- Mercy candidates plead only when a raider comes within normal dialogue range. Each villager waits 30–60 seconds between pleas, and the raid allows at most one plea every five seconds.\n- Raiders win after every mercy candidate has been spared, killed, or converted. The normal abandonment timer continues during mercy.\n- The village wins if no living, non-spectator raider player remains inside its footprint for the configured abandonment time (30 seconds by default).\n- At either outcome, each surviving recruited raider villager delivers one of 15 victory or 15 loss reactions to online raider players.\n- During the active siege, a raider player wearing a banner helmet can use a goat horn to make tracked defenders within 48 blocks glow for 3 seconds. During mercy, the same signal reveals unresolved mercy candidates.\n\nOperators can settle the Player Raid involving them or containing their current position with `/vr admin debug raid win` or `/vr admin debug raid lose`.\n\n## Configuration\n\nThe `playerRaids` config section controls activation, preparation and abandonment ticks, defended-village cooldown days, boss-bar range, and the golem formula. `reputation.fearedThreshold` now defaults to `-1000`. The exact legacy default of `-750` migrates automatically, while custom values are preserved.\n\n## Datapack loadouts\n\nPlace loadout catalogs at:\n\n```text\ndata/villagerretaliation/player_raid_loadouts/*.json\n```\n\nEach file supports `replace` and a `loadouts` array. A loadout has a stable `id`, optional `professions` and `excluded_professions` filters, and `difficulty_pools` keyed by `peaceful`, `easy`, `normal`, and `hard`. Each pool can define `weapons`, `armor_chance`, `enchant_chance`, and weighted `armor_sets`.\n\n### Minimal profession weapon pool\n\nThis smallest useful profile gives fletchers a crossbow on every difficulty. When a requested difficulty is absent, the loader uses the first pool in the profile.\n\n```json\n{\n  \"loadouts\": [\n    {\n      \"id\": \"my_pack_fletcher_crossbow\",\n      \"professions\": [\"minecraft:fletcher\"],\n      \"difficulty_pools\": {\n        \"normal\": {\n          \"weapons\": [\"minecraft:crossbow\"]\n        }\n      }\n    }\n  ]\n}\n```\n\n### Advanced militia armor\n\n```json\n{\n  \"replace\": false,\n  \"loadouts\": [\n    {\n      \"id\": \"my_pack_militia\",\n      \"professions\": [\"minecraft:fletcher\"],\n      \"difficulty_pools\": {\n        \"normal\": {\n          \"weapons\": [\"minecraft:crossbow\"],\n          \"armor_chance\": 0.75,\n          \"enchant_chance\": 0.1,\n          \"armor_sets\": [\n            {\n              \"weight\": 1,\n              \"head\": \"minecraft:chainmail_helmet\",\n              \"chest\": \"minecraft:chainmail_chestplate\",\n              \"legs\": \"minecraft:chainmail_leggings\",\n              \"feet\": \"minecraft:chainmail_boots\"\n            }\n          ]\n        }\n      }\n    }\n  ]\n}\n```\n\nProfiles are checked in datapack order and the first profession match is used. Empty slots only are filled. Missing difficulty pools fall back to the first pool in that profile.\n\nThe forced-dialogue trigger name is `player_raid_betrayal`. The built-in resource exposes the `primary`, `chained`, and `turn` definition IDs, each with 15 line variations, under `data/villagerretaliation/forced_dialogue/events/player_raid_betrayal.json`.\n\nParty-villager outcome reactions use the global message keys `interaction.party.player_raid_victory` and `interaction.party.player_raid_loss`. Packs can override their line pools through normal localized dialogue message resources.\n\nMercy dialogue uses the global message keys under `interaction.player_raid.mercy.*`, including separate baby and nitwit plea, spared, and kill-response pools plus the three option labels. Packs can override them through normal localized dialogue message resources.\n",
      "text": "Player Raids Player Raids turn a tracked village into a continuous siege with the player and their snapshotted party as the raiders. Starting a raid Wear a helmet with an attached banner, stand inside a tracked village footprint, and begin using a goat horn. A raid cannot start during a vanilla raid, while that village or any participant is already in another Player Raid, or while a successfully defended village is on cooldown. The initiating player's current party is snapshotted. Later party changes do not change either side. Recruited villagers whose recorded home is the target village permanently leave the party, confront the initiating player in chained forced dialogue, and defend their home. Every snapshotted defender sets every raider player's reputation to at most 250. Values already below 250 lose another 250. Siege rules The red ten segment bar fills during the default 10 second preparation period. Once active, it displays combat capable defenders remaining. Babies and nitwits are snapshotted separately as noncombatants. Defectors remain on the appropriate side of that split, and iron golems do not count. Adults other than nitwits fill empty equipment slots from the player raid loadouts datapack catalog. Equipment persists and uses normal low mob equipment drop chances. Babies and nitwits seek hiding places until the armed defense is defeated. Capable villagers and aligned iron golems engage raiders, including when the villager's reputation tier is Feared. Golems arrive in batches at activation and the 75%, 50%, and 25% defender thresholds. The fixed budget is calculated once, and dead golems are not replaced. When every combat capable defender is dead or converted, the raid enters its mercy stage. Births and visiting villagers after declaration are not added, and villagers snapshotted as babies remain mercy candidates even if they mature during the raid. A living raider player can empty hand right click each unresolved baby or nitwit and choose Spare , Kill , or Say nothing . Spare leaves the villager alive and sets their reputation toward every snapshotted raider player to exactly 1000. Kill closes the menu so the player must attack manually, and Say nothing closes it without a response. Either unresolved choice can be reconsidered later. Mercy candidates plead only when a raider comes within normal dialogue range. Each villager waits 30–60 seconds between pleas, and the raid allows at most one plea every five seconds. Raiders win after every mercy candidate has been spared, killed, or converted. The normal abandonment timer continues during mercy. The village wins if no living, non spectator raider player remains inside its footprint for the configured abandonment time (30 seconds by default). At either outcome, each surviving recruited raider villager delivers one of 15 victory or 15 loss reactions to online raider players. During the active siege, a raider player wearing a banner helmet can use a goat horn to make tracked defenders within 48 blocks glow for 3 seconds. During mercy, the same signal reveals unresolved mercy candidates. Operators can settle the Player Raid involving them or containing their current position with /vr admin debug raid win or /vr admin debug raid lose. Configuration The playerRaids config section controls activation, preparation and abandonment ticks, defended village cooldown days, boss bar range, and the golem formula. reputation.fearedThreshold now defaults to 1000. The exact legacy default of 750 migrates automatically, while custom values are preserved. Datapack loadouts Place loadout catalogs at: Each file supports replace and a loadouts array. A loadout has a stable id, optional professions and excluded professions filters, and difficulty pools keyed by peaceful, easy, normal, and hard. Each pool can define weapons, armor chance, enchant chance, and weighted armor sets. Minimal profession weapon pool This smallest useful profile gives fletchers a crossbow on every difficulty. When a requested difficulty is absent, the loader uses the first pool in the profile. Advanced militia armor Profiles are checked in datapack order and the first profession match is used. Empty slots only are filled. Missing difficulty pools fall back to the first pool in that profile. The forced dialogue trigger name is player raid betrayal. The built in resource exposes the primary, chained, and turn definition IDs, each with 15 line variations, under data/villagerretaliation/forced dialogue/events/player raid betrayal.json. Party villager outcome reactions use the global message keys interaction.party.player raid victory and interaction.party.player raid loss. Packs can override their line pools through normal localized dialogue message resources. Mercy dialogue uses the global message keys under interaction.player raid.mercy. , including separate baby and nitwit plea, spared, and kill response pools plus the three option labels. Packs can override them through normal localized dialogue message resources.",
      "headings": [
        {
          "level": 2,
          "title": "Starting a raid"
        },
        {
          "level": 2,
          "title": "Siege rules"
        },
        {
          "level": 2,
          "title": "Configuration"
        },
        {
          "level": 2,
          "title": "Datapack loadouts"
        },
        {
          "level": 3,
          "title": "Minimal profession weapon pool"
        },
        {
          "level": 3,
          "title": "Advanced militia armor"
        }
      ],
      "related": [
        "natural-job-armor",
        "forced-dialogue",
        "tracked-villages"
      ]
    },
    {
      "slug": "generated-containers",
      "file": "Generated-Containers.md",
      "source": "wiki/Generated-Containers.md",
      "sourceKind": "wiki",
      "group": "World & Identity",
      "icon": "package-search",
      "title": "Generated Containers",
      "description": "Populate authored containers once when a matching block entity loads.",
      "markdown": "# Generated Containers\n\nGenerated-container files tell Villager Retaliation which loot tables count as village property. They matter when `dialogue.containerWatchMode` is set to `GENERATED_LOOT_ONLY`.\n\nA listed loot table marks containers created with that table as eligible for watched-container dialogue. This does not change the loot table or its drops.\n\n## Path\n\n```text\ndata/<namespace>/generated_containers/<file>.json\n```\n\nFiles from every namespace are combined.\n\n## Example\n\n```json\n{\n  \"loot_tables\": [\n    \"examplemod:chests/village/alchemist_house\",\n    \"examplemod:chests/village/watch_tower\"\n  ]\n}\n```\n\nThis makes containers generated from either table eligible for container-opened and container-theft reactions when the server uses generated-loot-only watching.\n\nA single entry also works:\n\n```json\n{\n  \"loot_table\": \"examplemod:chests/village/alchemist_house\"\n}\n```\n\nFor a large integration pack, `entries` can group several definitions:\n\n```json\n{\n  \"entries\": [\n    {\n      \"loot_tables\": [\n        \"examplemod:chests/village/alchemist_house\",\n        \"examplemod:chests/village/watch_tower\"\n      ]\n    },\n    {\n      \"loot_table\": \"anothermod:chests/village_store\"\n    }\n  ]\n}\n```\n\n## Loading And Overrides\n\nAll valid loot table IDs are added to one set. Repeating an ID has no extra effect.\n\nTo replace a lower-priority file, use the same namespace and file path. There is no `replace` or `remove` field for individual IDs. If another pack must remove a built-in group, it must override the exact built-in resource path with a file that lists only the IDs it wants to keep.\n\nThe built-in village list is:\n\n```text\ndata/villagerretaliation/generated_containers/village_property.json\n```\n\nA container that has already generated and resolved its loot table no longer exposes that table to this check.\n",
      "text": "Generated Containers Generated container files tell Villager Retaliation which loot tables count as village property. They matter when dialogue.containerWatchMode is set to GENERATED LOOT ONLY. A listed loot table marks containers created with that table as eligible for watched container dialogue. This does not change the loot table or its drops. Path Files from every namespace are combined. Example This makes containers generated from either table eligible for container opened and container theft reactions when the server uses generated loot only watching. A single entry also works: For a large integration pack, entries can group several definitions: Loading And Overrides All valid loot table IDs are added to one set. Repeating an ID has no extra effect. To replace a lower priority file, use the same namespace and file path. There is no replace or remove field for individual IDs. If another pack must remove a built in group, it must override the exact built in resource path with a file that lists only the IDs it wants to keep. The built in village list is: A container that has already generated and resolved its loot table no longer exposes that table to this check.",
      "headings": [
        {
          "level": 2,
          "title": "Path"
        },
        {
          "level": 2,
          "title": "Example"
        },
        {
          "level": 2,
          "title": "Loading And Overrides"
        }
      ],
      "related": [
        "forced-dialogue",
        "quest-scenes",
        "example-packs"
      ]
    },
    {
      "slug": "resource-pack-models",
      "file": "Resource-Pack-Models.md",
      "source": "wiki/Resource-Pack-Models.md",
      "sourceKind": "wiki",
      "group": "Resource Packs",
      "icon": "image",
      "title": "Resource Pack Models",
      "description": "Combat textures, item models, trader visuals, and compatibility advice.",
      "markdown": "# Resource Pack Models\n\nVillager Retaliation uses one villager model and texture layout for both crossed-arms and side-arms rendering.\n\n## Texture Paths\n\n| Entity | Normal texture | Villager Retaliation texture |\n| --- | --- | --- |\n| Villager | `assets/minecraft/textures/entity/villager/villager.png` | `assets/villagerretaliation/textures/entity/villager/villager.png` |\n| Wandering trader | `assets/minecraft/textures/entity/wandering_trader.png` | `assets/villagerretaliation/textures/entity/wandering_trader/wandering_trader.png` |\n\nFor Villager Retaliation's side-arm/crossed-arm model, put the 128x128 base, profession, profession level, and biome type textures under the `villagerretaliation` path. The renderer uses those `villagerretaliation` overlays only while the Villager Retaliation model is active.\n\nOnly replace files under `assets/minecraft/textures/entity/villager/` when you intentionally want to override the vanilla/base CEM villager textures too. Base Fresh Animations uses 64x64 `minecraft` villager textures, so pairing its `villager.jem` with 128x128 Villager Retaliation textures will look mis-mapped.\n\n## Model Path\n\n```text\nassets/villagerretaliation/models/entity/villager/combat_villager.json\n```\n\nThis one model must include both crossed arms and side arms. The renderer swaps visibility between `arms` and `RightArm`/`LeftArm`.\n\n## Minimal Model Shape\n\n```json\n{\n  \"texture_width\": 128,\n  \"texture_height\": 128,\n  \"parts\": [\n    { \"name\": \"body\", \"cubes\": [] },\n    { \"name\": \"arms\", \"pivot\": [0.0, 2.0, 0.0], \"rotation\": [-45.0, 0.0, 0.0], \"cubes\": [] },\n    { \"name\": \"RightArm\", \"pivot\": [-5.0, 2.0, 0.0], \"cubes\": [] },\n    { \"name\": \"LeftArm\", \"pivot\": [5.0, 2.0, 0.0], \"cubes\": [] },\n    { \"name\": \"RightLeg\", \"pivot\": [-2.0, 12.0, 0.0], \"cubes\": [] },\n    { \"name\": \"LeftLeg\", \"pivot\": [2.0, 12.0, 0.0], \"cubes\": [] },\n    { \"name\": \"head\", \"cubes\": [] }\n  ]\n}\n```\n\nRequired part names:\n\n```text\nbody\narms\nhead\nRightArm\nLeftArm\nRightLeg\nLeftLeg\n```\n\n## Fresh Animations Compatibility\n\nWhen Entity Model Features is installed and a resource pack provides `assets/minecraft/optifine/cem/villager.jem`, default villagers with empty hands use the vanilla EMF-backed villager model so base Fresh Animations can animate them. That fallback is only enabled when the active top `minecraft:textures/entity/villager/villager.png` is 64x64, matching Fresh Animations' vanilla CEM layout.\n\nVillagers switch back to the Villager Retaliation JSON model whenever they need side arms for held items, weapons, shields, bows, potions, or throwing poses. The side-arm model uses the 128x128 `villagerretaliation` texture layout and should not be paired with base Fresh Animations' vanilla `villager.jem`.\n\n## Practical Advice\n\n- Start from the built-in model and change it gradually.\n- Keep required part names exactly as documented.\n- If your pack only changes textures, you do not need model JSON at all.\n",
      "text": "Resource Pack Models Villager Retaliation uses one villager model and texture layout for both crossed arms and side arms rendering. Texture Paths Entity Normal texture Villager Retaliation texture Villager assets/minecraft/textures/entity/villager/villager.png assets/villagerretaliation/textures/entity/villager/villager.png Wandering trader assets/minecraft/textures/entity/wandering trader.png assets/villagerretaliation/textures/entity/wandering trader/wandering trader.png For Villager Retaliation's side arm/crossed arm model, put the 128x128 base, profession, profession level, and biome type textures under the villagerretaliation path. The renderer uses those villagerretaliation overlays only while the Villager Retaliation model is active. Only replace files under assets/minecraft/textures/entity/villager/ when you intentionally want to override the vanilla/base CEM villager textures too. Base Fresh Animations uses 64x64 minecraft villager textures, so pairing its villager.jem with 128x128 Villager Retaliation textures will look mis mapped. Model Path This one model must include both crossed arms and side arms. The renderer swaps visibility between arms and RightArm/LeftArm. Minimal Model Shape Required part names: Fresh Animations Compatibility When Entity Model Features is installed and a resource pack provides assets/minecraft/optifine/cem/villager.jem, default villagers with empty hands use the vanilla EMF backed villager model so base Fresh Animations can animate them. That fallback is only enabled when the active top minecraft:textures/entity/villager/villager.png is 64x64, matching Fresh Animations' vanilla CEM layout. Villagers switch back to the Villager Retaliation JSON model whenever they need side arms for held items, weapons, shields, bows, potions, or throwing poses. The side arm model uses the 128x128 villagerretaliation texture layout and should not be paired with base Fresh Animations' vanilla villager.jem. Practical Advice Start from the built in model and change it gradually. Keep required part names exactly as documented. If your pack only changes textures, you do not need model JSON at all.",
      "headings": [
        {
          "level": 2,
          "title": "Texture Paths"
        },
        {
          "level": 2,
          "title": "Model Path"
        },
        {
          "level": 2,
          "title": "Minimal Model Shape"
        },
        {
          "level": 2,
          "title": "Fresh Animations Compatibility"
        },
        {
          "level": 2,
          "title": "Practical Advice"
        }
      ],
      "related": []
    },
    {
      "slug": "pack-format-changes",
      "file": "Pack-Format-Changes.md",
      "source": "wiki/Pack-Format-Changes.md",
      "sourceKind": "wiki",
      "group": "Reference",
      "icon": "history",
      "title": "Pack Format Changes",
      "description": "Live beta.13 target notes plus migration guidance for older pack layouts.",
      "markdown": "# Pack Format Changes\n\nThis page is the migration note for pack authors, not the player-facing changelog.\n\n## Current Target\n\nThe current repo wiki targets `1.0.0-beta.13`. The datapack generator keeps frozen beta.11 and beta.12 documentation snapshots for packs that intentionally remain on those targets.\n\nIf you are still maintaining a beta.11 pack, keep using the beta.11 snapshot in `tools/datapack-builder/wiki/1.0.0-beta.11/` until you are ready to retarget manually.\n\n## Beta.12 To Beta.13 Checklist\n\nBeta.13 is primarily additive for pack authors. Existing beta.12 dialogue, notification, gift, pacification, loot, trade, name, story, and builder-structure files do not need a format-only rewrite.\n\n1. Quest runs now receive a persisted, repeat-safe UUID before entry actions and `STARTED` triggers execute.\n2. Persistent scene definitions live under `data/<namespace>/quest_scenes/` and use `schema: \"villagerretaliation:scene/v1\"`.\n3. Encounter definitions live under `data/<namespace>/quest_encounters/` and can coordinate scaling, phases, objectives, cleanup, retries, navigation guidance, and rewards.\n4. Quest actions can launch or reuse a scene and optionally wait for its durable terminal result before continuing.\n5. Quest providers and scene actors can opt into the downed-state protection contract when the story requires a villager to survive ordinary lethal damage.\n6. New beta.13 examples live in `example-packs/cinematic-gate-ambush/` and `example-packs/repeatable-scene-run-id/`.\n7. Sell-price `item` fields accept item tags such as `#minecraft:logs` as well as exact item IDs.\n8. Shared actions accept `type: \"draw_weapon\"`, while `player_item_proximity` forced-dialogue rules can set `draw_weapon: true` and a draw duration without starting retaliation.\n\nStart with [Persistent Quest Scenes](Quest-Scenes.md) for the authoring surface. [Quest Scene Runtime](../docs/quest-scene-runtime.md) defines the underlying ownership, continuation, recovery, and cleanup guarantees for developers who need the high-level runtime contract.\n\nBasic quest module v2 files remain valid without an extracted scene. Add `quest_scenes` and `quest_encounters` only when a sequence needs persistent actors, resumable timing, controlled combat, or recovery across unloads.\n\n## Beta.11 To Beta.12 Checklist\n\nBeta.12 is not a marker-only update. Review these areas before changing pack target:\n\n1. Dialogue layout: beta.12 strongly prefers folderized dialogue such as `options/`, `lines/`, `messages/`, `openings/`, `closings/`, and `pacify/`.\n2. Dialogue requests: options use `request`, and typed option files can omit `type` entirely.\n3. Complex logic: newer content should prefer `conditions` over older one-off helper fields.\n4. Dialogue filtering: beta.12 adds temporary mood filters, Social Attribute score filters, `priority`, `category`, and `text_key`.\n5. Quests: quest module v2 is preferred for new quests. Legacy v1 quest JSON remains supported through the compatibility adapter.\n6. Skill trades: beta.12 adds trade refresh behavior, persistent trade pools, and targetable Special Orders.\n7. Builder structures: eligible hired-builder structures are now data driven through `data/<namespace>/builder_structures/`.\n8. Builder workflow: there is no automatic beta.11 to beta.12 conversion pass.\n\n## Most Important Authoring Differences\n\n### 1. Dialogue Is Easier To Split\n\nOld style:\n\n```text\ndata/my_pack/dialogue/en_us/global.json\n```\n\nPreferred beta.12 style:\n\n```text\ndata/my_pack/dialogue/en_us/global/options/00_rumor.json\ndata/my_pack/dialogue/en_us/global/lines/00_rumor.json\ndata/my_pack/dialogue/en_us/global/messages/00_shared_text.json\n```\n\n### 2. `conditions` Are The Long-Term Shape\n\nInstead of stacking many special-purpose booleans, move new work toward:\n\n```json\n{\n  \"id\": \"my_pack.line.family_storm\",\n  \"request\": \"question\",\n  \"conditions\": [\n    { \"type\": \"family\", \"relation\": \"child\" },\n    { \"type\": \"weather\", \"state\": \"thunder\" }\n  ],\n  \"text\": \"Storm nights are worse when you have children to worry about.\"\n}\n```\n\n### 3. Quests Prefer Central Modules\n\nPreferred quest module v2 shape:\n\n```json\n{\n  \"schema\": \"villagerretaliation:quest/v2\",\n  \"id\": \"my_pack:bread_delivery\",\n  \"metadata\": {\n    \"title\": \"Bread Delivery\",\n    \"description\": \"Bring 16 bread to the village stores.\"\n  },\n  \"provider\": {\n    \"type\": \"villagerretaliation:villager\",\n    \"filters\": {\n      \"professions\": [\"minecraft:farmer\"]\n    }\n  },\n  \"entry_stage\": \"gather\",\n  \"stages\": [\n    {\n      \"id\": \"gather\",\n      \"objectives\": [\n        {\n          \"id\": \"bring_bread\",\n          \"type\": \"item_check\",\n          \"item\": \"minecraft:bread\",\n          \"count\": 16\n        }\n      ]\n    }\n  ]\n}\n```\n\nV1 quest files without `schema: \"villagerretaliation:quest/v2\"` still load. New simple quests should start as one v2 file with inline dialogue, and only extract dialogue trees or forced dialogue when the scene is large or event-driven.\n\n### 4. Skill Trades Can Power Special Orders\n\nEntries can now expose direct requests:\n\n```json\n{\n\"request\": {\n  \"targetable\": true,\n  \"display_priority\": 20,\n  \"min_reputation\": \"respected\",\n  \"wait_days\": 2,\n  \"cooldown_days\": 3\n}\n}\n```\n\n### 5. Builder Structures Can Include Modded Templates\n\nAdd builder-menu structures through normal datapack files:\n\n```json\n{\n  \"entries\": [\n    {\n      \"structure\": \"examplemod:village/houses/carpenter_house\",\n      \"category\": \"Modded Village\",\n      \"label\": \"Carpenter House\",\n      \"base_cost\": 18\n    }\n  ]\n}\n```\n\nSee [Builder Structures](Builder-Structures.md) for remove and replace examples.\n\n## Safe Migration Plan\n\n1. Leave the pack on beta.11 while you review it.\n2. Move dialogue into folderized beta.12 paths if the current files are large.\n3. Leave working v1 quests in place unless you are intentionally migrating them.\n4. Convert new or migrated simple quests to quest module v2 first. Extract dialogue trees only when needed.\n5. Replace older helper-heavy logic with `conditions` where practical.\n6. Test each system separately.\n7. Only then change the pack target to beta.12.\n\n## What Did Not Change\n\nThese habits are still correct:\n\n- Use stable `id` values.\n- Use exact path overrides only when you really want to replace built-in content.\n- Keep notifications, dialogue, and forced dialogue in their own loaders.\n- Keep v1 quest files and dialogue trees when they are still the authoritative source.\n- Use a resource pack for GUI text and models.\n\n## When In Doubt\n\nUse the beta.12 example pack and builder template as the source of truth for new content. They are easier to trust than trying to \"incrementally guess\" a beta.11 file into the new surface.\n",
      "text": "Pack Format Changes This page is the migration note for pack authors, not the player facing changelog. Current Target The current repo wiki targets 1.0.0 beta.13. The datapack generator keeps frozen beta.11 and beta.12 documentation snapshots for packs that intentionally remain on those targets. If you are still maintaining a beta.11 pack, keep using the beta.11 snapshot in tools/datapack builder/wiki/1.0.0 beta.11/ until you are ready to retarget manually. Beta.12 To Beta.13 Checklist Beta.13 is primarily additive for pack authors. Existing beta.12 dialogue, notification, gift, pacification, loot, trade, name, story, and builder structure files do not need a format only rewrite. 1. Quest runs now receive a persisted, repeat safe UUID before entry actions and STARTED triggers execute. 2. Persistent scene definitions live under data/ /quest scenes/ and use schema: \"villagerretaliation:scene/v1\". 3. Encounter definitions live under data/ /quest encounters/ and can coordinate scaling, phases, objectives, cleanup, retries, navigation guidance, and rewards. 4. Quest actions can launch or reuse a scene and optionally wait for its durable terminal result before continuing. 5. Quest providers and scene actors can opt into the downed state protection contract when the story requires a villager to survive ordinary lethal damage. 6. New beta.13 examples live in example packs/cinematic gate ambush/ and example packs/repeatable scene run id/. 7. Sell price item fields accept item tags such as minecraft:logs as well as exact item IDs. 8. Shared actions accept type: \"draw weapon\", while player item proximity forced dialogue rules can set draw weapon: true and a draw duration without starting retaliation. Start with Persistent Quest Scenes Quest Scenes.md for the authoring surface. Quest Scene Runtime ../docs/quest scene runtime.md defines the underlying ownership, continuation, recovery, and cleanup guarantees for developers who need the high level runtime contract. Basic quest module v2 files remain valid without an extracted scene. Add quest scenes and quest encounters only when a sequence needs persistent actors, resumable timing, controlled combat, or recovery across unloads. Beta.11 To Beta.12 Checklist Beta.12 is not a marker only update. Review these areas before changing pack target: 1. Dialogue layout: beta.12 strongly prefers folderized dialogue such as options/, lines/, messages/, openings/, closings/, and pacify/. 2. Dialogue requests: options use request, and typed option files can omit type entirely. 3. Complex logic: newer content should prefer conditions over older one off helper fields. 4. Dialogue filtering: beta.12 adds temporary mood filters, Social Attribute score filters, priority, category, and text key. 5. Quests: quest module v2 is preferred for new quests. Legacy v1 quest JSON remains supported through the compatibility adapter. 6. Skill trades: beta.12 adds trade refresh behavior, persistent trade pools, and targetable Special Orders. 7. Builder structures: eligible hired builder structures are now data driven through data/ /builder structures/. 8. Builder workflow: there is no automatic beta.11 to beta.12 conversion pass. Most Important Authoring Differences 1. Dialogue Is Easier To Split Old style: Preferred beta.12 style: 2. conditions Are The Long Term Shape Instead of stacking many special purpose booleans, move new work toward: 3. Quests Prefer Central Modules Preferred quest module v2 shape: V1 quest files without schema: \"villagerretaliation:quest/v2\" still load. New simple quests should start as one v2 file with inline dialogue, and only extract dialogue trees or forced dialogue when the scene is large or event driven. 4. Skill Trades Can Power Special Orders Entries can now expose direct requests: 5. Builder Structures Can Include Modded Templates Add builder menu structures through normal datapack files: See Builder Structures Builder Structures.md for remove and replace examples. Safe Migration Plan 1. Leave the pack on beta.11 while you review it. 2. Move dialogue into folderized beta.12 paths if the current files are large. 3. Leave working v1 quests in place unless you are intentionally migrating them. 4. Convert new or migrated simple quests to quest module v2 first. Extract dialogue trees only when needed. 5. Replace older helper heavy logic with conditions where practical. 6. Test each system separately. 7. Only then change the pack target to beta.12. What Did Not Change These habits are still correct: Use stable id values. Use exact path overrides only when you really want to replace built in content. Keep notifications, dialogue, and forced dialogue in their own loaders. Keep v1 quest files and dialogue trees when they are still the authoritative source. Use a resource pack for GUI text and models. When In Doubt Use the beta.12 example pack and builder template as the source of truth for new content. They are easier to trust than trying to \"incrementally guess\" a beta.11 file into the new surface.",
      "headings": [
        {
          "level": 2,
          "title": "Current Target"
        },
        {
          "level": 2,
          "title": "Beta.12 To Beta.13 Checklist"
        },
        {
          "level": 2,
          "title": "Beta.11 To Beta.12 Checklist"
        },
        {
          "level": 2,
          "title": "Most Important Authoring Differences"
        },
        {
          "level": 3,
          "title": "1. Dialogue Is Easier To Split"
        },
        {
          "level": 3,
          "title": "2. `conditions` Are The Long-Term Shape"
        },
        {
          "level": 3,
          "title": "3. Quests Prefer Central Modules"
        },
        {
          "level": 3,
          "title": "4. Skill Trades Can Power Special Orders"
        },
        {
          "level": 3,
          "title": "5. Builder Structures Can Include Modded Templates"
        },
        {
          "level": 2,
          "title": "Safe Migration Plan"
        },
        {
          "level": 2,
          "title": "What Did Not Change"
        },
        {
          "level": 2,
          "title": "When In Doubt"
        }
      ],
      "related": [
        "pack-development",
        "example-packs",
        "datapack-generator"
      ]
    },
    {
      "slug": "quest-scene-runtime",
      "file": "quest-scene-runtime.md",
      "source": "docs/quest-scene-runtime.md",
      "sourceKind": "implementation",
      "group": "Runtime Internals",
      "icon": "database-zap",
      "title": "Quest Scene Runtime",
      "description": "Durable quest-run identity, operation ownership, continuations, cleanup, and downed-state contracts.",
      "markdown": "# Quest Scene Runtime\n\nThis page explains the saved runtime rules for contributors who change quest or scene code. Pack authors usually need [Quest Scenes](../wiki/Quest-Scenes.md) instead.\n\nTerms used on this page:\n\n- **Run ID**: the UUID that identifies one attempt at a quest.\n- **Owner**: the player, party, quest run, or world record responsible for a scene.\n- **Operation ID**: an author-chosen name that prevents the same scene action from starting twice.\n- **Continuation**: saved work that resumes after a scene finishes.\n- **Receipt**: a saved record showing whether a one-time effect has run.\n- **Tombstone**: a small record retained after old scene details are removed. It prevents a completed operation from running again.\n- **Cleanup**: removal of scene-owned entities and restoration of scene-owned block changes.\n\n## Definitive quest-run identity\n\nA solo run ID comes from the player UUID, quest resource ID, and saved start count. It remains stable for one run and changes for every valid repeat. A shared party quest uses the saved `PartySharedQuestRecord.instanceId` for every member, regardless of personal start history.\n\nStartup establishes identity before authored effects:\n\n1. Decide solo or shared-party ownership.\n2. Allocate or recover the definitive run ID.\n3. Save quest, provider, and run state.\n4. Enroll or link party members using that ID.\n5. Run stage-entry actions.\n6. Dispatch `STARTED` triggers.\n7. Synchronize tracker and UI state.\n\nDuplicate start requests use the already-active progress and do not increment `startCount`.\n\n## Owners and operation keys\n\n- `PLAYER`: player identity plus the quest, run, and operation key. Repeated quests stay separate.\n- `PARTY`: one owner per party and shared quest run. Reuse adds missing participants and bindings but never replaces a fixed binding.\n- `QUEST_INSTANCE`: the definitive quest-run UUID. Unrelated players cannot collide.\n- `WORLD`: one shared owner for a dimension, scene, quest, and operation ID. It is not tied to one quest run. Authors must use an operation ID that remains unique in that dimension.\n\nThe runtime rebuilds its operation lookup table when a world loads. Older owner aliases and compact tombstones remain searchable, so an operation completed by an earlier version cannot start again.\n\n## Wait-for-result continuations\n\n`wait_for_result: false` starts or reuses a scene and continues immediately. `true` saves a continuation without freezing the server. It records enough information to resume at the next action, including the scene, quest run, player, provider, compiled actions, replacement values, result, and completion receipt.\n\nSuccess resumes the remaining actions once. Failure and cancellation are recorded separately and do not run success actions. Duplicate packets reuse the pending continuation. Offline players or unloaded providers leave it pending. If an optional scene definition is missing after reload, the runtime skips it. A required missing scene stops the sequence. Scene `action_batch` cannot pause safely, so compilation rejects that combination.\n\nMaintenance processes at most 16 continuations per tick.\n\n## Deadlines\n\nAn overall timeout of zero disables the timeout for compatibility. Any other value creates a deadline from `startGameTime + timeoutTicks`. If that addition would exceed the largest supported number, the value is capped. Waiting scenes schedule whichever comes first, their next step or the overall deadline. Blocked scenes keep a deadline wake-up without checking every tick. Reload recreates the same deadline. An overdue scene is processed once.\n\nDefinition reload uses the newly compiled timeout with the saved start time. Shortening a timeout past the current time fires it immediately. Changing it to zero disables the overall deadline.\n\n## Failure and cancellation policies\n\nAll executor failures, encounter failures, actor-policy failures, overall timeouts, quest-terminal callbacks, and operator cancellations use one saved transition service:\n\n- `FAIL_SCENE`: end the scene as failed.\n- `CANCEL_SCENE`: end the scene as cancelled.\n- `BLOCK_FOR_REPAIR`: save a blocked state that an operator can repair.\n- `RUN_FAILURE_STEP`: advance once to the current step's `failure_step`.\n\nA missing failure step blocks with a focused error. Transition intent and the applied marker survive reload. Operator resume accepts only repairable blocks created by a policy.\n\nThe typed `quest_transition` scene step accepts exactly one destination. Use `target_stage` or `target: stage` for a stage, or use `target: complete`, `target: fail`, or `target: abandon`. Mixed and unknown forms fail compilation. When a quest ends, its callback excludes the scene that caused the transition but still applies the cancellation policy to sibling scenes.\n\n## Cleanup lifecycle\n\nScene result and cleanup are saved separately. A terminal scene keeps `COMPLETED`, `FAILED`, or `CANCELLED` while `CleanupStatus` moves from `RUNNING` to `COMPLETE`. Cleanup can instead become `BLOCKED` with an explanation and a delayed retry. `CLEANING_UP` exists only for older saved data and is not the current cleanup marker.\n\nEvery terminal path queues cleanup once. Encounter cleanup removes or releases only entities owned by the scene. It restores a changed block only when the block still matches the scene's replacement, which preserves later player edits. Missing definitions retry after 1,200 ticks instead of checking continuously. Operator inspection shows the cleanup status, explanation, and retry time.\n\n## Saves, legacy aliases, and exactly-once records\n\nScene save version 3 introduced explicit `RunIdentityKind`: `QUEST_RUN` or `LEGACY_OWNER`. This marker survives every save and keeps older `PLAYER` and `QUEST_INSTANCE` operations reusable until they finish. Version 4 added terminal tombstones.\n\nEffects that must run only once use operation receipts with `PREPARED`, `APPLIED`, and `COMPLETED` states. Work left in an uncertain prepared state blocks for operator repair instead of risking a duplicate effect. Continuations have their own completion receipt. Data cleanup never removes unresolved receipts, pending continuations or rewards, active encounters, blocked cleanup, or unfinished scenes.\n\nFinished scenes retain full details for seven in-game days. The runtime then reduces them gradually to tombstones that contain the operation identity, result, times, and completed receipt IDs. Maintenance inspects at most 16 candidates every 200 ticks and retains up to 4,096 tombstones.\n\n## Protected villagers and the downed state\n\nProtected villagers do not enter the normal death path after ordinary lethal damage. Final damage is capped in `LivingDamageEvent.Pre`, the villager remains at one health, and a saved downed record stores when the state began, when recovery can begin, which protection source applied, the data version, and the AI or pickup settings to restore. Party contracts, quest provider bindings, scene bindings, inventories, and hired work state remain intact.\n\nProtection is active when any of these sources applies:\n\n- `combat.allVillagersUseDownedState` is enabled.\n- The villager is in a vanilla or player raid and `combat.raidVillagersUseDownedState` is enabled.\n- The villager has an active hired contract and `combat.hiredVillagersUseDownedState` is enabled.\n- The villager has an active party contract and `combat.partyVillagersUseDownedState` is enabled.\n- An active quest run from that exact provider UUID uses `death_protection: \"while_active\"`.\n- That provider successfully started a quest using `death_protection: \"after_start\"`.\n- An active scene binds the exact villager to an actor with `lethal_damage_policy: \"downed\"`.\n- The entity has the permanent scoreboard tag `villagerretaliation_essential`.\n\nAfter a protection source qualifies the villager, separate player, mob, and environmental damage settings decide whether that lethal source may down them. Disabled source categories can finish an already-downed villager with a lethal hit. Operator kill, void, and invulnerability-bypassing sources always bypass protection.\n\nWhile downed, AI, navigation, attacks, work, following, item pickup, trading, gifts, breeding, and dialogue are suspended. Repeated attacks still cause normal hit effects once but cannot reduce health. Nearby mobs targeting the villager are cleared on entry and once per second. The client receives state changes and renders one of three stable whole-body poses. Each pose also adjusts the hitbox and name-tag position.\n\nRecovery requires the configured minimum duration, no nearby natural hostile or mob targeting the villager within `downedThreatRadius`, and `downedQuietTicks` of quiet. Health returns to `downedRecoveryHealthPercent` of maximum, with a minimum of one. Previous AI and pickup settings are restored. `DOWNED` scene bindings return to `LIVE` and wake their scenes. Recovery still finishes normally if the original protection expires while the villager is downed.\n\nWhen Second Wind is installed, every villager protected by this resolver is also available through Second Wind's player revive interaction. Villager Retaliation still owns the state. There is no bleedout deadline, and a villager that is not manually revived continues through the normal quiet-period recovery. Compatible clients select a downed pose for each new record. Ordinary unprotected villagers are not included.\n\n`/kill` with `minecraft:generic_kill`, out-of-world damage, damage tagged `minecraft:bypasses_invulnerability`, and direct entity removal bypass protection. Void damage is allowed to kill so a protected villager cannot fall forever.\n\n### Quest provider example\n\n```json\n{\n  \"provider\": {\n    \"type\": \"villagerretaliation:villager\",\n    \"death_protection\": \"while_active\",\n    \"filters\": {\n      \"professions\": [\"minecraft:cartographer\"]\n    }\n  }\n}\n```\n\n`none` is the default. `while_active` applies only while at least one active run is bound to the exact provider UUID. `after_start` writes the originating quest ID to the villager only after startup is saved successfully. Offers and viewed dialogue do not protect the villager. The marker survives completed or failed quests and reloads. Multiple quest IDs can coexist without repeating start effects. Invalid values report a focused error and fall back to `none`.\n\n### Scene actor example\n\n```json\n{\n  \"alias\": \"guide\",\n  \"type\": \"villagerretaliation:villager\",\n  \"binding_source\": \"quest_provider\",\n  \"replacement_policy\": \"fixed\",\n  \"missing_actor_policy\": \"block\",\n  \"lethal_damage_policy\": \"downed\",\n  \"death_policy\": \"apply_missing_policy\"\n}\n```\n\n`lethal_damage_policy` defaults to `normal`. It prevents death only while the owning scene is active and is separate from the post-death `death_policy`. A downed binding is saved as `DOWNED`. Steps that need that actor remain blocked without checking every tick. Recovery wakes and resumes the scene.\n",
      "text": "Quest Scene Runtime This page explains the saved runtime rules for contributors who change quest or scene code. Pack authors usually need Quest Scenes ../wiki/Quest Scenes.md instead. Terms used on this page: Run ID : the UUID that identifies one attempt at a quest. Owner : the player, party, quest run, or world record responsible for a scene. Operation ID : an author chosen name that prevents the same scene action from starting twice. Continuation : saved work that resumes after a scene finishes. Receipt : a saved record showing whether a one time effect has run. Tombstone : a small record retained after old scene details are removed. It prevents a completed operation from running again. Cleanup : removal of scene owned entities and restoration of scene owned block changes. Definitive quest run identity A solo run ID comes from the player UUID, quest resource ID, and saved start count. It remains stable for one run and changes for every valid repeat. A shared party quest uses the saved PartySharedQuestRecord.instanceId for every member, regardless of personal start history. Startup establishes identity before authored effects: 1. Decide solo or shared party ownership. 2. Allocate or recover the definitive run ID. 3. Save quest, provider, and run state. 4. Enroll or link party members using that ID. 5. Run stage entry actions. 6. Dispatch STARTED triggers. 7. Synchronize tracker and UI state. Duplicate start requests use the already active progress and do not increment startCount. Owners and operation keys PLAYER: player identity plus the quest, run, and operation key. Repeated quests stay separate. PARTY: one owner per party and shared quest run. Reuse adds missing participants and bindings but never replaces a fixed binding. QUEST INSTANCE: the definitive quest run UUID. Unrelated players cannot collide. WORLD: one shared owner for a dimension, scene, quest, and operation ID. It is not tied to one quest run. Authors must use an operation ID that remains unique in that dimension. The runtime rebuilds its operation lookup table when a world loads. Older owner aliases and compact tombstones remain searchable, so an operation completed by an earlier version cannot start again. Wait for result continuations wait for result: false starts or reuses a scene and continues immediately. true saves a continuation without freezing the server. It records enough information to resume at the next action, including the scene, quest run, player, provider, compiled actions, replacement values, result, and completion receipt. Success resumes the remaining actions once. Failure and cancellation are recorded separately and do not run success actions. Duplicate packets reuse the pending continuation. Offline players or unloaded providers leave it pending. If an optional scene definition is missing after reload, the runtime skips it. A required missing scene stops the sequence. Scene action batch cannot pause safely, so compilation rejects that combination. Maintenance processes at most 16 continuations per tick. Deadlines An overall timeout of zero disables the timeout for compatibility. Any other value creates a deadline from startGameTime + timeoutTicks. If that addition would exceed the largest supported number, the value is capped. Waiting scenes schedule whichever comes first, their next step or the overall deadline. Blocked scenes keep a deadline wake up without checking every tick. Reload recreates the same deadline. An overdue scene is processed once. Definition reload uses the newly compiled timeout with the saved start time. Shortening a timeout past the current time fires it immediately. Changing it to zero disables the overall deadline. Failure and cancellation policies All executor failures, encounter failures, actor policy failures, overall timeouts, quest terminal callbacks, and operator cancellations use one saved transition service: FAIL SCENE: end the scene as failed. CANCEL SCENE: end the scene as cancelled. BLOCK FOR REPAIR: save a blocked state that an operator can repair. RUN FAILURE STEP: advance once to the current step's failure step. A missing failure step blocks with a focused error. Transition intent and the applied marker survive reload. Operator resume accepts only repairable blocks created by a policy. The typed quest transition scene step accepts exactly one destination. Use target stage or target: stage for a stage, or use target: complete, target: fail, or target: abandon. Mixed and unknown forms fail compilation. When a quest ends, its callback excludes the scene that caused the transition but still applies the cancellation policy to sibling scenes. Cleanup lifecycle Scene result and cleanup are saved separately. A terminal scene keeps COMPLETED, FAILED, or CANCELLED while CleanupStatus moves from RUNNING to COMPLETE. Cleanup can instead become BLOCKED with an explanation and a delayed retry. CLEANING UP exists only for older saved data and is not the current cleanup marker. Every terminal path queues cleanup once. Encounter cleanup removes or releases only entities owned by the scene. It restores a changed block only when the block still matches the scene's replacement, which preserves later player edits. Missing definitions retry after 1,200 ticks instead of checking continuously. Operator inspection shows the cleanup status, explanation, and retry time. Saves, legacy aliases, and exactly once records Scene save version 3 introduced explicit RunIdentityKind: QUEST RUN or LEGACY OWNER. This marker survives every save and keeps older PLAYER and QUEST INSTANCE operations reusable until they finish. Version 4 added terminal tombstones. Effects that must run only once use operation receipts with PREPARED, APPLIED, and COMPLETED states. Work left in an uncertain prepared state blocks for operator repair instead of risking a duplicate effect. Continuations have their own completion receipt. Data cleanup never removes unresolved receipts, pending continuations or rewards, active encounters, blocked cleanup, or unfinished scenes. Finished scenes retain full details for seven in game days. The runtime then reduces them gradually to tombstones that contain the operation identity, result, times, and completed receipt IDs. Maintenance inspects at most 16 candidates every 200 ticks and retains up to 4,096 tombstones. Protected villagers and the downed state Protected villagers do not enter the normal death path after ordinary lethal damage. Final damage is capped in LivingDamageEvent.Pre, the villager remains at one health, and a saved downed record stores when the state began, when recovery can begin, which protection source applied, the data version, and the AI or pickup settings to restore. Party contracts, quest provider bindings, scene bindings, inventories, and hired work state remain intact. Protection is active when any of these sources applies: combat.allVillagersUseDownedState is enabled. The villager is in a vanilla or player raid and combat.raidVillagersUseDownedState is enabled. The villager has an active hired contract and combat.hiredVillagersUseDownedState is enabled. The villager has an active party contract and combat.partyVillagersUseDownedState is enabled. An active quest run from that exact provider UUID uses death protection: \"while active\". That provider successfully started a quest using death protection: \"after start\". An active scene binds the exact villager to an actor with lethal damage policy: \"downed\". The entity has the permanent scoreboard tag villagerretaliation essential. After a protection source qualifies the villager, separate player, mob, and environmental damage settings decide whether that lethal source may down them. Disabled source categories can finish an already downed villager with a lethal hit. Operator kill, void, and invulnerability bypassing sources always bypass protection. While downed, AI, navigation, attacks, work, following, item pickup, trading, gifts, breeding, and dialogue are suspended. Repeated attacks still cause normal hit effects once but cannot reduce health. Nearby mobs targeting the villager are cleared on entry and once per second. The client receives state changes and renders one of three stable whole body poses. Each pose also adjusts the hitbox and name tag position. Recovery requires the configured minimum duration, no nearby natural hostile or mob targeting the villager within downedThreatRadius, and downedQuietTicks of quiet. Health returns to downedRecoveryHealthPercent of maximum, with a minimum of one. Previous AI and pickup settings are restored. DOWNED scene bindings return to LIVE and wake their scenes. Recovery still finishes normally if the original protection expires while the villager is downed. When Second Wind is installed, every villager protected by this resolver is also available through Second Wind's player revive interaction. Villager Retaliation still owns the state. There is no bleedout deadline, and a villager that is not manually revived continues through the normal quiet period recovery. Compatible clients select a downed pose for each new record. Ordinary unprotected villagers are not included. /kill with minecraft:generic kill, out of world damage, damage tagged minecraft:bypasses invulnerability, and direct entity removal bypass protection. Void damage is allowed to kill so a protected villager cannot fall forever. Quest provider example none is the default. while active applies only while at least one active run is bound to the exact provider UUID. after start writes the originating quest ID to the villager only after startup is saved successfully. Offers and viewed dialogue do not protect the villager. The marker survives completed or failed quests and reloads. Multiple quest IDs can coexist without repeating start effects. Invalid values report a focused error and fall back to none. Scene actor example lethal damage policy defaults to normal. It prevents death only while the owning scene is active and is separate from the post death death policy. A downed binding is saved as DOWNED. Steps that need that actor remain blocked without checking every tick. Recovery wakes and resumes the scene.",
      "headings": [
        {
          "level": 2,
          "title": "Definitive quest-run identity"
        },
        {
          "level": 2,
          "title": "Owners and operation keys"
        },
        {
          "level": 2,
          "title": "Wait-for-result continuations"
        },
        {
          "level": 2,
          "title": "Deadlines"
        },
        {
          "level": 2,
          "title": "Failure and cancellation policies"
        },
        {
          "level": 2,
          "title": "Cleanup lifecycle"
        },
        {
          "level": 2,
          "title": "Saves, legacy aliases, and exactly-once records"
        },
        {
          "level": 2,
          "title": "Protected villagers and the downed state"
        },
        {
          "level": 3,
          "title": "Quest provider example"
        },
        {
          "level": 3,
          "title": "Scene actor example"
        }
      ],
      "related": [
        "quest-scenes",
        "quests",
        "quest-runtime-roadmap"
      ]
    },
    {
      "slug": "tracked-villages",
      "file": "tracked-villages.md",
      "source": "docs/tracked-villages.md",
      "sourceKind": "implementation",
      "group": "Runtime Internals",
      "icon": "map-pinned",
      "title": "Tracked Villages",
      "description": "Tracked-village identity, footprints, allegiance, naming, lifecycle, and administration.",
      "markdown": "# Tracked Villages\n\nThis page explains how village identity works for contributors and pack authors.\n\nA **point of interest**, or POI, is a workstation, bell, bed, or other block that Minecraft uses to recognize village activity. A **footprint** is the set of 16 by 16 block sections that Villager Retaliation considers part of a village. A **canonical village** is the surviving shared identity after two village records merge.\n\nTracked villages are saved on the server and begin around occupied village POIs. Each record has a UUID, a generated or custom name, a canonical identity, a footprint, a resident list, a lifecycle state, and the time it was last observed. The footprint combines POI influence, tagged generated structures, and connected tagged terrain.\n\n## Data-driven footprint support\n\nTwo ordinary datapack tags control non-POI coverage:\n\n- `villagerretaliation:village_footprint` is a `worldgen/structure` tag. It includes `#minecraft:village` by default, so vanilla village buildings and roads are covered.\n- `villagerretaliation:village_terrain` is a block tag containing `minecraft:dirt_path` by default. Sections containing these blocks extend the footprint only when they form a connected chain from the POI or structure footprint. Unrelated paths elsewhere remain outside.\n\nMods and modpacks can append structures or terrain blocks with normal `replace: false` tag files. Structure and terrain scans inspect loaded chunks only and do not force-load world generation.\n\nFor example, this file adds a custom village structure:\n\n```text\ndata/villagerretaliation/tags/worldgen/structure/village_footprint.json\n```\n\n```json\n{\n  \"replace\": false,\n  \"values\": [\"my_pack:river_village\"]\n}\n```\n\nThis file lets connected custom paths extend a village:\n\n```text\ndata/villagerretaliation/tags/block/village_terrain.json\n```\n\n```json\n{\n  \"replace\": false,\n  \"values\": [\"my_pack:packed_mud_path\"]\n}\n```\n\nRun `/reload` after changing either tag. Inspect the result with the debug overlay described below.\n\n## Allegiance rules\n\n- Villagers and naturally created iron golems spawned inside a tracked footprint receive that village permanently.\n- Newborn villagers born inside a village receive that village, regardless of their parents' homes. Outside a village, a baby inherits the first parent's known home. If neither parent has one, the baby is a Wanderer.\n- Villagers spawned outside every tracked footprint are Wanderers. They are neutral to all villages and can be recruited against any village.\n- A non-party Wanderer who remains inside the same active village for 24,000 ticks settles there automatically. Leaving, changing villages, joining a party, or a backward game-time change resets the settlement clock.\n- Villagers who already have a home never change it merely by traveling or claiming a local bed or workstation.\n- Party villagers never settle automatically. A party villager can adopt the current village only when ordered by a Revered or Royalty player in that same party. Outside players cannot issue the order.\n- Recruited foreign residents can fight another village without the target belonging to a party.\n- Same-party and same-canonical-village combat is always rejected. A merge therefore invalidates an older combat authorization immediately.\n- Community retaliation begins only after damage lands and spreads only through the harmed resident's canonical village.\n- Conversion preserves current-version allegiance. Older or missing data is classified from the entity's position when it loads.\n- An uncertain assignment remains pending across entity saves and retries until the surrounding chunks provide enough evidence to resolve it safely.\n- Deliberate reassignment requires Revered individual trust and a repeated confirmation within 30 seconds. The party restriction above is applied before trust is considered.\n\nConnected POI footprints merge automatically only after three checks at different times show the same occupied POI connection. Terrain-only or diagonal section contact cannot merge identities. Current footprints may shrink as evidence changes, while historical coverage remains available for debugging. Empty records move toward archival only while every footprint chunk is loaded and observed. After 72,000 observed ticks with no occupied source POI, the identity is archived. Rebuilding creates a new identity.\n\nThe player-facing Home topic answers direct questions about the villager's home and the current village. Technical assignment history remains available through allegiance inspection commands. The entity data keeps the latest eight changes, including the player responsible for a trusted reassignment.\n\n## Village naming\n\nUse any banner on a bell inside an active tracked village. The banner stays in the player's hand. A non-operator must have Revered or Royalty reputation with at least half, rounded up, of the tracked living adult residents. Names are 1 to 32 characters. Extra whitespace is removed, formatting codes are rejected, and names must be unique across tracked villages.\n\n## Debug visualization\n\nSet `debugOverlay.showVillageBounds` in the generated config screen. The default is `false`.\n\nWhen enabled, the option:\n\n- Subscribes to a server preview of active and recently emptied villages within 256 blocks.\n- Draws the actual POI section boundary without internal faces.\n- Shows a gold center marker that matches the job-site debug marker.\n- Shows the canonical village name as gold text at the top center while the player is inside its footprint.\n- Hides the label with the rest of the GUI when F1 is pressed.\n- Clears saved preview shapes and labels after disabling the option, logging out, changing dimension, unsubscribing, or missing the next preview update.\n\nThe preview never force-loads chunks, sends archived footprints, or updates players who are not subscribed. One update is limited to 64 villages, 512 sections per village, and 4,096 sections in total.\n\n## Administration\n\n```mcfunction\n/vr admin villager allegiance inspect <villager>\n/vr admin villager allegiance explain <villager>\n/vr admin villager allegiance repair <villager>\n/vr admin villager allegiance statistics\n/vr admin villager allegiance undoMerge <source-uuid>\n/vr admin village inspectHere\n/vr admin village list\n/vr admin village renameHere <name>\n```\n\nUse `inspect` for the saved assignment, `explain` for the rule that produced it, and `repair` when the assignment is stuck pending. The village commands inspect, list, rename, or undo a merge for tracked village identities.\n",
      "text": "Tracked Villages This page explains how village identity works for contributors and pack authors. A point of interest , or POI, is a workstation, bell, bed, or other block that Minecraft uses to recognize village activity. A footprint is the set of 16 by 16 block sections that Villager Retaliation considers part of a village. A canonical village is the surviving shared identity after two village records merge. Tracked villages are saved on the server and begin around occupied village POIs. Each record has a UUID, a generated or custom name, a canonical identity, a footprint, a resident list, a lifecycle state, and the time it was last observed. The footprint combines POI influence, tagged generated structures, and connected tagged terrain. Data driven footprint support Two ordinary datapack tags control non POI coverage: villagerretaliation:village footprint is a worldgen/structure tag. It includes minecraft:village by default, so vanilla village buildings and roads are covered. villagerretaliation:village terrain is a block tag containing minecraft:dirt path by default. Sections containing these blocks extend the footprint only when they form a connected chain from the POI or structure footprint. Unrelated paths elsewhere remain outside. Mods and modpacks can append structures or terrain blocks with normal replace: false tag files. Structure and terrain scans inspect loaded chunks only and do not force load world generation. For example, this file adds a custom village structure: This file lets connected custom paths extend a village: Run /reload after changing either tag. Inspect the result with the debug overlay described below. Allegiance rules Villagers and naturally created iron golems spawned inside a tracked footprint receive that village permanently. Newborn villagers born inside a village receive that village, regardless of their parents' homes. Outside a village, a baby inherits the first parent's known home. If neither parent has one, the baby is a Wanderer. Villagers spawned outside every tracked footprint are Wanderers. They are neutral to all villages and can be recruited against any village. A non party Wanderer who remains inside the same active village for 24,000 ticks settles there automatically. Leaving, changing villages, joining a party, or a backward game time change resets the settlement clock. Villagers who already have a home never change it merely by traveling or claiming a local bed or workstation. Party villagers never settle automatically. A party villager can adopt the current village only when ordered by a Revered or Royalty player in that same party. Outside players cannot issue the order. Recruited foreign residents can fight another village without the target belonging to a party. Same party and same canonical village combat is always rejected. A merge therefore invalidates an older combat authorization immediately. Community retaliation begins only after damage lands and spreads only through the harmed resident's canonical village. Conversion preserves current version allegiance. Older or missing data is classified from the entity's position when it loads. An uncertain assignment remains pending across entity saves and retries until the surrounding chunks provide enough evidence to resolve it safely. Deliberate reassignment requires Revered individual trust and a repeated confirmation within 30 seconds. The party restriction above is applied before trust is considered. Connected POI footprints merge automatically only after three checks at different times show the same occupied POI connection. Terrain only or diagonal section contact cannot merge identities. Current footprints may shrink as evidence changes, while historical coverage remains available for debugging. Empty records move toward archival only while every footprint chunk is loaded and observed. After 72,000 observed ticks with no occupied source POI, the identity is archived. Rebuilding creates a new identity. The player facing Home topic answers direct questions about the villager's home and the current village. Technical assignment history remains available through allegiance inspection commands. The entity data keeps the latest eight changes, including the player responsible for a trusted reassignment. Village naming Use any banner on a bell inside an active tracked village. The banner stays in the player's hand. A non operator must have Revered or Royalty reputation with at least half, rounded up, of the tracked living adult residents. Names are 1 to 32 characters. Extra whitespace is removed, formatting codes are rejected, and names must be unique across tracked villages. Debug visualization Set debugOverlay.showVillageBounds in the generated config screen. The default is false. When enabled, the option: Subscribes to a server preview of active and recently emptied villages within 256 blocks. Draws the actual POI section boundary without internal faces. Shows a gold center marker that matches the job site debug marker. Shows the canonical village name as gold text at the top center while the player is inside its footprint. Hides the label with the rest of the GUI when F1 is pressed. Clears saved preview shapes and labels after disabling the option, logging out, changing dimension, unsubscribing, or missing the next preview update. The preview never force loads chunks, sends archived footprints, or updates players who are not subscribed. One update is limited to 64 villages, 512 sections per village, and 4,096 sections in total. Administration Use inspect for the saved assignment, explain for the rule that produced it, and repair when the assignment is stuck pending. The village commands inspect, list, rename, or undo a merge for tracked village identities.",
      "headings": [
        {
          "level": 2,
          "title": "Data-driven footprint support"
        },
        {
          "level": 2,
          "title": "Allegiance rules"
        },
        {
          "level": 2,
          "title": "Village naming"
        },
        {
          "level": 2,
          "title": "Debug visualization"
        },
        {
          "level": 2,
          "title": "Administration"
        }
      ],
      "related": [
        "village-names",
        "story-discovery",
        "player-raids"
      ]
    }
  ],
  "examples": [
    {
      "id": "pack-development-example-1",
      "page": "pack-development",
      "pageTitle": "Pack Development",
      "section": "Pack Types",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"pack\": {\n    \"pack_format\": 48,\n    \"description\": \"My Villager Retaliation addon\"\n  }\n}"
    },
    {
      "id": "pack-development-example-2",
      "page": "pack-development",
      "pageTitle": "Pack Development",
      "section": "Override Rules",
      "level": "Advanced",
      "language": "json",
      "code": "{ \"replace\": true }"
    },
    {
      "id": "pack-development-example-3",
      "page": "pack-development",
      "pageTitle": "Pack Development",
      "section": "Suggested Workflow",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.message.test\",\n  \"key\": \"my_pack.message.test\",\n  \"text\": \"Testing.\"\n}"
    },
    {
      "id": "json-reference-example-1",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Stable Ids",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.greeting.rainy_day\",\n  \"request\": \"greeting\",\n  \"text\": \"Rain makes even short roads feel longer.\"\n}"
    },
    {
      "id": "json-reference-example-2",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "`text` vs `lines`",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.line.variants\",\n  \"request\": \"question\",\n  \"lines\": [\n    \"Quiet roads are usually planning something.\",\n    \"Roads are safer when someone else has already checked them.\"\n  ],\n  \"weight\": 10\n}"
    },
    {
      "id": "json-reference-example-3",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "`replace` and `remove`",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"replace\": true,\n  \"notifications\": []\n}"
    },
    {
      "id": "json-reference-example-4",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "`replace` and `remove`",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"preferences\": [\n    {\n      \"id\": \"villagerretaliation.default.bad_gift\",\n      \"remove\": true\n    }\n  ]\n}"
    },
    {
      "id": "json-reference-example-5",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "`replace` and `remove`",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"entries\": [\n    {\n      \"structure\": \"minecraft:village/plains/houses/plains_small_house_1\",\n      \"remove\": true\n    }\n  ]\n}"
    },
    {
      "id": "json-reference-example-6",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Arrays and Single Values",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"professions\": [\"minecraft:farmer\", \"minecraft:fletcher\"]\n}"
    },
    {
      "id": "json-reference-example-7",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Reputation Filters",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.notification.low_trust\",\n  \"trigger\": \"trade.refused\",\n  \"text\": \"Not today.\",\n  \"reputation_levels\": [\"hostile\", \"despised\", \"feared\"]\n}"
    },
    {
      "id": "json-reference-example-8",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Item and Tag Selectors",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"items\": [\"minecraft:emerald\"]\n}"
    },
    {
      "id": "json-reference-example-9",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Item and Tag Selectors",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"items\": [\"#minecraft:flowers\"]\n}"
    },
    {
      "id": "json-reference-example-10",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Currency",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"item\": \"minecraft:emerald\",\n  \"name\": \"emerald\",\n  \"plural_name\": \"emeralds\",\n  \"wallet_label\": \"Emeralds\",\n  \"text_color\": \"#55ff55\"\n}"
    },
    {
      "id": "json-reference-example-11",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Conditions",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.line.night_storm\",\n  \"request\": \"village_event_report\",\n  \"conditions\": [\n    { \"type\": \"time\", \"value\": \"night\" },\n    { \"type\": \"weather\", \"state\": \"thunder\" }\n  ],\n  \"text\": \"Storm nights make bad fences and worse promises.\"\n}"
    },
    {
      "id": "json-reference-example-12",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Mood Conditions",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"conditions\": [\n    { \"type\": \"mood\", \"mood\": \"protective\", \"min_mood_intensity\": 30 }\n  ]\n}"
    },
    {
      "id": "json-reference-example-13",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Quest Facts",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"conditions\": [\n    {\n      \"type\": \"quest_fact\",\n      \"scope\": \"quest\",\n      \"quest\": \"my_pack:old_road\",\n      \"tag\": \"my_pack:warned_the_guard\"\n    }\n  ]\n}"
    },
    {
      "id": "json-reference-example-14",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Quest Facts",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"conditions\": [\n    {\n      \"type\": \"quest_fact\",\n      \"scope\": \"quest\",\n      \"quest\": \"my_pack:old_road\",\n      \"key\": \"route\",\n      \"value\": \"river\"\n    },\n    {\n      \"type\": \"quest_fact\",\n      \"scope\": \"player\",\n      \"counter\": \"raiders_defeated\",\n      \"min\": 5\n    }\n  ]\n}"
    },
    {
      "id": "json-reference-example-15",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Quest Facts",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"conditions\": [\n    {\n      \"type\": \"quest_stage\",\n      \"quest\": \"my_pack:old_road\",\n      \"stage\": \"warned_guard\"\n    }\n  ]\n}"
    },
    {
      "id": "json-reference-example-16",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Quest Facts",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"parent\": \"my_pack:first_chapter\",\n  \"offer\": {\n    \"conditions\": [\n      {\n        \"type\": \"quest_fact\",\n        \"scope\": \"world\",\n        \"tag\": \"my_pack:bridge_repaired\"\n      }\n    ]\n  }\n}"
    },
    {
      "id": "json-reference-example-17",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Quest Facts",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"rules\": {\n    \"exclusive_group\": \"my_pack:faction_choice\",\n    \"exclusive_on\": \"started\",\n    \"blocks_on_completion\": [\"my_pack:other_outcome\"]\n  }\n}"
    },
    {
      "id": "json-reference-example-18",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Quest Module V2",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"dialogue\": {\n    \"offer\": {\n      \"label\": \"Bread Delivery\",\n      \"request\": \"question\",\n      \"lines\": [\"The bins are low.\"],\n      \"responses\": [\n        {\n          \"id\": \"accept\",\n          \"label\": \"I can help.\",\n          \"scene\": \"start_quest\"\n        }\n      ]\n    }\n  }\n}"
    },
    {
      "id": "json-reference-example-19",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Quest Module V2",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"external_scenes\": [\"my_pack:quests/village_supply/bread_delivery\"],\n  \"dialogue\": {\n    \"offer\": {\n      \"label\": \"Bread Delivery\",\n      \"request\": \"question\",\n      \"external_scene\": {\n        \"tree\": \"my_pack:quests/village_supply/bread_delivery\",\n        \"entry\": \"offer\"\n      }\n    }\n  }\n}"
    },
    {
      "id": "json-reference-example-20",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Shared Actions",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"actions\": [\n    { \"type\": \"quest\", \"quest\": \"my_pack:old_road\", \"action\": \"start\" },\n    { \"type\": \"notification\", \"trigger\": \"quest.updated\", \"text\": \"Quest updated: {quest}\" }\n  ]\n}"
    },
    {
      "id": "json-reference-example-21",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Shared Actions",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"actions\": [\n    { \"type\": \"draw_weapon\", \"duration_seconds\": 10 }\n  ]\n}"
    },
    {
      "id": "json-reference-example-22",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Shared Actions",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"type\": \"quest\",\n  \"quest\": \"my_pack:smuggle_the_relic\",\n  \"action\": \"block\"\n}"
    },
    {
      "id": "json-reference-example-23",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Shared Actions",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"actions\": [\n    {\n      \"type\": \"set_tag\",\n      \"scope\": \"quest\",\n      \"quest\": \"my_pack:old_road\",\n      \"tag\": \"my_pack:warned_the_guard\"\n    },\n    {\n      \"type\": \"set_variable\",\n      \"scope\": \"quest\",\n      \"quest\": \"my_pack:old_road\",\n      \"key\": \"route\",\n      \"value\": \"river\"\n    },\n    {\n      \"type\": \"set_stage\",\n      \"quest\": \"my_pack:old_road\",\n      \"stage\": \"warned_guard\"\n    },\n    {\n      \"type\": \"counter\",\n      \"scope\": \"player\",\n      \"counter\": \"raiders_defeated\",\n      \"amount\": 1\n    }\n  ]\n}"
    },
    {
      "id": "json-reference-example-24",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Weights and Priority",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.line.high_priority_warning\",\n  \"request\": \"question\",\n  \"priority\": 20,\n  \"weight\": 1,\n  \"text\": \"You should deal with the raid first.\"\n}"
    },
    {
      "id": "json-reference-example-25",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Message Keys",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.line.shared_warning\",\n  \"request\": \"question\",\n  \"text_key\": \"my_pack.warning.road_closed\"\n}"
    },
    {
      "id": "json-reference-example-26",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Message Keys",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.message.road_closed\",\n  \"key\": \"my_pack.warning.road_closed\",\n  \"text\": \"The road is closed until morning.\"\n}"
    },
    {
      "id": "json-reference-example-27",
      "page": "json-reference",
      "pageTitle": "JSON Reference",
      "section": "Troubleshooting Example",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.debug\",\n  \"request\": \"question\",\n  \"text\": \"Debug line.\"\n}"
    },
    {
      "id": "first-quest-example-1",
      "page": "first-quest",
      "pageTitle": "First Quest Guide",
      "section": "File: Quest Module V2",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"schema\": \"villagerretaliation:quest/v2\",\n  \"id\": \"my_pack:bread_delivery\",\n  \"metadata\": {\n    \"title\": \"Bread Delivery\",\n    \"description\": \"Bring 16 bread to the village stores.\",\n    \"questline\": \"village_supply\",\n    \"tags\": [\"group.village_supply\"]\n  },\n  \"provider\": {\n    \"type\": \"villagerretaliation:villager\",\n    \"filters\": {\n      \"professions\": [\"minecraft:farmer\"],\n      \"min_villager_level\": \"novice\"\n    }\n  },\n  \"availability\": {\n    \"repeatable\": true,\n    \"completion_cooldown_days\": 1,\n    \"locked_to_villager\": true,\n    \"cross_villager_compatible\": false,\n    \"abandonment\": \"allow_repickup\",\n    \"consume_on_completion\": true\n  },\n  \"entry_stage\": \"gather\",\n  \"stages\": [\n    {\n      \"id\": \"gather\",\n      \"objectives\": [\n        {\n          \"id\": \"bring_bread\",\n          \"type\": \"item_check\",\n          \"item\": \"minecraft:bread\",\n          \"count\": 16,\n          \"tracker\": {\n            \"text\": \"Bring 16 bread back to the quest giver.\",\n            \"complete_text\": \"The bread is packed and ready.\",\n            \"show_progress\": true,\n            \"progress\": 0.75\n          }\n        }\n      ],\n      \"dialogue\": {\n        \"offer\": {\n          \"label\": \"Bread Delivery\",\n          \"request\": \"question\",\n          \"order\": -20,\n          \"show_for_babies\": false,\n          \"lines\": [\n            \"The bins are low. Sixteen bread would quiet a lot of worried stomachs.\"\n          ],\n          \"responses\": [\n            {\n              \"id\": \"accept\",\n              \"label\": \"I can help stock the larder.\",\n              \"scene\": \"start_quest\"\n            },\n            {\n              \"id\": \"decline\",\n              \"label\": \"Another time.\",\n              \"scene\": \"decline\"\n            }\n          ]\n        },\n        \"reminder\": {\n          \"label\": \"About Bread Delivery\",\n          \"request\": \"question\",\n          \"order\": -20,\n          \"show_for_babies\": false,\n          \"lines\": [\n            \"Bread Delivery is still open. The tracker has the count.\"\n          ],\n          \"responses\": [\n            {\n              \"id\": \"leave\",\n              \"label\": \"I'll keep looking.\",\n              \"scene\": \"end\"\n            }\n          ]\n        },\n        \"turn_in\": {\n          \"label\": \"About Bread Delivery\",\n          \"request\": \"question\",\n          \"order\": -20,\n          \"show_for_babies\": false,\n          \"lines\": [\n            \"If that pack smells like fresh bread, you may have saved me an argument.\"\n          ],\n          \"responses\": [\n            {\n              \"id\": \"complete\",\n              \"label\": \"Show what I brought.\",\n              \"scene\": \"complete_quest\"\n            },\n            {\n              \"id\": \"leave\",\n              \"label\": \"Not yet.\",\n              \"scene\": \"end\"\n            }\n          ]\n        }\n      },\n      \"scenes\": [\n        {\n          \"id\": \"start_quest\",\n          \"actions\": [\n            {\n              \"type\": \"quest\",\n              \"action\": \"start\",\n              \"lines\": {\n                \"started\": [\n                  \"Good. Bring the bread back when the count is ready.\"\n                ],\n                \"unavailable\": [\n                  \"The larder is not asking you for bread right now.\"\n                ]\n              }\n            }\n          ]\n        },\n        {\n          \"id\": \"complete_quest\",\n          \"actions\": [\n            {\n              \"type\": \"quest\",\n              \"action\": \"turn_in\",\n              \"lines\": {\n                \"completed\": [\n                  \"Good. A full shelf makes brave talk sound less hollow.\"\n                ],\n                \"missing_objectives\": [\n                  \"Bread Delivery is still short. The tracker has the exact count.\"\n                ],\n                \"unavailable\": [\n                  \"This bread delivery is not ready to close yet.\"\n                ]\n              }\n            }\n          ]\n        },\n        {\n          \"id\": \"decline\",\n          \"text\": \"Then I will keep counting crumbs and pretending it is planning.\"\n        },\n        {\n          \"id\": \"end\",\n          \"text\": \"Keep the bread close until you are ready.\"\n        }\n      ]\n    }\n  ],\n  \"rewards\": {\n    \"experience\": 60,\n    \"reputation\": 5,\n    \"gossip_reputation\": 2\n  },\n  \"ui\": {\n    \"tracker_text\": \"Bring 16 bread.\",\n    \"icon\": \"minecraft:bread\",\n    \"color\": \"#DCEBA6\"\n  }\n}"
    },
    {
      "id": "example-packs-example-1",
      "page": "example-packs",
      "pageTitle": "Example Packs",
      "section": "Smallest Copyable Pack",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.option.ask_rumor\",\n  \"label\": \"Ask For A Rumor\",\n  \"request\": \"story\"\n}"
    },
    {
      "id": "example-packs-example-2",
      "page": "example-packs",
      "pageTitle": "Example Packs",
      "section": "Smallest Copyable Pack",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.line.rumor\",\n  \"request\": \"story\",\n  \"option\": \"my_pack.option.ask_rumor\",\n  \"text\": \"Roads carry stories faster than traders do.\"\n}"
    },
    {
      "id": "example-packs-example-3",
      "page": "example-packs",
      "pageTitle": "Example Packs",
      "section": "Minimal `pack.mcmeta`",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"pack\": {\n    \"pack_format\": 48,\n    \"description\": \"Villager Retaliation example pack\"\n  }\n}"
    },
    {
      "id": "dialogue-example-1",
      "page": "dialogue",
      "pageTitle": "Dialogue",
      "section": "Example: Custom Talk Option",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.option.ask_rumor\",\n  \"label\": \"Ask For A Rumor\",\n  \"request\": \"story\"\n}"
    },
    {
      "id": "dialogue-example-2",
      "page": "dialogue",
      "pageTitle": "Dialogue",
      "section": "Example: Custom Talk Option",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.line.rumor\",\n  \"request\": \"story\",\n  \"option\": \"my_pack.option.ask_rumor\",\n  \"text\": \"Roads carry stories faster than traders do.\",\n  \"weight\": 10\n}"
    },
    {
      "id": "dialogue-example-3",
      "page": "dialogue",
      "pageTitle": "Dialogue",
      "section": "Example: Shared Message Text",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.message.rain_warning\",\n  \"key\": \"my_pack.message.rain_warning\",\n  \"lines\": [\n    \"Rain makes bad roads worse.\",\n    \"Rain keeps the careful indoors.\"\n  ]\n}"
    },
    {
      "id": "dialogue-example-4",
      "page": "dialogue",
      "pageTitle": "Dialogue",
      "section": "Example: Shared Message Text",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.line.rain_warning\",\n  \"request\": \"question\",\n  \"text_key\": \"my_pack.message.rain_warning\"\n}"
    },
    {
      "id": "dialogue-example-5",
      "page": "dialogue",
      "pageTitle": "Dialogue",
      "section": "Example: Opening",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.opening.trusted_farmer\",\n  \"professions\": [\"minecraft:farmer\"],\n  \"reputation_levels\": [\"trusted\", \"respected\", \"revered\", \"royalty\"],\n  \"text\": \"Good to see you. The fields have been calmer lately.\"\n}"
    },
    {
      "id": "dialogue-example-6",
      "page": "dialogue",
      "pageTitle": "Dialogue",
      "section": "Example: Closing",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.closing.friendly\",\n  \"dispositions\": [\"friendly\", \"respectful\"],\n  \"text\": \"Travel safe.\"\n}"
    },
    {
      "id": "dialogue-example-7",
      "page": "dialogue",
      "pageTitle": "Dialogue",
      "section": "Example: Closing",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.opening.ominous_resident\",\n  \"requires_ominous_banner\": true,\n  \"village_allegiance\": \"known\",\n  \"reputation_levels\": [\"suspicious\", \"hostile\", \"despised\"],\n  \"text\": \"Do not carry that raider mark through my village.\"\n}"
    },
    {
      "id": "dialogue-example-8",
      "page": "dialogue",
      "pageTitle": "Dialogue",
      "section": "Example: Closing",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.opening.custom_uniform\",\n  \"player_item_tag\": \"my_pack:village_guard_uniforms\",\n  \"player_item_slots\": [\"armor\"],\n  \"text\": \"I recognize that {player_item}.\"\n}"
    },
    {
      "id": "dialogue-example-9",
      "page": "dialogue",
      "pageTitle": "Dialogue",
      "section": "Example: Pacify Line",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.pacify.neutral\",\n  \"professions\": [\"minecraft:toolsmith\"],\n  \"text\": \"Fine. Leave the payment and walk away slower next time.\"\n}"
    },
    {
      "id": "dialogue-example-10",
      "page": "dialogue",
      "pageTitle": "Dialogue",
      "section": "Example: Profession-Specific Line",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.line.map_talk\",\n  \"request\": \"question\",\n  \"text\": \"A good map is just a promise written carefully.\"\n}"
    },
    {
      "id": "dialogue-requests-example-1",
      "page": "dialogue-requests",
      "pageTitle": "Dialogue Requests",
      "section": "Basic Pattern",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.option.ask_weather\",\n  \"label\": \"Ask About Weather\",\n  \"request\": \"question\"\n}"
    },
    {
      "id": "dialogue-requests-example-2",
      "page": "dialogue-requests",
      "pageTitle": "Dialogue Requests",
      "section": "Basic Pattern",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.line.weather\",\n  \"request\": \"question\",\n  \"option\": \"my_pack.option.ask_weather\",\n  \"text\": \"Clear skies never last as long as confident people think.\"\n}"
    },
    {
      "id": "dialogue-requests-example-3",
      "page": "dialogue-requests",
      "pageTitle": "Dialogue Requests",
      "section": "Example: Report-Style Request",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.line.map_report\",\n  \"request\": \"map_report\",\n  \"text\": \"Good. A map earns its ink when someone returns from the place it promised.\"\n}"
    },
    {
      "id": "dialogue-requests-example-4",
      "page": "dialogue-requests",
      "pageTitle": "Dialogue Requests",
      "section": "Example: `share_story`",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.line.haunted_keep\",\n  \"request\": \"share_story\",\n  \"option\": \"adult_share_story\",\n  \"story_structure\": \"examplemod:haunted_keep\",\n  \"text\": \"{target_article}. If you found it, leave before dark.\"\n}"
    },
    {
      "id": "dialogue-requests-example-5",
      "page": "dialogue-requests",
      "pageTitle": "Dialogue Requests",
      "section": "Example: Social Request",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.line.apology\",\n  \"request\": \"apology\",\n  \"player_event_tags\": [\"player_attacked_villager\"],\n  \"text\": \"If you mean that apology, start by not making me need another one.\"\n}"
    },
    {
      "id": "dialogue-trees-example-1",
      "page": "dialogue-trees",
      "pageTitle": "Dialogue Trees",
      "section": "Minimal Tree",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack:road_ledger\",\n  \"display\": {\n    \"title\": \"Road Ledger\",\n    \"description\": \"A small branching request scene.\"\n  },\n  \"entries\": [\n    {\n      \"id\": \"offer\",\n      \"label\": \"Road Ledger\",\n      \"request\": \"question\",\n      \"conditions\": [\n        { \"type\": \"quest\", \"state\": \"available\" }\n      ],\n      \"start\": \"offer\"\n    }\n  ],\n  \"nodes\": {\n    \"offer\": {\n      \"lines\": [\n        \"I lost a ledger on the old road. If you find it, bring it back.\"\n      ],\n      \"responses\": [\n        { \"id\": \"accept\", \"label\": \"I can look for it.\", \"next\": \"start_quest\" },\n        { \"id\": \"decline\", \"label\": \"Another time.\", \"next\": \"decline\" }\n      ]\n    },\n    \"start_quest\": {\n      \"actions\": [\n        {\n          \"type\": \"quest\",\n          \"action\": \"start\",\n          \"lines\": {\n            \"started\": [\n              \"Good. Search the road and return the ledger if you find it.\"\n            ]\n          }\n        }\n      ],\n      \"end\": true\n    },\n    \"decline\": {\n      \"text\": \"Then the road keeps its paper a little longer.\",\n      \"end\": true\n    }\n  }\n}"
    },
    {
      "id": "dialogue-trees-example-2",
      "page": "dialogue-trees",
      "pageTitle": "Dialogue Trees",
      "section": "Referencing A Tree From Quest Module V2",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"external_scenes\": [\"my_pack:quests/old_roads/road_ledger\"],\n  \"stages\": [\n    {\n      \"id\": \"start\",\n      \"objectives\": [],\n      \"dialogue\": {\n        \"offer\": {\n          \"label\": \"Road Ledger\",\n          \"request\": \"question\",\n          \"external_scene\": {\n            \"tree\": \"my_pack:quests/old_roads/road_ledger\",\n            \"entry\": \"offer\"\n          }\n        }\n      }\n    }\n  ]\n}"
    },
    {
      "id": "dialogue-trees-example-3",
      "page": "dialogue-trees",
      "pageTitle": "Dialogue Trees",
      "section": "Replacing Or Removing Built-Ins",
      "level": "Advanced",
      "language": "json",
      "code": "{ \"replace\": true }"
    },
    {
      "id": "dialogue-trees-example-4",
      "page": "dialogue-trees",
      "pageTitle": "Dialogue Trees",
      "section": "Replacing Or Removing Built-Ins",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"villagerretaliation:bread_delivery\",\n  \"remove\": true\n}"
    },
    {
      "id": "dialogue-trees-example-5",
      "page": "dialogue-trees",
      "pageTitle": "Dialogue Trees",
      "section": "Example: Non-Quest Branch",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack:village_history\",\n  \"entries\": [\n    {\n      \"id\": \"history\",\n      \"label\": \"Ask About The Village\",\n      \"request\": \"story\",\n      \"start\": \"history\"\n    }\n  ],\n  \"nodes\": {\n    \"history\": {\n      \"lines\": [\n        \"This place was smaller once. Safer too, depending on who you ask.\"\n      ],\n      \"responses\": [\n        { \"id\": \"leave\", \"label\": \"Thanks.\", \"end\": true }\n      ]\n    }\n  }\n}"
    },
    {
      "id": "forced-dialogue-example-1",
      "page": "forced-dialogue",
      "pageTitle": "Forced Dialogue",
      "section": "Example: Locked Theft Scene",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"entries\": [\n    {\n      \"id\": \"my_pack.container_theft.warning\",\n      \"trigger\": \"container_theft\",\n      \"output\": {\n        \"mode\": \"forced_dialogue\"\n      },\n      \"line\": \"Hands off that {container}. I saw what you took.\",\n      \"witness_radius\": 10,\n      \"requires_line_of_sight\": true,\n      \"initiate_dialogue\": true,\n      \"options\": [\n        {\n          \"id\": \"apologize\",\n          \"label\": \"Apologize\",\n          \"response\": \"Then prove it next time before the village has to ask.\",\n          \"reputation\": 2,\n          \"end_conversation\": true\n        },\n        {\n          \"id\": \"talk_back\",\n          \"label\": \"Talk back\",\n          \"response\": \"Wrong answer.\",\n          \"reputation\": -6,\n          \"aggro\": true,\n          \"end_conversation\": true\n        }\n      ]\n    }\n  ]\n}"
    },
    {
      "id": "forced-dialogue-example-2",
      "page": "forced-dialogue",
      "pageTitle": "Forced Dialogue",
      "section": "Example: Payment Option",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"offer_payment\",\n  \"label\": \"Offer payment\",\n  \"response\": \"Payment does not make it yours, but it can make things right.\",\n  \"take_items\": {\n    \"items\": [\"minecraft:emerald\"],\n    \"count\": 8,\n    \"destination\": \"villager_inventory\",\n    \"failure_response\": \"Do not offer emeralds you do not have.\"\n  },\n  \"end_conversation\": true\n}"
    },
    {
      "id": "forced-dialogue-example-3",
      "page": "forced-dialogue",
      "pageTitle": "Forced Dialogue",
      "section": "Locale-Friendly Text",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.container_theft.warning\",\n  \"message_prefix\": \"forced.my_pack.container_theft.warning\",\n  \"trigger\": \"container_theft\",\n  \"line\": \"Hands off that {container}. I saw what you took.\",\n  \"options\": [\n    {\n      \"id\": \"apologize\",\n      \"label\": \"Apologize\",\n      \"response\": \"Then prove it next time before the village has to ask.\"\n    }\n  ]\n}"
    },
    {
      "id": "forced-dialogue-example-4",
      "page": "forced-dialogue",
      "pageTitle": "Forced Dialogue",
      "section": "Example: Chat Bark",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"entries\": [\n    {\n      \"id\": \"my_pack.retaliation.chat\",\n      \"trigger\": \"retaliation_started\",\n      \"output\": {\n        \"mode\": \"chat\",\n        \"radius\": 18,\n        \"look_at_player\": true\n      },\n      \"lines\": [\n        \"You picked the wrong village.\",\n        \"Run while you still remember how.\"\n      ],\n      \"chance\": 0.5\n    }\n  ]\n}"
    },
    {
      "id": "forced-dialogue-example-5",
      "page": "forced-dialogue",
      "pageTitle": "Forced Dialogue",
      "section": "Held Item Proximity",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.aimed_sword_warning\",\n  \"trigger\": \"player_item_proximity\",\n  \"output\": {\n    \"mode\": \"chat\",\n    \"look_at_player\": true\n  },\n  \"line\": \"Lower {held_item}, {player}.\",\n  \"witness_radius\": 8,\n  \"chance\": 1.0,\n  \"requires_player_aiming_at_witness\": true,\n  \"player_items\": [\"minecraft:diamond_sword\"],\n  \"player_item_slots\": [\"hands\"],\n  \"draw_weapon\": true,\n  \"draw_weapon_duration_seconds\": 10\n}"
    },
    {
      "id": "forced-dialogue-example-6",
      "page": "forced-dialogue",
      "pageTitle": "Forced Dialogue",
      "section": "Replacing Or Removing Built-Ins",
      "level": "Advanced",
      "language": "json",
      "code": "{ \"replace\": true }"
    },
    {
      "id": "forced-dialogue-example-7",
      "page": "forced-dialogue",
      "pageTitle": "Forced Dialogue",
      "section": "Replacing Or Removing Built-Ins",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"player_aiming_sword_neutral\",\n  \"remove\": true\n}"
    },
    {
      "id": "notifications-example-1",
      "page": "notifications",
      "pageTitle": "Notifications",
      "section": "Minimal File",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"notifications\": [\n    {\n      \"id\": \"my_pack.quest.started\",\n      \"trigger\": \"quest.started\",\n      \"text\": \"Quest started: {quest}\",\n      \"kind\": \"quest\",\n      \"color\": \"#FFD166\"\n    }\n  ]\n}"
    },
    {
      "id": "notifications-example-2",
      "page": "notifications",
      "pageTitle": "Notifications",
      "section": "Ambient World Text",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.ambient.trusted_farmer\",\n  \"trigger\": \"ambient.murmur\",\n  \"text\": \"Good harvest follows good neighbors\",\n  \"world_text_kind\": \"murmur\",\n  \"professions\": [\"minecraft:farmer\"],\n  \"reputation_levels\": [\"trusted\", \"respected\", \"revered\", \"royalty\"]\n}"
    },
    {
      "id": "notifications-example-3",
      "page": "notifications",
      "pageTitle": "Notifications",
      "section": "Quest HUD Notice",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.quest.completed\",\n  \"trigger\": \"quest.completed\",\n  \"text\": \"{quest} complete.\",\n  \"kind\": \"quest\",\n  \"color\": \"#FFE29A\"\n}"
    },
    {
      "id": "notifications-example-4",
      "page": "notifications",
      "pageTitle": "Notifications",
      "section": "Trade Refusal Flavor",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.trade.refused.hostile\",\n  \"trigger\": \"trade.refused\",\n  \"text\": \"Not today.\",\n  \"world_text_kind\": \"negative\",\n  \"reputation_levels\": [\"hostile\", \"despised\", \"feared\"]\n}"
    },
    {
      "id": "notification-triggers-example-1",
      "page": "notification-triggers",
      "pageTitle": "Notification Triggers",
      "section": "Example: Quest Trigger",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.quest.started\",\n  \"trigger\": \"quest.started\",\n  \"text\": \"Quest started: {quest}\",\n  \"kind\": \"quest\"\n}"
    },
    {
      "id": "notification-triggers-example-2",
      "page": "notification-triggers",
      "pageTitle": "Notification Triggers",
      "section": "Example: Discovery Trigger",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.rumor.found\",\n  \"trigger\": \"dialogue.rumor.found\",\n  \"text\": \"Found rumored place: {target}\",\n  \"kind\": \"map_discovery\",\n  \"color\": \"#55AAFF\"\n}"
    },
    {
      "id": "notification-triggers-example-3",
      "page": "notification-triggers",
      "pageTitle": "Notification Triggers",
      "section": "Example: Ambient Trigger",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.ambient.revered\",\n  \"trigger\": \"ambient.murmur\",\n  \"lines\": [\n    \"There they are\",\n    \"Good omen\"\n  ],\n  \"world_text_kind\": \"murmur\",\n  \"reputation_levels\": [\"revered\"]\n}"
    },
    {
      "id": "notification-triggers-example-4",
      "page": "notification-triggers",
      "pageTitle": "Notification Triggers",
      "section": "Example: Reputation Trigger",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.rep.trusted.improved\",\n  \"trigger\": \"reputation.tier.trusted.improved\",\n  \"text\": \"You feel yourself gaining {villager_possessive} trust.\",\n  \"color\": \"green\"\n}"
    },
    {
      "id": "event-tags-example-1",
      "page": "event-tags",
      "pageTitle": "Event Tags",
      "section": "Which Field To Use",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.line.raid_thanks\",\n  \"request\": \"village_defense_report\",\n  \"event_tags\": [\"raid\"],\n  \"player_event_tags\": [\"player_defended_raid\"],\n  \"text\": \"You stood with us when the banners came over the hill.\"\n}"
    },
    {
      "id": "event-tags-example-2",
      "page": "event-tags",
      "pageTitle": "Event Tags",
      "section": "Example: Family Life",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.line.new_baby\",\n  \"request\": \"question\",\n  \"event_tags\": [\"baby_born\"],\n  \"text\": \"There is a new little voice in the village today.\"\n}"
    },
    {
      "id": "event-tags-example-3",
      "page": "event-tags",
      "pageTitle": "Event Tags",
      "section": "Example: Crime Memory",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.line.theft_memory\",\n  \"request\": \"apology\",\n  \"player_event_tags\": [\"player_container_theft\"],\n  \"text\": \"Village stores are not souvenirs.\"\n}"
    },
    {
      "id": "event-tags-example-4",
      "page": "event-tags",
      "pageTitle": "Event Tags",
      "section": "Example: Defense Memory",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.line.raid_defense\",\n  \"request\": \"village_defense_report\",\n  \"player_event_tags\": [\"player_defended_raid\"],\n  \"text\": \"The village still talks about the way you fought that raid.\"\n}"
    },
    {
      "id": "villager-event-triggers-example-1",
      "page": "villager-event-triggers",
      "pageTitle": "Villager Event Triggers",
      "section": "Minimal Example",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"memory\": \"villagerretaliation:player_defended_raid\",\n  \"scope\": \"player\",\n  \"cooldown\": 24000,\n  \"actions\": [\n    {\n      \"type\": \"notification\",\n      \"trigger\": \"quest.updated\",\n      \"text\": \"The village remembers what {player} did during the raid.\"\n    }\n  ]\n}"
    },
    {
      "id": "villager-event-triggers-example-2",
      "page": "villager-event-triggers",
      "pageTitle": "Villager Event Triggers",
      "section": "Quest Fact Example",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack:remember_first_defense\",\n  \"tags\": [\"villagerretaliation:player_defended_village\"],\n  \"scope\": \"player\",\n  \"repeatable\": false,\n  \"actions\": [\n    {\n      \"type\": \"set_tag\",\n      \"scope\": \"player\",\n      \"tag\": \"my_pack:first_village_defense\"\n    },\n    {\n      \"type\": \"notification\",\n      \"trigger\": \"quest.updated\",\n      \"text\": \"A village now knows you as a defender.\"\n    }\n  ]\n}"
    },
    {
      "id": "localization-example-1",
      "page": "localization",
      "pageTitle": "Localization",
      "section": "1. Datapack Locale Files",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.message.weather\",\n  \"key\": \"my_pack.message.weather\",\n  \"text\": \"Rain keeps the fields honest.\"\n}"
    },
    {
      "id": "localization-example-2",
      "page": "localization",
      "pageTitle": "Localization",
      "section": "1. Datapack Locale Files",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.message.weather\",\n  \"key\": \"my_pack.message.weather\",\n  \"text\": \"La pluie garde les champs honnetes.\"\n}"
    },
    {
      "id": "localization-example-3",
      "page": "localization",
      "pageTitle": "Localization",
      "section": "Item Counts In Dialogue",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"forms\": [\n    { \"id\": \"one\", \"count_pattern\": \"1\", \"format\": \"{item}\" },\n    { \"id\": \"few\", \"count_pattern\": \"(,:2|3|4)\", \"format\": \"{count} {item}\" },\n    { \"id\": \"other\", \"format\": \"{count} {item}\" }\n  ],\n  \"currency\": {\n    \"one\": \"emerald\",\n    \"few\": \"emeralds\",\n    \"other\": \"emeralds\"\n  },\n  \"items\": {\n    \"minecraft:bread\": {\n      \"one\": \"bread\",\n      \"few\": \"loaves of bread\",\n      \"other\": \"loaves of bread\"\n    }\n  },\n  \"rules\": [\n    {\n      \"forms\": [\"few\", \"other\"],\n      \"pattern\": \"(,i)(.*[^aeiou])y$\",\n      \"replacement\": \"$1ies\"\n    }\n  ]\n}"
    },
    {
      "id": "localization-example-4",
      "page": "localization",
      "pageTitle": "Localization",
      "section": "2. Resource-Pack Language Files",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"villagerretaliation.gui.root.talk\": \"Parler\",\n  \"villagerretaliation.gui.root.trade\": \"Commercer\",\n  \"villagerretaliation.reputation.value_format\": \"Reputation : %s\"\n}"
    },
    {
      "id": "localization-example-5",
      "page": "localization",
      "pageTitle": "Localization",
      "section": "When To Use `text_key`",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.line.weather_rain\",\n  \"request\": \"question\",\n  \"text_key\": \"my_pack.message.weather\"\n}"
    },
    {
      "id": "localization-example-6",
      "page": "localization",
      "pageTitle": "Localization",
      "section": "Quests",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"display\": {\n    \"title\": \"Bread Delivery\",\n    \"title_key\": \"quest.village_supply.bread_delivery.title\"\n  },\n  \"dialogue\": {\n    \"start\": [\"Bring me 16 bread.\"],\n    \"start_key\": \"quest.village_supply.bread_delivery.dialogue.start\"\n  }\n}"
    },
    {
      "id": "localization-example-7",
      "page": "localization",
      "pageTitle": "Localization",
      "section": "Forced Dialogue",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"message_prefix\": \"forced.my_pack.theft.warning\",\n  \"line\": \"Hands off that {container}.\",\n  \"options\": [\n    {\n      \"label\": \"Apologize\",\n      \"response\": \"Then prove it.\"\n    }\n  ]\n}"
    },
    {
      "id": "localization-example-8",
      "page": "localization",
      "pageTitle": "Localization",
      "section": "Profession Names",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"entity.minecraft.villager.farmer\": \"Farmer\"\n}"
    },
    {
      "id": "localization-example-9",
      "page": "localization",
      "pageTitle": "Localization",
      "section": "Profession Names",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"entity.minecraft.villager.my_mod.crystal_smith\": \"Crystal Smith\"\n}"
    },
    {
      "id": "quests-example-1",
      "page": "quests",
      "pageTitle": "Quests",
      "section": "One-File Quest",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"schema\": \"villagerretaliation:quest/v2\",\n  \"id\": \"my_pack:bread_delivery\",\n  \"metadata\": {\n    \"title\": \"Bread Delivery\",\n    \"description\": \"Bring 16 bread to the village stores.\",\n    \"questline\": \"village_supply\",\n    \"tags\": [\"group.village_supply\"]\n  },\n  \"provider\": {\n    \"type\": \"villagerretaliation:villager\",\n    \"filters\": {\n      \"professions\": [\"minecraft:farmer\"],\n      \"min_villager_level\": \"novice\"\n    }\n  },\n  \"availability\": {\n    \"repeatable\": true,\n    \"completion_cooldown_days\": 1,\n    \"locked_to_villager\": true,\n    \"cross_villager_compatible\": false,\n    \"abandonment\": \"allow_repickup\",\n    \"consume_on_completion\": true\n  },\n  \"entry_stage\": \"gather\",\n  \"stages\": [\n    {\n      \"id\": \"gather\",\n      \"objectives\": [\n        {\n          \"id\": \"bring_bread\",\n          \"type\": \"item_check\",\n          \"item\": \"minecraft:bread\",\n          \"count\": 16,\n          \"tracker\": {\n            \"text\": \"Bring 16 bread back to the quest giver.\",\n            \"complete_text\": \"The bread is packed and ready.\",\n            \"show_progress\": true,\n            \"progress\": 0.75\n          }\n        }\n      ],\n      \"dialogue\": {\n        \"offer\": {\n          \"label\": \"Bread Delivery\",\n          \"request\": \"question\",\n          \"order\": -20,\n          \"show_for_babies\": false,\n          \"lines\": [\n            \"The bins are low. Sixteen bread would quiet a lot of worried stomachs.\"\n          ],\n          \"responses\": [\n            {\n              \"id\": \"accept\",\n              \"label\": \"I can help stock the larder.\",\n              \"scene\": \"start_quest\"\n            },\n            {\n              \"id\": \"decline\",\n              \"label\": \"Another time.\",\n              \"scene\": \"decline\"\n            }\n          ]\n        },\n        \"reminder\": {\n          \"label\": \"About Bread Delivery\",\n          \"request\": \"question\",\n          \"order\": -20,\n          \"show_for_babies\": false,\n          \"lines\": [\n            \"Bread Delivery is still open. The tracker has the count.\"\n          ],\n          \"responses\": [\n            {\n              \"id\": \"leave\",\n              \"label\": \"I'll keep looking.\",\n              \"scene\": \"end\"\n            }\n          ]\n        },\n        \"turn_in\": {\n          \"label\": \"About Bread Delivery\",\n          \"request\": \"question\",\n          \"order\": -20,\n          \"show_for_babies\": false,\n          \"lines\": [\n            \"If that pack smells like fresh bread, you may have saved me an argument.\"\n          ],\n          \"responses\": [\n            {\n              \"id\": \"complete\",\n              \"label\": \"Show what I brought.\",\n              \"scene\": \"complete_quest\"\n            },\n            {\n              \"id\": \"leave\",\n              \"label\": \"Not yet.\",\n              \"scene\": \"end\"\n            }\n          ]\n        }\n      },\n      \"scenes\": [\n        {\n          \"id\": \"start_quest\",\n          \"actions\": [\n            {\n              \"type\": \"quest\",\n              \"action\": \"start\",\n              \"lines\": {\n                \"started\": [\n                  \"Good. Bring the bread back when the count is ready.\"\n                ],\n                \"unavailable\": [\n                  \"The larder is not asking you for bread right now.\"\n                ]\n              }\n            }\n          ]\n        },\n        {\n          \"id\": \"complete_quest\",\n          \"actions\": [\n            {\n              \"type\": \"quest\",\n              \"action\": \"turn_in\",\n              \"lines\": {\n                \"completed\": [\n                  \"Good. A full shelf makes brave talk sound less hollow.\"\n                ],\n                \"missing_objectives\": [\n                  \"Bread Delivery is still short. The tracker has the exact count.\"\n                ],\n                \"unavailable\": [\n                  \"This bread delivery is not ready to close yet.\"\n                ]\n              }\n            }\n          ]\n        },\n        {\n          \"id\": \"decline\",\n          \"text\": \"Then I will keep counting crumbs and pretending it is planning.\"\n        },\n        {\n          \"id\": \"end\",\n          \"text\": \"Keep the bread close until you are ready.\"\n        }\n      ]\n    }\n  ],\n  \"rewards\": {\n    \"experience\": 60,\n    \"reputation\": 5,\n    \"gossip_reputation\": 2\n  },\n  \"ui\": {\n    \"tracker_text\": \"Bring 16 bread.\",\n    \"icon\": \"minecraft:bread\",\n    \"color\": \"#DCEBA6\"\n  }\n}"
    },
    {
      "id": "quests-example-2",
      "page": "quests",
      "pageTitle": "Quests",
      "section": "Journal Presentation",
      "level": "Advanced",
      "language": "json",
      "code": "\"ui\": {\n  \"icon\": \"minecraft:filled_map\",\n  \"color\": \"#d4a35a\",\n  \"outline_color\": \"#201408\",\n  \"priority\": 25,\n  \"hidden\": false,\n  \"tracker_text\": \"Survey the old road.\"\n}"
    },
    {
      "id": "quests-example-3",
      "page": "quests",
      "pageTitle": "Quests",
      "section": "Quest Pools",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"schema\": \"villagerretaliation:quest_pool/v1\",\n  \"id\": \"my_pack:daily_commissions\",\n  \"scope\": \"village\",\n  \"refresh_days\": 1,\n  \"max_offers\": 3,\n  \"anti_repeat_rotations\": 2,\n  \"any_tags\": [\"pool.daily\", \"pool.commission\"],\n  \"exclude_tags\": [\"difficulty.extreme\"],\n  \"weights\": {\n    \"my_pack:urgent_repairs\": 4\n  }\n}"
    },
    {
      "id": "quests-example-4",
      "page": "quests",
      "pageTitle": "Quests",
      "section": "Generic Criterion Objectives",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"forge_blades\",\n  \"type\": \"criterion\",\n  \"criterion\": \"villagerretaliation:crafted\",\n  \"item\": \"minecraft:iron_sword\",\n  \"match\": {\n    \"item\": \"minecraft:iron_sword\"\n  },\n  \"count\": 3\n}"
    },
    {
      "id": "quests-example-5",
      "page": "quests",
      "pageTitle": "Quests",
      "section": "Generic Criterion Objectives",
      "level": "Advanced",
      "language": "json",
      "code": "\"tags\": [\n  \"group.village_supply\",\n  \"role.request\",\n  \"activity.deliver\",\n  \"activity.gather\",\n  \"destination.village\",\n  \"tier.early\",\n  \"difficulty.easy\",\n  \"commitment.quick\",\n  \"theme.community\",\n  \"pool.daily\",\n  \"pool.quest_board\"\n]"
    },
    {
      "id": "quests-example-6",
      "page": "quests",
      "pageTitle": "Quests",
      "section": "Objective Composition And Bonuses",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"complete_when\": [\"secure_gate\", \"rescue_smith\", \"recover_ledger\"],\n  \"completion\": { \"mode\": \"at_least\", \"count\": 2 },\n  \"bonuses\": [\n    {\n      \"id\": \"save_everyone\",\n      \"when\": [\"secure_gate\", \"rescue_smith\", \"recover_ledger\"],\n      \"mode\": \"all\",\n      \"actions\": [\n        { \"type\": \"experience\", \"amount\": 100 },\n        { \"type\": \"reputation\", \"amount\": 5 }\n      ]\n    }\n  ]\n}"
    },
    {
      "id": "quests-example-7",
      "page": "quests",
      "pageTitle": "Quests",
      "section": "Prerequisites And Restart Rules",
      "level": "Advanced",
      "language": "json",
      "code": "\"availability\": {\n  \"active\": {\n    \"conditions\": [{ \"type\": \"quest_fact\", \"tag\": \"my_pack:road_open\" }],\n    \"hide_when_unmet\": false,\n    \"pause_progress_when_unmet\": true\n  },\n  \"expiration\": {\n    \"after_days\": 3,\n    \"consume\": false,\n    \"allow_repickup\": true,\n    \"notify\": true\n  },\n  \"branch\": {\n    \"exclusive_group\": \"my_pack:route_choice\",\n    \"exclusive_on\": \"completed\",\n    \"blocks_on_completion\": [\"my_pack:other_route\"]\n  }\n}"
    },
    {
      "id": "quests-example-8",
      "page": "quests",
      "pageTitle": "Quests",
      "section": "Definition Revisions And Live-Save Migration",
      "level": "Advanced",
      "language": "json",
      "code": "\"metadata\": {\n  \"revision\": 3,\n  \"migration\": {\n    \"active_policy\": \"keep\",\n    \"stage_aliases\": {\n      \"find_ruins\": \"survey_ruins\"\n    },\n    \"objective_aliases\": {\n      \"find_ruins.old_map\": \"survey_ruins.map_fragment\"\n    }\n  }\n}"
    },
    {
      "id": "quests-example-9",
      "page": "quests",
      "pageTitle": "Quests",
      "section": "Definition Revisions And Live-Save Migration",
      "level": "Advanced",
      "language": "json",
      "code": "{\n\"availability\": {\n  \"prerequisites\": [\n    \"my_pack:first_steps\",\n    \"my_pack:earn_their_trust\",\n    \"my_pack:find_the_map\"\n  ]\n}\n}"
    },
    {
      "id": "quests-example-10",
      "page": "quests",
      "pageTitle": "Quests",
      "section": "Branch Example",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"schema\": \"villagerretaliation:quest/v2\",\n  \"id\": \"my_pack:choose_supply_route\",\n  \"metadata\": {\n    \"title\": \"Choose Supply Route\",\n    \"description\": \"Choose how the village will move supplies.\",\n    \"questline\": \"village_supply\",\n    \"tags\": [\"group.village_supply\"]\n  },\n  \"provider\": {\n    \"type\": \"villagerretaliation:villager\",\n    \"filters\": {\n      \"professions\": [\"minecraft:cartographer\"]\n    }\n  },\n  \"availability\": {\n    \"repeatable\": false,\n    \"max_completions\": 1,\n    \"locked_to_villager\": true\n  },\n  \"entry_stage\": \"choose_route\",\n  \"stages\": [\n    {\n      \"id\": \"choose_route\",\n      \"objectives\": [\n        {\n          \"id\": \"choose_route\",\n          \"type\": \"choice\",\n          \"choices\": [\"river\", \"ridge\"],\n          \"tracker\": {\n            \"text\": \"Choose a supply route.\",\n            \"complete_text\": \"Route chosen: {objective_choice_value}.\"\n          }\n        }\n      ],\n      \"dialogue\": {\n        \"offer\": {\n          \"label\": \"Choose Supply Route\",\n          \"request\": \"question\",\n          \"lines\": [\n            \"The village needs a safer supply route. River or ridge,\"\n          ],\n          \"responses\": [\n            {\n              \"id\": \"river\",\n              \"label\": \"Use the river.\",\n              \"actions\": [\n                {\n                  \"type\": \"set_variable\",\n                  \"scope\": \"quest\",\n                  \"key\": \"choice\",\n                  \"value\": \"river\"\n                }\n              ],\n              \"transition\": {\n                \"stage\": \"river_route\"\n              }\n            },\n            {\n              \"id\": \"ridge\",\n              \"label\": \"Use the ridge.\",\n              \"actions\": [\n                {\n                  \"type\": \"set_variable\",\n                  \"scope\": \"quest\",\n                  \"key\": \"choice\",\n                  \"value\": \"ridge\"\n                }\n              ],\n              \"transition\": {\n                \"stage\": \"ridge_route\"\n              }\n            }\n          ]\n        }\n      }\n    },\n    {\n      \"id\": \"river_route\",\n      \"objectives\": [],\n      \"dialogue\": {\n        \"turn_in\": {\n          \"label\": \"River Route\",\n          \"request\": \"question\",\n          \"lines\": [\"The river road will move quietly.\"],\n          \"responses\": [\n            {\n              \"id\": \"complete\",\n              \"label\": \"Mark the river route.\",\n              \"complete\": true\n            }\n          ]\n        }\n      }\n    },\n    {\n      \"id\": \"ridge_route\",\n      \"objectives\": [],\n      \"dialogue\": {\n        \"turn_in\": {\n          \"label\": \"Ridge Route\",\n          \"request\": \"question\",\n          \"lines\": [\"The ridge road will keep watch over the valley.\"],\n          \"responses\": [\n            {\n              \"id\": \"complete\",\n              \"label\": \"Mark the ridge route.\",\n              \"complete\": true\n            }\n          ]\n        }\n      }\n    }\n  ],\n  \"ui\": {\n    \"tracker_text\": \"Choose a route.\",\n    \"icon\": \"minecraft:map\"\n  }\n}"
    },
    {
      "id": "quests-example-11",
      "page": "quests",
      "pageTitle": "Quests",
      "section": "Structure Target Example",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"schema\": \"villagerretaliation:quest/v2\",\n  \"id\": \"my_pack:trail_marker\",\n  \"metadata\": {\n    \"title\": \"Trail Marker\",\n    \"description\": \"Find nearby Trail Ruins and return with a brush.\",\n    \"tags\": [\"group.old_roads\"]\n  },\n  \"provider\": {\n    \"type\": \"villagerretaliation:villager\",\n    \"filters\": {\n      \"professions\": [\"minecraft:cartographer\", \"minecraft:mason\"]\n    }\n  },\n  \"availability\": {\n    \"repeatable\": false,\n    \"max_completions\": 1,\n    \"locked_to_villager\": true\n  },\n  \"target\": {\n    \"structure\": \"minecraft:trail_ruins\",\n    \"dimension\": \"minecraft:overworld\",\n    \"search_radius\": 192,\n    \"discovery_radius\": 96,\n    \"proof_item\": \"minecraft:brush\"\n  },\n  \"entry_stage\": \"survey\",\n  \"stages\": [\n    {\n      \"id\": \"survey\",\n      \"objectives\": [\n        {\n          \"id\": \"visit_ruins\",\n          \"type\": \"structure_visit\",\n          \"structure\": \"minecraft:trail_ruins\",\n          \"tracker\": {\n            \"text\": \"Find the Trail Ruins near {target_x}, {target_z}.\",\n            \"complete_text\": \"You found the old road.\"\n          }\n        },\n        {\n          \"id\": \"bring_brush\",\n          \"type\": \"item_check\",\n          \"item\": \"minecraft:brush\",\n          \"count\": 1,\n          \"tracker\": {\n            \"text\": \"Bring a brush back from the ruins.\",\n            \"complete_text\": \"The brush is ready.\"\n          }\n        }\n      ],\n      \"complete_when\": [\"visit_ruins\", \"bring_brush\"],\n      \"dialogue\": {\n        \"offer\": {\n          \"label\": \"Trail Marker\",\n          \"request\": \"question\",\n          \"lines\": [\"The old road left a mark under the dust.\"],\n          \"responses\": [\n            {\n              \"id\": \"accept\",\n              \"label\": \"Mark the ruins.\",\n              \"scene\": \"start_quest\"\n            }\n          ]\n        },\n        \"turn_in\": {\n          \"label\": \"Trail Marker\",\n          \"request\": \"question\",\n          \"lines\": [\"You found the mark and brought a brush.\"],\n          \"responses\": [\n            {\n              \"id\": \"complete\",\n              \"label\": \"Hand over the notes.\",\n              \"complete\": true\n            }\n          ]\n        }\n      },\n      \"scenes\": [\n        {\n          \"id\": \"start_quest\",\n          \"actions\": [\n            {\n              \"type\": \"quest\",\n              \"action\": \"start\",\n              \"lines\": {\n                \"started\": [\"The ruins should be near {target_x}, {target_z}.\"],\n                \"locate_failed\": [\"The old road is hiding from the map today.\"]\n              }\n            }\n          ]\n        }\n      ]\n    }\n  ],\n  \"rewards\": {\n    \"experience\": 80,\n    \"reputation\": 6\n  },\n  \"ui\": {\n    \"tracker_text\": \"Find the Trail Ruins.\",\n    \"icon\": \"minecraft:brush\"\n  }\n}"
    },
    {
      "id": "quests-example-12",
      "page": "quests",
      "pageTitle": "Quests",
      "section": "Forced Or External Scene Example",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"schema\": \"villagerretaliation:quest/v2\",\n  \"id\": \"my_pack:storm_warning\",\n  \"metadata\": {\n    \"title\": \"Storm Warning\",\n    \"description\": \"Ask a cleric about a storm omen.\",\n    \"questline\": \"lost_civilization\",\n    \"tags\": [\"group.lost_civilization\"]\n  },\n  \"provider\": {\n    \"type\": \"villagerretaliation:villager\",\n    \"filters\": {\n      \"professions\": [\"minecraft:cleric\"]\n    }\n  },\n  \"availability\": {\n    \"repeatable\": false,\n    \"max_completions\": 1,\n    \"locked_to_villager\": true\n  },\n  \"external_scenes\": [\"my_pack:quests/storm_warning\"],\n  \"entry_stage\": \"ask\",\n  \"stages\": [\n    {\n      \"id\": \"ask\",\n      \"objectives\": [\n        {\n          \"id\": \"hear_warning\",\n          \"type\": \"choice\",\n          \"choices\": [\"heard\"],\n          \"tracker\": {\n            \"text\": \"Hear the storm warning.\",\n            \"complete_text\": \"The warning is clear.\"\n          }\n        }\n      ],\n      \"dialogue\": {\n        \"offer\": {\n          \"label\": \"Storm Warning\",\n          \"request\": \"question\",\n          \"external_scene\": {\n            \"tree\": \"my_pack:quests/storm_warning\",\n            \"entry\": \"offer\"\n          }\n        },\n        \"turn_in\": {\n          \"label\": \"Storm Warning\",\n          \"request\": \"question\",\n          \"lines\": [\"The storm warning is clear now.\"],\n          \"responses\": [\n            {\n              \"id\": \"complete\",\n              \"label\": \"I understand the omen.\",\n              \"complete\": true\n            }\n          ]\n        }\n      }\n    }\n  ],\n  \"events\": [\n    {\n      \"id\": \"storm_reminder\",\n      \"event\": \"near_provider\",\n      \"radius\": 10,\n      \"cooldown_seconds\": 120,\n      \"conditions\": [\n        { \"type\": \"weather\", \"state\": \"thunder\" }\n      ],\n      \"actions\": [\n        {\n          \"type\": \"forced_dialogue\",\n          \"forced_dialogue\": \"my_pack.quest.storm_warning.reminder\"\n        }\n      ]\n    }\n  ],\n  \"ui\": {\n    \"tracker_text\": \"Hear the storm warning.\",\n    \"icon\": \"minecraft:lightning_rod\"\n  }\n}"
    },
    {
      "id": "quests-example-13",
      "page": "quests",
      "pageTitle": "Quests",
      "section": "Forced Or External Scene Example",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack:quests/storm_warning\",\n  \"metadata\": {\n    \"quest\": \"my_pack:storm_warning\",\n    \"questline\": \"lost_civilization\"\n  },\n  \"entries\": [\n    {\n      \"id\": \"offer\",\n      \"label\": \"Storm Warning\",\n      \"request\": \"question\",\n      \"start\": \"offer\"\n    }\n  ],\n  \"nodes\": {\n    \"offer\": {\n      \"lines\": [\n        \"Thunder is not the omen. The silence after it is.\"\n      ],\n      \"responses\": [\n        {\n          \"id\": \"heard\",\n          \"label\": \"I will listen for it.\",\n          \"actions\": [\n            {\n              \"type\": \"set_variable\",\n              \"scope\": \"quest\",\n              \"key\": \"choice\",\n              \"value\": \"heard\"\n            }\n          ],\n          \"end\": true\n        }\n      ]\n    }\n  }\n}"
    },
    {
      "id": "quests-example-14",
      "page": "quests",
      "pageTitle": "Quests",
      "section": "Forced Or External Scene Example",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"metadata\": {\n    \"quest\": \"my_pack:storm_warning\",\n    \"questline\": \"lost_civilization\"\n  },\n  \"entries\": [\n    {\n      \"id\": \"my_pack.quest.storm_warning.reminder\",\n      \"trigger\": \"quest\",\n      \"output\": {\n        \"mode\": \"forced_dialogue\"\n      },\n      \"lines\": [\n        \"Storms make old warnings easier to hear. Stay close to shelter.\"\n      ],\n      \"requires_line_of_sight\": true,\n      \"force_camera_towards_villager\": true,\n      \"options\": [\n        {\n          \"id\": \"my_pack.quest.storm_warning.ok\",\n          \"label\": \"I understand.\",\n          \"response\": \"Then keep the warning near your feet.\",\n          \"end_conversation\": true\n        }\n      ]\n    }\n  ]\n}"
    },
    {
      "id": "quests-example-15",
      "page": "quests",
      "pageTitle": "Quests",
      "section": "Localization",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"metadata\": {\n    \"title\": \"Bread Delivery\",\n    \"title_key\": \"quest.my_pack.bread_delivery.title\",\n    \"description\": \"Bring 16 bread.\",\n    \"description_key\": \"quest.my_pack.bread_delivery.description\"\n  },\n  \"ui\": {\n    \"tracker_text\": \"Bring 16 bread.\",\n    \"tracker_text_key\": \"quest.my_pack.bread_delivery.tracker\"\n  }\n}"
    },
    {
      "id": "dialogue-and-quests-example-1",
      "page": "dialogue-and-quests",
      "pageTitle": "Dialogue And Quests",
      "section": "One-File Ownership",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"schema\": \"villagerretaliation:quest/v2\",\n  \"id\": \"my_pack:road_ledger\",\n  \"metadata\": {\n    \"title\": \"Road Ledger\",\n    \"tags\": [\"group.old_roads\"]\n  },\n  \"provider\": {\n    \"type\": \"villagerretaliation:villager\",\n    \"filters\": {\n      \"professions\": [\"minecraft:cartographer\"]\n    }\n  },\n  \"entry_stage\": \"start\",\n  \"stages\": [\n    {\n      \"id\": \"start\",\n      \"objectives\": [],\n      \"dialogue\": {\n        \"offer\": {\n          \"label\": \"Road Ledger\",\n          \"request\": \"question\",\n          \"lines\": [\"Paper survives rain worse than stone does.\"],\n          \"responses\": [\n            {\n              \"id\": \"complete\",\n              \"label\": \"Mark that down.\",\n              \"complete\": true\n            }\n          ]\n        }\n      }\n    }\n  ]\n}"
    },
    {
      "id": "dialogue-and-quests-example-2",
      "page": "dialogue-and-quests",
      "pageTitle": "Dialogue And Quests",
      "section": "Extracted Scene Ownership",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"external_scenes\": [\"my_pack:quests/old_roads/road_ledger\"],\n  \"stages\": [\n    {\n      \"id\": \"start\",\n      \"objectives\": [],\n      \"dialogue\": {\n        \"offer\": {\n          \"label\": \"Road Ledger\",\n          \"request\": \"question\",\n          \"external_scene\": {\n            \"tree\": \"my_pack:quests/old_roads/road_ledger\",\n            \"entry\": \"offer\"\n          }\n        }\n      }\n    }\n  ]\n}"
    },
    {
      "id": "dialogue-and-quests-example-3",
      "page": "dialogue-and-quests",
      "pageTitle": "Dialogue And Quests",
      "section": "Message Ownership",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.message.road_ledger_hint\",\n  \"key\": \"quest.my_pack.road_ledger.hint\",\n  \"text\": \"Paper survives rain worse than stone does.\"\n}"
    },
    {
      "id": "dialogue-and-quests-example-4",
      "page": "dialogue-and-quests",
      "pageTitle": "Dialogue And Quests",
      "section": "Forced Dialogue Ownership",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"events\": [\n    {\n      \"id\": \"storm_reminder\",\n      \"event\": \"near_provider\",\n      \"radius\": 10,\n      \"cooldown_seconds\": 120,\n      \"actions\": [\n        {\n          \"type\": \"forced_dialogue\",\n          \"forced_dialogue\": \"my_pack.quest.road_ledger.storm_warning\"\n        }\n      ]\n    }\n  ]\n}"
    },
    {
      "id": "dialogue-and-quests-example-5",
      "page": "dialogue-and-quests",
      "pageTitle": "Dialogue And Quests",
      "section": "Do Not Duplicate Gates",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"provider\": {\n    \"type\": \"villagerretaliation:villager\",\n    \"filters\": {\n      \"professions\": [\"minecraft:farmer\"]\n    }\n  }\n}"
    },
    {
      "id": "dialogue-and-quests-example-6",
      "page": "dialogue-and-quests",
      "pageTitle": "Dialogue And Quests",
      "section": "Do Not Duplicate Gates",
      "level": "Advanced",
      "language": "json",
      "code": "{ \"type\": \"quest\", \"state\": \"available\" }"
    },
    {
      "id": "quest-scenes-example-1",
      "page": "quest-scenes",
      "pageTitle": "Persistent Quest Scenes",
      "section": "Smallest Complete Scene",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"schema\": \"villagerretaliation:scene/v1\",\n  \"id\": \"my_pack:short_pause\",\n  \"definition_version\": 1,\n  \"ownership\": \"quest_instance\",\n  \"entry_step\": \"pause\",\n  \"actors\": [],\n  \"steps\": [\n    {\n      \"id\": \"pause\",\n      \"type\": \"villagerretaliation:wait_ticks\",\n      \"data\": {\n        \"ticks\": 20\n      },\n      \"next\": \"done\"\n    },\n    {\n      \"id\": \"done\",\n      \"type\": \"villagerretaliation:scene_complete\"\n    }\n  ]\n}"
    },
    {
      "id": "quest-scenes-example-2",
      "page": "quest-scenes",
      "pageTitle": "Persistent Quest Scenes",
      "section": "Smallest Complete Scene",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"type\": \"start_scene\",\n  \"scene\": \"my_pack:short_pause\",\n  \"operation_id\": \"short_pause_v1\",\n  \"wait_for_result\": true\n}"
    },
    {
      "id": "quest-scenes-example-3",
      "page": "quest-scenes",
      "pageTitle": "Persistent Quest Scenes",
      "section": "Starting a scene",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"type\": \"start_scene\",\n  \"scene\": \"example:gate_ambush\",\n  \"operation_id\": \"gate_ambush_v1\",\n  \"wait_for_result\": true\n}"
    },
    {
      "id": "quest-scenes-example-4",
      "page": "quest-scenes",
      "pageTitle": "Persistent Quest Scenes",
      "section": "Scene format",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"schema\": \"villagerretaliation:scene/v1\",\n  \"id\": \"example:gate_ambush\",\n  \"definition_version\": 1,\n  \"metadata\": { \"title\": \"Ambush at the Gate\" },\n  \"ownership\": \"party\",\n  \"entry_step\": \"move_captain\",\n  \"timeout_ticks\": 2400,\n  \"failure_policy\": \"block_for_repair\",\n  \"cancellation_policy\": \"cancel_scene\",\n  \"cleanup_policy\": \"all_owned\",\n  \"actors\": [],\n  \"steps\": []\n}"
    },
    {
      "id": "quest-scenes-example-5",
      "page": "quest-scenes",
      "pageTitle": "Persistent Quest Scenes",
      "section": "Encounters",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"schema\": \"villagerretaliation:encounter/v1\",\n  \"id\": \"example:gate_ambush\",\n  \"version\": 1,\n  \"controller\": \"villagerretaliation:controlled\",\n  \"members\": [\n    { \"entity\": \"minecraft:zombie\", \"count\": 3 },\n    {\n      \"entity\": \"minecraft:pillager\",\n      \"count\": 1,\n      \"custom_name\": \"Gate Captain\",\n      \"name_visible\": true,\n      \"glowing\": true,\n      \"persistent\": true,\n      \"health\": 40,\n      \"movement_speed\": 0.35,\n      \"attack_damage\": 8,\n      \"armor\": 10,\n      \"knockback_resistance\": 0.3,\n      \"boss\": true,\n      \"boss_bar_color\": \"purple\",\n      \"boss_bar_overlay\": \"notched_10\",\n      \"equipment\": {\n        \"mainhand\": {\n          \"item\": \"minecraft:crossbow\",\n          \"enchantments\": { \"minecraft:quick_charge\": 2 },\n          \"drop_chance\": 0.05\n        },\n        \"head\": { \"item\": \"minecraft:iron_helmet\" }\n      }\n    }\n  ],\n  \"spawn_mode\": \"group\",\n  \"spawn_points\": [\n    { \"id\": \"west_gate\", \"marker\": \"gate\", \"offset_x\": -8, \"weight\": 2 },\n    { \"id\": \"east_gate\", \"x\": 120, \"y\": 64, \"z\": -32, \"dimension\": \"minecraft:overworld\" }\n  ],\n  \"spawn_selection\": \"weighted\",\n  \"extra_per_player\": 1,\n  \"max_party_size\": 4,\n  \"placement_attempts\": 16,\n  \"spawn_radius\": 8,\n  \"area\": {\n    \"radius\": 32,\n    \"vertical_radius\": 16,\n    \"leave_behavior\": \"warn\",\n    \"leave_timeout_ticks\": 200,\n    \"mob_behavior\": \"return\"\n  },\n  \"respawn_policy\": \"missing_if_loaded\",\n  \"cleanup_policy\": \"remove_survivors\",\n  \"completion_condition\": \"all_defeated\"\n}"
    },
    {
      "id": "quest-scenes-example-6",
      "page": "quest-scenes",
      "pageTitle": "Persistent Quest Scenes",
      "section": "Completion objectives",
      "level": "Advanced",
      "language": "json",
      "code": "{\n\"completion_objectives\": {\n  \"mode\": \"all\",\n  \"objectives\": [\n    { \"id\": \"hold_gate\", \"type\": \"prevent_entry\", \"point\": \"west_gate\", \"duration_ticks\": 600, \"radius\": 5 },\n    { \"id\": \"stop_captain\", \"type\": \"defeat_leader\", \"member\": \"raider_captain\" }\n  ]\n}\n}"
    },
    {
      "id": "quest-scenes-example-7",
      "page": "quest-scenes",
      "pageTitle": "Persistent Quest Scenes",
      "section": "Friendly participants",
      "level": "Advanced",
      "language": "json",
      "code": "{\n\"allies\": [\n  {\n    \"id\": \"village_guard\",\n    \"entity\": \"minecraft:iron_golem\",\n    \"revivable\": true,\n    \"revive_delay_ticks\": 100,\n    \"replacement_policy\": \"missing_if_loaded\",\n    \"cleanup_policy\": \"preserve\",\n    \"affects_completion\": true\n  },\n  {\n    \"id\": \"watch_captain\",\n    \"actor\": \"watch_captain\",\n    \"invulnerable\": true,\n    \"cleanup_policy\": \"preserve\"\n  }\n]\n}"
    },
    {
      "id": "quest-scenes-example-8",
      "page": "quest-scenes",
      "pageTitle": "Persistent Quest Scenes",
      "section": "Failure and retry policies",
      "level": "Advanced",
      "language": "json",
      "code": "{\n\"failure\": {\n  \"on_player_death\": \"reset_wave\",\n  \"on_protected_actor_death\": \"branch_scene\",\n  \"branch_step\": \"failed\",\n  \"retry_delay_ticks\": 200,\n  \"max_attempts\": 3,\n  \"retain_defeated\": false\n}\n}"
    },
    {
      "id": "quest-scenes-example-9",
      "page": "quest-scenes",
      "pageTitle": "Persistent Quest Scenes",
      "section": "Deterministic encounter variants",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"schema\": \"villagerretaliation:encounter/v1\",\n  \"id\": \"my_pack:roadblock_variants\",\n  \"variants\": [\n    { \"id\": \"zombie_roadblock\", \"weight\": 3, \"template\": \"my_pack:zombie_roadblock\" },\n    { \"id\": \"skeleton_ambush\", \"weight\": 2, \"template\": \"my_pack:skeleton_ambush\" }\n  ]\n}"
    },
    {
      "id": "quest-scenes-example-10",
      "page": "quest-scenes",
      "pageTitle": "Persistent Quest Scenes",
      "section": "Deterministic encounter variants",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"start_roadblock\",\n  \"type\": \"villagerretaliation:start_encounter\",\n  \"data\": { \"template\": \"my_pack:roadblock_variants\", \"x\": 120, \"y\": 64, \"z\": -40 },\n  \"transitions\": {\n    \"zombie_roadblock\": \"warn_about_zombies\",\n    \"skeleton_ambush\": \"raise_shields\"\n  }\n}"
    },
    {
      "id": "quest-scenes-example-11",
      "page": "quest-scenes",
      "pageTitle": "Persistent Quest Scenes",
      "section": "Environmental setup and restoration",
      "level": "Advanced",
      "language": "json",
      "code": "{\n\"environment\": {\n  \"cues\": [\n    { \"id\": \"alarm\", \"type\": \"sound\", \"sound\": \"minecraft:block.bell.use\", \"volume\": 1.0, \"pitch\": 0.8 },\n    { \"id\": \"gate_column\", \"type\": \"glowing_column\", \"particle\": \"minecraft:end_rod\", \"offset_y\": 1, \"count\": 32, \"height\": 8 }\n  ],\n  \"temporary_blocks\": [\n    { \"id\": \"gate_light\", \"block\": \"minecraft:light\", \"offset_y\": 3 }\n  ]\n}\n}"
    },
    {
      "id": "quest-scenes-example-12",
      "page": "quest-scenes",
      "pageTitle": "Persistent Quest Scenes",
      "section": "Navigation guidance",
      "level": "Advanced",
      "language": "json",
      "code": "{\n\"guidance\": {\n  \"coordinate_message\": \"Find {location}. It is {distance}m {direction}.\",\n  \"arrival_message\": \"You reached {coordinates}.\",\n  \"discovery_radius\": 64,\n  \"arrival_radius\": 8,\n  \"distance_tracker\": true,\n  \"compass_target\": true,\n  \"directional_particles\": true,\n  \"hud_marker\": true,\n  \"exact_coordinates\": \"after_discovery\",\n  \"update_interval_ticks\": 20\n}\n}"
    },
    {
      "id": "quest-scenes-example-13",
      "page": "quest-scenes",
      "pageTitle": "Persistent Quest Scenes",
      "section": "Rewards and mob drops",
      "level": "Advanced",
      "language": "json",
      "code": "{\n\"rewards\": {\n  \"waves\": [\n    { \"id\": \"scout_supplies\", \"wave\": \"scouts\", \"item\": \"minecraft:arrow\", \"count\": 4 }\n  ],\n  \"phases\": [\n    { \"id\": \"captain_token\", \"phase\": \"captain_falls\", \"item\": \"minecraft:iron_nugget\" }\n  ],\n  \"completion\": [\n    { \"id\": \"village_medal\", \"item\": \"minecraft:emerald\", \"trophy_name\": \"Village Medal\" },\n    { \"id\": \"bonus_cache\", \"loot_table\": \"example:encounters/gate_cache\" }\n  ],\n  \"trophies\": [\n    { \"id\": \"captain_badge\", \"member\": \"gate_captain\", \"item\": \"minecraft:gold_nugget\", \"name\": \"Captain Badge\" }\n  ],\n  \"drop_policy\": \"trophy_only\"\n}\n}"
    },
    {
      "id": "quest-scenes-example-14",
      "page": "quest-scenes",
      "pageTitle": "Persistent Quest Scenes",
      "section": "Spawn modes",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"schema\": \"villagerretaliation:encounter/v1\",\n  \"id\": \"example:three_wave_raid\",\n  \"members\": [{ \"entity\": \"minecraft:pillager\", \"count\": 3 }],\n  \"spawn_mode\": \"raid_waves\",\n  \"wave_count\": 3,\n  \"wave_interval_ticks\": 100,\n  \"wave_trigger\": \"all_defeated\",\n  \"boss_bar\": true,\n  \"spawn_radius\": 12\n}"
    },
    {
      "id": "quest-scenes-example-15",
      "page": "quest-scenes",
      "pageTitle": "Persistent Quest Scenes",
      "section": "Spawn modes",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"schema\": \"villagerretaliation:encounter/v1\",\n  \"id\": \"example:gate_defense\",\n  \"spawn_mode\": \"raid_waves\",\n  \"waves\": [\n    {\n      \"id\": \"scouts\",\n      \"members\": [{ \"entity\": \"minecraft:zombie\", \"count\": 3 }],\n      \"boss_bar_title\": \"Gate Defense - Scouts\"\n    },\n    {\n      \"id\": \"captain\",\n      \"members\": [{ \"entity\": \"minecraft:pillager\" }],\n      \"delay_ticks\": 100,\n      \"trigger\": \"all_defeated\",\n      \"boss_bar_title\": \"Gate Defense - Captain\",\n      \"equipment\": { \"mainhand\": { \"item\": \"minecraft:crossbow\" } },\n      \"dialogue_hook\": { \"id\": \"captain_arrives\", \"text\": \"Their captain is here!\" }\n    }\n  ]\n}"
    },
    {
      "id": "gifts-example-1",
      "page": "gifts",
      "pageTitle": "Gifts",
      "section": "Minimal Preference Example",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"preferences\": [\n    {\n      \"id\": \"my_pack.librarian.favorite_book\",\n      \"professions\": [\"minecraft:librarian\"],\n      \"reaction\": \"loved\",\n      \"items\": [\"minecraft:enchanted_book\", \"minecraft:name_tag\"],\n      \"response_key\": \"my_pack.gift.librarian.favorite_book\",\n      \"priority\": 20\n    }\n  ]\n}"
    },
    {
      "id": "gifts-example-2",
      "page": "gifts",
      "pageTitle": "Gifts",
      "section": "Minimal Reward Example",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"rewards\": [\n    {\n      \"id\": \"my_pack.librarian.reward\",\n      \"professions\": [\"minecraft:librarian\"],\n      \"reputation_levels\": [\"revered\", \"royalty\"],\n      \"item\": \"minecraft:book\",\n      \"min_count\": 2,\n      \"max_count\": 5,\n      \"weight\": 10\n    }\n  ]\n}"
    },
    {
      "id": "gifts-example-3",
      "page": "gifts",
      "pageTitle": "Gifts",
      "section": "Example: Shared Response Text",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.gift_message.favorite_book\",\n  \"key\": \"my_pack.gift.librarian.favorite_book\",\n  \"text\": \"{gift_item}, This belongs near a reading lamp, not forgotten in a chest.\"\n}"
    },
    {
      "id": "pacification-example-1",
      "page": "pacification",
      "pageTitle": "Pacification",
      "section": "Simple Example",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"payments\": [\n    {\n      \"items\": [\"minecraft:emerald\"],\n      \"count\": 8,\n      \"priority\": 10\n    }\n  ]\n}"
    },
    {
      "id": "pacification-example-2",
      "page": "pacification",
      "pageTitle": "Pacification",
      "section": "Modded Currency Example",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"payments\": [\n    {\n      \"item\": \"numismatic-overhaul:gold_coin\",\n      \"count\": 12,\n      \"name\": \"gold coin\",\n      \"plural_name\": \"gold coins\",\n      \"priority\": 20\n    }\n  ]\n}"
    },
    {
      "id": "pacification-example-3",
      "page": "pacification",
      "pageTitle": "Pacification",
      "section": "Item Tag Example",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"payments\": [\n    {\n      \"tags\": [\"#c:ingots/iron\"],\n      \"count\": 4\n    }\n  ]\n}"
    },
    {
      "id": "pacification-example-4",
      "page": "pacification",
      "pageTitle": "Pacification",
      "section": "Profession-Specific Example",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"payments\": [\n    {\n      \"professions\": [\"minecraft:toolsmith\"],\n      \"items\": [\"minecraft:iron_ingot\"],\n      \"min_count\": 2,\n      \"max_count\": 4,\n      \"priority\": 30\n    }\n  ]\n}"
    },
    {
      "id": "pacification-example-5",
      "page": "pacification",
      "pageTitle": "Pacification",
      "section": "Dialogue Example",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.pacify.toolsmith\",\n  \"professions\": [\"minecraft:toolsmith\"],\n  \"text\": \"Fine. Leave the {payment_cost} {payment_items} and walk away.\"\n}"
    },
    {
      "id": "profession-loot-example-1",
      "page": "profession-loot",
      "pageTitle": "Profession Loot",
      "section": "Complete Example",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"tables\": [\n    {\n      \"id\": \"my_pack.alchemist.common\",\n      \"professions\": [\"examplemod:alchemist\"],\n      \"loot_table\": \"my_pack:villager/profession/alchemist/common\",\n      \"chance\": \"always\"\n    }\n  ]\n}"
    },
    {
      "id": "profession-loot-example-2",
      "page": "profession-loot",
      "pageTitle": "Profession Loot",
      "section": "Complete Example",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"type\": \"minecraft:entity\",\n  \"pools\": [\n    {\n      \"rolls\": 1,\n      \"entries\": [\n        {\n          \"type\": \"minecraft:item\",\n          \"name\": \"minecraft:amethyst_shard\"\n        }\n      ]\n    }\n  ]\n}"
    },
    {
      "id": "profession-loot-example-3",
      "page": "profession-loot",
      "pageTitle": "Profession Loot",
      "section": "Add, Replace, Or Remove",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"tables\": [\n    {\n      \"id\": \"villagerretaliation.profession_loot.farmer.rare\",\n      \"professions\": [\"minecraft:farmer\"],\n      \"loot_table\": \"my_pack:villager/profession/farmer/rare\",\n      \"chance\": 0.2\n    }\n  ]\n}"
    },
    {
      "id": "profession-loot-example-4",
      "page": "profession-loot",
      "pageTitle": "Profession Loot",
      "section": "Add, Replace, Or Remove",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"tables\": [\n    {\n      \"id\": \"villagerretaliation.profession_loot.farmer.rare\",\n      \"remove\": true\n    }\n  ]\n}"
    },
    {
      "id": "profession-loot-example-5",
      "page": "profession-loot",
      "pageTitle": "Profession Loot",
      "section": "Add, Replace, Or Remove",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"replace\": true,\n  \"tables\": []\n}"
    },
    {
      "id": "currency-and-item-text-example-1",
      "page": "currency-and-item-text",
      "pageTitle": "Currency And Item Text",
      "section": "Currency Example",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"item\": \"examplemod:copper_coin\",\n  \"accepted_items\": [\n    \"examplemod:copper_coin\",\n    \"examplemod:silver_coin\"\n  ],\n  \"accepted_tags\": [\n    \"examplemod:coins\"\n  ],\n  \"name\": \"copper coin\",\n  \"plural_name\": \"copper coins\",\n  \"wallet_label\": \"Coins\",\n  \"icon_sprite\": \"examplemod:item/copper_coin\",\n  \"text_color\": \"#D9824A\"\n}"
    },
    {
      "id": "currency-and-item-text-example-2",
      "page": "currency-and-item-text",
      "pageTitle": "Currency And Item Text",
      "section": "Keep The Currency Tag In Sync",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"replace\": false,\n  \"values\": [\n    \"examplemod:copper_coin\",\n    \"examplemod:silver_coin\"\n  ]\n}"
    },
    {
      "id": "currency-and-item-text-example-3",
      "page": "currency-and-item-text",
      "pageTitle": "Currency And Item Text",
      "section": "Simple Item Name Example",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"items\": {\n    \"examplemod:copper_coin\": {\n      \"one\": \"copper coin\",\n      \"other\": \"copper coins\"\n    },\n    \"minecraft:bread\": {\n      \"one\": \"bread\",\n      \"other\": \"bread\"\n    }\n  }\n}"
    },
    {
      "id": "currency-and-item-text-example-4",
      "page": "currency-and-item-text",
      "pageTitle": "Currency And Item Text",
      "section": "Count Forms",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"forms\": [\n    {\n      \"id\": \"one\",\n      \"count_pattern\": \"1\",\n      \"format\": \"{item}\"\n    },\n    {\n      \"id\": \"other\",\n      \"format\": \"{count} {item}\"\n    }\n  ]\n}"
    },
    {
      "id": "currency-and-item-text-example-5",
      "page": "currency-and-item-text",
      "pageTitle": "Currency And Item Text",
      "section": "Count Forms",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"forms\": [\n    {\n      \"id\": \"one\",\n      \"count_pattern\": \"1\",\n      \"format\": \"{item}\"\n    },\n    {\n      \"id\": \"few\",\n      \"count_pattern\": \"[2-4]\",\n      \"format\": \"{count} {item}\"\n    },\n    {\n      \"id\": \"other\",\n      \"format\": \"{count} {item}\"\n    }\n  ]\n}"
    },
    {
      "id": "currency-and-item-text-example-6",
      "page": "currency-and-item-text",
      "pageTitle": "Currency And Item Text",
      "section": "Count Forms",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"currency\": {\n    \"one\": \"copper coin\",\n    \"few\": \"copper coins\",\n    \"other\": \"copper coins\"\n  }\n}"
    },
    {
      "id": "currency-and-item-text-example-7",
      "page": "currency-and-item-text",
      "pageTitle": "Currency And Item Text",
      "section": "Automatic Word Rules",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"rules\": [\n    {\n      \"forms\": [\"other\"],\n      \"pattern\": \"(?i)(.*[^aeiou])y$\",\n      \"replacement\": \"$1ies\"\n    }\n  ]\n}"
    },
    {
      "id": "duel-kits-example-1",
      "page": "duel-kits",
      "pageTitle": "Duel Kits",
      "section": "Small Melee Kit",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"name\": \"iron practice gear\",\n  \"description\": \"Iron swords and shields.\",\n  \"sort_order\": 50,\n  \"combat_style\": \"melee\",\n  \"player\": {\n    \"inventory\": [\n      {\n        \"slot\": 0,\n        \"stack\": {\n          \"id\": \"minecraft:iron_sword\"\n        }\n      }\n    ],\n    \"equipment\": {\n      \"offhand\": {\n        \"id\": \"minecraft:shield\"\n      }\n    }\n  },\n  \"villager\": {\n    \"equipment\": {\n      \"mainhand\": {\n        \"id\": \"minecraft:iron_sword\"\n      },\n      \"offhand\": {\n        \"id\": \"minecraft:shield\"\n      }\n    }\n  }\n}"
    },
    {
      "id": "duel-kits-example-2",
      "page": "duel-kits",
      "pageTitle": "Duel Kits",
      "section": "Main Fields",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"slot\": 1,\n  \"stack\": {\n    \"id\": \"minecraft:arrow\",\n    \"count\": 64\n  }\n}"
    },
    {
      "id": "duel-kits-example-3",
      "page": "duel-kits",
      "pageTitle": "Duel Kits",
      "section": "Main Fields",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"minecraft:diamond_sword\",\n  \"components\": {\n    \"minecraft:enchantments\": {\n      \"levels\": {\n        \"minecraft:sharpness\": 3\n      }\n    }\n  }\n}"
    },
    {
      "id": "skill-trades-example-1",
      "page": "skill-trades",
      "pageTitle": "Skill Trades",
      "section": "Minimal Entry",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"entries\": [\n    {\n      \"id\": \"my_pack:cartographer_basic_map\",\n      \"professions\": [\"minecraft:cartographer\"],\n      \"skills\": [\"villagerretaliation:cartography\"],\n      \"min_rank\": \"novice\",\n      \"max_rank\": \"apprentice\",\n      \"villager_level\": 1,\n      \"chance\": 0.8,\n      \"weight\": 12,\n      \"cost\": { \"item\": \"minecraft:emerald\", \"count\": 8 },\n      \"result\": { \"item\": \"minecraft:map\", \"count\": 1 },\n      \"max_uses\": { \"base\": 4 },\n      \"xp\": 4,\n      \"price_multiplier\": 0.05,\n      \"quality_scaling\": true\n    }\n  ]\n}"
    },
    {
      "id": "skill-trades-example-2",
      "page": "skill-trades",
      "pageTitle": "Skill Trades",
      "section": "Low-Skill Extra Stock",
      "level": "Advanced",
      "language": "json",
      "code": "{\n\"min_rank\": \"novice\",\n\"max_rank\": \"apprentice\"\n}"
    },
    {
      "id": "skill-trades-example-3",
      "page": "skill-trades",
      "pageTitle": "Skill Trades",
      "section": "High-Skill Specialty Offer",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack:farmer_master_hoe\",\n  \"professions\": [\"minecraft:farmer\"],\n  \"skills\": [\"villagerretaliation:farming\"],\n  \"min_rank\": \"master\",\n  \"villager_level\": 5,\n  \"cost\": { \"item\": \"minecraft:emerald\", \"count\": 18 },\n  \"result\": {\n    \"item\": \"minecraft:diamond_hoe\",\n    \"count\": 1\n  },\n  \"conditions\": {\n    \"config_flags\": [\"skillTradeAllowHighTierEquipment\"]\n  }\n}"
    },
    {
      "id": "skill-trades-example-4",
      "page": "skill-trades",
      "pageTitle": "Skill Trades",
      "section": "Targetable Special Order",
      "level": "Advanced",
      "language": "json",
      "code": "{\n\"request\": {\n  \"targetable\": true,\n  \"display_priority\": 20,\n  \"min_reputation\": \"respected\",\n  \"wait_days\": 2,\n  \"cooldown_days\": 3\n}\n}"
    },
    {
      "id": "skill-trades-example-5",
      "page": "skill-trades",
      "pageTitle": "Skill Trades",
      "section": "Wandering Trader Entry",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack:wandering_trader_shell\",\n  \"professions\": [\"minecraft:wandering_trader\"],\n  \"skills\": [\"villagerretaliation:trading\"],\n  \"min_rank\": \"master\",\n  \"chance\": 0.3,\n  \"cost\": { \"item\": \"minecraft:emerald\", \"count\": 15 },\n  \"result\": { \"item\": \"minecraft:nautilus_shell\", \"count\": 1 }\n}"
    },
    {
      "id": "sell-box-and-daily-market-example-1",
      "page": "sell-box-and-daily-market",
      "pageTitle": "Sell Box And Daily Market",
      "section": "Use Explicit Rates For New Packs",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"item\": \"minecraft:coal\",\n  \"rates\": [\n    {\n      \"item_count\": 15,\n      \"currency_count\": 1\n    }\n  ],\n  \"market_group\": \"villagerretaliation:fuel\"\n}"
    },
    {
      "id": "sell-box-and-daily-market-example-2",
      "page": "sell-box-and-daily-market",
      "pageTitle": "Sell Box And Daily Market",
      "section": "Use Explicit Rates For New Packs",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"item\": \"#minecraft:logs\",\n  \"rates\": [\n    {\n      \"item_count\": {\n        \"min\": 5,\n        \"max\": 8\n      },\n      \"currency_count\": 1\n    }\n  ],\n  \"market_group\": \"villagerretaliation:logs\"\n}"
    },
    {
      "id": "sell-box-and-daily-market-example-3",
      "page": "sell-box-and-daily-market",
      "pageTitle": "Sell Box And Daily Market",
      "section": "Use Explicit Rates For New Packs",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"item\": \"minecraft:diamond\",\n  \"rates\": [\n    {\n      \"item_count\": 1,\n      \"currency_count\": {\n        \"min\": 10,\n        \"max\": 15\n      }\n    }\n  ]\n}"
    },
    {
      "id": "sell-box-and-daily-market-example-4",
      "page": "sell-box-and-daily-market",
      "pageTitle": "Sell Box And Daily Market",
      "section": "Discrete Prices Stay Discrete",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"item\": \"#vr-food-sellbox:cocktails\",\n  \"rates\": [\n    {\n      \"item_count\": 1,\n      \"currency_count\": 10\n    },\n    {\n      \"item_count\": 1,\n      \"currency_count\": 15\n    },\n    {\n      \"item_count\": 1,\n      \"currency_count\": 25\n    }\n  ],\n  \"market_group\": \"vfs:cocktails\"\n}"
    },
    {
      "id": "sell-box-and-daily-market-example-5",
      "page": "sell-box-and-daily-market",
      "pageTitle": "Sell Box And Daily Market",
      "section": "Discrete Prices Stay Discrete",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"item\": \"minecraft:iron_ingot\",\n  \"rates\": [\n    {\n      \"item_count\": 5,\n      \"currency_count\": 1\n    },\n    {\n      \"item_count\": 10,\n      \"currency_count\": 3\n    },\n    {\n      \"item_count\": 20,\n      \"currency_count\": 8\n    }\n  ]\n}"
    },
    {
      "id": "sell-box-and-daily-market-example-6",
      "page": "sell-box-and-daily-market",
      "pageTitle": "Sell Box And Daily Market",
      "section": "Existing beta.13 Files Still Work",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"item\": \"minecraft:charcoal\",\n  \"item_count\": {\n    \"min\": 12,\n    \"max\": 20\n  },\n  \"currency_count\": 1\n}"
    },
    {
      "id": "sell-box-and-daily-market-example-7",
      "page": "sell-box-and-daily-market",
      "pageTitle": "Sell Box And Daily Market",
      "section": "Matching Item Components",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"item\": \"kaleidoscope_tavern:miners_star\",\n  \"components\": {\n    \"kaleidoscope_tavern:brew_level\": 6\n  },\n  \"priority\": 10,\n  \"rates\": [\n    {\n      \"item_count\": 1,\n      \"currency_count\": 15\n    }\n  ],\n  \"market_group\": \"kaleidoscope_tavern:miners_stars\"\n}"
    },
    {
      "id": "sell-box-and-daily-market-example-8",
      "page": "sell-box-and-daily-market",
      "pageTitle": "Sell Box And Daily Market",
      "section": "Matching Item Components",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"item\": \"example:graded_gem\",\n  \"components\": {\n    \"example:quality\": {\n      \"min\": 3,\n      \"max\": 6\n    }\n  },\n  \"rates\": [\n    {\n      \"item_count\": 1,\n      \"currency_count\": 8\n    }\n  ]\n}"
    },
    {
      "id": "sell-box-and-daily-market-example-9",
      "page": "sell-box-and-daily-market",
      "pageTitle": "Sell Box And Daily Market",
      "section": "Matching Item Components",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"item\": \"example:keepsake\",\n  \"components\": {\n    \"minecraft:custom_data\": {\n      \"maker\": \"ada\",\n      \"edition\": 3\n    }\n  },\n  \"rates\": [\n    {\n      \"item_count\": 1,\n      \"currency_count\": 4\n    }\n  ]\n}"
    },
    {
      "id": "sell-box-and-daily-market-example-10",
      "page": "sell-box-and-daily-market",
      "pageTitle": "Sell Box And Daily Market",
      "section": "Exact Component Shorthand",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"item\": \"kaleidoscope_tavern:miners_star[kaleidoscope_tavern:brew_level=6]\",\n  \"rates\": [\n    {\n      \"item_count\": 1,\n      \"currency_count\": 15\n    }\n  ]\n}"
    },
    {
      "id": "sell-box-and-daily-market-example-11",
      "page": "sell-box-and-daily-market",
      "pageTitle": "Sell Box And Daily Market",
      "section": "Remaining Durability",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"item\": \"#example:valuable_pickaxes\",\n  \"components\": {\n    \"example:quality\": {\n      \"min\": 3\n    }\n  },\n  \"durability\": {\n    \"min\": 250\n  },\n  \"rates\": [\n    {\n      \"item_count\": 1,\n      \"currency_count\": 5\n    },\n    {\n      \"item_count\": 1,\n      \"currency_count\": 8\n    },\n    {\n      \"item_count\": 1,\n      \"currency_count\": 12\n    }\n  ],\n  \"market_group\": \"example:tools\"\n}"
    },
    {
      "id": "sell-box-and-daily-market-example-12",
      "page": "sell-box-and-daily-market",
      "pageTitle": "Sell Box And Daily Market",
      "section": "Overlapping Definitions And Priority",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"item\": \"kaleidoscope_tavern:miners_star\",\n  \"rates\": [\n    {\n      \"item_count\": 1,\n      \"currency_count\": 5\n    }\n  ]\n}"
    },
    {
      "id": "sell-box-and-daily-market-example-13",
      "page": "sell-box-and-daily-market",
      "pageTitle": "Sell Box And Daily Market",
      "section": "Overlapping Definitions And Priority",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"item\": \"kaleidoscope_tavern:miners_star\",\n  \"components\": {\n    \"kaleidoscope_tavern:brew_level\": 6\n  },\n  \"priority\": 10,\n  \"rates\": [\n    {\n      \"item_count\": 1,\n      \"currency_count\": 15\n    }\n  ]\n}"
    },
    {
      "id": "sell-box-and-daily-market-example-14",
      "page": "sell-box-and-daily-market",
      "pageTitle": "Sell Box And Daily Market",
      "section": "Add, Replace, Or Disable",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"enabled\": false\n}"
    },
    {
      "id": "builder-structures-example-1",
      "page": "builder-structures",
      "pageTitle": "Builder Structures",
      "section": "Add One Structure",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"entries\": [\n    {\n      \"structure\": \"examplemod:village/houses/carpenter_house\",\n      \"category\": \"Modded Village\",\n      \"label\": \"Carpenter House\",\n      \"base_cost\": 18\n    }\n  ]\n}"
    },
    {
      "id": "builder-structures-example-2",
      "page": "builder-structures",
      "pageTitle": "Builder Structures",
      "section": "Add Several Structures",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"entries\": [\n    {\n      \"category\": \"Modded Village\",\n      \"base_cost\": 16,\n      \"structures\": [\n        \"examplemod:village/houses/small_house_1\",\n        \"examplemod:village/houses/small_house_2\",\n        \"examplemod:village/houses/fisher_house\"\n      ]\n    }\n  ]\n}"
    },
    {
      "id": "builder-structures-example-3",
      "page": "builder-structures",
      "pageTitle": "Builder Structures",
      "section": "Remove A Built-In Structure",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"entries\": [\n    {\n      \"structure\": \"minecraft:village/plains/houses/plains_small_house_1\",\n      \"remove\": true\n    }\n  ]\n}"
    },
    {
      "id": "builder-structures-example-4",
      "page": "builder-structures",
      "pageTitle": "Builder Structures",
      "section": "Replace The Whole List",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"replace\": true,\n  \"entries\": [\n    {\n      \"structure\": \"examplemod:village/houses/starter_home\",\n      \"category\": \"Starter Village\",\n      \"label\": \"Starter Home\",\n      \"base_cost\": 10\n    }\n  ]\n}"
    },
    {
      "id": "natural-job-armor-example-1",
      "page": "natural-job-armor",
      "pageTitle": "Natural Job Armor",
      "section": "Example",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"profiles\": [\n    {\n      \"id\": \"my_pack.guard_smiths\",\n      \"professions\": [\"armorer\", \"toolsmith\", \"weaponsmith\"],\n      \"chance\": {\n        \"peaceful\": 0.25,\n        \"easy\": 0.40,\n        \"normal\": 0.60,\n        \"hard\": 0.80\n      },\n      \"next_piece_chance\": {\n        \"peaceful\": 0.50,\n        \"easy\": 0.60,\n        \"normal\": 0.75,\n        \"hard\": 0.90\n      },\n      \"mixed_gear_chance\": {\n        \"peaceful\": 0.05,\n        \"easy\": 0.10,\n        \"normal\": 0.20,\n        \"hard\": 0.30\n      },\n      \"enchant_chance\": {\n        \"peaceful\": 0.01,\n        \"easy\": 0.03,\n        \"normal\": 0.08,\n        \"hard\": 0.16\n      },\n      \"armor_sets\": [\n        {\n          \"id\": \"iron\",\n          \"material\": \"iron\",\n          \"weight\": 95\n        },\n        {\n          \"id\": \"diamond\",\n          \"material\": \"diamond\",\n          \"weight_by_difficulty\": {\n            \"peaceful\": 0,\n            \"easy\": 1,\n            \"normal\": 2,\n            \"hard\": 5\n          }\n        }\n      ]\n    }\n  ]\n}"
    },
    {
      "id": "natural-job-armor-example-2",
      "page": "natural-job-armor",
      "pageTitle": "Natural Job Armor",
      "section": "Fields",
      "level": "Advanced",
      "language": "json",
      "code": "{\n\"professions\": [\"examplemod:guard\", \"examplemod:archer\"]\n}"
    },
    {
      "id": "natural-job-armor-example-3",
      "page": "natural-job-armor",
      "pageTitle": "Natural Job Armor",
      "section": "Armor Sets",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"material\": \"chainmail\",\n  \"weight\": 30\n}"
    },
    {
      "id": "natural-job-armor-example-4",
      "page": "natural-job-armor",
      "pageTitle": "Natural Job Armor",
      "section": "Armor Sets",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"modded_guard_set\",\n  \"weight\": 10,\n  \"items\": {\n    \"feet\": \"examplemod:guard_boots\",\n    \"legs\": \"examplemod:guard_leggings\",\n    \"chest\": \"examplemod:guard_chestplate\",\n    \"head\": \"examplemod:guard_helmet\"\n  }\n}"
    },
    {
      "id": "story-discovery-example-1",
      "page": "story-discovery",
      "pageTitle": "Story Discovery",
      "section": "Structure Example",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"radius\": 128,\n  \"entries\": [\n    {\n      \"structure\": \"examplemod:haunted_keep\",\n      \"name\": \"Haunted Keep\"\n    },\n    {\n      \"structures\": [\n        \"examplemod:ruined_watchtower\",\n        \"examplemod:ruined_gate\"\n      ],\n      \"radius\": 96\n    }\n  ]\n}"
    },
    {
      "id": "story-discovery-example-2",
      "page": "story-discovery",
      "pageTitle": "Story Discovery",
      "section": "Biome Example",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"entries\": [\n    {\n      \"biome\": \"examplemod:crystal_marsh\",\n      \"name\": \"Crystal Marsh\"\n    },\n    {\n      \"biomes\": [\n        \"examplemod:ashen_fen\",\n        \"examplemod:smoke_bog\"\n      ]\n    }\n  ]\n}"
    },
    {
      "id": "story-discovery-example-3",
      "page": "story-discovery",
      "pageTitle": "Story Discovery",
      "section": "Dialogue Example",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.story.haunted_keep\",\n  \"request\": \"share_story\",\n  \"option\": \"adult_share_story\",\n  \"story_structure\": \"examplemod:haunted_keep\",\n  \"text\": \"{target_article}. We do not say its name after sundown.\"\n}"
    },
    {
      "id": "villager-names-example-1",
      "page": "villager-names",
      "pageTitle": "Villager Names",
      "section": "Example",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"male_names\": [\n    \"Bram\",\n    \"Edric\"\n  ],\n  \"female_names\": [\n    \"Cora\",\n    \"Mira\"\n  ]\n}"
    },
    {
      "id": "villager-names-example-2",
      "page": "villager-names",
      "pageTitle": "Villager Names",
      "section": "Replace Example",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"replace\": true,\n  \"male_names\": [\"Alden\"],\n  \"female_names\": [\"Lyra\"]\n}"
    },
    {
      "id": "village-names-example-1",
      "page": "village-names",
      "pageTitle": "Village Names",
      "section": "Example",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"prefixes\": [\n    \"Copper\",\n    \"Juniper\"\n  ],\n  \"suffixes\": [\n    \"bridge\",\n    \"hollow\"\n  ]\n}"
    },
    {
      "id": "village-names-example-2",
      "page": "village-names",
      "pageTitle": "Village Names",
      "section": "Replace Example",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"replace\": true,\n  \"prefixes\": [\"Sun\"],\n  \"suffixes\": [\"haven\", \"wick\"]\n}"
    },
    {
      "id": "player-raids-example-1",
      "page": "player-raids",
      "pageTitle": "Player Raids",
      "section": "Minimal profession weapon pool",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"loadouts\": [\n    {\n      \"id\": \"my_pack_fletcher_crossbow\",\n      \"professions\": [\"minecraft:fletcher\"],\n      \"difficulty_pools\": {\n        \"normal\": {\n          \"weapons\": [\"minecraft:crossbow\"]\n        }\n      }\n    }\n  ]\n}"
    },
    {
      "id": "player-raids-example-2",
      "page": "player-raids",
      "pageTitle": "Player Raids",
      "section": "Advanced militia armor",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"replace\": false,\n  \"loadouts\": [\n    {\n      \"id\": \"my_pack_militia\",\n      \"professions\": [\"minecraft:fletcher\"],\n      \"difficulty_pools\": {\n        \"normal\": {\n          \"weapons\": [\"minecraft:crossbow\"],\n          \"armor_chance\": 0.75,\n          \"enchant_chance\": 0.1,\n          \"armor_sets\": [\n            {\n              \"weight\": 1,\n              \"head\": \"minecraft:chainmail_helmet\",\n              \"chest\": \"minecraft:chainmail_chestplate\",\n              \"legs\": \"minecraft:chainmail_leggings\",\n              \"feet\": \"minecraft:chainmail_boots\"\n            }\n          ]\n        }\n      }\n    }\n  ]\n}"
    },
    {
      "id": "generated-containers-example-1",
      "page": "generated-containers",
      "pageTitle": "Generated Containers",
      "section": "Example",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"loot_tables\": [\n    \"examplemod:chests/village/alchemist_house\",\n    \"examplemod:chests/village/watch_tower\"\n  ]\n}"
    },
    {
      "id": "generated-containers-example-2",
      "page": "generated-containers",
      "pageTitle": "Generated Containers",
      "section": "Example",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"loot_table\": \"examplemod:chests/village/alchemist_house\"\n}"
    },
    {
      "id": "generated-containers-example-3",
      "page": "generated-containers",
      "pageTitle": "Generated Containers",
      "section": "Example",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"entries\": [\n    {\n      \"loot_tables\": [\n        \"examplemod:chests/village/alchemist_house\",\n        \"examplemod:chests/village/watch_tower\"\n      ]\n    },\n    {\n      \"loot_table\": \"anothermod:chests/village_store\"\n    }\n  ]\n}"
    },
    {
      "id": "resource-pack-models-example-1",
      "page": "resource-pack-models",
      "pageTitle": "Resource Pack Models",
      "section": "Minimal Model Shape",
      "level": "Starter",
      "language": "json",
      "code": "{\n  \"texture_width\": 128,\n  \"texture_height\": 128,\n  \"parts\": [\n    { \"name\": \"body\", \"cubes\": [] },\n    { \"name\": \"arms\", \"pivot\": [0.0, 2.0, 0.0], \"rotation\": [-45.0, 0.0, 0.0], \"cubes\": [] },\n    { \"name\": \"RightArm\", \"pivot\": [-5.0, 2.0, 0.0], \"cubes\": [] },\n    { \"name\": \"LeftArm\", \"pivot\": [5.0, 2.0, 0.0], \"cubes\": [] },\n    { \"name\": \"RightLeg\", \"pivot\": [-2.0, 12.0, 0.0], \"cubes\": [] },\n    { \"name\": \"LeftLeg\", \"pivot\": [2.0, 12.0, 0.0], \"cubes\": [] },\n    { \"name\": \"head\", \"cubes\": [] }\n  ]\n}"
    },
    {
      "id": "pack-format-changes-example-1",
      "page": "pack-format-changes",
      "pageTitle": "Pack Format Changes",
      "section": "2. `conditions` Are The Long-Term Shape",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"id\": \"my_pack.line.family_storm\",\n  \"request\": \"question\",\n  \"conditions\": [\n    { \"type\": \"family\", \"relation\": \"child\" },\n    { \"type\": \"weather\", \"state\": \"thunder\" }\n  ],\n  \"text\": \"Storm nights are worse when you have children to worry about.\"\n}"
    },
    {
      "id": "pack-format-changes-example-2",
      "page": "pack-format-changes",
      "pageTitle": "Pack Format Changes",
      "section": "3. Quests Prefer Central Modules",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"schema\": \"villagerretaliation:quest/v2\",\n  \"id\": \"my_pack:bread_delivery\",\n  \"metadata\": {\n    \"title\": \"Bread Delivery\",\n    \"description\": \"Bring 16 bread to the village stores.\"\n  },\n  \"provider\": {\n    \"type\": \"villagerretaliation:villager\",\n    \"filters\": {\n      \"professions\": [\"minecraft:farmer\"]\n    }\n  },\n  \"entry_stage\": \"gather\",\n  \"stages\": [\n    {\n      \"id\": \"gather\",\n      \"objectives\": [\n        {\n          \"id\": \"bring_bread\",\n          \"type\": \"item_check\",\n          \"item\": \"minecraft:bread\",\n          \"count\": 16\n        }\n      ]\n    }\n  ]\n}"
    },
    {
      "id": "pack-format-changes-example-3",
      "page": "pack-format-changes",
      "pageTitle": "Pack Format Changes",
      "section": "4. Skill Trades Can Power Special Orders",
      "level": "Advanced",
      "language": "json",
      "code": "{\n\"request\": {\n  \"targetable\": true,\n  \"display_priority\": 20,\n  \"min_reputation\": \"respected\",\n  \"wait_days\": 2,\n  \"cooldown_days\": 3\n}\n}"
    },
    {
      "id": "pack-format-changes-example-4",
      "page": "pack-format-changes",
      "pageTitle": "Pack Format Changes",
      "section": "5. Builder Structures Can Include Modded Templates",
      "level": "Advanced",
      "language": "json",
      "code": "{\n  \"entries\": [\n    {\n      \"structure\": \"examplemod:village/houses/carpenter_house\",\n      \"category\": \"Modded Village\",\n      \"label\": \"Carpenter House\",\n      \"base_cost\": 18\n    }\n  ]\n}"
    }
  ],
  "packs": [
    {
      "id": "cinematic-gate-ambush",
      "title": "Cinematic Gate Ambush",
      "description": "Cinematic Gate Ambush This beta.13 example starts a persistent party owned scene from a quest response. Captain Mara is the fixed quest provider. Scout Tovin is an optional, operator rebindable second villager: bind that alias through the public actor binding API or /villagerreta",
      "files": [
        "data/gate_story/quests/gate_ambush.json",
        "data/gate_story/quest_encounters/gate_ambush.json",
        "data/gate_story/quest_scenes/gate_ambush.json",
        "pack.mcmeta",
        "README.md"
      ]
    },
    {
      "id": "custom-duel-kits",
      "title": "Custom Duel Kits",
      "description": "Custom Duel Kits Place this folder in a world's datapacks directory and run /reload. The duel examples:champion kit is then added to the duel loadout option list. A kit lives at data/ /duel kits/ .json. That path becomes its stable ID. Higher priority datapacks can replace a kit ",
      "files": [
        "data/duel_examples/duel_kits/champion.json",
        "pack.mcmeta",
        "README.md"
      ]
    },
    {
      "id": "dialogue-folder-template",
      "title": "Villager Retaliation Dialogue Folder Template",
      "description": "Villager Retaliation Dialogue Folder Template This beta.12 template gives pack developers a folder first starting point. Every dialogue request has one custom option and one response line with the text example. Replace ids, labels, filters, and text as your pack grows. The templa",
      "files": [
        "data/example_template/dialogue/en_us/example_template/closings/00_example.json",
        "data/example_template/dialogue/en_us/example_template/groups/example_group/lines/00_example.json",
        "data/example_template/dialogue/en_us/example_template/groups/example_group/lines/01_beta12_filters.json",
        "data/example_template/dialogue/en_us/example_template/lines/00_greeting.json",
        "data/example_template/dialogue/en_us/example_template/lines/01_question.json",
        "data/example_template/dialogue/en_us/example_template/lines/02_gift_preferences.json",
        "data/example_template/dialogue/en_us/example_template/lines/03_gift_advice_followup.json",
        "data/example_template/dialogue/en_us/example_template/lines/04_map_report.json",
        "data/example_template/dialogue/en_us/example_template/lines/05_story_hint_report.json",
        "data/example_template/dialogue/en_us/example_template/lines/06_combat_survival_report.json",
        "data/example_template/dialogue/en_us/example_template/lines/07_gear_report.json",
        "data/example_template/dialogue/en_us/example_template/lines/08_recruitment_followup.json",
        "data/example_template/dialogue/en_us/example_template/lines/09_cured_recognition.json",
        "data/example_template/dialogue/en_us/example_template/lines/10_village_event_report.json",
        "data/example_template/dialogue/en_us/example_template/lines/11_apology.json",
        "data/example_template/dialogue/en_us/example_template/lines/12_village_defense_report.json",
        "data/example_template/dialogue/en_us/example_template/lines/13_story.json",
        "data/example_template/dialogue/en_us/example_template/lines/14_share_story.json",
        "data/example_template/dialogue/en_us/example_template/lines/15_joke.json",
        "data/example_template/dialogue/en_us/example_template/lines/16_insult.json",
        "data/example_template/dialogue/en_us/example_template/messages/00_example.json",
        "data/example_template/dialogue/en_us/example_template/messages/01_beta12_filters.json",
        "data/example_template/dialogue/en_us/example_template/openings/00_example.json",
        "data/example_template/dialogue/en_us/example_template/options/00_greeting.json",
        "data/example_template/dialogue/en_us/example_template/options/01_question.json",
        "data/example_template/dialogue/en_us/example_template/options/02_gift_preferences.json",
        "data/example_template/dialogue/en_us/example_template/options/03_gift_advice_followup.json",
        "data/example_template/dialogue/en_us/example_template/options/04_map_report.json",
        "data/example_template/dialogue/en_us/example_template/options/05_story_hint_report.json",
        "data/example_template/dialogue/en_us/example_template/options/06_combat_survival_report.json",
        "data/example_template/dialogue/en_us/example_template/options/07_gear_report.json",
        "data/example_template/dialogue/en_us/example_template/options/08_recruitment_followup.json",
        "data/example_template/dialogue/en_us/example_template/options/09_cured_recognition.json",
        "data/example_template/dialogue/en_us/example_template/options/10_village_event_report.json",
        "data/example_template/dialogue/en_us/example_template/options/11_apology.json",
        "data/example_template/dialogue/en_us/example_template/options/12_village_defense_report.json",
        "data/example_template/dialogue/en_us/example_template/options/13_story.json",
        "data/example_template/dialogue/en_us/example_template/options/14_share_story.json",
        "data/example_template/dialogue/en_us/example_template/options/15_joke.json",
        "data/example_template/dialogue/en_us/example_template/options/16_insult.json",
        "data/example_template/dialogue/en_us/example_template/pacify/00_example_success.json",
        "data/example_template/dialogue/en_us/example_template/professions/farmer/closings/00_example.json",
        "data/example_template/dialogue/en_us/example_template/professions/farmer/lines/00_example.json",
        "data/example_template/dialogue/en_us/example_template/professions/farmer/messages/00_example.json",
        "data/example_template/dialogue/en_us/example_template/professions/farmer/openings/00_example.json",
        "data/example_template/dialogue/en_us/example_template/professions/farmer/options/00_example.json",
        "data/example_template/dialogue/en_us/example_template/professions/farmer/pacify/00_example_success.json",
        "data/example_template/dialogue/en_us/example_template/professions/farmer/share_stories/00_example_structure.json",
        "data/example_template/dialogue/en_us/example_template/professions/farmer/share_stories/01_example_biome.json",
        "data/example_template/dialogue/en_us/example_template/professions/unemployed/baby_share_stories/00_example_structure.json",
        "data/example_template/dialogue/en_us/example_template/professions/unemployed/baby_share_stories/01_example_biome.json",
        "data/example_template/forced_dialogue/example_template/00_container_theft.json",
        "data/example_template/forced_dialogue/example_template/01_retaliation_chat.json",
        "data/example_template/loot_table/villager/profession/farmer/common.json",
        "data/example_template/quests/first_steps.json",
        "data/example_template/story_biomes/00_example_biomes.json",
        "data/example_template/story_structures/00_example_structures.json",
        "data/villagerretaliation/gifts/example_template/00_gifts.json",
        "data/villagerretaliation/notifications/en_us/example_template/00_ambient.json",
        "data/villagerretaliation/notifications/en_us/example_template/01_quest.json",
        "data/villagerretaliation/pacification/example_template/00_payments.json",
        "data/villagerretaliation/profession_loot/example_template/00_loot.json",
        "data/villagerretaliation/villager_names/example_template_names.json",
        "pack.mcmeta",
        "README.md"
      ]
    },
    {
      "id": "repeatable-scene-run-id",
      "title": "Repeatable Scene Run ID",
      "description": "Repeatable Scene Run ID This small fixture proves that the stable operation id is reused inside one run and produces a new QUEST INSTANCE scene after a legitimate repeat. Two unrelated players starting their first run also receive different scenes. Use a librarian to accept Run t",
      "files": [
        "data/run_identity/quests/bell_again.json",
        "data/run_identity/quest_scenes/bell_scene.json",
        "pack.mcmeta",
        "README.md"
      ]
    },
    {
      "id": "sell-prices-example",
      "title": "Sell Price Examples",
      "description": "Sell Price Examples This pack puts the old beta.13 format and the new rates format side by side. It also shows item tags, discrete prices, component predicates, remaining durability, a built in override, and a disabled definition. The minecraft:logs example works with vanilla dat",
      "files": [
        "data/example/sell_prices/charcoal.json",
        "data/example/sell_prices/cocktails.json",
        "data/example/sell_prices/logs.json",
        "data/example/sell_prices/miners_star.json",
        "data/example/sell_prices/valuable_pickaxes.json",
        "data/villagerretaliation/sell_prices/coal.json",
        "data/villagerretaliation/sell_prices/rotten_flesh.json",
        "pack.mcmeta",
        "README.md"
      ]
    },
    {
      "id": "skill-trades-special-orders",
      "title": "Skill Trades and Special Orders",
      "description": "Skill Trades and Special Orders This example adds four custom skill trades under the trade examples namespace. It demonstrates: a normal farmer trade with skill based quality scaling a targetable, prepaid farmer Special Order a targetable cartographer order with multiple possible",
      "files": [
        "data/trade_examples/skill_trades/farming_orders.json",
        "data/trade_examples/skill_trades/profession_specialties.json",
        "pack.mcmeta",
        "README.md"
      ]
    }
  ]
};
