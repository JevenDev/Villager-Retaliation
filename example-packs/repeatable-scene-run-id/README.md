# Repeatable Scene Run ID

This small fixture proves that the stable `operation_id` is reused inside one run and produces a new `QUEST_INSTANCE` scene after a legitimate repeat. Two unrelated players starting their first run also receive different scenes.

The localized quest and private scene live together under `data/run_identity/quests/runtime_regressions/bell_again/`, so a layer either accepts the owner bundle or retains the lower valid bundle.

Use a librarian to accept **Run the Bell Again**. The entry action launches `run_identity:bell_scene` twice with the same operation ID. Only one scene is created. Complete and restart the repeatable quest to create a scene under the next definitive run ID.
