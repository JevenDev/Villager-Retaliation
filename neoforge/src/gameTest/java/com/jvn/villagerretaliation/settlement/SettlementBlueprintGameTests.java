package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementBlueprintGameTests {
    private static final ResourceLocation HOUSE = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1");

    private SettlementBlueprintGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_blueprints")
    public static void draftingRequiresConstructionAccessAndConsumesExactlyOnePaper(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var player = fixture.player();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAPER, 2));
        helper.assertFalse(SettlementBlueprintService.draft(player, fixture.town(), HOUSE), "Camp does not unlock settlement blueprints");
        fixture.town().advance();
        var delegate = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "DraftDelegate"));
        delegate.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAPER));
        fixture.town().setPermissions(player.getUUID(), delegate.getUUID(), Set.of(SettlementPermission.STAFF));
        helper.assertFalse(SettlementBlueprintService.draft(delegate, fixture.town(), HOUSE), "staff access cannot draft construction plans");
        helper.assertValueEqual(delegate.getMainHandItem().getCount(), 1, "denied drafting does not consume paper");
        helper.assertFalse(SettlementBlueprintService.draft(player, fixture.town(), ResourceLocation.withDefaultNamespace("missing_blueprint")),
                "the server catalog must contain the design");
        helper.assertValueEqual(player.getInventory().countItem(Items.PAPER), 2, "unknown designs do not consume paper");
        helper.assertTrue(SettlementBlueprintService.draft(player, fixture.town(), HOUSE), "one paper can be turned into a real blueprint");
        helper.assertValueEqual(player.getInventory().countItem(Items.PAPER), 1, "drafting consumes exactly one paper");
        helper.assertValueEqual(blueprintCount(player), 1, "drafting creates exactly one blueprint");
        var preview = ConstructionBlueprintItem.previewData(blueprint(player)).orElseThrow();
        helper.assertValueEqual(preview.structureId(), HOUSE, "the item contains the selected real design");
        helper.assertFalse(preview.started(), "drafting does not start a construction job");
        helper.assertTrue(fixture.town().projects().isEmpty(), "drafting does not reserve project capacity");
        fixture.town().setPermissions(player.getUUID(), delegate.getUUID(), Set.of(SettlementPermission.BUILD));
        helper.assertTrue(SettlementBlueprintService.draft(delegate, fixture.town(), HOUSE), "construction delegates can use their own paper");
        helper.assertFalse(SettlementBlueprintService.draft(delegate, fixture.town(), HOUSE), "another blueprint needs another paper");
        helper.assertValueEqual(blueprintCount(delegate), 1, "empty input cannot create additional blueprints");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_blueprints")
    public static void remoteBlueprintPlacementRotatesWithoutLoadingOrChangingTheWorld(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        fixture.town().advance();
        fixture.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAPER));
        fixture.player().getInventory().setItem(2, new ItemStack(Items.OAK_PLANKS, 7));
        int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
        helper.assertTrue(SettlementBlueprintService.draft(fixture.player(), fixture.town(), HOUSE), "remote drafting uses the catalog without loading a town");
        var blueprint = blueprint(fixture.player());
        var before = ConstructionBlueprintItem.previewData(blueprint).orElseThrow();
        helper.assertTrue(blueprint == fixture.player().getMainHandItem(), "the consumed held paper frees its inventory slot for the blueprint");
        BlockPos anchor = fixture.town().center().offset(20, 2, -20);
        SettlementTestPlots.claim(helper.getLevel(), fixture.town(), anchor.offset(-16, 0, -16), anchor.offset(16, 12, 16));
        helper.assertTrue(SettlementBlueprintService.place(fixture.player(), fixture.town(), before.jobId(), anchor, Rotation.CLOCKWISE_90),
                "a real blueprint can be rotated and positioned remotely");
        var after = ConstructionBlueprintItem.previewData(blueprint).orElseThrow();
        helper.assertValueEqual(after.jobId(), before.jobId(), "positioning keeps the blueprint identity");
        helper.assertValueEqual(after.rotation(), Rotation.CLOCKWISE_90, "the actual server blueprint rotates");
        helper.assertValueEqual(after.worldCenter().getX(), anchor.getX(), "rotation preserves the requested horizontal center");
        helper.assertValueEqual(after.worldCenter().getZ(), anchor.getZ(), "rotation preserves both center coordinates");
        helper.assertValueEqual(after.worldMin().getY(), anchor.getY(), "the requested height is the bottom of the blueprint");
        helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "drafting and preview placement do not load any town chunks");
        helper.assertValueEqual(fixture.player().getInventory().countItem(Items.OAK_PLANKS), 7, "preview placement does not consume building materials");
        helper.assertTrue(fixture.town().projects().isEmpty(), "positioning does not start building");
        helper.assertFalse(SettlementBlueprintService.place(fixture.player(), fixture.town(), before.jobId(),
                fixture.town().center().offset(fixture.town().stage().radius(), 0, 0), Rotation.NONE),
                "the whole blueprint must fit inside the settlement boundary");
        helper.assertFalse(SettlementBlueprintService.place(fixture.player(), fixture.town(), before.jobId(),
                new BlockPos(anchor.getX(), helper.getLevel().getMaxBuildHeight() - 1, anchor.getZ()), Rotation.NONE),
                "the whole blueprint must fit inside the world height");
        helper.assertValueEqual(ConstructionBlueprintItem.previewData(blueprint).orElseThrow(), after, "rejected placements leave the preview unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_blueprints")
    public static void placementRespectsHeldIdentityLocksAndConstructionAuthority(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        fixture.town().advance();
        fixture.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAPER));
        helper.assertTrue(SettlementBlueprintService.draft(fixture.player(), fixture.town(), HOUSE), "a blueprint is drafted");
        var blueprint = blueprint(fixture.player());
        var preview = ConstructionBlueprintItem.previewData(blueprint).orElseThrow();
        helper.assertFalse(SettlementBlueprintService.place(fixture.player(), fixture.town(), UUID.randomUUID(), fixture.town().center(), Rotation.NONE),
                "a request must identify the actual held blueprint");
        ConstructionBlueprintItem.togglePlacementLocked(blueprint);
        helper.assertFalse(SettlementBlueprintService.place(fixture.player(), fixture.town(), preview.jobId(), fixture.town().center(), Rotation.NONE),
                "placement locks are honored by settlement controls");
        ConstructionBlueprintItem.togglePlacementLocked(blueprint);
        var outsider = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "DraftOutsider"));
        outsider.setItemInHand(InteractionHand.MAIN_HAND, blueprint.copy());
        helper.assertFalse(SettlementBlueprintService.place(outsider, fixture.town(), preview.jobId(), fixture.town().center(), Rotation.NONE),
                "holding a blueprint does not grant construction access");
        ConstructionBlueprintItem.markStarted(blueprint, EntityType.VILLAGER.create(helper.getLevel()), 0, helper.getLevel().getGameTime());
        helper.assertFalse(SettlementBlueprintService.place(fixture.player(), fixture.town(), preview.jobId(), fixture.town().center(), Rotation.NONE),
                "commissioned blueprints cannot be moved");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_blueprints")
    public static void fullInventoryDropsTheDraftedBlueprintWithoutLosingPaperOrOutput(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        fixture.town().advance();
        for (int slot = 0; slot < fixture.player().getInventory().items.size(); slot++) {
            fixture.player().getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        }
        fixture.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAPER, 2));
        helper.assertTrue(SettlementBlueprintService.draft(fixture.player(), fixture.town(), HOUSE), "full inventories can receive the blueprint as a real drop");
        helper.assertValueEqual(fixture.player().getInventory().countItem(Items.PAPER), 1, "only one paper is consumed when inventory is full");
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, fixture.player().getBoundingBox().inflate(3.0D),
                item -> ConstructionBlueprintItem.isBlueprint(item.getItem()));
        helper.assertValueEqual(drops.size(), 1, "exactly one blueprint is dropped at the player's body");
        helper.assertValueEqual(drops.getFirst().getItem().getCount(), 1, "the dropped output is one actual item");
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_blueprints")
    public static void blueprintCommandsRoundTripAndUseTheSameServerValidation(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        fixture.town().advance();
        fixture.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAPER));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var draft = new SettlementManagementActionPayload(fixture.town().id().value(), new SettlementManagementActionPayload.DraftBlueprint(HOUSE));
            SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, draft);
            helper.assertValueEqual(SettlementManagementActionPayload.STREAM_CODEC.decode(buffer), draft, "blueprint drafting has a typed codec");
            helper.assertTrue(SettlementManagementService.execute(fixture.player(), draft), "management routes drafting through server validation");
            var preview = ConstructionBlueprintItem.previewData(blueprint(fixture.player())).orElseThrow();
            var place = new SettlementManagementActionPayload(fixture.town().id().value(),
                    new SettlementManagementActionPayload.PlaceBlueprint(preview.jobId(), fixture.town().center().above(), Rotation.COUNTERCLOCKWISE_90));
            SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, place);
            helper.assertValueEqual(SettlementManagementActionPayload.STREAM_CODEC.decode(buffer), place, "blueprint placement has a typed bounded payload");
            helper.assertTrue(SettlementManagementService.execute(fixture.player(), place), "management routes placement through server validation");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    private static int blueprintCount(ServerPlayer player) {
        int count = 0;
        for (var stack : player.getInventory().items) {
            if (ConstructionBlueprintItem.isBlueprint(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static ItemStack blueprint(ServerPlayer player) {
        return player.getInventory().items.stream().filter(ConstructionBlueprintItem::isBlueprint).findFirst().orElseThrow();
    }

    private static Fixture fixture(GameTestHelper helper, boolean remote) {
        var level = helper.getLevel();
        BlockPos body = helper.absolutePos(new BlockPos(8, 2, 8));
        BlockPos center = remote ? new BlockPos(10_000_000 + body.getX() * 8, 64, 10_000_000 + body.getZ() * 8) : body;
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "BlueprintLeader"));
        player.setPos(body.getCenter());
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, player.getUUID(), center, level.getGameTime()).orElseThrow();
        return new Fixture(player, town);
    }

    private record Fixture(ServerPlayer player, SettlementRecord town) {
    }
}
