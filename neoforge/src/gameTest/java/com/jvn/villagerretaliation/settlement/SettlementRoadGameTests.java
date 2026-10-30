package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.client.settlement.SettlementPathEditor;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderConstructionCost;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderSitePlanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.network.SettlementPathRequestPayload;
import com.jvn.villagerretaliation.network.SettlementPathResultPayload;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_road")
@PrefixGameTestTemplate(false)
public final class SettlementRoadGameTests {
    @GameTest(template = "empty")
    public static void palettesQuoteFractionalMaterialsAndSurviveReload(GameTestHelper helper) {
        var base = ground(helper, 12);
        var level = helper.getLevel();
        for (var material : SettlementPath.Material.values()) {
            for (var style : SettlementPath.Style.values()) {
                var path = new SettlementPath(path(base, base.east(11), material).nodes(), 3, style, material, 42, false);
                var preview = SettlementPathTerrain.preview(level, path, pos -> true);
                helper.assertTrue(preview.error().isEmpty(), preview.error());
                helper.assertValueEqual(preview, SettlementPathTerrain.preview(level, path, pos -> true), "material pattern is stable");
                var order = SettlementPathTerrain.order(preview);
                var id = UUID.randomUUID();
                var plan = order.plan(id);
                var restored = SettlementConstructionOrder.load(order.save()).plan(id);
                helper.assertValueEqual(BuilderStructureScanner.fingerprint(restored), BuilderStructureScanner.fingerprint(plan),
                        "palette, tools and discounted quote survive reload");
                if (material == SettlementPath.Material.DIRT) {
                    helper.assertTrue(BuilderConstructionCost.requirements(plan).isEmpty(), "dirt paths remain labor only");
                    continue;
                }
                var requirements = BuilderConstructionCost.requirements(plan);
                helper.assertValueEqual(plan.entry().constructionCost().mode(), BuilderConstructionCost.Mode.SIMPLIFIED, "roads use simplified billing");
                for (var requirement : requirements) {
                    long blocks = plan.blocks().stream().filter(block -> requirement.matches(block.requiredItem())).count();
                    helper.assertValueEqual(requirement.count(), Math.toIntExact((blocks + 3) / 4), "each material rounds once at one item per four blocks");
                }
                helper.assertTrue(plan.blocks().stream().allMatch(block -> block.toolAction() == BuilderStructureScanner.BuilderToolAction.SHOVEL_PAVE),
                        "soil preparation requires shovel durability for every road block");
                if (material == SettlementPath.Material.MIXED) {
                    helper.assertValueEqual(requirements.size(), 3, "mixed roads include gravel, cobblestone and andesite");
                    var reverse = new SettlementPath(List.of(path.nodes().getLast(), path.nodes().getFirst()), 3, style, material, 42, false);
                    var reversed = SettlementPathTerrain.preview(level, reverse, pos -> true);
                    for (var cell : preview.cells()) helper.assertTrue(reversed.cells().stream().anyMatch(other ->
                            other.position().equals(cell.position()) && other.target().equals(cell.target())), "reversing a road keeps its palette");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gravelSkipsUnsupportedGroundAndPreservesObstacles(GameTestHelper helper) {
        var base = ground(helper, 8);
        var level = helper.getLevel();
        level.setBlock(base.east().below(), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(base.east(2), Blocks.FARMLAND.defaultBlockState(), 2);
        level.setBlock(base.east(3), Blocks.CHEST.defaultBlockState(), 2);
        level.setBlock(base.east(4).above(), Blocks.POPPY.defaultBlockState(), 2);
        level.setBlock(base.east(5), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(base.east(6), Blocks.GRAVEL.defaultBlockState(), 2);
        var path = path(base, base.east(7), SettlementPath.Material.GRAVEL);
        var preview = SettlementPathTerrain.preview(level, path, pos -> true);
        helper.assertTrue(preview.error().isEmpty(), preview.error());
        helper.assertValueEqual(preview.count(SettlementPathTerrain.Kind.CONVERT), 2L, "only safe ground converts");
        helper.assertValueEqual(preview.count(SettlementPathTerrain.Kind.EXISTING), 1L, "existing gravel is free");
        helper.assertValueEqual(preview.quote().getFirst().count(), 1, "skipped and existing cells do not enter the bill");
        SettlementPathTerrain.apply(level, preview);
        helper.assertTrue(level.getBlockState(base.east()).is(Blocks.DIRT), "unsupported gravel is never placed");
        helper.assertTrue(level.getBlockState(base.east(3)).is(Blocks.CHEST), "containers survive");
        helper.assertTrue(level.getBlockState(base.east(4).above()).is(Blocks.POPPY), "flowers survive");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void materialChangesInvalidateReviewsAndParticipateInHistory(GameTestHelper helper) {
        var editor = new SettlementPathEditor(() -> 1000);
        var id = UUID.randomUUID();
        var base = ground(helper, 8);
        editor.begin(false);
        editor.draft().place(base.getCenter());
        editor.draft().finish();
        editor.draft().place(base.east(7).getCenter());
        editor.draft().finish();
        editor.tick(helper.getLevel(), id, pos -> true, request -> {});
        editor.accept(new SettlementPathResultPayload(id, 1, UUID.randomUUID(), SettlementPathResultPayload.Status.PREVIEW, List.of(), ""));
        helper.assertTrue(editor.canConfirm(), "initial draft can be reviewed");
        editor.draft().material(SettlementPath.Material.MIXED);
        helper.assertFalse(editor.canConfirm(), "changing material invalidates old approval immediately");
        var mixed = editor.draft().path();
        helper.assertValueEqual(editor.draft().preview(base.south(4).getCenter()).material(), mixed.material(), "cursor preview keeps material");
        editor.draft().undo();
        helper.assertValueEqual(editor.draft().path().material(), SettlementPath.Material.DIRT, "undo restores material");
        editor.draft().redo();
        helper.assertValueEqual(editor.draft().path(), mixed, "redo restores the same palette seed");
        editor.cancel(id, request -> {});
        editor.begin(false);
        helper.assertValueEqual(editor.draft().path().material(), SettlementPath.Material.MIXED, "material persists between drafts");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            for (var material : SettlementPath.Material.values()) {
                var request = new SettlementPathRequestPayload(id, 1, SettlementPathRequestPayload.Action.PREVIEW, id, path(base, base.east(7), material));
                SettlementPathRequestPayload.STREAM_CODEC.encode(buffer, request);
                helper.assertValueEqual(SettlementPathRequestPayload.STREAM_CODEC.decode(buffer), request, "each road material round trips");
            }
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void roadConfirmationRefreshesSupportAndQueuesTheReviewedBill(GameTestHelper helper) {
        var f = SettlementPathConstructionGameTests.fixture(helper);
        try {
            f.town().removeDuty(f.builder().getUUID());
            var level = helper.getLevel();
            var base = f.origin().offset(2, -1, 4);
            for (int i = 0; i < 8; i++) level.setBlock(base.east(i).below(), Blocks.DIRT.defaultBlockState(), 2);
            var session = new SettlementPathSession();
            var id = UUID.randomUUID();
            var preview = session.request(level, id, true, f.town()::contains,
                    new SettlementPathRequestPayload(id, 1, SettlementPathRequestPayload.Action.PREVIEW, id,
                            path(base, base.east(7), SettlementPath.Material.GRAVEL)), f.town(), f.leader());
            helper.assertValueEqual(preview.status(), SettlementPathResultPayload.Status.PREVIEW, preview.message());
            helper.assertTrue(preview.message().contains("2") && preview.message().contains("Gravel"), "server quotes the discounted gravel bill");
            var approved = SettlementPathTerrain.order(SettlementPathTerrain.preview(level, path(base, base.east(7), SettlementPath.Material.GRAVEL), f.town()::contains));
            var approvedPlan = approved.plan(UUID.randomUUID());
            var gravel = approvedPlan.blocks().stream().filter(block -> approvedPlan.worldPos(approved.origin(), block).equals(base.east())).findFirst().orElseThrow();
            level.setBlock(base.east().below(), Blocks.AIR.defaultBlockState(), 2);
            helper.assertFalse(BuilderSitePlanner.placementCheck(level, null, base.east(), gravel, true).valid(), "builders recheck gravel support before placement");
            var refreshed = session.request(level, id, true, f.town()::contains,
                    new SettlementPathRequestPayload(id, 1, SettlementPathRequestPayload.Action.CONFIRM, preview.token(), null), f.town(), f.leader());
            helper.assertValueEqual(refreshed.status(), SettlementPathResultPayload.Status.PREVIEW, "changed support requires review");
            helper.assertFalse(refreshed.token().equals(preview.token()), "old token is invalidated");
            var confirm = new SettlementPathRequestPayload(id, 1, SettlementPathRequestPayload.Action.CONFIRM, refreshed.token(), null);
            var accepted = session.request(level, id, true, f.town()::contains, confirm, f.town(), f.leader());
            helper.assertValueEqual(accepted.status(), SettlementPathResultPayload.Status.APPLIED, accepted.message());
            var project = f.town().projects().get(refreshed.token());
            helper.assertValueEqual(BuilderConstructionCost.requirements(project.order().plan(project.id())).getFirst().count(), 2, "queued quote matches seven safe blocks");
            helper.assertValueEqual(SettlementProject.load(project.save()).order().save(), project.order().save(), "queued road survives reload");
            helper.assertTrue(level.getBlockState(base).is(Blocks.DIRT), "confirmation never places road instantly");
            helper.assertValueEqual(session.request(level, id, true, f.town()::contains, confirm, f.town(), f.leader()), accepted, "retry queues once");
        } finally { f.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200, batch = "road_native")
    public static void nativeRoadUsesDiscountedEscrowAndFullToolWear(GameTestHelper helper) {
        var f = SettlementPathConstructionGameTests.fixture(helper);
        SettlementConstructionCrewGameTests.adopt(helper, f);
        var base = f.origin().offset(2, -1, 4);
        for (int i = 0; i < 8; i++) {
            helper.getLevel().setBlock(base.east(i).below(), Blocks.DIRT.defaultBlockState(), 2);
            if (i >= 4) helper.getLevel().setBlock(base.east(i), Blocks.COBBLESTONE.defaultBlockState(), 2);
        }
        var preview = SettlementPathTerrain.preview(helper.getLevel(), path(base, base.east(7), SettlementPath.Material.GRAVEL), f.town()::contains);
        helper.assertTrue(preview.error().isEmpty(), preview.error());
        var order = SettlementPathTerrain.order(preview);
        var id = UUID.randomUUID();
        helper.assertTrue(order.commission(f.leader(), f.town(), id).isEmpty(), "road queues");
        var project = f.town().projects().get(id);
        var plan = order.plan(id);
        helper.assertTrue(project.pending(), "road waits for discounted materials");
        SettlementConstructionGameTests.fundAndDispatch(helper, f.town(), plan, f.origin(), id);
        SettlementConstructionMaterials.collect(f.town(), project, f.builder().getUUID(), SettlementConstructionMaterials.collectionTarget(project));
        var inventory = HiredJobInventory.getJobInventory(f.builder());
        var worker = new BuilderWorker();
        helper.startSequence().thenWaitUntil(() -> {
            work(helper, f, project, worker);
            helper.assertValueEqual(project.blocker(), "missing_placement_tool", "road waits for a shovel");
        }).thenExecute(() -> inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_SHOVEL)))
                .thenWaitUntil(() -> {
                    work(helper, f, project, worker);
                    helper.assertTrue(helper.getLevel().getBlockState(base).is(Blocks.GRAVEL), "soil is paved with the shovel");
                    helper.assertValueEqual(project.blocker(), "missing_placement_tool", "repaving stone waits for a pickaxe");
                }).thenExecute(() -> inventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.IRON_PICKAXE)))
                .thenWaitUntil(() -> {
                    work(helper, f, project, worker);
                    helper.assertValueEqual(project.status(), SettlementProject.Status.COMPLETED, "road finishes: " + project.blocker());
                }).thenExecute(() -> {
                    for (int i = 0; i < 8; i++) helper.assertTrue(helper.getLevel().getBlockState(base.east(i)).is(Blocks.GRAVEL), "all eight road blocks are paved");
                    helper.assertValueEqual(inventory.findTool(stack -> stack.is(Items.IRON_SHOVEL)).getDamageValue(), 4, "four soil cells wear the shovel four times");
                    helper.assertValueEqual(inventory.findTool(stack -> stack.is(Items.IRON_PICKAXE)).getDamageValue(), 4, "four stone cells wear the pickaxe four times");
                    var quote = project.materialEscrow().getList("Quote", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
                    helper.assertValueEqual(quote.getInt("Spent"), 2, "eight road blocks spend only two gravel");
                    f.builder().discard();
                }).thenSucceed();
    }

    private static BlockPos ground(GameTestHelper helper, int length) {
        var base = helper.absolutePos(new BlockPos(1, 20, 1));
        for (int x = -2; x <= length + 2; x++) for (int z = -2; z <= 2; z++) for (int y = -1; y <= 4; y++)
            helper.getLevel().setBlock(base.offset(x, y, z), y <= 0 ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        return base;
    }

    private static SettlementPath path(BlockPos first, BlockPos last, SettlementPath.Material material) {
        return new SettlementPath(List.of(new SettlementPath.Node(first.getCenter()), new SettlementPath.Node(last.getCenter())),
                1, SettlementPath.Style.CLEAN, material, 42, false);
    }

    private static void work(GameTestHelper helper, SettlementConstructionGameTests.Fixture f, SettlementProject project, BuilderWorker worker) {
        var plan = project.order().plan(project.id());
        if (project.site() != null) SettlementConstructionSiteService.refresh(helper.getLevel(), project.site());
        SettlementConstructionService.reconcile(f.builder());
        int index = SettlementConstructionCoordination.select(f.town(), project, f.builder().getUUID(), plan,
                helper.getLevel(), helper.getLevel().getGameTime());
        if (index >= 0 && index < project.total())
            f.builder().setPos(plan.worldPos(project.origin(), plan.blocks().get(index)).north(2).atY(f.origin().getY()).getBottomCenter());
        var session = HiredWorkSession.create(helper.getLevel(), f.builder(), HiredVillagerRole.BUILDER);
        worker.tick(helper.getLevel(), f.builder(), null, session.context());
        SettlementConstructionService.observe(f.builder(), HiredWorkStateStore.state(f.builder()));
    }
}
