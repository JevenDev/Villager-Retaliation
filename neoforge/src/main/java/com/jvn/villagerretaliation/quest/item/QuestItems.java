package com.jvn.villagerretaliation.quest.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;

public final class QuestItems {
    public static final String IDENTITY = "villagerretaliation:quest_item";
    private static volatile Map<ResourceLocation, JsonObject> definitions = Map.of();
    private static final ThreadLocal<Map<ResourceLocation, JsonObject>> PREVIEW = new ThreadLocal<>();

    public static Scope preview(Map<ResourceLocation, JsonObject> values) {
        var previous = PREVIEW.get();
        PREVIEW.set(Map.copyOf(values));
        return new Scope(previous);
    }

    public record Scope(Map<ResourceLocation, JsonObject> previous) implements AutoCloseable {
        @Override public void close() { if (previous == null) PREVIEW.remove(); else PREVIEW.set(previous); }
    }

    public static JsonObject definition(ResourceLocation id) {
        var preview = PREVIEW.get();
        var value = preview != null && preview.containsKey(id) ? preview.get(id) : definitions.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown quest item: " + id);
        return value.deepCopy();
    }
    private QuestItems() { }

    public static void reload(ResourceManager manager) {
        var next = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (var entry : manager.listResources("quest_items", id -> id.getPath().endsWith(".json")).entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                var id = ResourceLocation.fromNamespaceAndPath(entry.getKey().getNamespace(),
                        entry.getKey().getPath().substring("quest_items/".length(), entry.getKey().getPath().length() - 5));
                next.put(id, JsonParser.parseReader(reader).getAsJsonObject());
            } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                throw new IllegalArgumentException("Invalid quest item " + entry.getKey(), exception);
            }
        }
        definitions = Map.copyOf(next);
    }

    public static ItemStack resolve(HolderLookup.Provider registries, ResourceLocation id) {
        return create(registries, id, definition(id));
    }

    public static ItemStack create(HolderLookup.Provider registries, ResourceLocation id, JsonObject definition) {
        var issues = com.jvn.villagerretaliation.quest.schema.QuestJsonSchema.validate(definition, schema());
        if (!issues.isEmpty()) throw new IllegalArgumentException(issues.getFirst().message());
        var stack = ItemStack.STRICT_CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), definition.get("stack"))
                .getOrThrow(message -> new IllegalArgumentException("Invalid quest item stack: " + message));
        if (stack.isEmpty()) throw new IllegalArgumentException("Choose a nonempty base item");
        if (definition.has("name")) stack.set(DataComponents.CUSTOM_NAME, text(definition.get("name").getAsString()));
        if (definition.has("lore")) stack.set(DataComponents.LORE, new ItemLore(definition.getAsJsonArray("lore").asList()
                .stream().map(value -> text(value.getAsString())).toList()));
        if (definition.has("damage")) {
            int damage = definition.get("damage").getAsInt();
            if (damage < 0 || damage > stack.getMaxDamage() || damage > 0 && stack.getMaxDamage() == 0)
                throw new IllegalArgumentException("Damage must fit the selected item's durability");
            if (stack.getMaxDamage() > 0) stack.setDamageValue(damage);
        }
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putString(IDENTITY, id.toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    public static boolean matches(ItemStack stack, ResourceLocation id) {
        return !stack.isEmpty() && id.toString().equals(identity(stack));
    }

    public static String identity(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(IDENTITY);
    }

    public static Component text(String markup) {
        var output = Component.empty().withStyle(Style.EMPTY.withItalic(false));
        for (var segment : DialogueTextSegment.parse(markup, DialogueTextEffects.NONE)) {
            var effects = segment.effects();
            if (effects.wavy() || effects.shake() || effects.pulse() || effects.jump() || effects.rainbow()
                    || effects.gradientStartColor() != null || !segment.cues().isEmpty())
                throw new IllegalArgumentException("Item tooltips support static colors, bold, italic, underline, strikethrough and obfuscated text");
            var style = Style.EMPTY.withBold(effects.bold()).withItalic(effects.italic()).withUnderlined(effects.underlined())
                    .withStrikethrough(effects.strikethrough()).withObfuscated(effects.obfuscated());
            if (effects.color() != null) style = style.withColor(effects.color());
            output.append(Component.literal(segment.text()).withStyle(style));
        }
        return output;
    }

    public static JsonObject schema() {
        return JsonParser.parseString("""
                {"type":"object","required":["stack"],"properties":{
                  "stack":{"type":"object","required":["id","count"],"properties":{"id":{"type":"string"},"count":{"type":"integer","minimum":1,"maximum":99},"components":{"type":"object"}}},
                  "name":{"type":"string","maxLength":512},"lore":{"type":"array","maxItems":256,"items":{"type":"string","maxLength":512}},
                  "damage":{"type":"integer","minimum":0}}}
                """).getAsJsonObject();
    }
}
