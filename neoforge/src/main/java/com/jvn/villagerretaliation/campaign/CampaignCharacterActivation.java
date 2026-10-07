package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignCharacterDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class CampaignCharacterActivation {
    private static final Map<MinecraftServer, Integer> CURSORS = new WeakHashMap<>();

    private CampaignCharacterActivation() { }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.overworld().getGameTime() % 100L == 0L) {
            activate(server, server.getPlayerList().getPlayers().stream().map(player -> player.getUUID()).toList());
        }
    }

    static void activate(MinecraftServer server, Collection<UUID> players) {
        if (players.isEmpty() || CampaignWorldModeSavedData.get(server.overworld()).mode() != CampaignWorldMode.CAMPAIGN) return;
        var catalog = CampaignResources.current(server);
        var campaigns = CampaignSavedData.get(server.overworld());
        var spawns = CampaignSpawnSavedData.get(server.overworld());
        var characters = CampaignCharacterSavedData.get(server.overworld());
        if (!spawns.diagnostic().isBlank() || !characters.diagnostic().isBlank()) return;
        var needed = new LinkedHashSet<CampaignCharacterBinding.Key>();
        for (var content : catalog.campaigns().values()) {
            var definition = content.definition();
            if (!campaigns.compatibility(definition).isBlank()) continue;
            for (var questId : definition.questBundles()) {
                var quest = catalog.quests().quest(questId).orElse(null);
                if (quest == null || players.stream().noneMatch(player -> CampaignQuestAccess.availabilityIssue(quest, player, campaigns, catalog).isBlank())) continue;
                var compiled = catalog.quests().compiledQuest(questId).orElse(null);
                var character = compiled == null ? null : definition.characters().get(compiled.provider().campaignRole());
                if (character == null) continue;
                if (needsResolution(definition, character, characters, spawns)) needed.add(new CampaignCharacterBinding.Key(definition.id(), character.id()));
            }
        }
        if (needed.isEmpty()) return;
        var roles = new ArrayList<>(needed);
        int cursor = Math.floorMod(CURSORS.getOrDefault(server, 0), roles.size());
        int attempts = Math.min(4, roles.size());
        for (int index = 0; index < attempts; index++) {
            var key = roles.get((cursor + index) % roles.size());
            CampaignCharacterService.resolve(server, key.campaign(), key.role());
        }
        CURSORS.put(server, (cursor + attempts) % roles.size());
    }

    private static boolean needsResolution(com.jvn.villagerretaliation.campaign.content.CampaignDefinition definition,
            CampaignCharacterDefinition character, CampaignCharacterSavedData characters, CampaignSpawnSavedData spawns) {
        var visited = new java.util.HashSet<String>();
        while (character != null && visited.add(character.id())) {
            var key = new CampaignCharacterBinding.Key(definition.id(), character.id());
            var replacement = characters.replacements().get(key);
            if (replacement != null) {
                if (!replacement.committed()) return true;
                character = definition.characters().get(replacement.replacementRole());
                continue;
            }
            var binding = characters.bindings().get(key);
            if (binding != null && binding.lost() && character.lossPolicy() == CampaignCharacterDefinition.LossPolicy.REPLACEMENT) return true;
            var spawn = spawns.receipts().get(key);
            return character.source() == CampaignCharacterDefinition.Source.OWNED_SPAWN && (spawn == null || !spawn.committed());
        }
        return false;
    }
}
