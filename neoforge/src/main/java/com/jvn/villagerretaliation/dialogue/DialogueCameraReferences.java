package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.CampaignLocationService;
import com.jvn.villagerretaliation.dialogue.DialogueCameraReference.Binding;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Camera;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class DialogueCameraReferences {
    public static final int MAX_REFERENCES = 128;
    private DialogueCameraReferences() { }
    private static final Map<net.minecraft.server.level.ServerPlayer, RuntimeReferences> runtime = new java.util.WeakHashMap<>();
    private record RuntimeReferences(java.util.UUID session, Map<String, Saved> references) { }

    public record Saved(DialogueCameraReference reference, Binding binding) {
        public Saved {
            java.util.Objects.requireNonNull(reference);
            java.util.Objects.requireNonNull(binding);
            if (reference.kind() == DialogueCameraReference.Kind.ITEM && binding.block() != null
                    || reference.kind() == DialogueCameraReference.Kind.BLOCK && binding.entity() != null)
                throw new IllegalArgumentException("Camera binding does not match its selector");
        }
    }

    public static Camera bind(Camera camera, Level level, Entity player, Entity speaker, ResourceLocation campaign,
            Map<String, Saved> references) {
        if (camera == null || camera.shots().isEmpty()) return camera;
        for (var shot : camera.shots()) {
            var reference = shot.reference();
            if (reference == null || references.containsKey(reference.id()) || references.size() >= MAX_REFERENCES) continue;
            references.put(reference.id(), new Saved(reference, resolve(reference, level, player, speaker, campaign)));
        }
        return apply(camera, references, level.dimension().location());
    }

    public static Camera apply(Camera camera, Map<String, Saved> references, ResourceLocation dimension) {
        if (camera == null || camera.shots().isEmpty()) return camera;
        return new Camera(camera.target(), camera.zoom(), camera.shots().stream().map(shot -> {
            if (shot.reference() == null) return shot;
            var saved = references.get(shot.reference().id());
            return shot.withBinding(saved != null && saved.reference().equals(shot.reference()) ? saved.binding()
                    : missing(dimension, saved == null ? "Reference was not bound." : "Reference ID has conflicting selectors."));
        }).toList());
    }

    public static List<DialogueSpeech> bind(List<DialogueSpeech> lines, net.minecraft.server.level.ServerPlayer player, Entity provider) {
        var references = runtimeReferences(player, provider);
        return lines.stream().map(line -> {
            var speaker = line.speaker() == null ? provider : player.serverLevel().getEntity(line.speaker());
            return new DialogueSpeech(line.speaker(), line.entityId(), line.name(), line.segments(),
                    bind(line.camera(), player.serverLevel(), player, speaker, null, references), line.presentationId());
        }).toList();
    }

    public static Camera bind(Camera camera, net.minecraft.server.level.ServerPlayer player, Entity provider) {
        return bind(camera, player.serverLevel(), player, provider, null, runtimeReferences(player, provider));
    }

    private static Map<String, Saved> runtimeReferences(net.minecraft.server.level.ServerPlayer player, Entity provider) {
        var session = provider instanceof net.minecraft.world.entity.LivingEntity living
                ? com.jvn.villagerretaliation.interaction.InteractionSessions.sessionId(player, living) : null;
        if (session == null) { runtime.remove(player); return new LinkedHashMap<>(); }
        var saved = runtime.get(player);
        if (saved == null || !saved.session().equals(session)) {
            saved = new RuntimeReferences(session, new LinkedHashMap<>());
            runtime.put(player, saved);
        }
        return saved.references();
    }

    private static Binding resolve(DialogueCameraReference reference, Level level, Entity player, Entity speaker, ResourceLocation campaign) {
        var dimension = level.dimension().location();
        Entity anchor = reference.origin() == DialogueCamera.Anchor.PLAYER ? player : speaker;
        if (anchor == null || anchor.level() != level) return missing(dimension, "Reference origin is unavailable.");
        BlockPos origin = anchor.blockPosition();
        if (!reference.location().isEmpty()) {
            if (!(level instanceof ServerLevel serverLevel)) return missing(dimension, "Campaign locations require a server-bound preview.");
            var result = CampaignLocationService.resolve(serverLevel.getServer(), campaign, reference.location());
            if (result.binding() == null || !result.binding().dimension().equals(dimension))
                return missing(dimension, "Campaign location is unavailable in this dimension.");
            origin = result.binding().position();
        } else if (!reference.marker().isEmpty()) {
            var markers = level.getEntitiesOfClass(Marker.class, anchor.getBoundingBox().inflate(32),
                    entity -> entity.isAlive() && entity.getTags().contains(reference.marker()));
            if (markers.size() != 1) return missing(dimension, markers.isEmpty() ? "Marker is missing." : "Marker tag is ambiguous.");
            origin = markers.getFirst().blockPosition();
        }
        origin = origin.offset(reference.offset());
        if (!level.isInWorldBounds(origin) || !level.getWorldBorder().isWithinBounds(origin))
            return missing(dimension, "Reference origin is outside world bounds.");
        int radius = reference.radius();
        if (reference.kind() == DialogueCameraReference.Kind.ITEM) {
            var bounds = new AABB(origin).inflate(radius);
            var items = level.getEntitiesOfClass(ItemEntity.class, bounds, entity -> entity.isAlive()
                    && BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).equals(reference.match())
                    && (reference.entityTag().isEmpty() || entity.getTags().contains(reference.entityTag())));
            if (items.size() != 1) return missing(dimension, items.isEmpty() ? "Dropped item is missing." : "Dropped item selector is ambiguous.");
            return new Binding(dimension, items.getFirst().getUUID(), null, "");
        }
        BlockPos found = null;
        for (var position : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
            if (!level.isInWorldBounds(position) || !level.getWorldBorder().isWithinBounds(position) || !level.hasChunkAt(position)) continue;
            var state = level.getBlockState(position);
            if (state.isAir() || !BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(reference.match())) continue;
            if (found != null) return missing(dimension, "Block selector is ambiguous.");
            found = position.immutable();
        }
        return found == null ? missing(dimension, "Block is missing from loaded chunks.") : new Binding(dimension, null, found, "");
    }

    private static Binding missing(ResourceLocation dimension, String diagnostic) { return new Binding(dimension, null, null, diagnostic); }

    public static CompoundTag save(Map<String, Saved> references) {
        var tag = new CompoundTag();
        references.forEach((id, saved) -> {
            var value = new CompoundTag();
            value.putString("Selector", saved.reference().json().toString());
            value.putString("Binding", saved.binding().json().toString());
            tag.put(id, value);
        });
        return tag;
    }

    public static Map<String, Saved> load(CompoundTag tag) {
        if (tag.size() > MAX_REFERENCES) throw new IllegalArgumentException("Too many saved camera references");
        var result = new LinkedHashMap<String, Saved>();
        for (var id : tag.getAllKeys()) {
            if (!tag.contains(id, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid saved camera reference");
            var value = tag.getCompound(id);
            if (!value.contains("Selector", Tag.TAG_STRING) || !value.contains("Binding", Tag.TAG_STRING)
                    || value.getString("Selector").length() > 4096 || value.getString("Binding").length() > 1024)
                throw new IllegalArgumentException("Invalid saved camera reference fields");
            var reference = DialogueCameraReference.read(object(value.getString("Selector")));
            if (!reference.id().equals(id)) throw new IllegalArgumentException("Saved camera reference ID mismatch");
            result.put(id, new Saved(reference, Binding.read(object(value.getString("Binding")))));
        }
        return result;
    }

    private static com.google.gson.JsonObject object(String text) {
        try {
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (com.google.gson.JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Invalid saved camera reference JSON", exception);
        }
    }
}
