package com.jvn.villagerretaliation.quest.discovery;

import com.jvn.villagerretaliation.campaign.CampaignCharacterService;
import com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.*;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvent;
import com.jvn.villagerretaliation.util.VillagerEntityResolver;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class QuestDiscoveryService {
    private static final Map<ServerPlayer, InventorySnapshot> INVENTORIES = new WeakHashMap<>();
    private static final Map<net.minecraft.server.MinecraftServer, Index> INDEXES = new WeakHashMap<>();

    private record Binding(CompiledQuest quest, QuestDiscoveryDefinition discovery) { }
    private record Index(com.jvn.villagerretaliation.quest.content.QuestContentCatalog catalog, Map<String, List<Binding>> events) { }
    private record InventorySnapshot(Index index, Map<String, Integer> counts) { }

    private static Index index(ServerPlayer player) {
        var server = player.serverLevel().getServer();
        var catalog = com.jvn.villagerretaliation.quest.content.QuestContentCatalogs.current(server);
        var previous = INDEXES.get(server);
        if (previous != null && previous.catalog() == catalog) return previous;
        Map<String, List<Binding>> events = new HashMap<>();
        for (var quest : catalog.compiledQuests())
            for (var discovery : quest.discovery())
                events.computeIfAbsent(discovery.event(), ignored -> new ArrayList<>()).add(new Binding(quest, discovery));
        var index = new Index(catalog, events);
        INDEXES.put(server, index);
        return index;
    }
    private QuestDiscoveryService() { }

    public static void tick(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return;
        var index = index(player);
        var snapshot = INVENTORIES.get(player);
        if (snapshot == null || snapshot.index() != index) {
            snapshot = new InventorySnapshot(index, new HashMap<>());
            INVENTORIES.put(player, snapshot);
        }
        var inventory = snapshot.counts();
        for (var binding : index.events().getOrDefault("item_acquired", List.of())) {
            var quest = binding.quest();
            var discovery = binding.discovery();
            int count = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                var stack = player.getInventory().getItem(slot);
                if (discovery.matchesItem(stack)) count += stack.getCount();
            }
            String key = quest.id() + "/" + discovery.id();
            int previous = inventory.getOrDefault(key, 0);
            inventory.put(key, count);
            if (count > previous && atMatchPosition(player, player.blockPosition(), discovery))
                discover(player, quest, discovery, null, Map.of());
        }
        if (player.tickCount % 20 == 0)
            for (var binding : index.events().getOrDefault("location", List.of()))
                if (atLocation(player, binding.discovery()))
                    discover(player, binding.quest(), binding.discovery(), null, Map.of());
    }

    public static void use(ServerPlayer player, ItemStack stack) {
        dispatchItem(player, stack, "item_used");
    }

    public static void bookOpened(ServerPlayer player, ItemStack stack) {
        dispatchItem(player, stack, "book_opened");
    }

    private static void dispatchItem(ServerPlayer player, ItemStack stack, String event) {
        if (!player.isAlive() || player.isSpectator()) return;
        for (var binding : index(player).events().getOrDefault(event, List.of()))
            if (binding.discovery().matchesItem(stack) && atMatchPosition(player, player.blockPosition(), binding.discovery()))
                discover(player, binding.quest(), binding.discovery(), null, Map.of());
    }

    public static void event(ServerPlayer player, QuestObjectiveEvent event) {
        String name = event.kind().name().toLowerCase(Locale.ROOT);
        var trigger = QuestTriggerRegistry.eventBySerializedName(name);
        if (trigger == null) return;
        var payload = QuestTriggerContext.of(null, trigger, player.serverLevel().getGameTime(), "", event).payload();
        if (!player.isAlive() || player.isSpectator()) return;
        for (var binding : index(player).events().getOrDefault(QuestTriggerRegistry.canonicalEventId(trigger), List.of())) {
            var discovery = binding.discovery();
            BlockPos position = event.blockPos() != null ? event.blockPos()
                    : event.killedEntity() != null ? event.killedEntity().blockPosition() : player.blockPosition();
            boolean hasItemMatch = List.of("item", "item_tag", "book", "components", "durability").stream().anyMatch(discovery.match()::has);
            var stack = event.offer() == null ? event.itemStack() : event.offer().getResult();
            if ((!hasItemMatch || discovery.matchesItem(stack)) && discovery.matchesPayload(payload) && atMatchPosition(player, position, discovery))
                discover(player, binding.quest(), discovery,
                        event.villager() instanceof net.minecraft.world.entity.npc.Villager v ? v.getUUID() : null, payload);
        }
    }

    public static DialogueContext context(ServerPlayer player, CompiledQuest quest, UUID provider) {
        var server = player.serverLevel().getServer();
        if (quest.provider().providerType().equals(ResourceLocation.parse("villagerretaliation:discovery"))) provider = null;
        if (!quest.provider().campaignRole().isBlank() && quest.metadata().campaign() != null) {
            var result = CampaignCharacterService.resolve(server, quest.metadata().campaign(), quest.provider().campaignRole());
            if (!result.available()) return null;
            provider = result.entity().getUUID();
        }
        if (provider != null) {
            var villager = VillagerEntityResolver.loaded(server, provider);
            if (villager == null || villager.level() != player.level()) return null;
            return VillagerInteractionService.createDialogueContext(player.serverLevel(), player, villager);
        }
        return DialogueContext.forPlayer(player);
    }

    private static void discover(ServerPlayer player, CompiledQuest quest, QuestDiscoveryDefinition discovery,
            UUID provider, Map<String, String> payload) {
        var context = context(player, quest, provider);
        if (context == null || !eligible(context, quest, discovery, payload)) return;
        boolean offer = discovery.offer() || CampaignQuestAcceptanceService.requiresConfirmation(context, quest.asQuestDefinition());
        var saved = QuestDiscoverySavedData.get(player.serverLevel());
        if (offer) {
            if (saved.offer(player.getUUID(), quest.id(), new QuestDiscoverySavedData.Offer(discovery.id(), context.providerId(), payload))) {
                player.sendSystemMessage(Component.translatable("villagerretaliation.quest.discovery.offer", title(player, quest)));
                VillagerQuestService.flashTracker(player, true);
            }
        } else {
            var outcome = VillagerQuestService.startDiscoveredQuest(context, quest.id());
            if (outcome.status().equals("started")) saved.remove(player.getUUID(), quest.id());
        }
    }

    private static Component title(ServerPlayer player, CompiledQuest quest) {
        return Component.literal(com.jvn.villagerretaliation.quest.tracking.QuestPresentationService.questTitle(player, quest.asQuestDefinition(), Map.of()));
    }

    private static boolean eligible(DialogueContext context, CompiledQuest quest, QuestDiscoveryDefinition discovery,
            Map<String, String> payload) {
        var progress = VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), quest.id());
        if (!VillagerQuestService.canStartDiscoveredQuest(context, quest.asQuestDefinition(), progress)) return false;
        var event = QuestTriggerRegistry.eventBySerializedName(discovery.event());
        var trigger = new QuestTriggerContext(event == null ? QuestDefinition.TriggerEvent.PLAYER_TICK : event,
                context.level().getGameTime(), "", payload, context);
        return DialogueCondition.matchesAll(context, trigger, discovery.conditions());
    }

    public static boolean available(ServerPlayer player, ResourceLocation id) {
        if (!player.isAlive() || player.isSpectator()) return false;
        var offer = QuestDiscoverySavedData.get(player.serverLevel()).offers(player.getUUID()).get(id);
        var quest = VillagerQuestResources.compiledQuest(player.serverLevel().getServer(), id).orElse(null);
        if (offer == null || quest == null) return false;
        var context = context(player, quest, offer.provider());
        return context != null && quest.discovery().stream().anyMatch(discovery -> discovery.id().equals(offer.discovery())
                && eligible(context, quest, discovery, offer.payload()));
    }

    public static boolean accept(ServerPlayer player, ResourceLocation id) {
        var saved = QuestDiscoverySavedData.get(player.serverLevel());
        var offer = saved.offers(player.getUUID()).get(id);
        var quest = VillagerQuestResources.compiledQuest(player.serverLevel().getServer(), id).orElse(null);
        if (offer == null || quest == null) return false;
        var discovery = quest.discovery().stream().filter(value -> value.id().equals(offer.discovery())).findFirst().orElse(null);
        var context = context(player, quest, offer.provider());
        if (discovery == null || context == null || !eligible(context, quest, discovery, offer.payload())) {
            player.sendSystemMessage(Component.translatable("villagerretaliation.quest.discovery.unavailable"));
            return false;
        }
        Runnable start = () -> {
            if (!available(player, id)) return;
            if (VillagerQuestService.startDiscoveredQuest(context(player, quest, offer.provider()), id).status().equals("started"))
                saved.remove(player.getUUID(), id);
            VillagerQuestService.flashTracker(player, true);
        };
        if (!CampaignQuestAcceptanceService.beforeDiscovery(context, id, start)) start.run();
        return true;
    }

    public static boolean atLocation(ServerPlayer player, QuestDiscoveryDefinition discovery) {
        return atMatchPosition(player, player.blockPosition(), discovery);
    }

    private static boolean atMatchPosition(ServerPlayer player, BlockPos pos, QuestDiscoveryDefinition discovery) {
        if (!discovery.string("dimension").isEmpty()
                && !player.level().dimension().location().toString().equals(discovery.string("dimension"))) return false;
        if (pos == null) return false;
        if (discovery.match().has("biome") && !player.level().getBiome(pos).is(ResourceLocation.parse(discovery.string("biome")))) return false;
        if (discovery.match().has("structure") && !com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker.isPlayerInsideStructure(
                player.serverLevel(), pos, ResourceLocation.parse(discovery.string("structure")))) return false;
        if (!discovery.match().has("pos")) return true;
        var coordinates = discovery.match().getAsJsonArray("pos");
        var target = new BlockPos(coordinates.get(0).getAsInt(), coordinates.get(1).getAsInt(), coordinates.get(2).getAsInt());
        return pos.distSqr(target) <= discovery.radius() * discovery.radius();
    }
}
