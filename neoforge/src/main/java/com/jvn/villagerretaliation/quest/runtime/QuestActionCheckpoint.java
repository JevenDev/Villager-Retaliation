package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.ActionStatus;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public record QuestActionCheckpoint(ActionStatus status, String diagnostic, UUID continuationId,
                                    Map<String, String> replacements) {
    public QuestActionCheckpoint {
        status = status == null ? ActionStatus.UNAVAILABLE : status;
        diagnostic = diagnostic == null ? "" : diagnostic;
        replacements = replacements == null ? Map.of() : Map.copyOf(replacements);
    }

    public ActionResult outcome() {
        VillagerActionResult output = new VillagerActionResult(false, "", "", replacements, false);
        return status == ActionStatus.SUSPENDED
                ? ActionResult.suspendedContinuation(continuationId, diagnostic, Set.of(), output)
                : new ActionResult(status, diagnostic, Set.of(), output);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Status", status.name());
        tag.putString("Diagnostic", diagnostic);
        if (continuationId != null) tag.putUUID("Continuation", continuationId);
        CompoundTag values = new CompoundTag();
        replacements.forEach(values::putString);
        tag.put("Replacements", values);
        return tag;
    }

    public static QuestActionCheckpoint load(CompoundTag tag) {
        ActionStatus status;
        try {
            status = ActionStatus.valueOf(tag.getString("Status"));
        } catch (IllegalArgumentException exception) {
            return new QuestActionCheckpoint(ActionStatus.UNAVAILABLE, "unrecognized saved action result", null, Map.of());
        }
        Map<String, String> replacements = new LinkedHashMap<>();
        CompoundTag values = tag.getCompound("Replacements");
        for (String key : values.getAllKeys()) replacements.put(key, values.getString(key));
        return new QuestActionCheckpoint(status, tag.getString("Diagnostic"),
                tag.hasUUID("Continuation") ? tag.getUUID("Continuation") : null, replacements);
    }
}
