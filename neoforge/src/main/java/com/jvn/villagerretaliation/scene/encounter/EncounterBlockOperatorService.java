package com.jvn.villagerretaliation.scene.encounter;

import com.jvn.villagerretaliation.campaign.CampaignBlockOwnership;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneAuditEntry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;

public final class EncounterBlockOperatorService {
    private EncounterBlockOperatorService() { }

    public static List<String> inspect(ServerLevel level, UUID encounterId, String blockId) {
        var encounter = SceneSavedData.get(level).encounter(encounterId).orElse(null);
        var block = encounter == null ? null : encounter.environmentBlocks().get(blockId);
        if (block == null) return List.of("Encounter block not found.");
        var lines = new ArrayList<String>();
        lines.add("encounter=" + encounterId + " block=" + blockId + " state=" + block.state()
                + " dimension=" + block.dimension() + " position=" + block.position().toShortString());
        lines.add("original=" + block.original() + " placed=" + block.placed() + " ownership=" + block.ownership().state()
                + " operation=" + block.ownership().operation());
        if (!block.ownership().issue().isBlank()) lines.add(block.ownership().issue());
        if (!block.ownership().diagnostic().isBlank()) lines.add(block.ownership().diagnostic());
        var target = level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, block.dimension()));
        var chunk = target == null ? null : target.getChunkSource().getChunkNow(block.position().getX() >> 4, block.position().getZ() >> 4);
        if (chunk == null) lines.add("Native block chunk is unloaded.");
        else {
            lines.add("current=" + chunk.getBlockState(block.position()));
            var receipt = CampaignBlockOwnership.receipt(chunk, block.position());
            lines.add("nativeOwnership=" + (receipt == null ? "missing" : receipt.state() + " owner=" + receipt.owner() + " operation=" + receipt.operation()));
            if (!CampaignBlockOwnership.diagnostic(chunk).isBlank()) lines.add(CampaignBlockOwnership.diagnostic(chunk));
            if (receipt != null && !receipt.diagnostic().isBlank()) lines.add(receipt.diagnostic());
        }
        lines.add("preserve keeps the current block and its contents, skips this encounter change, and records the operator's reason.");
        return List.copyOf(lines);
    }

    public static Result preserve(ServerLevel level, UUID encounterId, String blockId, String reason, String operator) {
        if (!level.getServer().isSameThread()) return new Result(false, "Encounter block repair must run on the server thread.");
        if (reason == null || reason.isBlank() || reason.length() > 256 || operator == null || operator.isBlank() || operator.length() > 128)
            return new Result(false, "Provide an operator identity and a repair reason of 1 to 256 characters.");
        var data = SceneSavedData.get(level);
        var encounter = data.encounter(encounterId).orElse(null);
        var block = encounter == null ? null : encounter.environmentBlocks().get(blockId);
        if (block == null || data.get(encounter.sceneId()).isEmpty()) return new Result(false, "The encounter block or its scene is missing.");
        if (!EncounterPersistence.required(data, encounter)) return new Result(false, "This block does not use campaign ownership.");
        try {
            data.persistCampaignHandoff(level.getServer().overworld());
            if (block.state() == EncounterInstance.EnvironmentBlockState.PRESERVED || block.state() == EncounterInstance.EnvironmentBlockState.RESTORED)
                return new Result(true, "This encounter block is already settled.");
            var target = level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, block.dimension()));
            if (target == null) return new Result(false, "The encounter block dimension is unavailable.");
            var result = CampaignBlockOwnership.preserve(target, block.position(), encounterId, block.ownership().operation());
            if (result.status() != CampaignBlockOwnership.Status.PRESERVED) return new Result(false, result.diagnostic());
            encounter.replaceEnvironmentBlock(block.withOwnership(block.ownership().conflict("Operator preserved this block: " + reason)).preserved());
            data.audit(new SceneAuditEntry(encounter.sceneId(), encounterId + "/" + blockId,
                    block.state().name() + "/" + block.ownership().state(), "PRESERVED", reason, level.getGameTime(), operator));
            data.changed();
            data.persistCampaignHandoff(level.getServer().overworld());
            return new Result(true, "Current block and contents preserved. The encounter change was skipped and the repair audit was saved.");
        } catch (IOException exception) {
            return new Result(false, "The repair is waiting for writable scene storage: " + exception.getMessage());
        }
    }

    public record Result(boolean success, String message) { }
}
