package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementRetentionGameTests {
    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_retention")
    public static void receiptRangesRetainHolesVersionsAndRestartNamespaces(GameTestHelper helper) throws IOException {
        var owner = new Object();
        var first = SettlementReceipt.issue(owner, "construction");
        var hole = SettlementReceipt.issue(owner, "construction");
        var last = SettlementReceipt.issue(owner, "construction");
        var ledger = new SettlementReceiptLedger();
        ledger.applied(first, 1);
        ledger.applied(last, 2);
        ledger = SettlementReceiptLedger.load(disk(ledger.save()));
        helper.assertValueEqual(ledger.version(first), 1L, "a disk reload retains the first acknowledgement");
        helper.assertValueEqual(ledger.version(hole), -1L, "an unacknowledged hole never becomes a watermark");
        helper.assertValueEqual(ledger.version(last), 2L, "birth delivery phases survive independently");
        ledger.applied(hole, 1);
        ledger.applied(last, 1);
        ledger.applied(first, 1);
        helper.assertValueEqual(ledger.ranges(), 2, "adjacent acknowledgements compact without losing the higher phase");
        var restarted = SettlementReceipt.issue(new Object(), "construction");
        helper.assertFalse(restarted.stream().equals(first.stream()), "fresh process state cannot reuse a sequence namespace");
        helper.assertValueEqual(ledger.version(restarted), -1L, "new operations are not mistaken for stale acknowledgements");
        var future = ledger.save();
        future.putInt("Format", 999);
        var preserved = SettlementReceiptLedger.load(future);
        helper.assertValueEqual(preserved.save(), future, "unknown receipt formats remain byte-equivalent");
        try {
            preserved.version(first);
            helper.fail("unknown receipt data allowed replay");
        } catch (IllegalStateException expected) { }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_retention")
    public static void receiptEnduranceKeepsSequentialCompletionsConstantSize(GameTestHelper helper) throws IOException {
        var owner = new Object();
        var ledger = new SettlementReceiptLedger();
        SettlementReceipt first = null;
        int firstSize = 0;
        long start = System.nanoTime();
        for (int index = 1; index <= 100_000; index++) {
            var receipt = SettlementReceipt.issue(owner, "construction");
            if (first == null) first = receipt;
            ledger.applied(receipt, 1);
            if (index == 1000) firstSize = bytes(ledger.save());
        }
        int finalSize = bytes(ledger.save());
        helper.assertValueEqual(ledger.ranges(), 1, "100000 completed placements retain one exact range");
        helper.assertValueEqual(finalSize, firstSize, "serialized receipt size stays constant after 1000 operations");
        var restored = SettlementReceiptLedger.load(disk(ledger.save()));
        helper.assertValueEqual(restored.version(first), 1L, "the oldest operation remains protected after fresh disk load");
        com.mojang.logging.LogUtils.getLogger().info("Settlement receipt endurance: operations=100000 ranges={} bytes={} elapsed_ms={}", ledger.ranges(), finalSize, (System.nanoTime() - start) / 1_000_000);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_retention")
    public static void projectRetirementRequiresWalletAcknowledgementAndSurvivesEitherSaveOrder(GameTestHelper helper) throws IOException {
        var fixture = fixture(helper);
        var town = fixture.town();
        var builder = fixture.builder();
        var project = project(fixture, 7);
        var beforeRequest = disk(town.save(helper.getLevel().registryAccess()));
        helper.assertTrue(town.startProject(town.leader(), project), "paid project starts");
        town.finishProject(project.id(), SettlementProject.Status.COMPLETED);
        var staleTown = disk(town.save(helper.getLevel().registryAccess()));
        var staleEntity = disk(builder.saveWithoutId(new CompoundTag()));
        town.maintainProjects();
        helper.assertTrue(town.projects().containsKey(project.id()), "terminal projects keep undelivered payment obligations");
        int before = VillagerWalletService.getCurrentEmeralds(builder);
        SettlementConstructionService.synchronizePayments(town, builder);
        int paid = VillagerWalletService.getCurrentEmeralds(builder);
        helper.assertValueEqual(paid, before + 7, "the construction fee is delivered once");
        town.maintainProjects();
        helper.assertFalse(town.projects().containsKey(project.id()), "durably captured wallet acknowledgement removes the hot project");
        helper.assertTrue(town.projectHistory().containsKey(project.id()), "recent completed work retains a summary");
        var newerTown = SettlementRecord.load(disk(town.save(helper.getLevel().registryAccess())), helper.getLevel().registryAccess(), () -> {});
        var olderEntity = EntityType.VILLAGER.create(helper.getLevel());
        olderEntity.load(staleEntity);
        SettlementConstructionService.synchronizePayments(newerTown, olderEntity);
        helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(olderEntity), paid, "a newer town restores both the retired payment and its receipt to an older entity");
        var olderTown = SettlementRecord.load(staleTown, helper.getLevel().registryAccess(), () -> {});
        SettlementConstructionService.synchronizePayments(olderTown, builder);
        helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(builder), paid, "a newer entity prevents an older town from paying again");
        olderTown.maintainProjects();
        SettlementConstructionService.synchronizePayments(olderTown, builder);
        helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(builder), paid, "repeated acknowledgements remain idempotent");
        var oldestTown = SettlementRecord.load(beforeRequest, helper.getLevel().registryAccess(), () -> {});
        var repeated = SettlementProject.create(oldestTown, fixture.area().id(), builder.getUUID(), fixture.plan(), oldestTown.center(), project.id(), 0);
        repeated.reservePayment(7);
        helper.assertTrue(oldestTown.startProject(oldestTown.leader(), repeated), "a snapshot from before commissioning can receive the original request");
        oldestTown.finishProject(repeated.id(), SettlementProject.Status.COMPLETED);
        SettlementConstructionService.synchronizePayments(oldestTown, builder);
        helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(builder), paid, "regenerating a request preserves the original payment identity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_retention")
    public static void projectEnduranceBoundsHotRecordsAndHistoryWithoutForgettingRequests(GameTestHelper helper) throws IOException {
        var fixture = fixture(helper);
        var town = fixture.town();
        UUID first = null;
        int firstSize = 0;
        long start = System.nanoTime();
        for (int index = 1; index <= 2048; index++) {
            var project = project(fixture, 1);
            if (first == null) first = project.id();
            helper.assertTrue(town.startProject(town.leader(), project), "sequential project starts");
            town.finishProject(project.id(), SettlementProject.Status.COMPLETED);
            SettlementConstructionService.synchronizePayments(town, fixture.builder());
            helper.assertTrue(town.maintainProjects() <= SettlementRecord.PROJECT_MAINTENANCE_LIMIT, "maintenance has a fixed visit budget");
            if (index == 1024) firstSize = bytes(town.save(helper.getLevel().registryAccess()));
        }
        var saved = town.save(helper.getLevel().registryAccess());
        int finalSize = bytes(saved);
        helper.assertValueEqual(town.projects().size(), 0, "completed work leaves no hot payment or construction scan entries");
        helper.assertValueEqual(town.projectHistory().size(), SettlementRecord.PROJECT_HISTORY_LIMIT, "recent summaries stay bounded");
        helper.assertValueEqual(saved.getLongArray("RetiredProjects").length, (2048 - SettlementRecord.PROJECT_HISTORY_LIMIT) * 2, "old random request identities retain only exact 16-byte guards");
        helper.assertTrue(finalSize - firstSize <= 128 * 1024, "save growth is limited to exact request and payment identities");
        var restored = SettlementRecord.load(disk(saved), helper.getLevel().registryAccess(), () -> {});
        helper.assertTrue(restored.knowsProject(first), "the oldest request cannot recommission after reload");
        helper.assertValueEqual(restored.maintainProjects(), 0, "retired workload adds no maintenance scan cost");
        helper.assertValueEqual(bytes(restored.save(helper.getLevel().registryAccess())), finalSize, "repeated compaction is stable");
        com.mojang.logging.LogUtils.getLogger().info("Settlement project endurance: projects=2048 hot={} summaries={} bytes_at_1024={} bytes_at_2048={} elapsed_ms={}", town.projects().size(), town.projectHistory().size(), firstSize, finalSize, (System.nanoTime() - start) / 1_000_000);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_retention")
    public static void cancelledProjectsRetainConflictingWorldWorkAndRefundOnlyOnce(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        var project = project(fixture, 9);
        helper.assertTrue(town.startProject(town.leader(), project), "project reserves its fee");
        project.observeSite(fixture.area());
        var site = project.site();
        var pos = project.origin();
        var section = SettlementTerrainSection.capture("ConstructionSurvey", project.id(), pos, pos, helper.getLevel().getChunkAt(pos));
        site.record(section);
        site.queue(new SettlementConstructionPlacement(helper.getLevel(), List.of(new SettlementConstructionPlacement.Part(pos,
                helper.getLevel().getBlockState(pos), Blocks.GOLD_BLOCK.defaultBlockState(), null))));
        town.finishProject(project.id(), SettlementProject.Status.CANCELLED);
        var balance = town.economy().balance();
        town.maintainProjects();
        helper.assertTrue(town.projects().containsKey(project.id()), "cancelled status does not discard pending world effects");
        town.finishProject(project.id(), SettlementProject.Status.CANCELLED);
        helper.assertValueEqual(town.economy().balance(), balance, "repeated cancellation cannot refund again");
        var loaded = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        loaded.maintainProjects();
        helper.assertTrue(loaded.projects().get(project.id()).site().unsettled(), "world obligations survive compaction and reload");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "settlement_retention")
    public static void placementRetiresOnlyAfterARealChunkCheckpoint(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 4, 2));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        var placement = new SettlementConstructionPlacement(level, List.of(new SettlementConstructionPlacement.Part(pos,
                Blocks.AIR.defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState(), null)));
        var stale = placement.save();
        UUID project = UUID.randomUUID();
        helper.assertTrue(placement.reconcile(level, project), "placement applies");
        helper.assertFalse(placement.durable(level), "in-memory application is not durable acknowledgement");
        var read = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<java.util.Optional<CompoundTag>>>();
        helper.succeedWhen(() -> {
            helper.assertTrue(placement.durable(level), "asynchronous chunk flush completed");
            if (read.get() == null) read.set(level.getChunkSource().chunkMap.read(new net.minecraft.world.level.ChunkPos(pos)));
            helper.assertTrue(read.get().isDone(), "flushed chunk can be read through region storage");
            var serialized = read.get().join().orElseThrow();
            var ledger = SettlementReceiptLedger.load(serialized.getCompound(net.neoforged.neoforge.attachment.AttachmentHolder.ATTACHMENTS_NBT_KEY)
                    .getCompound("villagerretaliation:settlement_receipts"));
            var token = SettlementReceipt.load(stale.getList("Parts", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).getCompound("Receipt"));
            helper.assertValueEqual(ledger.version(token), 1L, "the real chunk save contains replay protection");
            level.setBlock(pos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
            helper.assertTrue(SettlementConstructionPlacement.load(stale).reconcile(level, project), "old settlement data recognizes the compact receipt");
            helper.assertTrue(level.getBlockState(pos).is(Blocks.DIAMOND_BLOCK), "replayed placement cannot overwrite later player work");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_retention")
    public static void legacyPaymentsAndMergedRetirementGuardsRemainUnambiguous(GameTestHelper helper) throws IOException {
        var fixture = fixture(helper);
        var town = fixture.town();
        UUID oldest = null;
        for (int index = 0; index < 40; index++) {
            var project = project(fixture, 1);
            if (oldest == null) oldest = project.id();
            helper.assertTrue(town.startProject(town.leader(), project), "legacy project starts");
            town.finishProject(project.id(), SettlementProject.Status.CANCELLED);
        }
        helper.assertValueEqual(town.maintainProjects(), 8, "one pass cannot scan the entire terminal backlog");
        helper.assertValueEqual(town.projects().size(), 32, "remaining work stays queued for later passes");
        for (int index = 0; index < 4; index++) town.maintainProjects();
        var calf = EntityType.COW.create(helper.getLevel());
        calf.setPos(town.center().getCenter());
        var birth = SettlementAnimalBirth.create(calf, 2);
        town.recordAnimalBirth(birth);
        var target = new SettlementRecord(VillageAllegianceId.random(), town.dimension(), town.center().east(48), town.leader(), 0, () -> {});
        var merged = SettlementRecord.load(disk(SettlementLifecyclePlan.merge(helper.getLevel(), town, target)), helper.getLevel().registryAccess(), () -> {});
        helper.assertValueEqual(merged.animalBirths().get(birth.id()).receiptTown(merged), town.id().value(),
                "a merger preserves the original chunk receipt namespace for pending births");
        helper.assertTrue(merged.knowsProject(oldest), "merged towns retain old random request tombstones");
        helper.assertTrue(merged.projectHistory().values().stream().allMatch(project -> project.town().equals(target.id().value())), "retained summary references use the merged identity");
        helper.assertValueEqual(merged.projects().size(), 0, "merging never restores retired records to hot processing");
        var legacy = fixture(helper);
        var project = project(legacy, 3);
        legacy.town().startProject(legacy.town().leader(), project);
        legacy.town().finishProject(project.id(), SettlementProject.Status.COMPLETED);
        SettlementConstructionService.synchronizePayments(legacy.town(), legacy.builder());
        var saved = legacy.town().save(helper.getLevel().registryAccess());
        for (var raw : saved.getList("ResidentCare", net.minecraft.nbt.Tag.TAG_COMPOUND)) ((CompoundTag) raw).getCompound("Wallet").remove("ConstructionPayments");
        var restored = SettlementRecord.load(disk(saved), helper.getLevel().registryAccess(), () -> {});
        restored.maintainProjects();
        helper.assertTrue(restored.projectHistory().containsKey(project.id()), "legacy entity context supplies the wallet's matching receipt");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_retention")
    public static void loadedConstructionKeepsReplayWorkWhenTheSettlementSavesFirst(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, true, java.util.Map.of(BlockPos.ZERO, Blocks.GOLD_BLOCK.defaultBlockState()), fixture -> {
            var level = helper.getLevel();
            var pos = fixture.origin();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 2);
            SettlementConstructionService.placed(fixture.builder(), fixture.project().state(), pos, Blocks.AIR.defaultBlockState());
            fixture.town().finishProject(fixture.project().id(), SettlementProject.Status.COMPLETED);
            helper.assertTrue(fixture.site().unsettled(), "native placement retains a checkpoint journal");
            fixture.town().maintainProjects();
            helper.assertTrue(fixture.town().projects().containsKey(fixture.project().id()), "native completion cannot retire before its chunk is durable");
            var saved = fixture.town().save(level.registryAccess());
            var chunk = level.getChunkAt(pos);
            var receipts = chunk.writeAttachmentsToNBT(level.registryAccess());
            var type = net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES.get(ResourceLocation.parse("villagerretaliation:settlement_receipts"));
            try {
                chunk.removeData(type);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                var loaded = SettlementRecord.load(saved, level.registryAccess(), () -> {});
                var project = loaded.projects().get(fixture.project().id());
                helper.assertTrue(project.site().pending(), "fresh settlement load restores unacknowledged native effects");
                SettlementConstructionSiteService.update(level, loaded);
                helper.assertTrue(level.getBlockState(pos).is(Blocks.GOLD_BLOCK), "an older chunk receives the retained native placement");
                level.setBlock(pos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
                var older = SettlementRecord.load(saved, level.registryAccess(), () -> {});
                SettlementConstructionSiteService.update(level, older);
                helper.assertTrue(level.getBlockState(pos).is(Blocks.DIAMOND_BLOCK), "a repeated old snapshot cannot overwrite later world changes");
            } finally {
                if (receipts != null) chunk.readAttachmentsFromNBT(level.registryAccess(), receipts);
            }
        });
    }

    private static Fixture fixture(GameTestHelper helper) {
        var center = helper.absolutePos(new BlockPos(4, 4, 4));
        var town = new SettlementRecord(VillageAllegianceId.random(), helper.getLevel().dimension().location(), center, UUID.randomUUID(), 0, () -> {});
        town.advance();
        var builder = EntityType.VILLAGER.create(helper.getLevel());
        builder.setPos(center.getCenter());
        town.recordResident(SettlementResidentState.capture(builder, town, 0));
        var entry = BuilderStructureCatalog.byId(helper.getLevel().getServer(), ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1")).orElseThrow();
        var plan = BuilderStructureScanner.scan(helper.getLevel(), entry, Rotation.NONE).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.CONSTRUCTION, "Retention site", center.offset(plan.localMin()), center.offset(plan.localMax()), 5, true);
        SettlementTestPlots.claim(helper.getLevel(), town, area.min(), area.max());
        helper.assertTrue(town.putArea(town.leader(), area), "construction area registers");
        town.assignDuty(builder.getUUID(), area.id());
        town.economy().credit(CurrencyAmount.of(10000, 1));
        return new Fixture(town, builder, area, plan);
    }

    private static SettlementProject project(Fixture fixture, int fee) {
        var project = SettlementProject.create(fixture.town(), fixture.area().id(), fixture.builder().getUUID(), fixture.plan(), fixture.town().center(), UUID.randomUUID(), 0);
        project.reservePayment(fee);
        return project;
    }

    private static CompoundTag disk(CompoundTag tag) throws IOException {
        var file = Files.createTempFile("settlement-retention-", ".dat");
        try {
            NbtIo.writeCompressed(tag, file);
            return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } finally {
            Files.delete(file);
        }
    }

    private static int bytes(CompoundTag tag) throws IOException {
        var bytes = new java.io.ByteArrayOutputStream();
        NbtIo.write(tag, new java.io.DataOutputStream(bytes));
        return bytes.size();
    }

    private record Fixture(SettlementRecord town, Villager builder, SettlementArea area, BuilderStructureScanner.StructurePlan plan) { }
}
