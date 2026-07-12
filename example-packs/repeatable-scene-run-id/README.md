# Repeatable Scene Run ID

This small fixture proves that the stable `operation_id` is reused inside one run and produces a new `QUEST_INSTANCE` scene after a legitimate repeat. Two unrelated players starting their first run also receive different scenes.

Use a librarian to accept **Run the Bell Again**. The entry action launches `run_identity:bell_scene` twice with the same operation ID; only one scene is created. Complete and restart the repeatable quest to create a scene under the next definitive run ID.
