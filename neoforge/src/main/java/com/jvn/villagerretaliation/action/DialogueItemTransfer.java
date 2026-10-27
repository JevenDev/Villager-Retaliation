package com.jvn.villagerretaliation.action;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.CampaignPlayerRewards;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.quest.item.QuestItems;
import com.jvn.villagerretaliation.util.AtomicDataFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

public final class DialogueItemTransfer {
    private static final String RECEIPTS = "villagerretaliation:dialogue_transfers";
    private DialogueItemTransfer() { }

    public static JsonObject schema() {
        var schema = JsonParser.parseString("""
                {"type":"object","required":["operation_id","direction","selection","quantity"],"properties":{
                 "operation_id":{"type":"string","minLength":1,"maxLength":256,"pattern":"^[a-z0-9_.:/-]+$"},
                 "direction":{"enum":["give_player","take_player"]},"selection":{"enum":["specific","pool","random_inventory"]},
                 "quantity":{"type":"integer","minimum":1,"maximum":2304},
                 "item":{"type":"string"},"quest_item":{"type":"string"},"stack":{"type":"object"},
                 "pool":{"type":"array","minItems":1,"maxItems":256,"items":{"type":"object","properties":{"item":{"type":"string"},"quest_item":{"type":"string"},"stack":{"type":"object"}}}},
                 "matching":{"enum":["item","components"]},"destination":{"enum":["consume","villager"]},
                 "on_missing":{"enum":["fail","skip"]},"on_full":{"enum":["fail","skip"]},
                 "exclude_damageable":{"type":"boolean"},"exclude_enchanted":{"type":"boolean"},"exclude_named":{"type":"boolean"},
                 "exclude_quest_items":{"type":"boolean"},"excluded_items":{"type":"array","items":{"type":"string"}},
                 "excluded_slots":{"type":"array","items":{"type":"integer","minimum":0,"maximum":35}}}}
                """).getAsJsonObject();
        var properties = schema.getAsJsonObject("properties");
        properties.getAsJsonObject("operation_id").addProperty("description", "A stable ID. Ordinary dialogue executes once per player. Quest actions execute once per quest run. Keep this ID when editing or moving the action.");
        properties.getAsJsonObject("direction").addProperty("description", "Give player: the player receives newly supplied items. Take player: the player gives items. Armor and offhand slots are never removed by random selection.");
        properties.getAsJsonObject("selection").addProperty("description", "Specific: choose one item. Pool: choose an eligible configured entry. Random inventory: choose an eligible item from the player's main inventory.");
        properties.getAsJsonObject("quantity").addProperty("description", "Total items to give or take. This overrides the authored stack's default count.");
        properties.getAsJsonObject("matching").addProperty("description", "Item compares the base type. Components compares all item components. Quest-item references always require their separate quest identity.");
        properties.getAsJsonObject("destination").addProperty("description", "Consume removes taken items. Villager places them in the receiving villager's inventory.");
        for (String field : List.of("on_missing", "on_full")) properties.getAsJsonObject(field).addProperty("description",
                "Fail blocks the action and preserves the chosen item for retry. Skip permanently completes this operation without transferring items.");
        return schema;
    }

    public static void validate(JsonObject data) {
        var issues = com.jvn.villagerretaliation.quest.schema.QuestJsonSchema.validate(data, schema());
        if (!issues.isEmpty()) throw new IllegalArgumentException(issues.getFirst().message());
        String selection = string(data, "selection", "");
        if (selection.equals("random_inventory") && !string(data, "direction", "").equals("take_player"))
            throw new IllegalArgumentException("Random inventory selection requires the player to give items");
        if (selection.equals("specific")) requireItem(data);
        if (selection.equals("pool")) {
            if (!data.has("pool") || data.getAsJsonArray("pool").isEmpty()) throw new IllegalArgumentException("Configure an item pool");
            data.getAsJsonArray("pool").forEach(value -> requireItem(value.getAsJsonObject()));
        }
        if (!selection.equals("pool") && data.has("pool")) throw new IllegalArgumentException("Item pools require pool selection");
        if (data.has("excluded_items")) data.getAsJsonArray("excluded_items").forEach(value -> {
            String id = value.getAsString();
            if (ResourceLocation.tryParse(id.startsWith("#") ? id.substring(1) : id) == null)
                throw new IllegalArgumentException("Invalid excluded item or tag: " + id);
        });
    }

    private static void requireItem(JsonObject data) {
        if ((data.has("item") ? 1 : 0) + (data.has("quest_item") ? 1 : 0) + (data.has("stack") ? 1 : 0) != 1)
            throw new IllegalArgumentException("Choose exactly one base item, quest item or component stack");
        for (String field : List.of("item", "quest_item")) if (data.has(field) && ResourceLocation.tryParse(data.get(field).getAsString()) == null)
            throw new IllegalArgumentException("Invalid item reference");
    }

    public static ActionResult execute(com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext context, VillagerActionDefinition action) {
        var capabilities = VillagerActionRegistry.capabilities(action);
        if (context == null || context.player() == null) return ActionResult.failed("A player is required", capabilities);
        var villager = context.speaker() instanceof net.minecraft.world.entity.npc.Villager entity ? entity : null;
        var data = action.parameters();
        try {
            validate(data);
            boolean take = string(data, "direction", "").equals("take_player");
            boolean toVillager = take && string(data, "destination", "consume").equals("villager");
            if (toVillager && (villager == null || !villager.isAlive()))
                return ActionResult.failed("The receiving villager is unavailable", capabilities);
            String run = "";
            if (action.questId() != null) {
                var progress = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), action.questId());
                if (progress == null || progress.questRunId() == null) return ActionResult.failed("The owning quest run is unavailable", capabilities);
                run = "/" + progress.questRunId();
            }
            var operation = UUID.nameUUIDFromBytes(("dialogue-transfer/" + context.player().getUUID() + run + "/" + data.get("operation_id").getAsString()).getBytes(StandardCharsets.UTF_8));
            var file = context.level().getServer().getWorldPath(LevelResource.ROOT).resolve("data/villagerretaliation/dialogue_transfers/" + operation + ".dat");
            CompoundTag plan;
            if (Files.exists(file)) {
                plan = NbtIo.readCompressed(file, NbtAccounter.create(1048576));
                if (!JsonParser.parseString(plan.getString("Config")).equals(data))
                    return ActionResult.failed("This transfer ID already has different saved terms. Use a new operation ID.", capabilities);
                if (toVillager && !villager.getUUID().equals(plan.getUUID("Villager")))
                    return ActionResult.failed("This transfer belongs to a different receiving villager", capabilities);
            } else {
                plan = prepare(context, data);
                plan.putString("Config", data.toString());
                plan.putUUID("Token", UUID.randomUUID());
                if (toVillager) plan.putUUID("Villager", villager.getUUID());
                Files.createDirectories(file.getParent());
                AtomicDataFiles.writeCompressed(plan, file);
            }
            if (plan.getBoolean("Skipped")) return ActionResult.alreadySatisfied("Transfer skipped", capabilities, VillagerActionResult.success());
            if (plan.getBoolean("Missing") && !plan.contains("Selected", Tag.TAG_COMPOUND)) {
                var retry = prepare(context, data);
                for (String field : List.of("Selected", "Costs", "Rewards", "Slots", "Missing"))
                    if (retry.contains(field)) plan.put(field, retry.get(field).copy());
                AtomicDataFiles.writeCompressed(plan, file);
            }
            var costs = stacks(plan, "Costs");
            var rewards = stacks(plan, "Rewards");
            var slots = java.util.Arrays.stream(plan.getIntArray("Slots")).boxed().toList();
            if (rewards.size() > 256) return policy(data, plan, file, "on_full", "The player's inventory cannot hold the configured quantity", capabilities);
            var delivery = new CampaignPlayerRewards.Delivery(operation, plan.getUUID("Token"), context.player().getUUID(), 0, rewards, false, costs, slots);
            boolean delivered = CampaignPlayerRewards.delivered(context.player(), delivery);
            if (!delivered && take && plan.contains("Selected", Tag.TAG_COMPOUND)) {
                var selected = ItemStack.parse(context.player().registryAccess(), plan.getCompound("Selected"))
                        .orElseThrow(() -> new IllegalArgumentException("Saved transfer item is unavailable"));
                var retry = costs(context, data, selected);
                if (!retry.get("Costs").equals(plan.get("Costs")) || !retry.get("Slots").equals(plan.get("Slots"))
                        || retry.getBoolean("Missing") != plan.getBoolean("Missing")) {
                    plan.put("Costs", retry.get("Costs").copy());
                    plan.put("Slots", retry.get("Slots").copy());
                    plan.putBoolean("Missing", retry.getBoolean("Missing"));
                    AtomicDataFiles.writeCompressed(plan, file);
                }
                costs = stacks(plan, "Costs");
                slots = java.util.Arrays.stream(plan.getIntArray("Slots")).boxed().toList();
                delivery = new CampaignPlayerRewards.Delivery(operation, plan.getUUID("Token"), context.player().getUUID(), 0, rewards, false, costs, slots);
            }
            boolean missing = plan.getBoolean("Missing");
            if (!delivered && missing) return policy(data, plan, file, "on_missing", "The player lacks the selected items", capabilities);
            List<ItemStack> villagerItems = null;
            var villagerReceipts = toVillager ? villager.getPersistentData().getCompound(RECEIPTS) : new CompoundTag();
            boolean received = villagerReceipts.hasUUID(operation.toString());
            if (received && !villagerReceipts.getUUID(operation.toString()).equals(plan.getUUID("Token")))
                return ActionResult.failed("The receiving villager has a conflicting transfer receipt", capabilities);
            if (toVillager && !received) {
                if (VillagerInventoryAccess.hasOpenInventory(villager))
                    return ActionResult.failed("Close the receiving villager's inventory before completing this transfer", capabilities);
                villagerItems = new ArrayList<>(VillagerInventoryAccess.captureFullInventory(villager));
                for (var cost : costs) {
                    var stack = ItemStack.parse(context.player().registryAccess(), cost).orElseThrow(() -> new IllegalArgumentException("Saved transfer item is unavailable"));
                    if (!insert(villagerItems, stack, villager.getInventory().getMaxStackSize())) {
                        if (delivered) return ActionResult.failed("Make room in the receiving villager's inventory to finish the saved transfer", capabilities);
                        return policy(data, plan, file, "on_full", "The receiving villager's inventory is full", capabilities);
                    }
                }
            }
            if (!take && !delivered) {
                var candidate = new ArrayList<>(context.player().getInventory().items.stream().map(ItemStack::copy).toList());
                for (var reward : rewards) if (!insert(candidate, ItemStack.parse(context.player().registryAccess(), reward).orElseThrow(() -> new IllegalArgumentException("Saved transfer item is unavailable")), context.player().getInventory().getMaxStackSize()))
                    return policy(data, plan, file, "on_full", "The player's inventory is full", capabilities);
            }
            boolean changed = CampaignPlayerRewards.deliver(context.player(), delivery);
            if (villagerItems != null) {
                VillagerInventoryAccess.replaceFullInventory(villager, villagerItems);
                villagerReceipts.putUUID(operation.toString(), plan.getUUID("Token"));
                villager.getPersistentData().put(RECEIPTS, villagerReceipts);
                changed = true;
            }
            return changed ? ActionResult.success(VillagerActionResult.success(), capabilities)
                    : ActionResult.alreadySatisfied("Transfer already completed", capabilities, VillagerActionResult.success());
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            return ActionResult.failed("Item transfer could not complete: " + exception.getMessage(), capabilities);
        }
    }

    private static ActionResult policy(JsonObject data, CompoundTag plan, java.nio.file.Path file, String field, String message,
            java.util.Set<ActionCapability> capabilities) throws IOException {
        if (string(data, field, "fail").equals("skip")) {
            plan.putBoolean("Skipped", true);
            AtomicDataFiles.writeCompressed(plan, file);
            return ActionResult.success(VillagerActionResult.success(), capabilities);
        }
        return ActionResult.failed(message + ". The saved selection will be retried without rerolling.", capabilities);
    }

    private static CompoundTag prepare(com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext context, JsonObject data) {
        var plan = new CompoundTag();
        var costs = new ListTag();
        var rewards = new ListTag();
        var slots = new ArrayList<Integer>();
        int quantity = data.get("quantity").getAsInt();
        boolean take = string(data, "direction", "").equals("take_player");
        String selection = string(data, "selection", "");
        var candidates = new ArrayList<ItemStack>();
        if (selection.equals("specific")) candidates.add(item(context, data));
        else if (selection.equals("pool")) for (var value : data.getAsJsonArray("pool")) candidates.add(item(context, value.getAsJsonObject()));
        else for (int index = 0; index < context.player().getInventory().items.size(); index++) {
            var stack = context.player().getInventory().items.get(index);
            if (eligible(context, stack, index, data)) candidates.add(stack.copyWithCount(1));
        }
        if (take) {
            var available = candidates.stream().filter(candidate -> !costs(context, data, candidate).getBoolean("Missing")).toList();
            if (!available.isEmpty()) candidates = new ArrayList<>(available);
        }
        if (candidates.isEmpty()) {
            plan.putBoolean("Missing", true);
        } else {
            var selected = candidates.get(context.random().nextInt(candidates.size()));
            plan.put("Selected", selected.copyWithCount(1).save(context.player().registryAccess()));
            int remaining = quantity;
            if (take) {
                var removed = costs(context, data, selected);
                costs = removed.getList("Costs", Tag.TAG_COMPOUND);
                for (int slot : removed.getIntArray("Slots")) slots.add(slot);
                plan.putBoolean("Missing", removed.getBoolean("Missing"));
            } else {
                while (remaining > 0) {
                    int count = Math.min(remaining, selected.getMaxStackSize());
                    rewards.add(selected.copyWithCount(count).save(context.player().registryAccess()));
                    remaining -= count;
                }
            }
        }
        plan.put("Costs", costs);
        plan.put("Rewards", rewards);
        plan.putIntArray("Slots", slots);
        return plan;
    }

    private static CompoundTag costs(com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext context, JsonObject data, ItemStack selected) {
        var result = new CompoundTag();
        var costs = new ListTag();
        var slots = new ArrayList<Integer>();
        int remaining = data.get("quantity").getAsInt();
        for (int slot = 0; slot < context.player().getInventory().items.size() && remaining > 0; slot++) {
            var stack = context.player().getInventory().items.get(slot);
            if (!string(data, "selection", "").equals("specific") && !eligible(context, stack, slot, data) || !matches(stack, selected, data)) continue;
            int count = Math.min(remaining, stack.getCount());
            slots.add(slot);
            costs.add(stack.copyWithCount(count).save(context.player().registryAccess()));
            remaining -= count;
        }
        result.put("Costs", costs);
        result.putIntArray("Slots", slots);
        result.putBoolean("Missing", remaining > 0);
        return result;
    }

    private static ItemStack item(com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext context, JsonObject data) {
        if (data.has("quest_item")) return QuestItems.resolve(context.player().registryAccess(), ResourceLocation.parse(data.get("quest_item").getAsString()));
        if (data.has("stack")) return ItemStack.STRICT_CODEC.parse(context.player().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), data.get("stack"))
                .getOrThrow(message -> new IllegalArgumentException("Invalid component stack: " + message));
        var id = ResourceLocation.parse(data.get("item").getAsString());
        var item = BuiltInRegistries.ITEM.getOptional(id).orElseThrow(() -> new IllegalArgumentException("Unknown item: " + id));
        if (item == net.minecraft.world.item.Items.AIR) throw new IllegalArgumentException("Choose a nonempty item");
        return item.getDefaultInstance();
    }

    public static boolean matches(ItemStack actual, ItemStack expected, JsonObject data) {
        if (actual.isEmpty() || !ItemStack.isSameItem(actual, expected)) return false;
        if (!QuestItems.identity(expected).isBlank() && !QuestItems.identity(actual).equals(QuestItems.identity(expected))) return false;
        return !string(data, "matching", "item").equals("components") || ItemStack.isSameItemSameComponents(actual, expected);
    }

    private static boolean eligible(com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext context, ItemStack stack, int slot, JsonObject data) {
        if (!eligible(stack, slot, data)) return false;
        if (!flag(data, "exclude_quest_items")) return true;
        var saved = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(context.level());
        for (var entry : saved.progress(context.player().getUUID())) {
            var progress = entry.getValue();
            if (progress.state() != com.jvn.villagerretaliation.quest.VillagerQuestSavedData.QuestState.ACTIVE) continue;
            var quest = com.jvn.villagerretaliation.quest.VillagerQuestResources.quest(context.level().getServer(), entry.getKey()).orElse(null);
            if (quest == null) continue;
            if (quest.target().proofItem() != null && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(quest.target().proofItem())
                    && quest.target().proofItemPredicate().matches(stack)) return false;
            for (var objective : quest.objectives()) if (objective.type() == com.jvn.villagerretaliation.quest.QuestDefinition.ObjectiveType.ITEM_CHECK
                    && com.jvn.villagerretaliation.quest.inventory.QuestInventoryService.matchesObjectiveItemStack(objective, stack,
                    com.jvn.villagerretaliation.quest.objectives.QuestObjectiveQuery.resolvedItem(objective, progress))) return false;
        }
        return true;
    }

    public static boolean eligible(ItemStack stack, int slot, JsonObject data) {
        if (stack.isEmpty() || flag(data, "exclude_damageable") && stack.getMaxDamage() > 0
                || flag(data, "exclude_enchanted") && (stack.isEnchanted() || stack.has(DataComponents.STORED_ENCHANTMENTS))
                || flag(data, "exclude_named") && stack.has(DataComponents.CUSTOM_NAME)
                || flag(data, "exclude_quest_items") && !QuestItems.identity(stack).isBlank()) return false;
        if (data.has("excluded_slots") && data.getAsJsonArray("excluded_slots").asList().stream().anyMatch(value -> value.getAsInt() == slot)) return false;
        if (data.has("excluded_items")) for (var value : data.getAsJsonArray("excluded_items")) {
            String id = value.getAsString();
            if (id.startsWith("#") ? stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(id.substring(1))))
                    : BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(ResourceLocation.parse(id))) return false;
        }
        return true;
    }

    public static boolean insert(List<ItemStack> inventory, ItemStack input, int inventoryLimit) {
        var remaining = input.copy();
        for (var stack : inventory) {
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, remaining)) continue;
            int count = Math.min(remaining.getCount(), Math.max(0, Math.min(inventoryLimit, stack.getMaxStackSize()) - stack.getCount()));
            stack.grow(count);
            remaining.shrink(count);
            if (remaining.isEmpty()) return true;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (!inventory.get(slot).isEmpty()) continue;
            int count = Math.min(remaining.getCount(), Math.min(inventoryLimit, remaining.getMaxStackSize()));
            inventory.set(slot, remaining.copyWithCount(count));
            remaining.shrink(count);
            if (remaining.isEmpty()) return true;
        }
        return remaining.isEmpty();
    }

    private static List<CompoundTag> stacks(CompoundTag tag, String name) {
        return tag.getList(name, Tag.TAG_COMPOUND).stream().map(value -> (CompoundTag) value).toList();
    }
    private static String string(JsonObject data, String name, String fallback) { return data.has(name) ? data.get(name).getAsString() : fallback; }
    private static boolean flag(JsonObject data, String name) { return !data.has(name) || data.get(name).getAsBoolean(); }
}
