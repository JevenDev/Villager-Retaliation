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
        ItemStack expected = item.copy();
        expected.setDamageValue(1000 - 546);
        helper.assertTrue(ItemStack.matches(menu.getSlot(0).getItem(), expected), "repair must preserve every component except damage");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 7, "exactly one material consumed");
        helper.assertValueEqual(VillagerCurrencyPayment.count(fixture.player()), before - 6, "exact fee");
        helper.assertTrue(menu.clickMenuButton(fixture.player(), 0), "second repair accepted");
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
        villager.moveTo(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
        villager.setNoAi(true);
        helper.getLevel().addFreshEntity(villager);
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "SmithRepairOwner"));
        player.moveTo(pos.getX() + 1, pos.getY(), pos.getZ(), 0, 0);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 64));
        return new Fixture(player, villager, new SmithRepairMenu(1, player.getInventory(), villager));
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
