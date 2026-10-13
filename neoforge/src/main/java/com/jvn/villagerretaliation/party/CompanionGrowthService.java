package com.jvn.villagerretaliation.party;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import java.io.IOException;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.npc.Villager;

/** Small, authored personal milestones applied to the existing skill profile, not a second class system. */
public final class CompanionGrowthService {
    private static volatile Map<ResourceLocation, Story> stories = Map.of();
    private CompanionGrowthService() { }

    public static void reload(ResourceManager resources) {
        var next = new TreeMap<ResourceLocation, Story>();
        for (var entry : resources.listResources("companion_stories", id -> id.getPath().endsWith(".json")).entrySet()) {
            var path = entry.getKey();
            var id = ResourceLocation.fromNamespaceAndPath(path.getNamespace(),
                    path.getPath().substring("companion_stories/".length(), path.getPath().length() - 5));
            try (var reader = entry.getValue().openAsReader()) {
                next.put(id, parse(GsonHelper.parse(reader)));
            } catch (IOException | RuntimeException exception) {
                throw new IllegalStateException("Invalid companion story at " + path, exception);
            }
        }
        stories = Collections.unmodifiableMap(next);
    }

    static Story parse(JsonObject root) {
        var issues = com.jvn.villagerretaliation.quest.schema.QuestJsonSchema.validate(root, schema());
        if (!issues.isEmpty()) throw new IllegalArgumentException(issues.toString());
        var choices = new LinkedHashMap<String, Choice>();
        for (var value : root.getAsJsonArray("choices")) {
            var choice = value.getAsJsonObject();
            String id = choice.get("id").getAsString();
            var skill = VillagerSkill.bySerializedName(choice.get("skill").getAsString());
            if (skill == null) throw new IllegalArgumentException("Unknown skill");
            if (choices.putIfAbsent(id, new Choice(choice.get("text").getAsString(), skill,
                    choice.get("points").getAsInt())) != null) throw new IllegalArgumentException("Duplicate choice " + id);
        }
        return new Story(root.get("title").getAsString(), root.get("text").getAsString(),
                GsonHelper.getAsString(root, "profession", ""), root.get("experience_id").getAsString(),
                GsonHelper.getAsString(root, "requirement", "Share this experience together"), root.get("minimum_bond").getAsInt(), Collections.unmodifiableMap(choices));
    }

    public static JsonObject schema() {
        return JsonParser.parseString("""
                {"type":"object","additionalProperties":false,
                 "required":["title","text","experience_id","minimum_bond","choices"],"properties":{
                  "title":{"type":"string","minLength":1,"maxLength":120},
                  "text":{"type":"string","minLength":1,"maxLength":1000},
                  "profession":{"type":"string","pattern":"^[a-z0-9_.-]+:[a-z0-9_./-]+$"},
                  "experience_id":{"type":"string","minLength":1,"maxLength":128},
                  "requirement":{"type":"string","minLength":1,"maxLength":200},
                  "minimum_bond":{"type":"integer","minimum":0,"maximum":100},
                  "choices":{"type":"array","minItems":2,"maxItems":4,"items":{
                    "type":"object","additionalProperties":false,"required":["id","text","skill","points"],"properties":{
                      "id":{"type":"string","pattern":"^[a-z0-9_-]+$","maxLength":64},
                      "text":{"type":"string","minLength":1,"maxLength":200},
                      "skill":{"type":"string"},"points":{"type":"integer","minimum":1,"maximum":10}}}}}}
                """).getAsJsonObject();
    }

    public static void rememberProfession(Villager villager, CompoundTag state) {
        if (!state.contains("OriginProfession")) state.putString("OriginProfession",
                BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()).toString());
    }

    public static void rememberEvent(ServerLevel level, UUID villager, UUID player, String event) {
        var data = CompanionSavedData.get(level);
        var state = data.getOrCreate(villager);
        var events = state.getCompound("StoryEvents");
        events.putBoolean(player + "/" + event, true);
        state.put("StoryEvents", events);
        data.setDirty();
    }

    private static boolean matches(CompoundTag state, Story story) {
        return story.profession().isEmpty() || story.profession().equals(state.getString("OriginProfession"));
    }

    private static boolean ready(ServerLevel level, Villager villager, ServerPlayer player, CompoundTag state, Story story) {
        String event = player.getUUID() + "/" + story.experience();
        boolean remembered = state.getCompound("StoryEvents").getBoolean(event) || state.getCompound("Experiences").getBoolean(event)
                || story.experience().equals("villagerretaliation:rescued") && state.contains("LastRescue/" + player.getUUID());
        return matches(state, story) && remembered
                && CompanionRelationshipService.bond(level, villager.getUUID(), player.getUUID()) >= story.bond();
    }

    private static boolean nearby(ServerPlayer player, Villager villager) {
        return player.isAlive() && !player.isSpectator() && villager.isAlive()
                && player.serverLevel() == villager.level() && player.distanceToSqr(villager) <= 64;
    }

    public static boolean choose(ServerPlayer player, Villager villager, ResourceLocation id, String choiceId) {
        var level = player.serverLevel();
        var state = CompanionSavedData.get(level).find(villager.getUUID());
        var story = stories.get(id);
        if (!nearby(player, villager) || state == null || story == null
                || !player.getUUID().equals(CompanionLifecycleService.owner(level, villager.getUUID()))
                || CompanionRelationshipService.trustSuspended(level, villager.getUUID())
                || !CompanionLifecycleService.canPart(villager) || !ready(level, villager, player, state, story)) return false;
        var choices = state.getCompound("GrowthChoices");
        if (choices.contains(id.toString())) return false;
        var choice = story.choices().get(choiceId);
        if (choice == null) return false;
        int before = VillagerProfileManager.getSkill(level, villager, choice.skill());
        if (!VillagerProfileManager.setSkill(level, villager, choice.skill(), before + choice.points())) return false;
        var receipt = new CompoundTag();
        receipt.putString("Choice", choiceId);
        receipt.putUUID("Player", player.getUUID());
        receipt.putString("Skill", choice.skill().serializedName());
        int increase = VillagerProfileManager.getSkill(level, villager, choice.skill()) - before;
        receipt.putInt("Points", increase);
        choices.put(id.toString(), receipt);
        state.put("GrowthChoices", choices);
        CompanionSavedData.get(level).setDirty();
        if(id.equals(ResourceLocation.parse("villagerretaliation:a_place_to_return"))) CompanionAmbientService.claimHomecomingStory(villager);
        player.sendSystemMessage(Component.literal(story.title() + ": " + choice.text() + " (+" + increase + " " + choice.skill().serializedName() + ")"));
        return true;
    }

    static boolean ownsHomecoming(Villager villager, ServerPlayer player) {
        var level=player.serverLevel();var state=CompanionSavedData.get(level).find(villager.getUUID());
        var id=ResourceLocation.parse("villagerretaliation:a_place_to_return");var story=stories.get(id);
        return state!=null && story!=null && player.getUUID().equals(CompanionLifecycleService.owner(level,villager.getUUID()))
                && !state.getCompound("GrowthChoices").contains(id.toString()) && ready(level,villager,player,state,story);
    }

    public static boolean describe(ServerPlayer player, Villager villager) {
        if (!nearby(player, villager)) return false;
        var level = player.serverLevel();
        var data = CompanionSavedData.get(level);
        var state = data.find(villager.getUUID());
        if (state == null) {
            player.sendSystemMessage(Component.literal("No shared companion history yet. Recruitment is available through the villager's usual dialogue."));
            return true;
        }
        rememberProfession(villager, state);
        data.setDirty();
        String profession = com.jvn.villagerretaliation.util.VillagerProfessionUtil.parse(state.getString("OriginProfession"))
                .map(value -> com.jvn.villagerretaliation.util.VillagerProfessionUtil.displayName(value, "Unemployed")).orElse("Villager");
        player.sendSystemMessage(Component.literal(villager.getName().getString() + " — " + profession
                + "; personal bond " + CompanionRelationshipService.bond(level, villager.getUUID(), player.getUUID())));
        String prefix = player.getUUID() + "/";
        var offers = state.getCompound("ServiceOffers");
        for (String key : offers.getAllKeys().stream().sorted().toList()) {
            if (!key.startsWith(prefix)) continue;
            var offer = offers.getCompound(key);
            if (offer.getBoolean("Used")) continue;
            player.sendSystemMessage(Component.literal((offer.contains("Name") ? offer.getString("Name") : "Temporary companionship") + ": free for " + offer.getInt("Duration") / 20
                    + " seconds; reputation " + offer.getInt("MinimumReputation") + " required; uses one of four slots. ")
                    .append(button("Accept service", "/vr companion service " + villager.getUUID() + " " + offer.getString("Id"))));
        }
        if (state.contains("ActiveService")) player.sendSystemMessage(Component.literal("Temporary service remaining: "
                + Math.max(0, state.getCompound("ActiveService").getLong("End") - level.getServer().overworld().getGameTime()) / 20 + " seconds."));
        if (!player.getUUID().equals(CompanionLifecycleService.owner(level, villager.getUUID()))) return true;
        for (var entry : stories.entrySet()) {
            var story = entry.getValue();
            if (!matches(state, story)) continue;
            var receipt = state.getCompound("GrowthChoices").getCompound(entry.getKey().toString());
            if (!receipt.isEmpty()) {
                var remembered = story.choices().get(receipt.getString("Choice"));
                player.sendSystemMessage(Component.literal(story.title() + " — remembered: " + (remembered == null ? "a path chosen together" : remembered.text())));
            } else if (!ready(level, villager, player, state, story)) {
                player.sendSystemMessage(Component.literal(story.title() + " — " + story.requirement() + "; bond " + story.bond() + " needed."));
            } else {
                player.sendSystemMessage(Component.literal(story.title() + ": " + story.text()));
                story.choices().forEach((choiceId, choice) -> player.sendSystemMessage(button(choice.text() + " (+" + choice.points()
                        + " " + choice.skill().serializedName() + ")", "/vr companion grow " + villager.getUUID() + " " + entry.getKey() + " " + choiceId)));
            }
        }
        return true;
    }

    private static Component button(String text, String command) {
        return Component.literal("[" + text + "]").withStyle(style -> style.withColor(net.minecraft.ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    record Story(String title, String text, String profession, String experience, String requirement, int bond, Map<String, Choice> choices) { }
    record Choice(String text, VillagerSkill skill, int points) { }
}
