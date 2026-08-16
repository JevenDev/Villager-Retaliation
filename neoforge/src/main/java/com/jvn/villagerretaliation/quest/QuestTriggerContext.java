package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Immutable event snapshot used while evaluating and executing a quest trigger.
 * It intentionally stores registry ids and scalar values rather than retaining
 * live entities, block states, item stacks, or merchant offers.
 */
public record QuestTriggerContext(
        QuestDefinition.TriggerEvent event,
        long gameTime,
        String stage,
        Map<String, String> payload,
        DialogueContext dialogueContext
) {
    public QuestTriggerContext {
        stage = stage == null ? "" : stage;
        payload = freeze(payload);
    }

    public static QuestTriggerContext of(
            DialogueContext context,
            QuestDefinition.TriggerEvent event,
            long gameTime,
            String stage,
            QuestObjectiveEvent objectiveEvent) {
        return new QuestTriggerContext(event, gameTime, stage, snapshot(event, objectiveEvent), context);
    }

    public QuestTriggerContext withDialogueContext(DialogueContext context) {
        return new QuestTriggerContext(this.event, this.gameTime, this.stage, this.payload, context);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Event", QuestTriggerRegistry.canonicalEventId(this.event));
        tag.putLong("GameTime", this.gameTime);
        tag.putString("Stage", this.stage);
        CompoundTag payloadTag = new CompoundTag();
        this.payload.forEach(payloadTag::putString);
        tag.put("Payload", payloadTag);
        return tag;
    }

    public static QuestTriggerContext load(CompoundTag tag) {
        if (tag == null || !tag.contains("Event", Tag.TAG_STRING)) {
            return null;
        }
        QuestDefinition.TriggerEvent event = QuestDefinition.TriggerEvent.bySerializedName(tag.getString("Event"));
        if (event == null) {
            return null;
        }
        Map<String, String> payload = new LinkedHashMap<>();
        CompoundTag payloadTag = tag.getCompound("Payload");
        for (String key : payloadTag.getAllKeys()) {
            if (payloadTag.contains(key, Tag.TAG_STRING)) {
                payload.put(key, payloadTag.getString(key));
            }
        }
        return new QuestTriggerContext(event, tag.getLong("GameTime"), tag.getString("Stage"), payload, null);
    }

    public String value(String key) {
        return this.payload.getOrDefault(normalizeKey(key), "");
    }

    public boolean has(String key, String expected) {
        String actual = value(key);
        return expected == null ? !actual.isBlank() : actual.equalsIgnoreCase(expected.trim());
    }

    public Map<String, String> replacements() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("trigger_event", QuestTriggerRegistry.canonicalEventId(this.event));
        values.put("trigger_stage", this.stage);
        values.put("trigger_game_time", Long.toString(this.gameTime));
        this.payload.forEach((key, value) -> values.put("trigger_" + key, value));
        return Map.copyOf(values);
    }

    private static Map<String, String> snapshot(
            QuestDefinition.TriggerEvent event,
            QuestObjectiveEvent objectiveEvent) {
        Map<String, String> values = new LinkedHashMap<>();
        if (event != null) {
            values.put("event", QuestTriggerRegistry.canonicalEventId(event));
        }
        if (objectiveEvent == null) {
            return values;
        }
        if (objectiveEvent.killedEntity() != null) {
            put(values, "mob", BuiltInRegistries.ENTITY_TYPE.getKey(objectiveEvent.killedEntity().getType()));
            put(values, "entity", BuiltInRegistries.ENTITY_TYPE.getKey(objectiveEvent.killedEntity().getType()));
        }
        if (objectiveEvent.blockState() != null) {
            put(values, "block", BuiltInRegistries.BLOCK.getKey(objectiveEvent.blockState().getBlock()));
        }
        BlockPos pos = objectiveEvent.blockPos();
        if (pos != null) {
            values.put("block_x", Integer.toString(pos.getX()));
            values.put("block_y", Integer.toString(pos.getY()));
            values.put("block_z", Integer.toString(pos.getZ()));
        }
        putStack(values, "item", objectiveEvent.itemStack());
        if (objectiveEvent.giftReaction() != null) {
            values.put("gift_reaction", objectiveEvent.giftReaction().name().toLowerCase(Locale.ROOT));
        }
        if (objectiveEvent.villager() != null) {
            values.put("event_villager", objectiveEvent.villager().getUUID().toString());
            put(values, "event_villager_type", BuiltInRegistries.ENTITY_TYPE.getKey(objectiveEvent.villager().getType()));
        }
        if (objectiveEvent.offer() != null) {
            putStack(values, "trade_cost_a", objectiveEvent.offer().getCostA());
            putStack(values, "trade_cost_b", objectiveEvent.offer().getCostB());
            putStack(values, "trade_result", objectiveEvent.offer().getResult());
        }
        if (objectiveEvent.reputationValue() != null) {
            values.put("reputation", Integer.toString(objectiveEvent.reputationValue()));
        }
        put(values, "criterion", objectiveEvent.criterion());
        objectiveEvent.criterionData().forEach((key, value) -> {
            String normalized = normalizeKey(key);
            if (!normalized.isBlank() && value != null) {
                values.put("criterion_" + normalized, value);
            }
        });
        if (objectiveEvent.memoryEvent() != null && objectiveEvent.memoryEvent().tagId() != null) {
            put(values, "memory_tag", objectiveEvent.memoryEvent().tagId());
        }
        return values;
    }

    private static void putStack(Map<String, String> values, String prefix, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        put(values, prefix, BuiltInRegistries.ITEM.getKey(stack.getItem()));
        values.put(prefix + "_count", Integer.toString(stack.getCount()));
    }

    private static void put(Map<String, String> values, String key, ResourceLocation value) {
        if (value != null) {
            values.put(key, value.toString());
        }
    }

    private static Map<String, String> freeze(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String normalized = normalizeKey(key);
            if (!normalized.isBlank() && value != null) {
                copy.put(normalized, value);
            }
        });
        return copy.isEmpty() ? Map.of() : Collections.unmodifiableMap(copy);
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]+", "_");
    }
}
