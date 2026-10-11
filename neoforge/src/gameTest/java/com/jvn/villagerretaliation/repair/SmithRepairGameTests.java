package com.jvn.villagerretaliation.repair;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.inventory.SmithRepairMenu;
import com.mojang.authlib.GameProfile;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("smith_repairs")
@PrefixGameTestTemplate(false)
public final class SmithRepairGameTests {
    private SmithRepairGameTests() {
    }

    @GameTest(template = "empty", batch = "smith_repairs")
    public static void professionTagsCoverSpecialtiesAndOverlappingAxes(GameTestHelper helper) {
        helper.assertTrue(SmithRepairService.specialized(VillagerProfession.TOOLSMITH, new ItemStack(Items.DIAMOND_AXE)), "toolsmith axe");
        helper.assertTrue(SmithRepairService.specialized(VillagerProfession.WEAPONSMITH, new ItemStack(Items.DIAMOND_AXE)), "weaponsmith axe");
        helper.assertTrue(SmithRepairService.specialized(VillagerProfession.TOOLSMITH, new ItemStack(Items.SHEARS)), "toolsmith shears");
        helper.assertTrue(SmithRepairService.specialized(VillagerProfession.WEAPONSMITH, new ItemStack(Items.MACE)), "weaponsmith mace");
        helper.assertTrue(SmithRepairService.specialized(VillagerProfession.ARMORER, new ItemStack(Items.SHIELD)), "armorer shield");
        helper.assertTrue(SmithRepairService.specialized(VillagerProfession.ARMORER, new ItemStack(Items.DIAMOND_BOOTS)), "armorer boots");
        helper.assertFalse(SmithRepairService.specialized(VillagerProfession.TOOLSMITH, new ItemStack(Items.DIAMOND_SWORD)), "toolsmith sword");
        helper.assertFalse(SmithRepairService.specialized(VillagerProfession.ARMORER, new ItemStack(Items.ELYTRA)), "unclassified equipment");
        helper.assertFalse(SmithRepairService.repairable(new ItemStack(Items.BOW)), "bows have no vanilla material repair");
        helper.assertFalse(SmithRepairService.repairable(new ItemStack(Items.DIRT)), "non-equipment");
        helper.assertTrue(SmithRepairService.repairable(damaged(Items.ELYTRA, 100)), "elytra material repair");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "smith_repairs")
    public static void pricesUseTiersOverridesAndFallback(GameTestHelper helper) {
        var definition = SmithRepairPrices.defaults();
        Item[] items = {Items.WOODEN_AXE, Items.LEATHER_BOOTS, Items.STONE_SHOVEL, Items.CHAINMAIL_CHESTPLATE,
                Items.IRON_SWORD, Items.GOLDEN_HELMET, Items.DIAMOND_PICKAXE, Items.NETHERITE_LEGGINGS, Items.SHIELD};
        int[] prices = {1, 1, 2, 2, 3, 3, 6, 10, 4};
        for (int index = 0; index < items.length; index++) {
            helper.assertValueEqual(definition.price(new ItemStack(items[index])), prices[index], "tier price " + items[index]);
        }
        var custom = SmithRepairPrices.parse(JsonParser.parseString("{\"fallback\":7,\"tiers\":{\"diamond\":8},\"items\":{\"minecraft:diamond_pickaxe\":9}}").getAsJsonObject());
        helper.assertValueEqual(custom.price(new ItemStack(Items.DIAMOND_PICKAXE)), 9, "item override wins");
        helper.assertValueEqual(custom.price(new ItemStack(Items.DIAMOND_AXE)), 8, "tier override");
        helper.assertValueEqual(custom.price(new ItemStack(Items.ELYTRA)), 7, "fallback override");
        helper.assertValueEqual(SmithRepairService.cost(6, true, true), 6, "specialist with material");
        helper.assertValueEqual(SmithRepairService.cost(6, true, false), 12, "specialist without material");
        helper.assertValueEqual(SmithRepairService.cost(6, false, true), 12, "other smith with material");
        helper.assertValueEqual(SmithRepairService.cost(6, false, false), 24, "other smith without material");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "smith_repairs")
    public static void priceValidationRejectsMalformedAndOverflowingValues(GameTestHelper helper) {
        for (String json : List.of("{\"fallback\":0}", "{\"fallback\":1.5}", "{\"fallback\":1000001}",
                "{\"fallback\":\"3\"}", "{\"tiers\":{\"unknown\":3}}", "{\"items\":{\"minecraft:missing_repair_item\":2}}")) {
            boolean rejected = false;
            try {
                SmithRepairPrices.parse(JsonParser.parseString(json).getAsJsonObject());
            } catch (IllegalArgumentException exception) {
                rejected = true;
            }
            helper.assertTrue(rejected, "invalid price accepted: " + json);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "smith_repairs")
    public static void specialistRepairConsumesOneMaterialAndPreservesComponents(GameTestHelper helper) {
        Fixture fixture = fixture(helper, VillagerProfession.TOOLSMITH);
        SmithRepairMenu menu = fixture.menu();
        ItemStack item = damaged(Items.DIAMOND_PICKAXE, 1000);
        item.set(DataComponents.CUSTOM_NAME, Component.literal("Keep this name"));
        item.set(DataComponents.REPAIR_COST, 17);
        item.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING), 3);
        menu.getSlot(0).set(item);
        menu.getSlot(1).set(new ItemStack(Items.DIAMOND, 8));
        menu.broadcastChanges();
        int before = VillagerCurrencyPayment.count(fixture.player());
        helper.assertValueEqual(menu.cost(), 6, "specialist fee");
        helper.assertTrue(menu.clickMenuButton(fixture.player(), 0), "repair accepted");
        complete(fixture);
        ItemStack expected = item.copy();
        expected.setDamageValue(1000 - 546);
        helper.assertTrue(ItemStack.matches(menu.getSlot(0).getItem(), expected), "repair must preserve every component except damage");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 7, "exactly one material consumed");
        helper.assertValueEqual(VillagerCurrencyPayment.count(fixture.player()), before - 6, "exact fee");
        helper.assertTrue(menu.clickMenuButton(fixture.player(), 0), "second repair accepted");
        complete(fixture);
        helper.assertValueEqual(menu.getSlot(0).getItem().getDamageValue(), 0, "final repair clamps to full durability");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 6, "one material for partial final repair");
        int after = VillagerCurrencyPayment.count(fixture.player());
        helper.assertFalse(menu.clickMenuButton(fixture.player(), 0), "full durability rejects repeated request");
        helper.assertValueEqual(VillagerCurrencyPayment.count(fixture.player()), after, "rejected request costs nothing");
        menu.removed(fixture.player());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "smith_repairs")
    public static void nonSpecialistRepairsWithoutMaterialAtHigherPrice(GameTestHelper helper) {
        Fixture fixture = fixture(helper, VillagerProfession.ARMORER);
        fixture.menu().getSlot(0).set(damaged(Items.DIAMOND_PICKAXE, 1000));
        fixture.menu().broadcastChanges();
        helper.assertValueEqual(fixture.menu().cost(), 24, "non-specialist material-free fee");
        helper.assertTrue(fixture.menu().clickMenuButton(fixture.player(), 0), "material-free repair accepted");
        complete(fixture);
        helper.assertValueEqual(fixture.menu().getSlot(0).getItem().getDamageValue(), 610, "25 percent rounded down");
        helper.assertTrue(fixture.menu().getSlot(1).getItem().isEmpty(), "no material created");
        fixture.menu().removed(fixture.player());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "smith_repairs")
    public static void invalidInputsAndMissingPaymentNeverMutateItems(GameTestHelper helper) {
        Fixture fixture = fixture(helper, VillagerProfession.TOOLSMITH);
        SmithRepairMenu menu = fixture.menu();
        helper.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(Items.BOW)), "unrepairable item rejected");
        helper.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(Items.DIAMOND_PICKAXE)), "undamaged item rejected");
        menu.getSlot(0).set(damaged(Items.DIAMOND_PICKAXE, 1000));
        helper.assertFalse(menu.getSlot(1).mayPlace(new ItemStack(Items.IRON_INGOT)), "wrong material rejected");
        menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT, 4));
        menu.broadcastChanges();
        helper.assertValueEqual(menu.status(), SmithRepairMenu.Status.WRONG_MATERIAL, "incompatible material status");
        helper.assertFalse(menu.clickMenuButton(fixture.player(), 0), "incompatible material transaction rejected");
        menu.getSlot(1).set(new ItemStack(Items.DIAMOND, 4));
        fixture.player().getInventory().clearContent();
        menu.broadcastChanges();
        helper.assertValueEqual(menu.status(), SmithRepairMenu.Status.FUNDS, "missing currency status");
        helper.assertTrue(menu.getSlot(2).hasItem(), "preview remains visible without funds");
        helper.assertFalse(menu.clickMenuButton(fixture.player(), 0), "unpaid repair rejected");
        helper.assertValueEqual(menu.getSlot(0).getItem().getDamageValue(), 1000, "item unchanged");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 4, "material unchanged");
        fixture.player().getInventory().offhand.set(0, new ItemStack(Items.EMERALD, 6));
        helper.assertTrue(menu.clickMenuButton(fixture.player(), 0), "server rechecks fresh offhand payment");
        complete(fixture);
        helper.assertValueEqual(VillagerCurrencyPayment.count(fixture.player()), 0, "offhand payment consumed");
        menu.removed(fixture.player());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "smith_repairs")
    public static void previewCannotBeExtractedThroughAnyClickMode(GameTestHelper helper) {
        Fixture fixture = fixture(helper, VillagerProfession.TOOLSMITH);
        SmithRepairMenu menu = fixture.menu();
        menu.getSlot(0).set(damaged(Items.DIAMOND_PICKAXE, 1000));
        menu.broadcastChanges();
        ItemStack preview = menu.getSlot(2).getItem().copy();
        fixture.player().getAbilities().instabuild = true;
        for (ClickType type : ClickType.values()) {
            menu.clicked(2, 0, type, fixture.player());
            helper.assertTrue(ItemStack.matches(menu.getSlot(2).getItem(), preview), "preview changed through " + type);
            helper.assertTrue(menu.getCarried().isEmpty(), "preview extracted through " + type);
        }
        helper.assertTrue(menu.quickMoveStack(fixture.player(), 2).isEmpty(), "preview shift-click rejected");
        menu.setCarried(new ItemStack(Items.DIAMOND, 5));
        menu.clicked(-999, 0, ClickType.QUICK_CRAFT, fixture.player());
        menu.clicked(2, 1, ClickType.QUICK_CRAFT, fixture.player());
        menu.clicked(-999, 2, ClickType.QUICK_CRAFT, fixture.player());
        helper.assertValueEqual(menu.getCarried().getCount(), 5, "dragging onto preview leaves carried stack intact");
        fixture.player().getAbilities().instabuild = false;
        menu.removed(fixture.player());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "smith_repairs")
    public static void shiftClickRoutesInputsAndCloseReturnsOnlyRealItems(GameTestHelper helper) {
        Fixture fixture = fixture(helper, VillagerProfession.TOOLSMITH);
        SmithRepairMenu menu = fixture.menu();
        fixture.player().getInventory().setItem(9, damaged(Items.DIAMOND_PICKAXE, 1000));
        fixture.player().getInventory().setItem(10, new ItemStack(Items.DIAMOND, 8));
        menu.quickMoveStack(fixture.player(), 3);
        menu.quickMoveStack(fixture.player(), 4);
        menu.broadcastChanges();
        helper.assertTrue(menu.getSlot(0).getItem().is(Items.DIAMOND_PICKAXE), "equipment shift-click");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 8, "material shift-click");
        fixture.player().getInventory().setItem(1, new ItemStack(Items.IRON_INGOT));
        menu.clicked(1, 1, ClickType.SWAP, fixture.player());
        helper.assertTrue(menu.getSlot(1).getItem().is(Items.DIAMOND), "number-key swap cannot insert incompatible material");
        menu.removed(fixture.player());
        menu.removed(fixture.player());
        helper.assertValueEqual(count(fixture.player(), Items.DIAMOND_PICKAXE), 1, "preview never returned and close is idempotent");
        helper.assertValueEqual(count(fixture.player(), Items.DIAMOND), 8, "unused material returned exactly once");
        helper.assertTrue(fixture.villager().getTradingPlayer() == null, "smith reservation released");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "smith_repairs")
    public static void unavailableSmithAndForeignPlayerCannotRepair(GameTestHelper helper) {
        Fixture fixture = fixture(helper, VillagerProfession.TOOLSMITH);
        SmithRepairMenu menu = fixture.menu();
        menu.getSlot(0).set(damaged(Items.DIAMOND_PICKAXE, 1000));
        ServerPlayer other = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "OtherRepairOwner"));
        helper.assertFalse(menu.clickMenuButton(other, 0), "foreign owner rejected");
        helper.assertFalse(menu.clickMenuButton(fixture.player(), 1), "unknown action rejected");
        fixture.villager().setVillagerData(fixture.villager().getVillagerData().setProfession(VillagerProfession.FARMER));
        helper.assertFalse(menu.stillValid(fixture.player()), "profession change closes service");
        helper.assertFalse(menu.clickMenuButton(fixture.player(), 0), "unavailable smith cannot charge");
        helper.assertValueEqual(menu.getSlot(0).getItem().getDamageValue(), 1000, "invalidated repair preserves item");
        menu.removed(fixture.player());
        helper.assertValueEqual(count(fixture.player(), Items.DIAMOND_PICKAXE), 1, "invalidated item returned");
        var originalMenu = fixture.player().containerMenu;
        VillagerInteractionService.handleRepairRequest(fixture.player(), fixture.villager().getId());
        helper.assertTrue(fixture.player().containerMenu == originalMenu, "forged open request without conversation rejected");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "smith_repairs")
    public static void priceReloadUpdatesOpenMenuAndPreservesLargeCosts(GameTestHelper helper) {
        Fixture fixture = fixture(helper, VillagerProfession.TOOLSMITH);
        try {
            fixture.menu().getSlot(0).set(damaged(Items.DIAMOND_PICKAXE, 1000));
            var resources = resources(helper, "smith_repairs/prices.json", "{\"items\":{\"minecraft:diamond_pickaxe\":1000000}}");
            SmithRepairPrices.reload(helper.getLevel().getServer(), resources);
            fixture.menu().broadcastChanges();
            helper.assertValueEqual(fixture.menu().cost(), 2_000_000, "large fee survives menu data encoding");
            helper.assertValueEqual(fixture.menu().status(), SmithRepairMenu.Status.FUNDS, "reloaded fee applied to open menu");
            resources = resources(helper, "smith_repairs/prices.json", "{\"items\":{\"minecraft:diamond_pickaxe\":2}}");
            SmithRepairPrices.reload(helper.getLevel().getServer(), resources);
            helper.assertTrue(fixture.menu().clickMenuButton(fixture.player(), 0), "button uses current reloaded fee");
            complete(fixture);
            helper.assertValueEqual(VillagerCurrencyPayment.count(fixture.player()), 60, "reloaded price charged");
            SmithRepairPrices.clearCache();
            fixture.menu().broadcastChanges();
            helper.assertValueEqual(fixture.menu().cost(), 12, "clearing reload cache restores resource definition");
        } finally {
            SmithRepairPrices.clearCache();
            fixture.menu().removed(fixture.player());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "smith_repairs")
    public static void configuredCurrencyPaysForRepairs(GameTestHelper helper) {
        Fixture fixture = fixture(helper, VillagerProfession.TOOLSMITH);
        try {
            VillagerCurrencyResources.reload(helper.getLevel().getServer(), resources(helper, "currency/test_repair.json",
                    "{\"item\":\"minecraft:gold_nugget\",\"accepted_items\":[\"minecraft:iron_nugget\"],\"icon_sprite\":\"minecraft:item/gold_nugget\"}"));
            fixture.player().getInventory().clearContent();
            fixture.player().getInventory().setItem(0, new ItemStack(Items.GOLD_NUGGET, 3));
            fixture.player().getInventory().offhand.set(0, new ItemStack(Items.IRON_NUGGET, 3));
            fixture.menu().getSlot(0).set(damaged(Items.DIAMOND_PICKAXE, 1000));
            fixture.menu().getSlot(1).set(new ItemStack(Items.DIAMOND, 2));
            helper.assertTrue(fixture.menu().clickMenuButton(fixture.player(), 0), "configured primary and accepted currencies pay together");
            complete(fixture);
            helper.assertValueEqual(VillagerCurrencyPayment.count(fixture.player()), 0, "configured currency charged exactly");
        } finally {
            VillagerCurrencyResources.clearCache();
            fixture.menu().removed(fixture.player());
        }
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper, VillagerProfession profession) {
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(villager != null, "villager created");
        villager.setVillagerData(villager.getVillagerData().setProfession(profession));
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(pos.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos.offset(0, -1, 1), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var block = profession == VillagerProfession.TOOLSMITH ? net.minecraft.world.level.block.Blocks.SMITHING_TABLE
                : profession == VillagerProfession.WEAPONSMITH ? net.minecraft.world.level.block.Blocks.GRINDSTONE
                : net.minecraft.world.level.block.Blocks.BLAST_FURNACE;
        helper.getLevel().setBlockAndUpdate(pos.south(), block.defaultBlockState());
        villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.62, 0, 0);
        villager.setNoAi(true);
        villager.setOnGround(true);
        helper.getLevel().addFreshEntity(villager);
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "SmithRepairOwner"));
        player.moveTo(pos.getX() + 1, pos.getY(), pos.getZ(), 0, 0);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 64));
        SmithRepairMenu menu = new SmithRepairMenu(1, player.getInventory(), villager);
        player.containerMenu = menu;
        return new Fixture(player, villager, menu);
    }

    private static void complete(Fixture fixture) {
        var state = (SmithRepairAnimationState) fixture.villager();
        long started = fixture.villager().level().getGameTime() - SmithRepairWorkstation.WORK_TICKS - 10;
        state.villagerretaliation$repairAnimation(state.villagerretaliation$repairStation(), state.villagerretaliation$repairItem(),
                started == SmithRepairAnimationState.APPROACHING || started == SmithRepairAnimationState.RETURNING ? started - 2 : started);
        fixture.menu().broadcastChanges();
    }

    @GameTest(template = "empty", batch = "smith_repairs", timeoutTicks = 160)
    public static void repairWaitsForHammeringAndPreventsRepeatedPayment(GameTestHelper helper) {
        Fixture fixture = fixture(helper, VillagerProfession.WEAPONSMITH);
        SmithRepairMenu menu = fixture.menu();
        menu.getSlot(0).set(damaged(Items.DIAMOND_SWORD, 1000));
        menu.getSlot(1).set(new ItemStack(Items.DIAMOND, 4));
        helper.assertTrue(menu.clickMenuButton(fixture.player(), 0), "animated repair starts");
        helper.assertTrue(SmithRepairAnimationState.isWorking(fixture.villager()), "workstation animation starts after approach");
        helper.assertFalse(menu.clickMenuButton(fixture.player(), 0), "duplicate repair request rejected");
        for (ClickType type : ClickType.values()) menu.clicked(0, 0, type, fixture.player());
        helper.assertTrue(menu.quickMoveStack(fixture.player(), 0).isEmpty(), "working item cannot be shift-clicked out");
        helper.onEachTick(menu::broadcastChanges);
        helper.runAfterDelay(40, () -> {
            helper.assertValueEqual(menu.getSlot(0).getItem().getDamageValue(), 1000, "repair waits for the animation");
            helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 4, "materials held until completion");
            helper.assertValueEqual(VillagerCurrencyPayment.count(fixture.player()), 64, "currency held until completion");
        });
        helper.runAfterDelay(105, () -> {
            helper.assertValueEqual(menu.getSlot(0).getItem().getDamageValue(), 454, "repair finishes after hammering");
            helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "one material used");
            helper.assertValueEqual(VillagerCurrencyPayment.count(fixture.player()), 58, "one payment collected");
            helper.assertFalse(SmithRepairAnimationState.isActive(fixture.villager()), "animation clears after returning to the player");
            menu.removed(fixture.player());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "smith_repairs")
    public static void interruptionAndMissingWorkstationsPreserveItemsAndPayment(GameTestHelper helper) {
        Fixture fixture = fixture(helper, VillagerProfession.TOOLSMITH);
        SmithRepairMenu menu = fixture.menu();
        menu.getSlot(0).set(damaged(Items.DIAMOND_PICKAXE, 1000));
        menu.getSlot(1).set(new ItemStack(Items.DIAMOND, 4));
        helper.assertTrue(menu.clickMenuButton(fixture.player(), 0), "repair starts before workstation removal");
        var state = (SmithRepairAnimationState) fixture.villager();
        BlockPos station = state.villagerretaliation$repairStation();
        helper.getLevel().removeBlock(station, false);
        menu.broadcastChanges();
        helper.assertFalse(SmithRepairAnimationState.isActive(fixture.villager()), "broken workstation cancels animation");
        helper.assertFalse(menu.clickMenuButton(fixture.player(), 0), "repair requires a matching workstation");
        helper.assertValueEqual(menu.getSlot(0).getItem().getDamageValue(), 1000, "cancelled item stays damaged");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 4, "cancelled material stays intact");
        helper.assertValueEqual(VillagerCurrencyPayment.count(fixture.player()), 64, "cancelled repair costs nothing");
        helper.getLevel().setBlockAndUpdate(station, net.minecraft.world.level.block.Blocks.SMITHING_TABLE.defaultBlockState());
        menu.broadcastChanges();
        helper.assertTrue(menu.clickMenuButton(fixture.player(), 0), "repair restarts after workstation restoration");
        menu.removed(fixture.player());
        menu.removed(fixture.player());
        helper.assertFalse(SmithRepairAnimationState.isActive(fixture.villager()), "closing cancels the animation");
        helper.assertValueEqual(count(fixture.player(), Items.DIAMOND_PICKAXE), 1, "closing returns exactly one original item");
        helper.assertValueEqual(count(fixture.player(), Items.DIAMOND), 4, "closing returns all material");
        helper.assertValueEqual(VillagerCurrencyPayment.count(fixture.player()), 64, "closing unfinished repair costs nothing");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "smith_repairs", timeoutTicks = 500)
    public static void armorerWalksToWorkstationAndReturnsToPlayer(GameTestHelper helper) {
        walkingRepair(helper, VillagerProfession.ARMORER);
    }

    @GameTest(template = "empty", batch = "smith_repairs", timeoutTicks = 500)
    public static void toolsmithWalksToWorkstationAndReturnsToPlayer(GameTestHelper helper) {
        walkingRepair(helper, VillagerProfession.TOOLSMITH);
    }

    @GameTest(template = "empty", batch = "smith_repairs", timeoutTicks = 500)
    public static void weaponsmithWalksToWorkstationAndReturnsToPlayer(GameTestHelper helper) {
        walkingRepair(helper, VillagerProfession.WEAPONSMITH);
    }

    @GameTest(template = "empty", batch = "smith_repairs", timeoutTicks = 500)
    public static void toolsmithStepsDownBeforeHammering(GameTestHelper helper) {
        walkingRepair(helper, VillagerProfession.TOOLSMITH, true, false);
    }

    @GameTest(template = "empty", batch = "smith_repairs", timeoutTicks = 500)
    public static void repairWalkKeepsTheMenuValid(GameTestHelper helper) {
        walkingRepair(helper, VillagerProfession.TOOLSMITH, false, true);
    }

    @GameTest(template = "empty", batch = "smith_repairs", timeoutTicks = 500)
    public static void toolsmithWalksOutOfFarmland(GameTestHelper helper) {
        walkingRepair(helper, VillagerProfession.TOOLSMITH, false, false, true, false);
    }

    @GameTest(template = "empty", batch = "smith_repairs", timeoutTicks = 500)
    public static void toolsmithWalksAroundAnObstruction(GameTestHelper helper) {
        walkingRepair(helper, VillagerProfession.TOOLSMITH, false, false, false, true);
    }

    @GameTest(template = "empty", batch = "smith_repairs")
    public static void playerLeavingDuringRepairStillCancels(GameTestHelper helper) {
        Fixture fixture = fixture(helper, VillagerProfession.TOOLSMITH);
        var menu = fixture.menu();
        menu.getSlot(0).set(damaged(Items.IRON_PICKAXE, 200));
        helper.assertTrue(menu.clickMenuButton(fixture.player(), 0), "repair starts");
        fixture.player().setPos(fixture.player().getX() + 10, fixture.player().getY(), fixture.player().getZ());
        helper.assertFalse(menu.stillValid(fixture.player()), "leaving the interaction origin invalidates the menu");
        menu.broadcastChanges();
        helper.assertFalse(SmithRepairAnimationState.isActive(fixture.villager()), "leaving stops the animation");
        helper.assertValueEqual(menu.getSlot(0).getItem().getDamageValue(), 200, "cancelled item remains damaged");
        helper.assertValueEqual(VillagerCurrencyPayment.count(fixture.player()), 64, "cancelled repair costs nothing");
        menu.removed(fixture.player());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "smith_repairs", timeoutTicks = 180)
    public static void repairMenuBringsADistantSmithToThePlayer(GameTestHelper helper) {
        Fixture fixture = walkingFixture(helper);
        var smith = fixture.villager();
        fixture.player().setPos(smith.getX() + 5.8, smith.getY(), smith.getZ());
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(smith.distanceToSqr(fixture.player()) <= 1.75 * 1.75, "repair menu brings the smith to the player");
        }).thenExecute(() -> fixture.menu().removed(fixture.player())).thenSucceed();
    }

    @GameTest(template = "empty", batch = "smith_repairs", timeoutTicks = 400)
    public static void smithOpensADoorOnTheFinalApproach(GameTestHelper helper) {
        Fixture fixture = walkingFixture(helper);
        var smith = fixture.villager();
        BlockPos doorway = smith.blockPosition();
        var door = net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH);
        helper.getLevel().setBlock(doorway, door, 2);
        helper.getLevel().setBlock(doorway.above(), door.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 2);
        smith.setPos(doorway.getX() + 0.5, doorway.getY(), doorway.getZ() - 0.35);
        fixture.menu().getSlot(0).set(damaged(Items.IRON_PICKAXE, 200));
        helper.assertTrue(fixture.menu().clickMenuButton(fixture.player(), 0), "repair accepts the reachable doorway workstation");
        helper.onEachTick(fixture.menu()::broadcastChanges);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(SmithRepairAnimationState.isWorking(smith), "smith opens the door before hammering: " + smith.position());
        }).thenExecute(() -> fixture.menu().removed(fixture.player())).thenSucceed();
    }

    @GameTest(template = "empty", batch = "smith_repairs", timeoutTicks = 400)
    public static void smithWorksFromALowerSlabBesideTheStation(GameTestHelper helper) {
        Fixture fixture = walkingFixture(helper);
        var smith = fixture.villager();
        BlockPos station = smith.blockPosition().south();
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL)
            helper.getLevel().setBlockAndUpdate(station.relative(direction).below(),
                    net.minecraft.world.level.block.Blocks.STONE_SLAB.defaultBlockState());
        smith.setPos(smith.getX() + 3, smith.getY(), smith.getZ());
        fixture.menu().getSlot(0).set(damaged(Items.IRON_PICKAXE, 200));
        helper.assertTrue(fixture.menu().clickMenuButton(fixture.player(), 0), "repair accepts the slab approach");
        helper.onEachTick(fixture.menu()::broadcastChanges);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(SmithRepairAnimationState.isWorking(smith), "smith reaches the slab beside the station: " + smith.position());
        }).thenExecute(() -> {
            helper.assertTrue(Math.abs(smith.getY() - (station.getY() - 0.5)) < 0.1, "smith stands on the actual slab surface");
            fixture.menu().removed(fixture.player());
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "smith_repairs", timeoutTicks = 350)
    public static void smithReturnsToAPlayerAtABlockCorner(GameTestHelper helper) {
        Fixture fixture = walkingFixture(helper);
        var smith = fixture.villager();
        BlockPos origin = smith.blockPosition();
        fixture.player().setPos(origin.getX() + 5.99, smith.getY(), origin.getZ() + 3.99);
        fixture.menu().getSlot(0).set(damaged(Items.IRON_PICKAXE, 200));
        helper.assertTrue(fixture.menu().clickMenuButton(fixture.player(), 0), "repair begins beside the workstation");
        complete(fixture);
        helper.assertValueEqual(fixture.menu().status(), SmithRepairMenu.Status.RETURNING, "repair begins the return walk");
        helper.onEachTick(fixture.menu()::broadcastChanges);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertFalse(SmithRepairAnimationState.isActive(smith), "return walk completes");
        }).thenExecute(() -> {
            helper.assertTrue(smith.distanceToSqr(fixture.player()) <= 1.75 * 1.75,
                    "return finishes beside the player rather than timing out: " + smith.position());
            fixture.menu().removed(fixture.player());
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "smith_repairs", timeoutTicks = 350)
    public static void aPushDuringHammeringResumesTheApproach(GameTestHelper helper) {
        Fixture fixture = walkingFixture(helper);
        var smith = fixture.villager();
        fixture.menu().getSlot(0).set(damaged(Items.IRON_PICKAXE, 200));
        helper.assertTrue(fixture.menu().clickMenuButton(fixture.player(), 0), "repair starts");
        helper.assertTrue(SmithRepairAnimationState.isWorking(smith), "smith starts hammering");
        smith.setPos(smith.getX() + 0.6, smith.getY(), smith.getZ());
        fixture.menu().broadcastChanges();
        helper.assertTrue(SmithRepairAnimationState.isActive(smith), "a push repositions the smith without cancelling the repair");
        helper.onEachTick(fixture.menu()::broadcastChanges);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(fixture.menu().getSlot(0).getItem().getDamageValue() < 200, "smith resumes and finishes hammering");
        }).thenExecute(() -> {
            helper.assertValueEqual(VillagerCurrencyPayment.count(fixture.player()), 58, "repositioning charges once");
            fixture.menu().removed(fixture.player());
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "smith_repairs", timeoutTicks = 400)
    public static void repairSuppressesCompetingGoalsAndRestoresThemOnClose(GameTestHelper helper) {
        Fixture fixture = walkingFixture(helper);
        var smith = fixture.villager();
        smith.setPos(smith.getX() + 3, smith.getY(), smith.getZ());
        fixture.player().setPos(smith.getX() + 0.5, smith.getY(), smith.getZ() - 0.5);
        int[] competingTicks = {0};
        for (var selector : List.of(smith.goalSelector, smith.targetSelector)) {
            selector.addGoal(0, new net.minecraft.world.entity.ai.goal.Goal() {
                @Override
                public boolean canUse() { return true; }

                @Override
                public boolean requiresUpdateEveryTick() { return true; }

                @Override
                public void tick() {
                    competingTicks[0]++;
                    smith.getNavigation().stop();
                    smith.getMoveControl().setWantedPosition(smith.getX(), smith.getY(), smith.getZ(), 0);
                }
            });
            selector.tick();
        }
        helper.assertTrue(competingTicks[0] > 0, "competing movement goals were running before repair");
        competingTicks[0] = 0;
        fixture.menu().getSlot(0).set(damaged(Items.IRON_PICKAXE, 200));
        helper.assertTrue(fixture.menu().clickMenuButton(fixture.player(), 0), "repair starts with competing goals installed");
        helper.onEachTick(fixture.menu()::broadcastChanges);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(SmithRepairAnimationState.isWorking(smith), "smith physically reaches the station despite competing goals");
        }).thenWaitUntil(() -> {
            helper.assertTrue(fixture.menu().getSlot(0).getItem().getDamageValue() < 200
                    && !SmithRepairAnimationState.isActive(smith), "smith completes the repair and return walk");
        }).thenExecute(() -> {
            helper.assertTrue(smith.distanceToSqr(fixture.player()) <= 1.75 * 1.75, "smith physically returns to the player");
            helper.assertValueEqual(competingTicks[0], 0, "goal and target selectors stay suppressed throughout repair");
            fixture.menu().removed(fixture.player());
            fixture.player().containerMenu = fixture.player().inventoryMenu;
        }).thenWaitUntil(() -> helper.assertTrue(competingTicks[0] > 0, "ordinary goals resume after closing repair")).thenSucceed();
    }

    @GameTest(template = "empty", batch = "smith_repairs")
    public static void repairKeepsItsPathWhenCombatAiIsDisabled(GameTestHelper helper) {
        Fixture fixture = walkingFixture(helper);
        var smith = fixture.villager();
        smith.setPos(smith.getX() + 3, smith.getY(), smith.getZ());
        fixture.player().setPos(smith.getX() + 0.5, smith.getY(), smith.getZ() - 0.5);
        fixture.menu().getSlot(0).set(damaged(Items.IRON_PICKAXE, 200));
        helper.assertTrue(fixture.menu().clickMenuButton(fixture.player(), 0), "repair starts");
        var path = smith.getNavigation().getPath();
        helper.assertTrue(path != null && !path.isDone(), "repair owns a workstation path");
        var enabled = com.jvn.villagerretaliation.config.VillagerRetaliationConfig.ENABLE_VILLAGER_RETALIATION;
        boolean previous = enabled.get();
        try {
            enabled.set(false);
            com.jvn.villagerretaliation.event.VillagerRetaliationEvents.onEntityTickPost(
                    new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(smith));
            helper.assertTrue(smith.getNavigation().getPath() == path && !smith.getNavigation().isDone(),
                    "passive combat cleanup cannot cancel the repair path");
        } finally {
            enabled.set(previous);
            fixture.menu().removed(fixture.player());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "smith_repairs")
    public static void openingRepairStopsExistingTradingGoalsBeforeReservingSmith(GameTestHelper helper) {
        Fixture fixture = walkingFixture(helper);
        var smith = fixture.villager();
        var tradeGoal = new net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal(smith);
        smith.goalSelector.addGoal(0, tradeGoal);
        smith.goalSelector.tick();
        helper.assertTrue(smith.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.isRunning()),
                "ordinary trading goal is already running");
        SmithRepairMenu replacement = new SmithRepairMenu(2, fixture.player().getInventory(), smith);
        fixture.player().containerMenu = replacement;
        helper.assertFalse(smith.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.isRunning()),
                "opening repair stops existing goals");
        helper.assertTrue(smith.getTradingPlayer() == fixture.player() && replacement.stillValid(fixture.player()),
                "stopping the trading goal does not release the new repair reservation");
        replacement.removed(fixture.player());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "smith_repairs")
    public static void closingRepairCancelsItsIdleApproach(GameTestHelper helper) {
        Fixture fixture = walkingFixture(helper);
        var smith = fixture.villager();
        fixture.player().setPos(smith.getX() + 5.8, smith.getY(), smith.getZ());
        fixture.menu().broadcastChanges();
        helper.assertFalse(smith.getNavigation().isDone(), "repair starts its idle approach to the player");
        fixture.menu().removed(fixture.player());
        helper.assertTrue(smith.getNavigation().isDone(), "closing stops the repair-owned approach");
        helper.assertTrue(smith.getTradingPlayer() == null && SmithRepairMenu.controlling(smith) == null,
                "closing releases repair control");
        helper.succeed();
    }

    private static Fixture walkingFixture(GameTestHelper helper) {
        Fixture fixture = fixture(helper, VillagerProfession.TOOLSMITH);
        BlockPos floor = fixture.villager().blockPosition().below();
        for (BlockPos pos : BlockPos.betweenClosed(floor.offset(-3, 0, -3), floor.offset(8, 0, 6)))
            helper.getLevel().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        fixture.villager().setNoAi(false);
        return fixture;
    }

    private static void walkingRepair(GameTestHelper helper, VillagerProfession profession) {
        walkingRepair(helper, profession, false, false);
    }

    private static void walkingRepair(GameTestHelper helper, VillagerProfession profession, boolean onStation, boolean distantPlayer) {
        walkingRepair(helper, profession, onStation, distantPlayer, false, false);
    }

    private static void walkingRepair(GameTestHelper helper, VillagerProfession profession, boolean onStation, boolean distantPlayer,
                                      boolean farmland, boolean obstruction) {
        Fixture fixture = fixture(helper, profession);
        var smith = fixture.villager();
        var menu = fixture.menu();
        BlockPos floor = smith.blockPosition().below();
        for (BlockPos pos : BlockPos.betweenClosed(floor.offset(-1, 0, -1), floor.offset(10, 0, 3)))
            helper.getLevel().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        smith.setNoAi(false);
        smith.setPos(smith.getX() + 3, smith.getY(), smith.getZ());
        fixture.player().setPos(smith.getX() + 0.5, smith.getY(), smith.getZ() - 0.5);
        if (onStation) {
            smith.setPos(floor.getX() + 0.5, floor.getY() + 2, floor.getZ() + 1.5);
            smith.setOnGround(true);
        }
        if (distantPlayer) fixture.player().setPos(smith.getX() + 6, smith.getY(), smith.getZ());
        if (farmland) {
            for (BlockPos pos : BlockPos.betweenClosed(floor.offset(2, 0, -1), floor.offset(5, 0, 3))) {
                helper.getLevel().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(pos.above(), net.minecraft.world.level.block.Blocks.WHEAT.defaultBlockState());
            }
            smith.setPos(smith.getX(), smith.getY() - 0.0625, smith.getZ());
        }
        if (obstruction) {
            for (BlockPos pos : BlockPos.betweenClosed(floor.offset(2, 1, -1), floor.offset(2, 3, 1)))
                helper.getLevel().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }
        Item item = profession == VillagerProfession.ARMORER ? Items.IRON_CHESTPLATE
                : profession == VillagerProfession.TOOLSMITH ? Items.IRON_PICKAXE : Items.IRON_SWORD;
        menu.getSlot(0).set(damaged(item, 200));
        helper.assertTrue(menu.clickMenuButton(fixture.player(), 0), "repair starts with an approach");
        helper.assertFalse(SmithRepairAnimationState.isWorking(smith), "hammering waits until the smith reaches the station");
        helper.onEachTick(menu::broadcastChanges);
        var workingPosition = new net.minecraft.world.phys.Vec3[1];
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(SmithRepairAnimationState.isWorking(smith), "smith reaches the workstation: " + smith.position() + ", " + menu.status());
        }).thenExecute(() -> {
            workingPosition[0] = smith.position();
            helper.assertTrue(smith.onGround(), "smith has landed beside the workstation before hammering");
            helper.assertTrue(Math.abs(smith.getY() - (floor.getY() + 1)) < 0.1, "smith works at ground level");
            helper.assertFalse(smith.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(floor.above().south())),
                    "smith stands beside the workstation");
            helper.assertValueEqual(menu.getSlot(0).getItem().getDamageValue(), 200, "approach does not repair early");
        }).thenWaitUntil(() -> {
            helper.assertValueEqual(menu.status(), SmithRepairMenu.Status.RETURNING, "smith carries the repaired item back");
        }).thenExecute(() -> {
            helper.assertFalse(SmithRepairAnimationState.isWorking(smith), "hammering stops before the return walk");
            helper.assertTrue(menu.getSlot(0).getItem().getDamageValue() < 200, "item repaired before the return walk");
            helper.assertFalse(menu.clickMenuButton(fixture.player(), 0), "return walk prevents repeated repairs");
            helper.assertTrue(menu.quickMoveStack(fixture.player(), 0).isEmpty(), "return walk keeps the item protected");
        }).thenWaitUntil(() -> {
            helper.assertFalse(SmithRepairAnimationState.isActive(smith), "smith finishes returning to the player");
        }).thenExecute(() -> {
            helper.assertTrue(smith.distanceToSqr(fixture.player()) <= 1.75 * 1.75, "smith arrives back near the player");
            helper.assertTrue(smith.position().distanceToSqr(workingPosition[0]) > 1, "smith walks away from the workstation");
            helper.assertValueEqual(VillagerCurrencyPayment.count(fixture.player()), 58, "one payment collected for the full sequence");
            menu.removed(fixture.player());
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "smith_repairs")
    public static void closingDuringReturnPreservesCompletedRepair(GameTestHelper helper) {
        Fixture fixture = fixture(helper, VillagerProfession.TOOLSMITH);
        fixture.player().setPos(fixture.player().getX() + 3, fixture.player().getY(), fixture.player().getZ());
        var menu = fixture.menu();
        menu.getSlot(0).set(damaged(Items.DIAMOND_PICKAXE, 1000));
        menu.getSlot(1).set(new ItemStack(Items.DIAMOND, 4));
        helper.assertTrue(menu.clickMenuButton(fixture.player(), 0), "repair starts");
        complete(fixture);
        helper.assertValueEqual(menu.status(), SmithRepairMenu.Status.RETURNING, "completed repair starts the return walk");
        menu.removed(fixture.player());
        menu.removed(fixture.player());
        helper.assertFalse(SmithRepairAnimationState.isActive(fixture.villager()), "closing clears the return animation");
        helper.assertValueEqual(count(fixture.player(), Items.DIAMOND_PICKAXE), 1, "closing returns one repaired item");
        ItemStack returned = fixture.player().getInventory().items.stream().filter(stack -> stack.is(Items.DIAMOND_PICKAXE)).findFirst().orElseThrow();
        helper.assertValueEqual(returned.getDamageValue(), 454, "completed repair survives closing");
        helper.assertValueEqual(count(fixture.player(), Items.DIAMOND), 3, "closing returns unused material");
        helper.assertValueEqual(VillagerCurrencyPayment.count(fixture.player()), 58, "completed repair charged once");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "smith_repairs")
    public static void missingPaymentAtCompletionDoesNotConsumeEquipmentOrMaterial(GameTestHelper helper) {
        Fixture fixture = fixture(helper, VillagerProfession.TOOLSMITH);
        fixture.menu().getSlot(0).set(damaged(Items.DIAMOND_PICKAXE, 1000));
        fixture.menu().getSlot(1).set(new ItemStack(Items.DIAMOND, 4));
        helper.assertTrue(fixture.menu().clickMenuButton(fixture.player(), 0), "repair starts while funds are available");
        fixture.player().getInventory().clearContent();
        complete(fixture);
        helper.assertValueEqual(fixture.menu().status(), SmithRepairMenu.Status.FUNDS, "payment is checked again at completion");
        helper.assertValueEqual(fixture.menu().getSlot(0).getItem().getDamageValue(), 1000, "unpaid item remains unchanged");
        helper.assertValueEqual(fixture.menu().getSlot(1).getItem().getCount(), 4, "unpaid repair consumes no material");
        helper.assertFalse(SmithRepairAnimationState.isActive(fixture.villager()), "failed payment clears animation");
        fixture.menu().removed(fixture.player());
        helper.succeed();
    }

    private static ItemStack damaged(Item item, int damage) {
        ItemStack stack = new ItemStack(item);
        stack.setDamageValue(damage);
        return stack;
    }

    private static int count(ServerPlayer player, Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static ResourceManager resources(GameTestHelper helper, String path, String json) {
        PackResources source = helper.getLevel().getServer().getResourceManager()
                .getResource(VillagerRetaliation.id("smith_repairs/prices.json")).orElseThrow().source();
        Resource resource = new Resource(source, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        return new MemoryResources(VillagerRetaliation.id(path), resource);
    }

    private record Fixture(ServerPlayer player, Villager villager, SmithRepairMenu menu) {
    }

    private record MemoryResources(ResourceLocation id, Resource resource) implements ResourceManager {
        @Override
        public Set<String> getNamespaces() { return Set.of(id.getNamespace()); }
        @Override
        public Optional<Resource> getResource(ResourceLocation location) { return id.equals(location) ? Optional.of(resource) : Optional.empty(); }
        @Override
        public List<Resource> getResourceStack(ResourceLocation location) { return id.equals(location) ? List.of(resource) : List.of(); }
        @Override
        public Map<ResourceLocation, Resource> listResources(String root, Predicate<ResourceLocation> filter) {
            return id.getPath().startsWith(root + "/") && filter.test(id) ? Map.of(id, resource) : Map.of();
        }
        @Override
        public Map<ResourceLocation, List<Resource>> listResourceStacks(String root, Predicate<ResourceLocation> filter) {
            return listResources(root, filter).isEmpty() ? Map.of() : Map.of(id, List.of(resource));
        }
        @Override
        public Stream<PackResources> listPacks() { return Stream.of(resource.source()); }
    }
}
