package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.campaign.content.CampaignCharacterDefinition;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import java.io.IOException;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

public final class CampaignCharacterService {
    private CampaignCharacterService() { }

    public static Result resolve(MinecraftServer server, ResourceLocation campaign, String role) {
        return CampaignCharacterReplacements.resolve(server, campaign, role);
    }

    static Result resolveDeclared(MinecraftServer server, ResourceLocation campaign, String role) {
        if (server == null || !server.isSameThread()) return unavailable("Campaign characters require the server thread.");
        if (campaign == null || role == null || role.isBlank()) return unavailable("A campaign character reference is required.");
        if (CampaignWorldModeSavedData.get(server.overworld()).mode() != CampaignWorldMode.CAMPAIGN) return unavailable("This character requires a Campaign Mode world.");
        var content = CampaignResources.current(server).campaigns().get(campaign);
        if (content == null) return unavailable("Campaign character content is missing. Restore the campaign datapack.");
        var definition = content.definition();
        var character = definition.characters().get(role);
        if (character == null) return unavailable("Unknown campaign character " + role + ". Restore its authored definition.");
        var campaigns = CampaignSavedData.get(server.overworld());
        String issue = campaigns.compatibility(definition);
        if (!issue.isBlank()) return unavailable(issue);
        var data = CampaignCharacterSavedData.get(server.overworld());
        if (!data.diagnostic().isBlank()) return unavailable(data.diagnostic());
        var binding = data.bindings().get(new CampaignCharacterBinding.Key(campaign, role));
        var instance = campaigns.instances().get(campaign);
        if (binding != null && (instance == null || !binding.instance().equals(instance.instanceId())
                || binding.definitionRevision() != definition.revision() || !binding.fingerprint().equals(definition.fingerprint())
                || binding.source() != character.source() || binding.lossPolicy() != character.lossPolicy())) {
            return unavailable("Saved campaign character ownership is incompatible. Restore the matching campaign and character saves.");
        }
        String pendingLoss = CampaignCharacterLossService.diagnostic(server, binding);
        if (!pendingLoss.isBlank()) return unavailable(pendingLoss);
        if (character.source() == CampaignCharacterDefinition.Source.OWNED_SPAWN) {
            return CampaignOwnedSpawns.resolve(server, content, character, data);
        }
        if (binding != null) return observe(server, data, binding);
        if (character.profession() != null && !BuiltInRegistries.VILLAGER_PROFESSION.containsKey(character.profession())) {
            return unavailable("Campaign character profession is unavailable: " + character.profession());
        }
        var location = CampaignLocationService.resolve(server, campaign, character.location());
        if (!location.available()) return unavailable(location.diagnostic());
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, location.binding().dimension()));
        var origin = location.binding().position();
        var candidates = level.getEntitiesOfClass(Villager.class, new AABB(origin).inflate(character.searchRadius()),
                villager -> villager.isAlive() && !villager.isBaby() && !VillagerDownedService.isDowned(villager)
                        && !data.reserved(villager.getUUID()) && !CampaignOwnedSpawns.hasOwner(villager)
                        && (character.profession() == null || character.profession().equals(VillagerProfessionUtil.id(villager.getVillagerData().getProfession())))
                        && villager.blockPosition().distSqr(origin) <= (double) character.searchRadius() * character.searchRadius());
        var selected = candidates.stream().min(Comparator.<Villager>comparingDouble(villager -> villager.blockPosition().distSqr(origin))
                .thenComparing(Villager::getUUID)).orElse(null);
        if (selected == null) return unavailable("No eligible loaded villager is available for campaign role " + role + ". Visit its saved location and retry.");
        try {
            instance = campaigns.open(content, level.getGameTime());
            binding = data.bind(new CampaignCharacterBinding(campaign, instance.instanceId(), definition.revision(), definition.fingerprint(),
                    role, character.source(), character.lossPolicy(), selected.getUUID(), level.dimension().location(), selected.blockPosition(), level.getGameTime(), false));
            return observe(server, data, binding);
        } catch (IOException | IllegalArgumentException exception) {
            return unavailable("Campaign character could not be saved: " + exception.getMessage());
        }
    }

    static Result observe(MinecraftServer server, CampaignCharacterSavedData data, CampaignCharacterBinding binding) {
        String pendingLoss = CampaignCharacterLossService.diagnostic(server, binding);
        if (!pendingLoss.isBlank()) return unavailable(pendingLoss);
        if (data.replacements().containsKey(binding.key())) return new Result(binding, null, "This character has a saved replacement. Continue with its successor.");
        Entity entity = null;
        for (var level : server.getAllLevels()) {
            entity = level.getEntity(binding.entityId());
            if (entity != null) break;
        }
        if (entity == null) return new Result(binding, null, binding.lost()
                ? binding.lossPolicy() == CampaignCharacterDefinition.LossPolicy.ALTERNATE_PATH
                        ? "Campaign character was permanently removed. Continue its authored alternate story path."
                        : "Campaign character was permanently removed. Restore the original character or repair its binding."
                : "Campaign character is unloaded or unavailable. Its saved identity is retained.");
        if (!(entity instanceof Villager villager) || !villager.isAlive()) return new Result(binding, null, "Campaign character is not a living villager. Restore its original identity.");
        if (binding.lost()) {
            try { data.recordLoss(binding.entityId(), false); }
            catch (IOException exception) { return unavailable("Recovered campaign character could not be saved: " + exception.getMessage()); }
            binding = data.forEntity(binding.entityId());
        }
        return new Result(binding, villager, VillagerDownedService.isDowned(villager) ? "Campaign character is downed and must recover before continuing." : "");
    }

    public static boolean matchesBoundCharacter(MinecraftServer server, ResourceLocation campaign, String role, UUID entity) {
        if (entity == null) return false;
        var state = CampaignBindingQueries.character(server, campaign, role);
        return state.available() && entity.equals(state.binding().entityId());
    }

    public static boolean matchesProvider(MinecraftServer server, QuestDefinition quest, UUID entity) {
        if (quest == null || quest.campaign() == null) return true;
        String role = VillagerQuestResources.compiledQuest(server, quest.id()).map(value -> value.provider().campaignRole()).orElse("");
        if (role.isBlank()) return true;
        if (entity == null) return false;
        var result = resolve(server, quest.campaign(), role);
        return result.binding() != null && !result.binding().lost() && entity.equals(result.binding().entityId());
    }

    public static boolean protects(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)
                || CampaignWorldModeSavedData.get(level).mode() != CampaignWorldMode.CAMPAIGN) return false;
        var binding = CampaignCharacterSavedData.get(level).forEntity(villager.getUUID());
        return binding != null && binding.lossPolicy() == CampaignCharacterDefinition.LossPolicy.DOWNED;
    }

    public static void onConversion(net.neoforged.neoforge.event.entity.living.LivingConversionEvent.Pre event) {
        if (event.getEntity() instanceof Villager villager && protects(villager)) event.setCanceled(true);
    }

    public static void onRemoved(Entity entity) {
        CampaignCharacterLossService.record(entity);
    }

    private static Result unavailable(String diagnostic) { return new Result(null, null, diagnostic); }

    public record Result(CampaignCharacterBinding binding, Villager entity, String diagnostic) {
        public boolean available() { return entity != null && diagnostic.isBlank(); }
    }
}
