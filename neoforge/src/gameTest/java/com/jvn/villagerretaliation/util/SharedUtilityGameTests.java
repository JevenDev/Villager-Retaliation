package com.jvn.villagerretaliation.util;

import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("shared_utilities")
@PrefixGameTestTemplate(false)
public final class SharedUtilityGameTests {
    private SharedUtilityGameTests() { }

    @GameTest(template = "empty")
    public static void tickThrottlingPreservesOffsetsAndWindows(GameTestHelper helper) {
        var id = new UUID(7, 11);
        var nextTicks = new HashMap<UUID, Long>();
        helper.assertValueEqual(TickThrottle.spreadOffset(id, 10), 1L, "spread uses the low UUID bits");
        helper.assertValueEqual(TickThrottle.stagger(id, 10), 2L, "stagger uses both UUID halves");
        helper.assertFalse(TickThrottle.consume(id, nextTicks, 100, 10), "first work is staggered");
        helper.assertFalse(TickThrottle.consume(id, nextTicks, 101, 10), "work waits for its stagger");
        helper.assertTrue(TickThrottle.consume(id, nextTicks, 102, 10), "staggered work runs");
        helper.assertFalse(TickThrottle.consume(id, nextTicks, 111, 10), "work respects the interval");
        helper.assertTrue(TickThrottle.consume(id, nextTicks, 112, 10), "next window runs");
        helper.assertTrue(TickThrottle.isSpreadTick(id, 101, 10), "spread tick keeps its phase");
        helper.assertValueEqual(TickThrottle.spreadOffset(-1, 10), 9L, "negative seeds use floor modulus");
        nextTicks.clear();
        helper.assertTrue(TickThrottle.consume(id, nextTicks, 100, 0), "zero intervals run immediately");
        helper.assertFalse(TickThrottle.consume(id, nextTicks, 100, 0), "zero intervals still gate repeated work");
        helper.assertTrue(TickThrottle.consume(id, nextTicks, 101, 0), "zero intervals resume next tick");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void datapackReadersPreserveFallbacksAndDurationPrecedence(GameTestHelper helper) {
        var json = JsonParser.parseString("""
                {"names":[" first ",null,{},2,""],"name":" second ","bad":"oops","snake":null,"camel":7,
                "enabled":"true","numeric":1,"delay_ticks":"bad","delay_days":2,"delay_seconds":3}
                """).getAsJsonObject();
        helper.assertValueEqual(DatapackJsonReader.readStringList(json, "names", "name"),
                List.of("first", "2", "second"), "string fields preserve order and skip malformed entries");
        helper.assertValueEqual(DatapackJsonReader.readInt(json, "bad", 9), 9, "invalid numbers use the fallback");
        helper.assertValueEqual(DatapackJsonReader.readInt(json, "snake", "camel", 9), 9,
                "an explicit snake key takes precedence even when invalid");
        helper.assertTrue(DatapackJsonReader.readBoolean(json, "enabled"), "boolean strings remain supported");
        helper.assertTrue(DatapackJsonReader.readBoolean(json, "numeric", true), "numeric booleans use the fallback");
        helper.assertValueEqual(DatapackJsonReader.readDurationTicks(json, "delay", 5), 48000L,
                "VR keeps days ahead of seconds when ticks are invalid");
        json.addProperty("delay_ticks", -2);
        helper.assertValueEqual(DatapackJsonReader.readDurationTicks(json, "delay", 5), 0L,
                "explicit ticks take precedence and clamp negatives");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void paymentPreviewAndRemovalKeepInventoryOrderAndComponents(GameTestHelper helper) {
        var json = JsonParser.parseString("""
                {"take_items":{"item":"minecraft:stone","count":3}}
                """).getAsJsonObject();
        var removal = VillagerInventoryItemRemoval.read(json, "take_items").orElseThrow();
        var player = helper.makeMockServerPlayerInLevel();
        var main = new ItemStack(Items.STONE, 2);
        main.set(DataComponents.CUSTOM_NAME, Component.literal("main"));
        var offhand = new ItemStack(Items.STONE, 3);
        offhand.set(DataComponents.CUSTOM_NAME, Component.literal("offhand"));
        var armor = new ItemStack(Items.STONE, 8);
        player.getInventory().setItem(0, main);
        player.setItemInHand(InteractionHand.OFF_HAND, offhand);
        player.setItemSlot(EquipmentSlot.HEAD, armor);
        helper.assertValueEqual(removal.matchingCount(player), 5, "payments count main inventory and offhand only");
        var preview = removal.previewRemovedStacks(player);
        helper.assertValueEqual(preview.size(), 2, "preview keeps separate component stacks");
        helper.assertValueEqual(main.getCount(), 2, "preview leaves main inventory intact");
        helper.assertValueEqual(offhand.getCount(), 3, "preview leaves offhand intact");
        var removed = removal.removeStacks(player).orElseThrow();
        for (int i = 0; i < preview.size(); i++)
            helper.assertTrue(ItemStack.matches(preview.get(i), removed.get(i)), "removal matches the preview in order");
        helper.assertTrue(main.isEmpty(), "main inventory is consumed first");
        helper.assertValueEqual(offhand.getCount(), 2, "only the remainder comes from offhand");
        helper.assertValueEqual(armor.getCount(), 8, "armor is never consumed");
        helper.assertFalse(removal.remove(player), "insufficient payment fails without partial removal");
        helper.assertValueEqual(offhand.getCount(), 2, "failed payment leaves the inventory intact");
        helper.assertTrue(VillagerInventoryItemRemoval.empty().remove(player), "empty payment remains successful");
        helper.succeed();
    }
}
