package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.CampaignBlockOwnershipLedger.Receipt;
import com.jvn.villagerretaliation.campaign.CampaignBlockOwnershipLedger.State;
import com.jvn.villagerretaliation.util.WorldChunkCheckpoint;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.attachment.AttachmentHolder;

final class CampaignOwnedBlockTestFixture implements AutoCloseable {
    final GameTestHelper helper;
    final ServerLevel level;
    final BlockPos position;
    final LevelChunk chunk;
    final Receipt request;
    private final CampaignBlockSnapshot priorBlock;
    private final CampaignBlockOwnershipLedger priorOwnership;

    CampaignOwnedBlockTestFixture(GameTestHelper helper, BlockState placed) {
        this.helper = helper;
        level = helper.getLevel();
        position = helper.absolutePos(new BlockPos(2, 2, 2));
        chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        priorBlock = CampaignBlockSnapshot.capture(level, chunk, position);
        priorOwnership = chunk.getExistingDataOrNull(CampaignBlockOwnership.OWNERSHIP.get());
        chunk.setData(CampaignBlockOwnership.OWNERSHIP, new CampaignBlockOwnershipLedger());
        level.removeBlockEntity(position);
        level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        request = new Receipt(position, UUID.randomUUID(), UUID.randomUUID(), CampaignBlockSnapshot.capture(level, chunk, position),
                CampaignBlockSnapshot.defaultState(level, position, placed), State.RESERVED, "");
    }

    CampaignBlockOwnershipLedger ledger() { return chunk.getData(CampaignBlockOwnership.OWNERSHIP); }
    Receipt receipt() { return ledger().get(position); }

    void apply() {
        helper.assertValueEqual(CampaignBlockOwnership.reserve(level, request).status(), CampaignBlockOwnership.Status.READY, "ownership is reserved");
        var result = CampaignBlockOwnership.apply(level, request);
        helper.assertValueEqual(result.status(), CampaignBlockOwnership.Status.APPLIED, "authored block is applied: " + result.diagnostic());
    }

    CompoundTag disk() {
        return level.getChunkSource().chunkMap.read(chunk.getPos()).join().orElseThrow();
    }

    void reloadOwnership() {
        chunk.setData(CampaignBlockOwnership.OWNERSHIP, CampaignBlockOwnershipLedger.load(
                disk().getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).getCompound("villagerretaliation:campaign_blocks")));
    }

    void reloadWorld(CampaignBlockSnapshot saved) {
        var disk = disk();
        helper.assertTrue(saved.includedIn(disk, position), "crash recovery uses the actual native chunk snapshot");
        chunk.setData(CampaignBlockOwnership.OWNERSHIP, new CampaignBlockOwnershipLedger());
        setBlock(saved);
        chunk.setData(CampaignBlockOwnership.OWNERSHIP, CampaignBlockOwnershipLedger.load(
                disk.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).getCompound("villagerretaliation:campaign_blocks")));
    }

    void checkpoint() throws IOException { WorldChunkCheckpoint.persist(level, position); }

    private void setBlock(CampaignBlockSnapshot snapshot) {
        level.removeBlockEntity(position);
        level.setBlock(position, snapshot.state(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        if (snapshot.blockEntity() != null) level.setBlockEntity(BlockEntity.loadStatic(position, snapshot.state(), snapshot.blockEntity(), level.registryAccess()));
    }

    @Override public void close() {
        chunk.setData(CampaignBlockOwnership.OWNERSHIP, new CampaignBlockOwnershipLedger());
        setBlock(priorBlock);
        if (priorOwnership == null) chunk.removeData(CampaignBlockOwnership.OWNERSHIP);
        else chunk.setData(CampaignBlockOwnership.OWNERSHIP, priorOwnership);
    }
}
