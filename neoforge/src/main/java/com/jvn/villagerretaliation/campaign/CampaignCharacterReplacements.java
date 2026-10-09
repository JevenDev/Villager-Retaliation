package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignCharacterDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class CampaignCharacterReplacements {
    private CampaignCharacterReplacements() { }

    static CampaignCharacterService.Result resolve(MinecraftServer server, ResourceLocation campaign, String role) {
        if (server == null || !server.isSameThread() || campaign == null || role == null || role.isBlank()) {
            return CampaignCharacterService.resolveDeclared(server, campaign, role);
        }
        var content = CampaignResources.current(server).campaigns().get(campaign);
        if (content == null || CampaignWorldModeSavedData.get(server.overworld()).mode() != CampaignWorldMode.CAMPAIGN
                || !CampaignSavedData.get(server.overworld()).compatibility(content.definition()).isBlank()) {
            return CampaignCharacterService.resolveDeclared(server, campaign, role);
        }
        var data = CampaignCharacterSavedData.get(server.overworld());
        if (!data.diagnostic().isBlank()) return unavailable(data.diagnostic());
        var visited = new HashSet<String>();
        try {
            while (visited.add(role)) {
                var declaration = content.definition().characters().get(role);
                if (declaration == null) return unavailable("Unknown campaign replacement role: " + role);
                var key = new CampaignCharacterBinding.Key(campaign, role);
                var receipt = data.replacements().get(key);
                if (receipt == null) {
                    var result = CampaignCharacterService.resolveDeclared(server, campaign, role);
                    if (declaration.lossPolicy() != CampaignCharacterDefinition.LossPolicy.REPLACEMENT
                            || result.binding() == null || !result.binding().lost()) return result;
                    var lost = CampaignBindingQueries.originalCharacter(server, campaign, role);
                    if (!lost.diagnostic().isBlank()) return unavailable(lost.diagnostic());
                    if (lost.binding() == null || !lost.binding().lost()) return result;
                    receipt = data.prepareReplacement(lost.binding(), declaration.replacementRole(), server.overworld().getGameTime());
                }
                var original = CampaignBindingQueries.originalCharacter(server, campaign, role);
                if (!original.diagnostic().isBlank()) return unavailable(original.diagnostic());
                if (!receipt.committed()) {
                    var resolved = CampaignCharacterService.resolveDeclared(server, campaign, receipt.replacementRole());
                    var target = CampaignBindingQueries.originalCharacter(server, campaign, receipt.replacementRole());
                    if (!target.diagnostic().isBlank()) return unavailable(target.diagnostic());
                    if (target.binding() == null) return unavailable(resolved.diagnostic().isBlank()
                            ? "Campaign replacement has no saved successor binding." : resolved.diagnostic());
                    receipt = data.commitReplacement(key, target.binding(), server.overworld().getGameTime());
                }
                role = receipt.replacementRole();
            }
            return unavailable("Campaign character replacements contain a cycle. Repair their authored roles.");
        } catch (IOException | IllegalArgumentException exception) {
            return unavailable("Campaign character replacement could not be saved: " + exception.getMessage());
        }
    }

    public static String authorization(MinecraftServer server, ResourceLocation campaign, String role, UUID previous, UUID replacement) {
        if (server == null || campaign == null || role == null || role.isBlank()
                || previous == null || replacement == null || previous.equals(replacement)) return "";
        var visited = new HashSet<String>();
        var operations = new ArrayList<String>();
        boolean containsPrevious = false;
        boolean authoredSuccessor = false;
        UUID incoming = null;
        var data = CampaignCharacterSavedData.get(server.overworld());
        while (visited.add(role)) {
            var state = CampaignBindingQueries.originalCharacter(server, campaign, role);
            if (!state.diagnostic().isBlank() || state.binding() == null) return "";
            UUID current = state.binding().entityId();
            if (containsPrevious && incoming != null && !incoming.equals(current)) {
                var repairs = data.authorization(state.binding().key(), incoming, current);
                if (repairs.isEmpty()) return "";
                repairs.forEach(repair -> operations.add(repair.operation().toString()));
            }
            if (!containsPrevious) {
                var repairs = data.authorization(state.binding().key(), previous, current);
                containsPrevious = previous.equals(current) || !repairs.isEmpty();
                if (containsPrevious) repairs.forEach(repair -> operations.add(repair.operation().toString()));
            }
            var receipt = data.replacements().get(state.binding().key());
            if (receipt == null) return containsPrevious && replacement.equals(current) && !operations.isEmpty()
                    ? (authoredSuccessor ? "campaign_replacement:" : "campaign_repair:") + String.join("/", operations) : "";
            if (!receipt.committed()) return "";
            if (containsPrevious) {
                authoredSuccessor = true;
                operations.add(receipt.operation().toString());
            }
            incoming = receipt.replacementEntity();
            role = receipt.replacementRole();
        }
        return "";
    }

    static UUID repairOperator(MinecraftServer server, ResourceLocation campaign, String role, UUID previous, UUID replacement) {
        String authorization = authorization(server, campaign, role, previous, replacement);
        if (!authorization.startsWith("campaign_repair:")) return null;
        var operations = authorization.substring("campaign_repair:".length()).split("/");
        var data = CampaignCharacterSavedData.get(server.overworld());
        var repair = data.repairs().get(UUID.fromString(operations[operations.length - 1]));
        return repair == null ? null : repair.operator();
    }

    private static CampaignCharacterService.Result unavailable(String issue) { return new CampaignCharacterService.Result(null, null, issue); }
}
