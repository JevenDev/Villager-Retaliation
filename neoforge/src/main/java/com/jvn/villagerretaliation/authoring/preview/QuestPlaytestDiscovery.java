package com.jvn.villagerretaliation.authoring.preview;

import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.discovery.QuestDiscoveryDefinition;
import com.jvn.villagerretaliation.quest.provider.DiscoveryQuestProviderType;
import com.jvn.villagerretaliation.util.item.ComponentPredicate;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public final class QuestPlaytestDiscovery {
    private QuestPlaytestDiscovery() { }

    public static boolean playerOnly(MinecraftServer server, ResourceLocation quest) {
        return VillagerQuestResources.compiledQuest(server, quest)
                .map(value -> value.provider().providerType().equals(DiscoveryQuestProviderType.ID)).orElse(false);
    }

    public static List<QuestPlaytestView.Discovery> routes(MinecraftServer server, ResourceLocation quest) {
        return VillagerQuestResources.compiledQuest(server, quest).map(value -> value.discovery().stream()
                .map(route -> new QuestPlaytestView.Discovery(route.id(), route.event(), route.match().toString(),
                        route.match().has("item") || route.match().has("item_tag") || route.match().has("book"),
                        route.match().has("pos"))).toList()).orElse(List.of());
    }

    public static String prepare(ServerPlayer player, ResourceLocation quest, String id, boolean travel) {
        QuestPlaytestSession.require(player, quest);
        var route = VillagerQuestResources.compiledQuest(player.server, quest).orElseThrow().discovery().stream()
                .filter(value -> value.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown discovery route " + id));
        if (travel) {
            if (!route.match().has("pos")) throw new IllegalStateException("This route has no explicit coordinates. Visit its authored biome or structure.");
            var coordinates = route.match().getAsJsonArray("pos");
            var position = new BlockPos(coordinates.get(0).getAsInt(), coordinates.get(1).getAsInt(), coordinates.get(2).getAsInt());
            var dimension = route.string("dimension");
            var level = dimension.isBlank() ? player.serverLevel()
                    : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension)));
            QuestPlaytestSetup.teleport(player, level, position);
            return "Travelled to discovery " + id + ". Authored conditions and biome or structure filters still apply.";
        }
        var sample = sample(player, route);
        if (!player.getInventory().add(sample)) player.drop(sample, false);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return "Provided discovery item for " + id + ". "
                + (route.event().equals("book_opened") ? "Open the book to trigger the route."
                : route.event().equals("item_used") ? "Use the item to trigger the route."
                : route.event().equals("item_acquired") ? "The normal inventory check will detect it."
                : "Perform the authored event with this item.");
    }

    public static ItemStack sample(ServerPlayer player, QuestDiscoveryDefinition route) {
        for (var stack : player.getInventory().items) if (route.matchesItem(stack)) return stack.copyWithCount(1);
        boolean book = route.event().equals("book_opened") || route.match().has("book");
        var item = BuiltInRegistries.ITEM.stream()
                .filter(candidate -> !book || candidate == Items.WRITTEN_BOOK)
                .filter(candidate -> route.string("item").isBlank() || BuiltInRegistries.ITEM.getKey(candidate).toString().equals(route.string("item")))
                .filter(candidate -> route.string("item_tag").isBlank() || candidate.builtInRegistryHolder().is(
                        TagKey.create(Registries.ITEM, ResourceLocation.parse(route.string("item_tag")))))
                .filter(candidate -> candidate != Items.AIR)
                .sorted(java.util.Comparator.comparing(candidate -> BuiltInRegistries.ITEM.getKey(candidate).toString()))
                .findFirst().orElseThrow(() -> new IllegalStateException("No item matches this discovery route"));
        var stack = new ItemStack(item);
        if (book) {
            stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new net.minecraft.world.item.component.WrittenBookContent(
                    net.minecraft.server.network.Filterable.passThrough("Playtest discovery"), "Playtest", 0,
                    List.of(net.minecraft.server.network.Filterable.passThrough(net.minecraft.network.chat.Component.literal(
                            "Open this book to test the authored discovery route."))), true));
        }
        for (var predicate : route.itemPredicate().components()) {
            if (predicate instanceof ComponentPredicate.Exact<?> exact) QuestPlaytestSetup.applyExact(stack, exact);
            else if (predicate instanceof ComponentPredicate.NumericRange range) QuestPlaytestSetup.applyRange(stack, range);
        }
        var durability = route.itemPredicate().durability();
        if (durability != null && !durability.matches(stack)) {
            if (!stack.isDamageableItem()) throw new IllegalStateException("The discovery item cannot have durability");
            int remaining = Math.min(stack.getMaxDamage(), durability.max() == null ? stack.getMaxDamage() : durability.max());
            stack.setDamageValue(stack.getMaxDamage() - remaining);
        }
        if (route.match().has("book")) {
            var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            data.putString("villagerretaliation:quest_book", route.string("book"));
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        }
        if (!route.matchesItem(stack)) throw new IllegalStateException("The discovery item requirements conflict. Add a matching inventory sample.");
        return stack;
    }
}
