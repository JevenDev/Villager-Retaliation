# Forced Dialogue

Forced dialogue is for event-driven villager reactions that should interrupt the normal flow. Use it for crimes, confrontations, authored quest interruptions, and chat barks.

## Path

```text
data/<namespace>/forced_dialogue/<file>.json
```

## Output Modes

| Mode | Use it for |
| --- | --- |
| `forced_dialogue` | Locked scenes with player response buttons |
| `chat` | One-shot nearby villager speech without opening a conversation |

## Example: Locked Theft Scene

```json
{
  "entries": [
    {
      "id": "my_pack.container_theft.warning",
      "trigger": "container_theft",
      "output": {
        "mode": "forced_dialogue"
      },
      "line": "Hands off that {container}. I saw what you took.",
      "witness_radius": 10,
      "requires_line_of_sight": true,
      "initiate_dialogue": true,
      "options": [
        {
          "id": "apologize",
          "label": "Apologize",
          "response": "Then prove it next time before the village has to ask.",
          "reputation": 2,
          "end_conversation": true
        },
        {
          "id": "talk_back",
          "label": "Talk back",
          "response": "Wrong answer.",
          "reputation": -6,
          "aggro": true,
          "end_conversation": true
        }
      ]
    }
  ]
}
```

## Example: Payment Option

Forced-dialogue options can take items directly from the player.

```json
{
  "id": "offer_payment",
  "label": "Offer payment",
  "response": "Payment does not make it yours, but it can make things right.",
  "take_items": {
    "items": ["minecraft:emerald"],
    "count": 8,
    "destination": "villager_inventory",
    "failure_response": "Do not offer emeralds you do not have."
  },
  "end_conversation": true
}
```

## Example: Chat Bark

```json
{
  "entries": [
    {
      "id": "my_pack.retaliation.chat",
      "trigger": "retaliation_started",
      "output": {
        "mode": "chat",
        "radius": 18
      },
      "lines": [
        "You picked the wrong village.",
        "Run while you still remember how."
      ],
      "chance": 0.5
    }
  ]
}
```

## When To Use Forced Dialogue Instead Of Normal Dialogue

Use forced dialogue when:

- the villager should react immediately to an event
- the player must answer before returning to normal interaction
- you need event-specific buttons such as apology, payment, or escalation
- you want a reactive bark tied to a trigger instead of a Talk menu request

Use normal [Dialogue](Dialogue.md) when the player chooses to ask something on purpose.
