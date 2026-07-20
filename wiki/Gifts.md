# Gifts

Gift files define two things:

- which items villagers like or dislike
- which items high-trust villagers may give back as rewards

## Path

```text
data/villagerretaliation/gifts/<file>.json
```

## Minimal Preference Example

```json
{
  "preferences": [
    {
      "id": "my_pack.librarian.favorite_book",
      "professions": ["minecraft:librarian"],
      "reaction": "loved",
      "items": ["minecraft:enchanted_book", "minecraft:name_tag"],
      "response_key": "my_pack.gift.librarian.favorite_book",
      "priority": 20
    }
  ]
}
```

## Minimal Reward Example

```json
{
  "rewards": [
    {
      "id": "my_pack.librarian.reward",
      "professions": ["minecraft:librarian"],
      "reputation_levels": ["revered", "royalty"],
      "item": "minecraft:book",
      "min_count": 2,
      "max_count": 5,
      "weight": 10
    }
  ]
}
```

## Reactions

Current reaction values:

```text
loved
liked
neutral
disliked
hated
```

Pick the reaction first, then tune specifics with `reputation_per_item`, profession filters, and `priority`.

## Reputation Limits

Positive gift reputation is tracked separately for each player-villager relationship. By default, the first stack of an item earns its full value, additional stacks of the same item on that Minecraft day earn 10%, and total positive gift reputation is capped at 120 per day. Disliked and hated gift penalties are not reduced or counted against that cap.

Servers can tune this behavior with `gifts.repeatedGiftReputationMultiplier`, `gifts.dailyGiftReputationCap`, and `gifts.giftRequestCooldownTicks`. Item repetition is based on the item ID, so changing stack components does not create a new first gift.

## Example: Shared Response Text

Gift files stay language-neutral by using a response key:

```json
{
  "id": "my_pack.gift_message.favorite_book",
  "key": "my_pack.gift.librarian.favorite_book",
  "text": "{gift_item}? This belongs near a reading lamp, not forgotten in a chest."
}
```

That message lives in normal dialogue under `messages/`.

## Add, Override, Remove

- Add a new file to add more gift rules.
- Reuse an existing `id` to replace one rule.
- Use `"remove": true` with an `id` to remove one rule.
- Use top-level `replace: true` only when you want to rebuild the entire gift table.

## Good Uses

- profession-specific favorites
- one universally hated prank gift
- special rewards for high-trust villagers
- modded items or item tags as custom gift content
