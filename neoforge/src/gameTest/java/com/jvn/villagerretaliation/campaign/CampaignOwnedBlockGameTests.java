package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.CampaignBlockOwnership.Status;
import com.jvn.villagerretaliation.campaign.CampaignBlockOwnershipLedger.Receipt;
import com.jvn.villagerretaliation.campaign.CampaignBlockOwnershipLedger.State;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignOwnedBlockGameTests {
    private CampaignOwnedBlockGameTests() { }

    @GameTest(template = "empty", batch = "campaign_owned_blocks")
    public static void reservationPlacementAndRestorationShareNativeChunkReceipts(GameTestHelper helper) {
        try (var fixture = new CampaignOwnedBlockTestFixture(helper, Blocks.GLASS.defaultBlockState())) {
            helper.assertValueEqual(CampaignBlockOwnership.reserve(fixture.level, fixture.request).status(), Status.READY, "reservation is durable");
            helper.assertTrue(fixture.request.original().includedIn(fixture.disk(), fixture.position), "reservation preserves the original native terrain");
            fixture.reloadOwnership();
            helper.assertValueEqual(fixture.receipt().state(), State.RESERVED, "native reload retains reservation");
            helper.assertValueEqual(CampaignBlockOwnership.apply(fixture.level, fixture.request).status(), Status.APPLIED, "reserved block is applied");
            helper.assertTrue(fixture.request.placed().includedIn(fixture.disk(), fixture.position), "placed block is durable before acknowledgement");
            fixture.reloadOwnership();
            helper.assertValueEqual(fixture.receipt().state(), State.APPLIED, "placement receipt shares the native snapshot");
            helper.assertValueEqual(CampaignBlockOwnership.restore(fixture.level, fixture.request).status(), Status.RESTORED, "restoration succeeds");
            helper.assertTrue(fixture.request.original().includedIn(fixture.disk(), fixture.position), "restoration reaches native storage");
            fixture.reloadOwnership();
            helper.assertValueEqual(fixture.receipt().state(), State.RESTORED, "restoration receipt shares the native snapshot");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_owned_blocks")
    public static void inventoryEditsRemainConflictsEvenWhenTheOriginalContentsReturn(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignOwnedBlockTestFixture(helper, Blocks.CHEST.defaultBlockState())) {
            fixture.apply();
            var container = (ChestBlockEntity) fixture.level.getBlockEntity(fixture.position);
            container.setItem(0, new ItemStack(Items.DIAMOND)); container.setChanged();
            fixture.checkpoint(); fixture.reloadOwnership();
            helper.assertValueEqual(fixture.receipt().state(), State.CONFLICT, "inventory edit relinquishes campaign ownership");
            helper.assertValueEqual(CampaignBlockOwnership.restore(fixture.level, fixture.request).status(), Status.CONFLICT, "cleanup cannot overwrite player contents");
            helper.assertTrue(container.getItem(0).is(Items.DIAMOND), "player item remains in the live container");
            helper.assertTrue(CampaignBlockSnapshot.capture(fixture.level, fixture.chunk, fixture.position).includedIn(fixture.disk(), fixture.position),
                    "container contents and lost ownership are both durable");
            container.clearContent(); container.setChanged();
            fixture.checkpoint(); fixture.reloadOwnership();
            helper.assertTrue(fixture.request.placed().equals(CampaignBlockSnapshot.capture(fixture.level, fixture.chunk, fixture.position)),
                    "fixture returns the container to its original contents");
            helper.assertValueEqual(CampaignBlockOwnership.restore(fixture.level, fixture.request).status(), Status.CONFLICT,
                    "returning to identical contents does not restore campaign ownership");
            helper.assertTrue(fixture.level.getBlockEntity(fixture.position) == container, "edited container is retained");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_owned_blocks")
    public static void unchangedOwnedContainersCanBeRestoredWithoutLeavingEntities(GameTestHelper helper) {
        try (var fixture = new CampaignOwnedBlockTestFixture(helper, Blocks.CHEST.defaultBlockState())) {
            fixture.apply(); fixture.reloadOwnership();
            var result = CampaignBlockOwnership.restore(fixture.level, fixture.request);
            helper.assertValueEqual(result.status(), Status.RESTORED, "unchanged container restores: " + result.diagnostic());
            helper.assertTrue(fixture.level.getBlockState(fixture.position).isAir(), "original terrain returns");
            helper.assertTrue(fixture.level.getBlockEntity(fixture.position) == null, "temporary block entity is removed");
            helper.assertTrue(fixture.request.original().includedIn(fixture.disk(), fixture.position), "native save contains no stale block entity");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_owned_blocks")
    public static void repeatingRestorationPreservesLaterPlayerConstruction(GameTestHelper helper) {
        try (var fixture = new CampaignOwnedBlockTestFixture(helper, Blocks.GLASS.defaultBlockState())) {
            fixture.apply();
            helper.assertValueEqual(CampaignBlockOwnership.restore(fixture.level, fixture.request).status(), Status.RESTORED, "first restoration succeeds");
            fixture.level.setBlock(fixture.position, Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
            fixture.reloadOwnership();
            helper.assertValueEqual(CampaignBlockOwnership.restore(fixture.level, fixture.request).status(), Status.RESTORED, "older caller sees completed restoration");
            helper.assertTrue(fixture.level.getBlockState(fixture.position).is(Blocks.GLASS), "repeated restoration never removes later player construction");
            helper.assertValueEqual(CampaignBlockOwnership.apply(fixture.level, fixture.request).status(), Status.CONFLICT, "retired operation cannot run again");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_owned_blocks")
    public static void competingOwnersAndChangedRequestsCannotReuseAReservation(GameTestHelper helper) {
        try (var fixture = new CampaignOwnedBlockTestFixture(helper, Blocks.GLASS.defaultBlockState())) {
            helper.assertValueEqual(CampaignBlockOwnership.reserve(fixture.level, fixture.request).status(), Status.READY, "first owner reserves");
            var other = new Receipt(fixture.position, UUID.randomUUID(), UUID.randomUUID(), fixture.request.original(), fixture.request.placed(), State.RESERVED, "");
            helper.assertValueEqual(CampaignBlockOwnership.reserve(fixture.level, other).status(), Status.CONFLICT, "second owner cannot steal the position");
            var changed = new Receipt(fixture.position, fixture.request.owner(), fixture.request.operation(), fixture.request.original(),
                    CampaignBlockSnapshot.defaultState(fixture.level, fixture.position, Blocks.STONE.defaultBlockState()), State.RESERVED, "");
            helper.assertValueEqual(CampaignBlockOwnership.apply(fixture.level, changed).status(), Status.CONFLICT, "same operation cannot change its authored effect");
            helper.assertTrue(fixture.level.getBlockState(fixture.position).isAir(), "conflicting requests leave terrain unchanged");
            helper.assertValueEqual(CampaignBlockOwnership.apply(fixture.level, fixture.request).status(), Status.APPLIED, "original request remains playable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_owned_blocks")
    public static void omittedOwnershipDuringSerializationStopsReservation(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignOwnedBlockTestFixture(helper, Blocks.GLASS.defaultBlockState())) {
            fixture.checkpoint();
            Consumer<ChunkDataEvent.Save> listener = event -> {
                if (event.getChunk() == fixture.chunk)
                    event.getData().getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).remove("villagerretaliation:campaign_blocks");
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                helper.assertValueEqual(CampaignBlockOwnership.reserve(fixture.level, fixture.request).status(), Status.WAITING,
                        "incomplete native serialization cannot acknowledge reservation");
                helper.assertTrue(fixture.level.getBlockState(fixture.position).isAir(), "failed reservation cannot mutate terrain");
            } finally { NeoForge.EVENT_BUS.unregister(listener); }
            fixture.apply();
            helper.assertValueEqual(fixture.ledger().receipts().size(), 1, "repair retains one operation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_owned_blocks")
    public static void failedPlacementCheckpointReplaysOnlyTheSavedNativeReservation(GameTestHelper helper) {
        try (var fixture = new CampaignOwnedBlockTestFixture(helper, Blocks.GLASS.defaultBlockState())) {
            helper.assertValueEqual(CampaignBlockOwnership.reserve(fixture.level, fixture.request).status(), Status.READY, "reservation is saved first");
            Consumer<ChunkDataEvent.Save> listener = event -> {
                if (event.getChunk() == fixture.chunk && fixture.level.getBlockState(fixture.position).is(Blocks.GLASS))
                    event.getData().getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).remove("villagerretaliation:campaign_blocks");
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                helper.assertValueEqual(CampaignBlockOwnership.apply(fixture.level, fixture.request).status(), Status.WAITING, "failed completion checkpoint is not acknowledged");
                helper.assertTrue(fixture.request.original().includedIn(fixture.disk(), fixture.position), "older native save still contains the reserved original");
            } finally { NeoForge.EVENT_BUS.unregister(listener); }
            fixture.reloadWorld(fixture.request.original());
            helper.assertValueEqual(fixture.receipt().state(), State.RESERVED, "crash recovery restores the saved reservation");
            helper.assertValueEqual(CampaignBlockOwnership.apply(fixture.level, fixture.request).status(), Status.APPLIED, "same operation recovers after storage repair");
            helper.assertValueEqual(fixture.ledger().receipts().size(), 1, "recovery does not allocate another receipt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_owned_blocks")
    public static void failedRestorationCheckpointCannotEraseSubsequentEdits(GameTestHelper helper) {
        try (var fixture = new CampaignOwnedBlockTestFixture(helper, Blocks.GLASS.defaultBlockState())) {
            fixture.apply();
            Consumer<ChunkDataEvent.Save> listener = event -> {
                if (event.getChunk() == fixture.chunk && fixture.level.getBlockState(fixture.position).isAir())
                    event.getData().getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).remove("villagerretaliation:campaign_blocks");
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                helper.assertValueEqual(CampaignBlockOwnership.restore(fixture.level, fixture.request).status(), Status.WAITING, "unsaved restoration waits");
                helper.assertTrue(fixture.request.placed().includedIn(fixture.disk(), fixture.position), "disk retains the last acknowledged placement");
            } finally { NeoForge.EVENT_BUS.unregister(listener); }
            fixture.level.setBlock(fixture.position, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            helper.assertValueEqual(CampaignBlockOwnership.restore(fixture.level, fixture.request).status(), Status.RESTORED, "retry saves completion");
            helper.assertTrue(fixture.level.getBlockState(fixture.position).is(Blocks.DIAMOND_BLOCK), "retry preserves the new player block");
            fixture.reloadOwnership();
            helper.assertValueEqual(fixture.receipt().state(), State.RESTORED, "completion remains durable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_owned_blocks")
    public static void malformedFutureAndMissingOwnershipRemainRepairable(GameTestHelper helper) {
        try (var fixture = new CampaignOwnedBlockTestFixture(helper, Blocks.GLASS.defaultBlockState())) {
            fixture.apply();
            var original = fixture.ledger().save();
            var future = original.copy(); future.putInt("DataVersion", 99);
            var missing = original.copy(); missing.getList("Receipts", Tag.TAG_COMPOUND).getCompound(0).remove("Operation");
            var unknownBlock = original.copy();
            unknownBlock.getList("Receipts", Tag.TAG_COMPOUND).getCompound(0).getCompound("Placed").getCompound("BlockState").putString("Name", "missing:block");
            var duplicate = original.copy();
            duplicate.getList("Receipts", Tag.TAG_COMPOUND).add(duplicate.getList("Receipts", Tag.TAG_COMPOUND).getCompound(0).copy());
            var extraRoot = original.copy(); extraRoot.putString("FutureField", "retain");
            var extraReceipt = original.copy(); extraReceipt.getList("Receipts", Tag.TAG_COMPOUND).getCompound(0).putString("FutureField", "retain");
            var extraSnapshot = original.copy(); extraSnapshot.getList("Receipts", Tag.TAG_COMPOUND).getCompound(0).getCompound("Placed").putString("FutureField", "retain");
            for (var raw : List.of(future, missing, unknownBlock, duplicate, extraRoot, extraReceipt, extraSnapshot)) {
                fixture.chunk.setData(CampaignBlockOwnership.OWNERSHIP, CampaignBlockOwnershipLedger.load(raw));
                helper.assertFalse(fixture.ledger().diagnostic().isBlank(), "invalid ownership is diagnosed");
                helper.assertValueEqual(fixture.ledger().save(), raw, "unreadable native data is preserved exactly");
                helper.assertValueEqual(CampaignBlockOwnership.restore(fixture.level, fixture.request).status(), Status.WAITING, "unreadable ownership cannot restore blocks");
            }
            fixture.chunk.removeData(CampaignBlockOwnership.OWNERSHIP);
            helper.assertValueEqual(CampaignBlockOwnership.restore(fixture.level, fixture.request).status(), Status.WAITING, "missing attachment cannot invent ownership");
            fixture.chunk.setData(CampaignBlockOwnership.OWNERSHIP, new CampaignBlockOwnershipLedger());
            helper.assertValueEqual(CampaignBlockOwnership.restore(fixture.level, fixture.request).status(), Status.WAITING, "missing position receipt also waits");
            fixture.chunk.setData(CampaignBlockOwnership.OWNERSHIP, CampaignBlockOwnershipLedger.load(original));
            helper.assertValueEqual(CampaignBlockOwnership.restore(fixture.level, fixture.request).status(), Status.RESTORED, "restoring compatible ownership resumes cleanup");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_owned_blocks")
    public static void nativeChunkLoaderRestoresOwnershipAndBlockEntityTogether(GameTestHelper helper) {
        try (var fixture = new CampaignOwnedBlockTestFixture(helper, Blocks.CHEST.defaultBlockState())) {
            fixture.apply();
            var info = new net.minecraft.world.level.chunk.storage.RegionStorageInfo("campaign-test", fixture.level.dimension(), "chunk");
            var loaded = net.minecraft.world.level.chunk.storage.ChunkSerializer.read(fixture.level, fixture.level.getPoiManager(), info,
                    fixture.chunk.getPos(), fixture.disk());
            helper.assertTrue(loaded instanceof net.minecraft.world.level.chunk.ImposterProtoChunk, "native save loads as a full chunk");
            var recovered = ((net.minecraft.world.level.chunk.ImposterProtoChunk) loaded).getWrapped();
            recovered.runPostLoad();
            var receipt = CampaignBlockOwnership.receipt(recovered, fixture.position);
            helper.assertTrue(receipt != null && receipt.sameIdentity(fixture.request), "native attachment loader retains the exact operation");
            helper.assertValueEqual(receipt.state(), State.APPLIED, "native load does not misclassify restoration as an external edit");
            helper.assertValueEqual(CampaignBlockSnapshot.capture(fixture.level, recovered, fixture.position), fixture.request.placed(),
                    "native chunk reload restores the owned block entity with its receipt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_owned_blocks")
    public static void incompatibleAuthoredBlockEntityCannotMutateTheWorld(GameTestHelper helper) {
        try (var fixture = new CampaignOwnedBlockTestFixture(helper, Blocks.CHEST.defaultBlockState())) {
            var payload = fixture.request.placed().blockEntity(); payload.putString("UnknownAuthoredField", "must survive");
            var request = new Receipt(fixture.position, fixture.request.owner(), fixture.request.operation(), fixture.request.original(),
                    new CampaignBlockSnapshot(fixture.request.placed().state(), payload), State.RESERVED, "");
            helper.assertValueEqual(CampaignBlockOwnership.reserve(fixture.level, request).status(), Status.READY, "incompatible payload can retain its authored reservation");
            helper.assertValueEqual(CampaignBlockOwnership.apply(fixture.level, request).status(), Status.WAITING, "lossy block entity loading cannot apply an authored effect");
            helper.assertTrue(fixture.level.getBlockState(fixture.position).isAir(), "preflight failure leaves terrain unchanged");
            fixture.reloadOwnership();
            helper.assertValueEqual(fixture.receipt().placed().blockEntity(), payload, "authored data is retained for compatible recovery");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_owned_blocks")
    public static void omittedBlockEntityDuringSerializationCannotAcknowledgePlacement(GameTestHelper helper) {
        try (var fixture = new CampaignOwnedBlockTestFixture(helper, Blocks.CHEST.defaultBlockState())) {
            helper.assertValueEqual(CampaignBlockOwnership.reserve(fixture.level, fixture.request).status(), Status.READY, "reservation is durable");
            Consumer<ChunkDataEvent.Save> listener = event -> {
                if (event.getChunk() == fixture.chunk && fixture.level.getBlockState(fixture.position).is(Blocks.CHEST))
                    event.getData().remove("block_entities");
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                helper.assertValueEqual(CampaignBlockOwnership.apply(fixture.level, fixture.request).status(), Status.WAITING,
                        "ownership alone cannot acknowledge a save missing the block entity");
                helper.assertTrue(fixture.request.original().includedIn(fixture.disk(), fixture.position), "failed write leaves the earlier native reservation intact");
            } finally { NeoForge.EVENT_BUS.unregister(listener); }
            helper.assertValueEqual(CampaignBlockOwnership.apply(fixture.level, fixture.request).status(), Status.APPLIED, "serialization repair resumes placement");
            helper.assertTrue(fixture.request.placed().includedIn(fixture.disk(), fixture.position), "complete native snapshot includes block entity data");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_owned_blocks")
    public static void ordinaryEditsAllocateNoOwnershipAndUnloadedClaimsDoNotLoadChunks(GameTestHelper helper) {
        try (var fixture = new CampaignOwnedBlockTestFixture(helper, Blocks.GLASS.defaultBlockState())) {
            fixture.chunk.removeData(CampaignBlockOwnership.OWNERSHIP);
            fixture.level.setBlock(fixture.position, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            helper.assertFalse(fixture.chunk.hasData(CampaignBlockOwnership.OWNERSHIP), "ordinary block changes allocate no campaign metadata");
            var distant = new BlockPos(19_000_000, fixture.position.getY(), 19_000_000);
            helper.assertTrue(fixture.level.getChunkSource().getChunkNow(distant.getX() >> 4, distant.getZ() >> 4) == null, "fixture destination is unloaded");
            var request = new Receipt(distant, UUID.randomUUID(), UUID.randomUUID(),
                    new CampaignBlockSnapshot(Blocks.AIR.defaultBlockState(), null), new CampaignBlockSnapshot(Blocks.GLASS.defaultBlockState(), null), State.RESERVED, "");
            helper.assertValueEqual(CampaignBlockOwnership.reserve(fixture.level, request).status(), Status.WAITING, "unloaded claim waits");
            helper.assertTrue(fixture.level.getChunkSource().getChunkNow(distant.getX() >> 4, distant.getZ() >> 4) == null, "claim never force-loads its chunk");
        }
        helper.succeed();
    }
}
