# Notification Triggers

Notification `trigger` values decide when a HUD notification or world-text line is eligible to appear. Each entry still has to pass its filters, chance, and weight after the trigger matches.

Use a stable `id` for anything you may translate or override later. The examples below are intentionally small, but the same fields from [Notifications JSON](Notifications.md) can be added to any trigger.

Enum-like fields are case-insensitive in code, but trigger strings should be written exactly as shown.

```json
{
  "notifications": [
    {
      "id": "my_pack.gift.liked",
      "trigger": "gift.liked",
      "text": "Good gift: {item}",
      "kind": "gift_liked",
      "color": "green"
    }
  ]
}
```

## Dropdown Examples

Each dropdown includes a minimal notification and an expanded version with useful filters, styling, or placeholders.

<details>
<summary><strong>gift.liked</strong></summary>

Fires for a liked gift HUD notice. Supports `{item}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.gift_liked.simple",
      "trigger": "gift.liked",
      "text": "Liked gift: {item}",
      "kind": "gift_liked",
      "color": "green"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.gift_liked.farmer",
      "trigger": "gift.liked",
      "text": "The farmer appreciated {item}.",
      "kind": "gift_liked",
      "professions": ["farmer"],
      "text_color": "#8FD694",
      "chat_color": "green",
      "weight": 20
    }
  ]
}
```

</details>

<details>
<summary><strong>gift.neutral</strong></summary>

Fires for a neutral gift HUD notice. Supports `{item}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.gift_neutral.simple",
      "trigger": "gift.neutral",
      "text": "Accepted gift: {item}",
      "kind": "gift_neutral",
      "color": "gray"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.gift_neutral.low_chance",
      "trigger": "gift.neutral",
      "text": "{item} was accepted, if not celebrated.",
      "kind": "gift_neutral",
      "color": "#C8C8C8",
      "chance": 0.6,
      "weight": 12
    }
  ]
}
```

</details>

<details>
<summary><strong>gift.disliked</strong></summary>

Fires for a disliked or hated gift HUD notice. Supports `{item}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.gift_disliked.simple",
      "trigger": "gift.disliked",
      "text": "Disliked gift: {item}",
      "kind": "gift_disliked",
      "color": "red"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.gift_disliked.hostile",
      "trigger": "gift.disliked",
      "text": "{item} made things worse.",
      "kind": "gift_disliked",
      "reputation_levels": ["hostile", "despised", "feared"],
      "color": "#FF6B6B",
      "weight": 22
    }
  ]
}
```

</details>

<details>
<summary><strong>gift.received_item</strong></summary>

Fires when a villager gives the player an item. Supports `{item}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.gift_received.simple",
      "trigger": "gift.received_item",
      "text": "Received item: {item}",
      "kind": "received_item",
      "color": "gold"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.gift_received.royalty",
      "trigger": "gift.received_item",
      "text": "{item}, given with real trust.",
      "kind": "received_item",
      "reputation_levels": ["revered", "royalty"],
      "text_color": "#FFD166",
      "chat_color": "gold",
      "weight": 18
    }
  ]
}
```

</details>

<details>
<summary><strong>dialogue.map.found</strong></summary>

Fires when a cartographer map destination is found. Supports `{target}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.map_found.simple",
      "trigger": "dialogue.map.found",
      "text": "Found map destination: {target}",
      "kind": "map_discovery",
      "color": "aqua"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.map_found.cartographer",
      "trigger": "dialogue.map.found",
      "text": "The map was right: {target}.",
      "kind": "map_discovery",
      "professions": ["cartographer"],
      "text_color": "#82DDF0",
      "weight": 20
    }
  ]
}
```

</details>

<details>
<summary><strong>dialogue.rumor.found</strong></summary>

Fires when a rumored structure or biome is found. Supports `{target}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.rumor_found.simple",
      "trigger": "dialogue.rumor.found",
      "text": "Found rumored place: {target}",
      "kind": "map_discovery",
      "color": "aqua"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.rumor_found.rare",
      "trigger": "dialogue.rumor.found",
      "text": "The rumor had a real place behind it: {target}.",
      "kind": "map_discovery",
      "text_color": "#BDE0FE",
      "chat_color": "aqua",
      "weight": 18
    }
  ]
}
```

</details>

<details>
<summary><strong>recruitment.follow_start</strong></summary>

Fires when a villager starts following the player. Commonly supports `{villager}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.follow_start.simple",
      "trigger": "recruitment.follow_start",
      "text": "{villager} is following you.",
      "kind": "villager_following",
      "color": "green"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.follow_start.trusted",
      "trigger": "recruitment.follow_start",
      "text": "{villager} trusts you enough to come along.",
      "kind": "villager_following",
      "reputation_levels": ["trusted", "respected", "revered", "royalty"],
      "color": "#8FD694",
      "weight": 20
    }
  ]
}
```

</details>

<details>
<summary><strong>recruitment.follow_stop</strong></summary>

Fires when a villager stops following the player. Commonly supports `{villager}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.follow_stop.simple",
      "trigger": "recruitment.follow_stop",
      "text": "{villager} is no longer following you.",
      "kind": "villager_dismissed",
      "color": "gray"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.follow_stop.safe",
      "trigger": "recruitment.follow_stop",
      "text": "{villager} heads home with the road still underfoot.",
      "kind": "villager_dismissed",
      "color": "#D0D5DD",
      "weight": 18
    }
  ]
}
```

</details>

<details>
<summary><strong>recruitment.hired</strong></summary>

Fires when a villager is hired. Commonly supports `{villager}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.hired.simple",
      "trigger": "recruitment.hired",
      "text": "{villager} hired.",
      "kind": "villager_hired",
      "color": "green"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.hired.high_rep",
      "trigger": "recruitment.hired",
      "text": "{villager} joins you with earned confidence.",
      "kind": "villager_hired",
      "reputation_levels": ["respected", "revered", "royalty"],
      "text_color": "#9BE7A1",
      "weight": 20
    }
  ]
}
```

</details>

<details>
<summary><strong>recruitment.fired</strong></summary>

Fires when a hired villager is fired. Commonly supports `{villager}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.fired.simple",
      "trigger": "recruitment.fired",
      "text": "{villager} fired.",
      "kind": "villager_fired",
      "color": "gray"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.fired.sour",
      "trigger": "recruitment.fired",
      "text": "{villager} leaves your service with little ceremony.",
      "kind": "villager_fired",
      "reputation_levels": ["suspicious", "hostile", "despised", "feared"],
      "color": "#C8C8C8",
      "weight": 16
    }
  ]
}
```

</details>

<details>
<summary><strong>recruitment.follower_death</strong></summary>

Fires when a following villager dies. Commonly supports `{villager}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.follower_death.simple",
      "trigger": "recruitment.follower_death",
      "text": "{villager} died while following you.",
      "kind": "villager_death",
      "color": "red"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.follower_death.weighted",
      "trigger": "recruitment.follower_death",
      "text": "{villager} did not make it home.",
      "kind": "villager_death",
      "text_color": "#FF6B6B",
      "chat_color": "dark_red",
      "weight": 25
    }
  ]
}
```

</details>

<details>
<summary><strong>recruitment.hired_death</strong></summary>

Fires when a hired villager dies. Commonly supports `{villager}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.hired_death.simple",
      "trigger": "recruitment.hired_death",
      "text": "{villager} died while hired by you.",
      "kind": "villager_death",
      "color": "red"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.hired_death.severe",
      "trigger": "recruitment.hired_death",
      "text": "{villager} trusted your lead and paid for it.",
      "kind": "villager_death",
      "text_color": "#F97066",
      "chat_color": "dark_red",
      "weight": 25
    }
  ]
}
```

</details>

<details>
<summary><strong>recruitment.betrayed_follower_death</strong></summary>

Fires when a follower dies after betrayal. Commonly supports `{villager}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.betrayed_follower_death.simple",
      "trigger": "recruitment.betrayed_follower_death",
      "text": "{villager} died after you broke their trust.",
      "kind": "villager_death",
      "color": "dark_red"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.betrayed_follower_death.feared",
      "trigger": "recruitment.betrayed_follower_death",
      "text": "{villager_possessive} death will not be treated as an accident.",
      "kind": "villager_death",
      "reputation_levels": ["hostile", "despised", "feared"],
      "text_color": "#D92D20",
      "chat_color": "dark_red",
      "weight": 30
    }
  ]
}
```

</details>

<details>
<summary><strong>reputation.tier.royalty.improved</strong></summary>

Fires when the player rises into Royalty with a villager.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.royalty_improved.simple",
      "trigger": "reputation.tier.royalty.improved",
      "text": "{villager} now treats you like royalty.",
      "color": "gold"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.royalty_improved.rare",
      "trigger": "reputation.tier.royalty.improved",
      "text": "{villager} would trust you with the village bell.",
      "reputation_levels": ["royalty"],
      "text_color": "#FFD166",
      "weight": 30
    }
  ]
}
```

</details>

<details>
<summary><strong>reputation.tier.royalty.worsened</strong></summary>

Fires when the player falls out of Royalty with a villager.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.royalty_worsened.simple",
      "trigger": "reputation.tier.royalty.worsened",
      "text": "{villager} no longer sees you as royalty.",
      "color": "yellow"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.royalty_worsened.personal",
      "trigger": "reputation.tier.royalty.worsened",
      "text": "{villager_possessive} highest trust in you has cracked.",
      "text_color": "#FEC84B",
      "chat_color": "gold",
      "weight": 25
    }
  ]
}
```

</details>

<details>
<summary><strong>reputation.tier.revered.improved</strong></summary>

Fires when the player rises into Revered with a villager.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.revered_improved.simple",
      "trigger": "reputation.tier.revered.improved",
      "text": "{villager} now reveres you.",
      "color": "gold"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.revered_improved.warm",
      "trigger": "reputation.tier.revered.improved",
      "text": "{villager_possessive} respect for you has become something lasting.",
      "reputation_levels": ["revered"],
      "text_color": "#FDE68A",
      "weight": 24
    }
  ]
}
```

</details>

<details>
<summary><strong>reputation.tier.revered.worsened</strong></summary>

Fires when the player falls out of Revered with a villager.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.revered_worsened.simple",
      "trigger": "reputation.tier.revered.worsened",
      "text": "{villager_possessive} reverence for you has faded.",
      "color": "yellow"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.revered_worsened.quiet",
      "trigger": "reputation.tier.revered.worsened",
      "text": "{villager} still knows you, but not with the same certainty.",
      "text_color": "#FEC84B",
      "weight": 20
    }
  ]
}
```

</details>

<details>
<summary><strong>reputation.tier.respected.improved</strong></summary>

Fires when the player rises into Respected with a villager.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.respected_improved.simple",
      "trigger": "reputation.tier.respected.improved",
      "text": "You feel yourself earning {villager_possessive} deep respect.",
      "color": "green"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.respected_improved.trade",
      "trigger": "reputation.tier.respected.improved",
      "text": "{villager} now speaks to you like a reliable neighbor.",
      "reputation_levels": ["respected"],
      "text_color": "#86EFAC",
      "weight": 22
    }
  ]
}
```

</details>

<details>
<summary><strong>reputation.tier.respected.worsened</strong></summary>

Fires when the player falls out of Respected with a villager.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.respected_worsened.simple",
      "trigger": "reputation.tier.respected.worsened",
      "text": "You feel {villager_possessive} deep respect for you slipping away.",
      "color": "yellow"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.respected_worsened.caution",
      "trigger": "reputation.tier.respected.worsened",
      "text": "{villager} is measuring your choices more carefully now.",
      "text_color": "#FDE047",
      "weight": 20
    }
  ]
}
```

</details>

<details>
<summary><strong>reputation.tier.trusted.improved</strong></summary>

Fires when the player rises into Trusted with a villager.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.trusted_improved.simple",
      "trigger": "reputation.tier.trusted.improved",
      "text": "You feel yourself gaining {villager_possessive} trust.",
      "color": "green"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.trusted_improved.first_step",
      "trigger": "reputation.tier.trusted.improved",
      "text": "{villager} has started treating you like someone who may stay.",
      "reputation_levels": ["trusted"],
      "text_color": "#9BE7A1",
      "weight": 22
    }
  ]
}
```

</details>

<details>
<summary><strong>reputation.tier.trusted.worsened</strong></summary>

Fires when the player falls out of Trusted with a villager.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.trusted_worsened.simple",
      "trigger": "reputation.tier.trusted.worsened",
      "text": "You feel {villager_possessive} trust in you weaken.",
      "color": "yellow"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.trusted_worsened.warning",
      "trigger": "reputation.tier.trusted.worsened",
      "text": "{villager} is no longer sure you belong close.",
      "text_color": "#FEC84B",
      "weight": 18
    }
  ]
}
```

</details>

<details>
<summary><strong>reputation.tier.neutral.improved</strong></summary>

Fires when the player improves back into Neutral.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.neutral_improved.simple",
      "trigger": "reputation.tier.neutral.improved",
      "text": "{villager} seems to feel neutral toward you again.",
      "color": "gray"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.neutral_improved.second_chance",
      "trigger": "reputation.tier.neutral.improved",
      "text": "{villager} is willing to start from even ground.",
      "reputation_levels": ["neutral"],
      "text_color": "#D0D5DD",
      "weight": 20
    }
  ]
}
```

</details>

<details>
<summary><strong>reputation.tier.neutral.worsened</strong></summary>

Fires when the player worsens into Neutral from a positive tier.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.neutral_worsened.simple",
      "trigger": "reputation.tier.neutral.worsened",
      "text": "{villager} seems to feel neutral toward you again.",
      "color": "gray"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.neutral_worsened.lost_warmth",
      "trigger": "reputation.tier.neutral.worsened",
      "text": "{villager_possessive} warmth has cooled into caution.",
      "text_color": "#D0D5DD",
      "weight": 18
    }
  ]
}
```

</details>

<details>
<summary><strong>reputation.tier.suspicious.improved</strong></summary>

Fires when the player improves out of a worse tier into Suspicious.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.suspicious_improved.simple",
      "trigger": "reputation.tier.suspicious.improved",
      "text": "{villager} seems less suspicious of you.",
      "color": "yellow"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.suspicious_improved.softened",
      "trigger": "reputation.tier.suspicious.improved",
      "text": "{villager} still watches you, but not like a threat at the door.",
      "reputation_levels": ["suspicious"],
      "text_color": "#FDE047",
      "weight": 20
    }
  ]
}
```

</details>

<details>
<summary><strong>reputation.tier.suspicious.worsened</strong></summary>

Fires when the player worsens into Suspicious.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.suspicious_worsened.simple",
      "trigger": "reputation.tier.suspicious.worsened",
      "text": "You feel {villager} becoming suspicious of you.",
      "color": "yellow"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.suspicious_worsened.trade",
      "trigger": "reputation.tier.suspicious.worsened",
      "text": "{villager} has started counting your mistakes.",
      "reputation_levels": ["suspicious"],
      "text_color": "#FEC84B",
      "weight": 20
    }
  ]
}
```

</details>

<details>
<summary><strong>reputation.tier.hostile.improved</strong></summary>

Fires when the player improves from worse reputation into Hostile.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.hostile_improved.simple",
      "trigger": "reputation.tier.hostile.improved",
      "text": "{villager} no longer sees you as completely unforgivable.",
      "color": "red"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.hostile_improved.edge",
      "trigger": "reputation.tier.hostile.improved",
      "text": "{villager} is angry, but no longer past all words.",
      "reputation_levels": ["hostile"],
      "text_color": "#FDA29B",
      "weight": 20
    }
  ]
}
```

</details>

<details>
<summary><strong>reputation.tier.hostile.worsened</strong></summary>

Fires when the player worsens into Hostile.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.hostile_worsened.simple",
      "trigger": "reputation.tier.hostile.worsened",
      "text": "You feel {villager} becoming hostile toward you.",
      "color": "red"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.hostile_worsened.alert",
      "trigger": "reputation.tier.hostile.worsened",
      "text": "{villager} has crossed from distrust into anger.",
      "reputation_levels": ["hostile"],
      "text_color": "#FF6B6B",
      "chat_color": "red",
      "weight": 25
    }
  ]
}
```

</details>

<details>
<summary><strong>reputation.tier.despised.improved</strong></summary>

Fires when the player improves from Feared into Despised.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.despised_improved.simple",
      "trigger": "reputation.tier.despised.improved",
      "text": "{villager_possessive} hatred for you has softened.",
      "color": "red"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.despised_improved.not_safe",
      "trigger": "reputation.tier.despised.improved",
      "text": "{villager} despises you, but the worst fear has loosened.",
      "reputation_levels": ["despised"],
      "text_color": "#F97066",
      "weight": 22
    }
  ]
}
```

</details>

<details>
<summary><strong>reputation.tier.despised.worsened</strong></summary>

Fires when the player worsens into Despised.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.despised_worsened.simple",
      "trigger": "reputation.tier.despised.worsened",
      "text": "You feel {villager} come to despise you.",
      "color": "dark_red"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.despised_worsened.danger",
      "trigger": "reputation.tier.despised.worsened",
      "text": "{villager} has learned to hate your shadow.",
      "reputation_levels": ["despised"],
      "text_color": "#D92D20",
      "chat_color": "dark_red",
      "weight": 26
    }
  ]
}
```

</details>

<details>
<summary><strong>reputation.tier.feared.improved</strong></summary>

Fires when the player improves out of Feared.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.feared_improved.simple",
      "trigger": "reputation.tier.feared.improved",
      "text": "{villager} no longer fears you completely.",
      "color": "dark_red"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.feared_improved.first_breath",
      "trigger": "reputation.tier.feared.improved",
      "text": "{villager} can breathe a little easier around you now.",
      "text_color": "#F97066",
      "weight": 24
    }
  ]
}
```

</details>

<details>
<summary><strong>reputation.tier.feared.worsened</strong></summary>

Fires when the player worsens into Feared.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.feared_worsened.simple",
      "trigger": "reputation.tier.feared.worsened",
      "text": "{villager} now fears you.",
      "color": "dark_red"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.feared_worsened.severe",
      "trigger": "reputation.tier.feared.worsened",
      "text": "{villager} has learned to fear what you might do next.",
      "reputation_levels": ["feared"],
      "text_color": "#B42318",
      "chat_color": "dark_red",
      "weight": 30
    }
  ]
}
```

</details>

<details>
<summary><strong>ambient.murmur</strong></summary>

Fires for ambient world text above villagers.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.ambient_murmur.simple",
      "trigger": "ambient.murmur",
      "text": "Quiet day.",
      "world_text_kind": "murmur"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.ambient_murmur.trusted_farmer",
      "trigger": "ambient.murmur",
      "text": "Good harvest follows good neighbors",
      "world_text_kind": "murmur",
      "professions": ["farmer"],
      "reputation_levels": ["trusted", "respected", "revered", "royalty"],
      "chance": 0.45,
      "weight": 24
    }
  ]
}
```

</details>

<details>
<summary><strong>ambient.sleep_breathing</strong></summary>

Fires for sleeping ambient breathing text.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.sleep_breathing.simple",
      "trigger": "ambient.sleep_breathing",
      "text": "Zzz",
      "world_text_kind": "sleep"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.sleep_breathing.baby",
      "trigger": "ambient.sleep_breathing",
      "text": "zzz...",
      "world_text_kind": "sleep",
      "show_for_adults": false,
      "show_for_babies": true,
      "chance": 0.3,
      "weight": 16
    }
  ]
}
```

</details>

<details>
<summary><strong>ambient.sleep_murmur</strong></summary>

Fires for occasional sleeping murmurs.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.sleep_murmur.simple",
      "trigger": "ambient.sleep_murmur",
      "text": "Mm...",
      "world_text_kind": "sleep"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.sleep_murmur.hostile",
      "trigger": "ambient.sleep_murmur",
      "text": "No... stay back...",
      "world_text_kind": "sleep",
      "reputation_levels": ["hostile", "despised", "feared"],
      "chance": 0.2,
      "weight": 18
    }
  ]
}
```

</details>

<details>
<summary><strong>trade.completed</strong></summary>

Fires after a completed trade.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.trade_completed.simple",
      "trigger": "trade.completed",
      "text": "Fair trade.",
      "world_text_kind": "trade",
      "color": "green"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.trade_completed.librarian",
      "trigger": "trade.completed",
      "text": "A good exchange keeps shelves and pockets honest.",
      "world_text_kind": "trade",
      "professions": ["librarian"],
      "reputation_levels": ["trusted", "respected", "revered", "royalty"],
      "weight": 20
    }
  ]
}
```

</details>

<details>
<summary><strong>trade.refused</strong></summary>

Fires when trade is refused.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.trade_refused.simple",
      "trigger": "trade.refused",
      "text": "Not today.",
      "world_text_kind": "negative",
      "color": "red"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.trade_refused.hostile",
      "trigger": "trade.refused",
      "text": "Trust is part of the price, and you are short.",
      "world_text_kind": "negative",
      "reputation_levels": ["hostile", "despised", "feared"],
      "text_color": "#FF6B6B",
      "weight": 24
    }
  ]
}
```

</details>

<details>
<summary><strong>dialogue.cooldown</strong></summary>

Fires when dialogue is on cooldown.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.dialogue_cooldown.simple",
      "trigger": "dialogue.cooldown",
      "text": "Give me a moment.",
      "world_text_kind": "dialogue"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.dialogue_cooldown.rude",
      "trigger": "dialogue.cooldown",
      "text": "I just answered you.",
      "world_text_kind": "negative",
      "reputation_levels": ["suspicious", "hostile", "despised", "feared"],
      "chance": 0.8,
      "weight": 18
    }
  ]
}
```

</details>

<details>
<summary><strong>dialogue.greeting</strong></summary>

Fires around greeting dialogue.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.dialogue_greeting.simple",
      "trigger": "dialogue.greeting",
      "text": "Hello.",
      "world_text_kind": "dialogue"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.dialogue_greeting.friendly",
      "trigger": "dialogue.greeting",
      "text": "Good to see you.",
      "world_text_kind": "positive",
      "reputation_levels": ["trusted", "respected", "revered", "royalty"],
      "weight": 20
    }
  ]
}
```

</details>

<details>
<summary><strong>dialogue.question</strong></summary>

Fires around question dialogue.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.dialogue_question.simple",
      "trigger": "dialogue.question",
      "text": "Hm.",
      "world_text_kind": "dialogue"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.dialogue_question.item",
      "trigger": "dialogue.question",
      "text": "Careful with {held_item}.",
      "world_text_kind": "alert",
      "player_items": ["#minecraft:swords"],
      "player_item_slots": ["main_hand"],
      "weight": 20
    }
  ]
}
```

</details>

<details>
<summary><strong>dialogue.joke.positive</strong></summary>

Fires around a positive joke result.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.joke_positive.simple",
      "trigger": "dialogue.joke.positive",
      "text": "Heh.",
      "world_text_kind": "positive"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.joke_positive.friendly",
      "trigger": "dialogue.joke.positive",
      "text": "That one was almost worth repeating.",
      "world_text_kind": "positive",
      "reputation_levels": ["trusted", "respected", "revered", "royalty"],
      "chance": 0.7,
      "weight": 18
    }
  ]
}
```

</details>

<details>
<summary><strong>dialogue.insult.negative</strong></summary>

Fires around a negative insult result.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.insult_negative.simple",
      "trigger": "dialogue.insult.negative",
      "text": "Watch it.",
      "world_text_kind": "negative"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.insult_negative.hostile",
      "trigger": "dialogue.insult.negative",
      "text": "You are running out of soft answers.",
      "world_text_kind": "alert",
      "reputation_levels": ["hostile", "despised", "feared"],
      "text_color": "#FF6B6B",
      "weight": 24
    }
  ]
}
```

</details>

<details>
<summary><strong>gift.high_reputation</strong></summary>

Fires as world text around high-reputation gift behavior.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.gift_high_reputation.simple",
      "trigger": "gift.high_reputation",
      "text": "For you.",
      "world_text_kind": "positive",
      "color": "gold"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.gift_high_reputation.royalty",
      "trigger": "gift.high_reputation",
      "text": "Good neighbors share what they can.",
      "world_text_kind": "positive",
      "reputation_levels": ["revered", "royalty"],
      "chance": 0.75,
      "weight": 20
    }
  ]
}
```

</details>

<details>
<summary><strong>gift.world.liked</strong></summary>

Fires as world text after a liked gift. Supports `{item}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.gift_world_liked.simple",
      "trigger": "gift.world.liked",
      "text": "Thank you for {item}.",
      "world_text_kind": "positive"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.gift_world_liked.profession",
      "trigger": "gift.world.liked",
      "text": "{item} will be useful at work.",
      "world_text_kind": "positive",
      "professions": ["toolsmith", "weaponsmith", "armorer"],
      "weight": 22
    }
  ]
}
```

</details>

<details>
<summary><strong>gift.world.neutral</strong></summary>

Fires as world text after a neutral gift. Supports `{item}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.gift_world_neutral.simple",
      "trigger": "gift.world.neutral",
      "text": "I can take {item}.",
      "world_text_kind": "dialogue"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.gift_world_neutral.cautious",
      "trigger": "gift.world.neutral",
      "text": "{item}. Hm. I suppose it has a use.",
      "world_text_kind": "dialogue",
      "reputation_levels": ["suspicious", "neutral", "trusted"],
      "chance": 0.8,
      "weight": 16
    }
  ]
}
```

</details>

<details>
<summary><strong>gift.world.disliked</strong></summary>

Fires as world text after a disliked or hated gift. Supports `{item}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.gift_world_disliked.simple",
      "trigger": "gift.world.disliked",
      "text": "Not {item}.",
      "world_text_kind": "negative"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.gift_world_disliked.hostile",
      "trigger": "gift.world.disliked",
      "text": "Do not bring {item} here again.",
      "world_text_kind": "negative",
      "reputation_levels": ["hostile", "despised", "feared"],
      "text_color": "#FF6B6B",
      "weight": 24
    }
  ]
}
```

</details>

<details>
<summary><strong>combat.player_killed</strong></summary>

Fires as world text above the villager or wandering trader credited with killing a player. Supports `{player}`, `{victim}`, `{villager}`, `{villager_name}`, `{villager_kind}`, and `{profession}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.combat_player_killed.simple",
      "trigger": "combat.player_killed",
      "text": "Stay down, {player}",
      "world_text_kind": "alert",
      "color": "red"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.combat_player_killed.profession",
      "trigger": "combat.player_killed",
      "text": "{villager_name} the {profession} ended {victim}.",
      "world_text_kind": "alert",
      "professions": ["weaponsmith", "fletcher"],
      "reputation_levels": ["hostile", "despised", "feared"],
      "text_color": "#F97066",
      "weight": 25
    }
  ]
}
```

</details>

<details>
<summary><strong>alert.player_attacked_villager</strong></summary>

Fires as an immediate response from a damaged villager when the attacker is a player. Supports `{player}`, `{attacker}`, `{villager}`, `{villager_name}`, `{villager_kind}`, and `{profession}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.alert_player_attacked.simple",
      "trigger": "alert.player_attacked_villager",
      "text": "Back off, {player}!",
      "world_text_kind": "alert",
      "color": "red"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.alert_player_attacked.feared",
      "trigger": "alert.player_attacked_villager",
      "text": "{villager_name} expected this from you, {player}.",
      "world_text_kind": "alert",
      "reputation_levels": ["despised", "feared"],
      "text_color": "#F97066",
      "weight": 24
    }
  ]
}
```

</details>

<details>
<summary><strong>alert.villager_damaged</strong></summary>

Fires when a villager is damaged and no more specific alert wins. Supports `{attacker}`, `{villager}`, `{villager_name}`, `{villager_kind}`, and `{profession}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.alert_villager_damaged.simple",
      "trigger": "alert.villager_damaged",
      "text": "Help!",
      "world_text_kind": "alert",
      "color": "red"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.alert_villager_damaged.profession",
      "trigger": "alert.villager_damaged",
      "text": "{villager_name} the {profession} is hurt!",
      "world_text_kind": "alert",
      "professions": ["farmer", "librarian", "cleric"],
      "text_color": "#FF6B6B",
      "weight": 22
    }
  ]
}
```

</details>

<details>
<summary><strong>alert.witness_attack.player</strong></summary>

Fires when a villager witnesses a player attack. Supports `{player}`, `{attacker}`, `{villager}`, `{villager_name}`, `{villager_kind}`, and `{profession}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.witness_attack_player.simple",
      "trigger": "alert.witness_attack.player",
      "text": "I saw that, {player}!",
      "world_text_kind": "alert",
      "color": "red"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.witness_attack_player.trusted",
      "trigger": "alert.witness_attack.player",
      "text": "{player}, I thought better of you.",
      "world_text_kind": "alert",
      "reputation_levels": ["trusted", "respected", "revered", "royalty"],
      "text_color": "#FEC84B",
      "weight": 22
    }
  ]
}
```

</details>

<details>
<summary><strong>alert.witness_attack</strong></summary>

Fires when a villager witnesses a non-player attack. Supports `{attacker}`, `{villager}`, `{villager_name}`, `{villager_kind}`, and `{profession}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.witness_attack.simple",
      "trigger": "alert.witness_attack",
      "text": "Danger!",
      "world_text_kind": "alert",
      "color": "red"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.witness_attack.night",
      "trigger": "alert.witness_attack",
      "text": "{attacker} is too close to the village!",
      "world_text_kind": "alert",
      "text_color": "#FF6B6B",
      "chance": 0.9,
      "weight": 20
    }
  ]
}
```

</details>

<details>
<summary><strong>alert.witness_death.player</strong></summary>

Fires when a villager witnesses a player-caused death. Supports `{player}`, `{attacker}`, `{villager}`, `{villager_name}`, `{villager_kind}`, and `{profession}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.witness_death_player.simple",
      "trigger": "alert.witness_death.player",
      "text": "{player}, what have you done?",
      "world_text_kind": "alert",
      "color": "dark_red"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.witness_death_player.severe",
      "trigger": "alert.witness_death.player",
      "text": "{villager_name} will remember what {player} did here.",
      "world_text_kind": "alert",
      "text_color": "#D92D20",
      "chat_color": "dark_red",
      "weight": 28
    }
  ]
}
```

</details>

<details>
<summary><strong>alert.witness_death</strong></summary>

Fires when a villager witnesses a non-player-caused death. Supports `{attacker}`, `{villager}`, `{villager_name}`, `{villager_kind}`, and `{profession}`.

Simple:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.witness_death.simple",
      "trigger": "alert.witness_death",
      "text": "No!",
      "world_text_kind": "alert",
      "color": "dark_red"
    }
  ]
}
```

Expanded:

```json
{
  "notifications": [
    {
      "id": "my_pack.trigger.witness_death.aftermath",
      "trigger": "alert.witness_death",
      "text": "{villager_name} saw death come too close.",
      "world_text_kind": "alert",
      "text_color": "#F97066",
      "chance": 0.85,
      "weight": 22
    }
  ]
}
```

</details>

