package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.campaign.content.CampaignCharacterDefinition;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

final class CampaignBindingQueries {
    private CampaignBindingQueries() { }

    static CharacterState character(MinecraftServer server, ResourceLocation campaign, String role) {
        var visited = new java.util.HashSet<String>();
        while (visited.add(role)) {
            var state = originalCharacter(server, campaign, role);
            if (!state.diagnostic().isBlank() || state.binding() == null) return state;
            var receipt = CampaignCharacterSavedData.get(server.overworld()).replacements().get(state.binding().key());
            if (receipt == null) return state;
            if (!receipt.committed()) return new CharacterState(null, null, "Campaign character replacement is waiting for its successor binding.");
            role = receipt.replacementRole();
        }
        return new CharacterState(null, null, "Campaign character replacements contain a cycle. Repair the campaign definition and saved decisions.");
    }

    static CharacterState originalCharacter(MinecraftServer server, ResourceLocation campaign, String role) {
        var context = context(server, campaign);
        if (!context.diagnostic().isBlank()) return new CharacterState(null, null, context.diagnostic());
        var definition = context.content().definition();
        var declared = role == null ? null : definition.characters().get(role);
        if (declared == null) return new CharacterState(null, null, "Unknown campaign character: " + role);
        var data = CampaignCharacterSavedData.get(server.overworld());
        if (!data.diagnostic().isBlank()) return new CharacterState(null, null, data.diagnostic());
        var key = new CampaignCharacterBinding.Key(campaign, role);
        var binding = data.bindings().get(key);
        CampaignSpawnReceipt receipt = null;
        if (declared.source() == CampaignCharacterDefinition.Source.OWNED_SPAWN) {
            var spawns = CampaignSpawnSavedData.get(server.overworld());
            if (!spawns.diagnostic().isBlank()) return new CharacterState(null, null, spawns.diagnostic());
            receipt = spawns.receipts().get(key);
            if (receipt != null && !receipt.committed()) return new CharacterState(null, null, "Campaign character is waiting for its spawn to finish.");
        }
        if (binding == null) return new CharacterState(null, null, receipt == null ? "" : "Campaign spawn is missing its saved character binding.");
        if (context.instance() == null || !binding.instance().equals(context.instance().instanceId())
                || binding.definitionRevision() != definition.revision() || !binding.fingerprint().equals(definition.fingerprint())
                || binding.source() != declared.source() || binding.lossPolicy() != declared.lossPolicy()) {
            return new CharacterState(null, null, "Saved campaign character ownership is incompatible. Restore matching campaign and character saves.");
        }
        if (binding.source() == CampaignCharacterDefinition.Source.OWNED_SPAWN
                && (receipt == null || !receipt.binding().equals(binding.withLoss(false)))) {
            return new CharacterState(null, null, "Campaign character does not match its spawn receipt. Restore matching saves.");
        }
        String pendingLoss = CampaignCharacterLossService.diagnostic(server, binding);
        if (!pendingLoss.isBlank()) return new CharacterState(null, null, pendingLoss);
        var replacement = data.replacements().get(key);
        if (replacement != null) {
            if (declared.lossPolicy() != CampaignCharacterDefinition.LossPolicy.REPLACEMENT
                    || !declared.replacementRole().equals(replacement.replacementRole())) {
                return new CharacterState(null, null, "Saved character replacement does not match its authored policy. Restore matching content and saves.");
            }
            return new CharacterState(binding, null, "");
        }
        Entity entity = null;
        for (var level : server.getAllLevels()) {
            entity = level.getEntity(binding.entityId());
            if (entity != null) break;
        }
        if (entity == null) return new CharacterState(binding, null, "");
        if (!(entity instanceof Villager villager) || !villager.isAlive() || receipt != null && !CampaignOwnedSpawns.owns(receipt, villager)) {
            return new CharacterState(null, null, "Campaign character identity or ownership is unavailable. Restore its original entity.");
        }
        if (binding.lost()) return new CharacterState(null, null, "The restored campaign character must resume its saved role before continuing.");
        return new CharacterState(binding, villager, "");
    }

    static LocationState location(MinecraftServer server, ResourceLocation campaign, String alias) {
        var context = context(server, campaign);
        if (!context.diagnostic().isBlank()) return new LocationState(null, context.diagnostic());
        var definition = context.content().definition();
        var declared = definition.locations().get(alias);
        if (declared == null) return new LocationState(null, "Unknown campaign location: " + alias);
        var data = CampaignLocationSavedData.get(server.overworld());
        if (!data.diagnostic().isBlank()) return new LocationState(null, data.diagnostic());
        var binding = data.bindings().get(new CampaignLocationBinding.Key(campaign, alias));
        if (binding == null) return new LocationState(null, "");
        if (context.instance() == null || !binding.instance().equals(context.instance().instanceId())
                || binding.definitionRevision() != definition.revision() || !binding.fingerprint().equals(definition.fingerprint())
                || binding.source() != declared.source() || !binding.dimension().equals(declared.dimension())) {
            return new LocationState(null, "Saved campaign location ownership is incompatible. Restore matching campaign and location saves.");
        }
        var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, binding.dimension()));
        if (level == null || level.isOutsideBuildHeight(binding.position()) || !level.getWorldBorder().isWithinBounds(binding.position())) {
            return new LocationState(null, "The saved campaign destination is outside the current dimension or world bounds.");
        }
        return new LocationState(binding, "");
    }

    private static Context context(MinecraftServer server, ResourceLocation campaign) {
        if (server == null || !server.isSameThread()) return new Context(null, null, "Campaign binding conditions require the server thread.");
        if (CampaignWorldModeSavedData.get(server.overworld()).mode() != CampaignWorldMode.CAMPAIGN) {
            return new Context(null, null, "Campaign Mode is required for this story condition.");
        }
        var content = campaign == null ? null : CampaignResources.current(server).campaigns().get(campaign);
        if (content == null) return new Context(null, null, "Campaign content is missing or invalid: " + campaign);
        var data = CampaignSavedData.get(server.overworld());
        return new Context(content, data.instances().get(campaign), data.compatibility(content.definition()));
    }

    private record Context(CampaignContentCatalog.Entry content, CampaignInstance instance, String diagnostic) { }
    record LocationState(CampaignLocationBinding binding, String diagnostic) { }
    record CharacterState(CampaignCharacterBinding binding, Villager entity, String diagnostic) {
        boolean available() { return entity != null && diagnostic.isBlank() && !VillagerDownedService.isDowned(entity); }
    }
}
