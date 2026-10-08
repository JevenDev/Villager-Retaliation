package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.VillagerRetaliation;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class CampaignGeneratedBlocks {
    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, VillagerRetaliation.MOD_ID);
    static final Supplier<AttachmentType<CampaignGenerationLedger>> GENERATION = TYPES.register("campaign_generated_blocks",
            () -> AttachmentType.builder(CampaignGenerationLedger::new)
                    .serialize(CompoundTag.CODEC.xmap(CampaignGenerationLedger::load, CampaignGenerationLedger::save)).build());

    private CampaignGeneratedBlocks() { }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        CampaignStructureProcessor.register(bus);
        NeoForge.EVENT_BUS.addListener(CampaignGeneratedBlocks::onChunkLoad);
    }

    public static Map<BlockPos, CampaignGeneratedBlock> cells(ChunkAccess chunk) {
        var ledger = chunk.getExistingDataOrNull(GENERATION.get());
        return ledger == null || !ledger.diagnostic().isBlank() ? Map.of() : ledger.cells();
    }

    public static String diagnostic(ChunkAccess chunk) {
        var ledger = chunk.getExistingDataOrNull(GENERATION.get());
        return ledger == null ? "" : ledger.diagnostic();
    }

    static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !(chunk.getLevel() instanceof ServerLevel level)) return;
        var ledger = chunk.getExistingDataOrNull(GENERATION.get());
        if (ledger == null || !ledger.diagnostic().isBlank()) return;
        for (var cell : List.copyOf(ledger.cells().values())) {
            if (cell.state() != CampaignGeneratedBlock.State.PENDING) continue;
            String issue = event.isNewChunk() ? CampaignBlockOwnership.claimGenerated(level, chunk, cell)
                    : "This chunk was already explored before generation ownership was established. Preserve its terrain or claim it explicitly.";
            if (issue.length() > 512) issue = "Native campaign ownership is unreadable. Restore compatible chunk data before changing this location.";
            ledger.record(issue.isBlank() ? cell.owned() : cell.conflict(issue));
            chunk.setUnsaved(true);
        }
    }
}
