package com.jvn.villagerretaliation.scene.executor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.RecoveryMode;
import com.jvn.villagerretaliation.api.scene.SceneStepExecutor;
import com.jvn.villagerretaliation.api.scene.SceneStepExecutors;
import com.jvn.villagerretaliation.scene.encounter.EncounterInstance;
import com.jvn.villagerretaliation.scene.encounter.EncounterResources;
import com.jvn.villagerretaliation.scene.encounter.EncounterService;
import com.jvn.villagerretaliation.scene.encounter.EncounterTemplate;
import com.jvn.villagerretaliation.scene.runtime.SceneExecutionContext;
import com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt;
import com.jvn.villagerretaliation.scene.runtime.SceneStepResult;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

public final class EncounterStepExecutors {
    private static boolean registered;

    private EncounterStepExecutors() {}

    public static synchronized void register() {
        if (registered) return;
        register("start_encounter", new Start());
        register("wait_encounter", new Wait());
        register("cancel_encounter", new Cancel());
        register("cleanup_encounter", new Cleanup());
        registered = true;
    }

    private static void register(String id, SceneStepExecutor value) {
        SceneStepExecutors.register(VillagerRetaliation.id(id), value);
    }

    private abstract static class Base implements SceneStepExecutor {
        public RecoveryMode recoveryMode() {
            return RecoveryMode.WORLD_RECONCILED;
        }

        public SceneStepResult prepare(SceneExecutionContext c) {
            return SceneStepResult.ready();
        }

        public SceneStepResult verify(SceneExecutionContext c) {
            return SceneStepResult.complete();
        }

        public SceneStepResult reconcile(SceneExecutionContext c) {
            return apply(c);
        }

        protected EncounterInstance encounter(SceneExecutionContext c) {
            String id = c.record().durableValues().get("encounter_id");
            if (id == null) {
                String start = string(c, "encounter_step", "");
                if (!start.isBlank() && c.instance().stepRecords().containsKey(start))
                    id = c.instance().stepRecords().get(start).durableValues().get("encounter_id");
            }
            if (id == null) {
                var matches =
                        c.repository().encounters().stream()
                                .filter(v -> v.sceneId().equals(c.instance().id()))
                                .sorted(java.util.Comparator.comparing(v -> v.id().toString()))
                                .toList();
                if (matches.size() == 1) id = matches.getFirst().id().toString();
            }
            if (id == null) return null;
            try {
                EncounterInstance value =
                        c.repository().encounter(java.util.UUID.fromString(id)).orElse(null);
                if (value != null) c.record().putDurableValue("encounter_id", id);
                return value;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private static final class Start extends Base {
        public SceneStepResult prepare(SceneExecutionContext c) {
            VariantChoice choice = variantChoice(c);
            if (!choice.valid())
                return SceneStepResult.fail("encounter_variant_invalid", choice.diagnostic());
            EncounterTemplate template = choice.template();
            String phaseError = validatePhases(c, template);
            if (!phaseError.isBlank())
                return SceneStepResult.fail("encounter_phase_invalid", phaseError);
            String objectiveError = validateObjectives(c, template);
            if (!objectiveError.isBlank())
                return SceneStepResult.fail("encounter_objective_invalid", objectiveError);
            BlockPos anchor = anchor(c);
            ResourceLocation anchorDimension = dimension(c);
            if (template.spawnMode() == EncounterTemplate.SpawnMode.NEAR_PLAYER) {
                var player =
                        c.instance().participants().stream()
                                .map(id -> c.server().getPlayerList().getPlayer(id))
                                .filter(java.util.Objects::nonNull)
                                .findFirst()
                                .orElse(null);
                if (player == null)
                    return SceneStepResult.waitUntil(
                            c.gameTime() + 20,
                            "near_player encounter is waiting for a participant");
                anchor = player.blockPosition();
                anchorDimension = player.serverLevel().dimension().location();
            }
            if (anchor == null)
                return SceneStepResult.fail(
                        "encounter_anchor_missing", "encounter requires anchor actor or position");
            anchor =
                    anchor.offset(
                            parameterInt(c, "offset_x", 0),
                            parameterInt(c, "offset_y", 0),
                            parameterInt(c, "offset_z", 0));
            if (bool(c, "surface_anchor", false)) {
                var level =
                        c.server()
                                .getLevel(
                                        net.minecraft.resources.ResourceKey.create(
                                                net.minecraft.core.registries.Registries.DIMENSION,
                                                anchorDimension));
                if (level == null || !level.hasChunkAt(anchor))
                    return SceneStepResult.waitUntil(
                            c.gameTime() + 20, "encounter destination chunk is unloaded");
                anchor =
                        new BlockPos(
                                anchor.getX(),
                                level.getHeight(
                                        net.minecraft.world.level.levelgen.Heightmap.Types
                                                .MOTION_BLOCKING_NO_LEAVES,
                                        anchor.getX(),
                                        anchor.getZ()),
                                anchor.getZ());
            }
            c.record().putDurableValue("anchor_x", Integer.toString(anchor.getX()));
            c.record().putDurableValue("anchor_y", Integer.toString(anchor.getY()));
            c.record().putDurableValue("anchor_z", Integer.toString(anchor.getZ()));
            c.record().putDurableValue("anchor_dimension", anchorDimension.toString());
            return SceneStepResult.ready();
        }

        public SceneStepResult apply(SceneExecutionContext c) {
            VariantChoice choice = variantChoice(c);
            if (!choice.valid())
                return SceneStepResult.fail("encounter_variant_invalid", choice.diagnostic());
            EncounterTemplate template = choice.template();
            BlockPos anchor =
                    new BlockPos(
                            integer(c, "anchor_x"), integer(c, "anchor_y"), integer(c, "anchor_z"));
            ResourceLocation anchorDimension =
                    ResourceLocation.parse(c.record().durableValues().get("anchor_dimension"));
            String operation = c.operationId("encounter");
            EncounterInstance existing =
                    c.repository().encounterByOperation(c.instance().id(), operation).orElse(null);
            ResolvedPoints resolved =
                    existing == null
                            ? resolveSpawnPoints(c, template, anchorDimension)
                            : new ResolvedPoints(
                                    List.copyOf(existing.resolvedSpawnPoints().values()), "");
            if (!resolved.diagnostic().isBlank())
                return SceneStepResult.fail(
                        "encounter_spawn_points_invalid", resolved.diagnostic());
            var started =
                    c.repository()
                            .startEncounter(
                                    template,
                                    choice.sourceTemplate(),
                                    choice.variantId(),
                                    choice.seed(),
                                    c.instance(),
                                    operation,
                                    anchorDimension,
                                    anchor,
                                    string(c, "difficulty", "normal"),
                                    resolved.points());
            EncounterInstance encounter = started.encounter();
            c.record().putDurableValue("encounter_id", encounter.id().toString());
            SceneOperationReceipt receipt =
                    c.prepareReceipt("encounter", SceneOperationReceipt.Kind.ENCOUNTER_CREATION);
            var result =
                    EncounterService.reconcileSpawn(
                            c.server(), c.repository(), encounter, template);
            if (result.status() == EncounterService.Status.WAITING)
                return SceneStepResult.waitUntil(c.gameTime() + 20, result.diagnostic());
            if (result.status() == EncounterService.Status.FAILED)
                return SceneStepResult.fail("encounter_spawn_failed", result.diagnostic());
            receipt.applied(
                    c.gameTime(),
                    "encounter="
                            + encounter.id()
                            + " variant="
                            + choice.variantId()
                            + " template="
                            + template.id());
            receipt.completed(
                    c.gameTime(),
                    "encounter="
                            + encounter.id()
                            + " variant="
                            + choice.variantId()
                            + " template="
                            + template.id());
            if (!choice.variantId().isBlank()
                    && c.step().transitions().containsKey(choice.variantId()))
                c.record().chooseTransition(choice.variantId());
            return SceneStepResult.applied();
        }
    }

    private static final class Wait extends Base {
        public SceneStepResult apply(SceneExecutionContext c) {
            EncounterInstance e = encounter(c);
            if (e == null)
                return SceneStepResult.block(
                        "encounter_missing", "wait_encounter has no persisted encounter id");
            var result = EncounterService.refresh(c.server(), c.repository(), e);
            if (!c.record().chosenTransition().isBlank()) return SceneStepResult.applied();
            return switch (result.status()) {
                case COMPLETED -> SceneStepResult.applied();
                case FAILED -> SceneStepResult.fail("encounter_failed", result.diagnostic());
                case ACTIVE, WAITING ->
                        SceneStepResult.waitUntil(c.gameTime() + 20, result.diagnostic());
            };
        }
    }

    private static final class Cancel extends Base {
        public SceneStepResult apply(SceneExecutionContext c) {
            EncounterInstance e = encounter(c);
            if (e == null) return SceneStepResult.skip();
            e.cancel();
            EncounterService.hideBossBars(e);
            c.repository().changed();
            return SceneStepResult.applied();
        }
    }

    private static final class Cleanup extends Base {
        public SceneStepResult apply(SceneExecutionContext c) {
            EncounterInstance e = encounter(c);
            if (e == null) return SceneStepResult.skip();
            var result =
                    EncounterService.cleanup(
                            c.server(), c.repository(), e, bool(c, "force_remove", false));
            return result.status() == EncounterService.Status.WAITING
                    ? SceneStepResult.waitUntil(c.gameTime() + 20, result.diagnostic())
                    : SceneStepResult.applied();
        }
    }

    private static ResolvedPoints resolveSpawnPoints(
            SceneExecutionContext c, EncounterTemplate template, ResourceLocation anchorDimension) {
        List<EncounterInstance.ResolvedSpawnPoint> values = new ArrayList<>();
        for (EncounterTemplate.SpawnPoint point : template.spawnPoints()) {
            ResourceLocation dimension;
            BlockPos position;
            if (point.actorSource()) {
                var binding = c.instance().actorBindings().get(point.actorAlias());
                if (binding == null
                        || binding.lastDimension() == null
                        || binding.lastPosition() == null)
                    return new ResolvedPoints(
                            List.of(),
                            "spawn point "
                                    + point.id()
                                    + " references missing or positionless actor "
                                    + point.actorAlias());
                dimension = binding.lastDimension();
                position = binding.lastPosition().offset(point.offset());
            } else {
                dimension = point.dimension() == null ? anchorDimension : point.dimension();
                position = point.position();
            }
            if (c.server()
                            .getLevel(
                                    net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.DIMENSION,
                                            dimension))
                    == null)
                return new ResolvedPoints(
                        List.of(),
                        "spawn point " + point.id() + " uses unknown dimension " + dimension);
            if (!dimension.equals(anchorDimension))
                return new ResolvedPoints(
                        List.of(),
                        "spawn point "
                                + point.id()
                                + " is in incompatible dimension "
                                + dimension
                                + "; expected "
                                + anchorDimension);
            values.add(
                    new EncounterInstance.ResolvedSpawnPoint(
                            point.id(), dimension, position, point.weight()));
        }
        return new ResolvedPoints(List.copyOf(values), "");
    }

    private record ResolvedPoints(
            List<EncounterInstance.ResolvedSpawnPoint> points, String diagnostic) {}

    private static VariantChoice variantChoice(SceneExecutionContext c) {
        String persisted = c.record().durableValues().get("encounter_resolved_template");
        if (persisted != null) {
            ResourceLocation resolved = ResourceLocation.tryParse(persisted),
                    source =
                            ResourceLocation.tryParse(
                                    c.record()
                                            .durableValues()
                                            .getOrDefault("encounter_source_template", persisted));
            EncounterTemplate template =
                    resolved == null
                            ? null
                            : EncounterResources.template(c.server(), resolved).orElse(null);
            long seed = durableLong(c, "encounter_variant_seed", 0L);
            return template == null || template.variantSelector()
                    ? new VariantChoice(
                            null,
                            source,
                            c.record().durableValues().getOrDefault("encounter_variant_id", ""),
                            seed,
                            "persisted resolved encounter template "
                                    + resolved
                                    + " is unavailable or still a selector")
                    : new VariantChoice(
                            template,
                            source,
                            c.record().durableValues().getOrDefault("encounter_variant_id", ""),
                            seed,
                            "");
        }
        long seed = EncounterResources.variantSeed(c.instance().id(), c.operationId("encounter"));
        ResourceLocation source;
        String selected;
        EncounterResources.VariantResolution resolution;
        JsonElement raw = c.step().parameters().get("variants");
        if (raw != null) {
            List<EncounterTemplate.Variant> variants = stepVariants(raw);
            if (variants.isEmpty())
                return new VariantChoice(
                        null,
                        null,
                        "",
                        seed,
                        "start_encounter variants must contain 1 to 32 valid entries");
            EncounterTemplate.Variant choice =
                    EncounterResources.selectVariant(variants, seed, c.step().id());
            source = choice.template();
            selected = choice.id();
            resolution = EncounterResources.resolve(c.server(), source, seed);
        } else {
            source = id(c, "template", "encounter_template");
            if (source == null)
                return new VariantChoice(
                        null,
                        null,
                        "",
                        seed,
                        "start_encounter requires exactly one template or variants array");
            resolution = EncounterResources.resolve(c.server(), source, seed);
            selected = resolution.selectedVariantId();
        }
        if (!resolution.valid())
            return new VariantChoice(null, source, selected, seed, resolution.diagnostic());
        c.record().putDurableValue("encounter_source_template", source.toString());
        c.record()
                .putDurableValue(
                        "encounter_resolved_template", resolution.resolvedTemplateId().toString());
        c.record().putDurableValue("encounter_variant_id", selected);
        c.record().putDurableValue("encounter_variant_seed", Long.toString(seed));
        return new VariantChoice(resolution.template(), source, selected, seed, "");
    }

    private static List<EncounterTemplate.Variant> stepVariants(JsonElement raw) {
        if (!raw.isJsonArray()
                || raw.getAsJsonArray().size() < 1
                || raw.getAsJsonArray().size() > 32) return List.of();
        List<EncounterTemplate.Variant> values = new ArrayList<>();
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (JsonElement element : raw.getAsJsonArray()) {
            if (!element.isJsonObject()) return List.of();
            JsonObject object = element.getAsJsonObject();
            if (object.keySet().stream()
                    .anyMatch(key -> !List.of("id", "weight", "template").contains(key)))
                return List.of();
            String id = object.has("id") ? object.get("id").getAsString() : "",
                    templateValue =
                            object.has("template") ? object.get("template").getAsString() : "";
            int weight;
            try {
                weight = object.has("weight") ? object.get("weight").getAsInt() : 1;
            } catch (RuntimeException e) {
                return List.of();
            }
            ResourceLocation template = ResourceLocation.tryParse(templateValue);
            try {
                EncounterTemplate.Variant variant =
                        new EncounterTemplate.Variant(id, weight, template);
                if (!ids.add(id)) return List.of();
                values.add(variant);
            } catch (IllegalArgumentException e) {
                return List.of();
            }
        }
        return List.copyOf(values);
    }

    private record VariantChoice(
            EncounterTemplate template,
            ResourceLocation sourceTemplate,
            String variantId,
            long seed,
            String diagnostic) {
        boolean valid() {
            return template != null && sourceTemplate != null && diagnostic.isBlank();
        }
    }

    private static String validatePhases(SceneExecutionContext c, EncounterTemplate template) {
        if (template.failure() != null
                && !template.failure().branchStep().isBlank()
                && !c.definition().steps().containsKey(template.failure().branchStep()))
            return "failure policy references missing scene step "
                    + template.failure().branchStep();
        for (EncounterTemplate.Phase phase : template.phases())
            for (EncounterTemplate.PhaseAction action : phase.actions()) {
                if (action.type() == EncounterTemplate.PhaseActionType.TRANSITION
                        && !c.definition().steps().containsKey(action.target()))
                    return "phase "
                            + phase.id()
                            + " references missing scene step "
                            + action.target();
                if (action.type() == EncounterTemplate.PhaseActionType.FACT
                        && action.scope() == EncounterTemplate.FactScope.QUEST
                        && c.instance().owningQuestId() == null)
                    return "phase "
                            + phase.id()
                            + " uses quest fact scope outside a linked quest scene";
                if ((action.type() == EncounterTemplate.PhaseActionType.NOTIFICATION
                                || action.type() == EncounterTemplate.PhaseActionType.DIALOGUE
                                || action.type() == EncounterTemplate.PhaseActionType.FACT
                                        && action.scope() != EncounterTemplate.FactScope.WORLD)
                        && c.instance().participants().isEmpty())
                    return "phase "
                            + phase.id()
                            + " action "
                            + action.id()
                            + " requires captured participants";
            }
        return "";
    }

    private static String validateObjectives(SceneExecutionContext c, EncounterTemplate template) {
        if (template.completionObjectives() == null) return "";
        for (EncounterTemplate.Objective objective : template.completionObjectives().objectives()) {
            List<String> actors =
                    switch (objective.type()) {
                        case PROTECT_ACTOR, ESCORT_ACTOR -> List.of(objective.actorAlias());
                        case DESTROY_TARGETS -> objective.actorAliases();
                        default -> List.of();
                    };
            for (String actor : actors)
                if (!c.instance().actorBindings().containsKey(actor))
                    return "objective "
                            + objective.id()
                            + " references missing scene actor "
                            + actor;
            if ((objective.type() == EncounterTemplate.ObjectiveType.RETRIEVE_ITEM
                            || objective.type() == EncounterTemplate.ObjectiveType.HOLD_AREAS)
                    && c.instance().participants().isEmpty())
                return "objective " + objective.id() + " requires captured participants";
        }
        return "";
    }

    private static ResourceLocation id(SceneExecutionContext c, String... keys) {
        for (String k : keys) {
            String v = string(c, k, "");
            if (!v.isBlank()) return ResourceLocation.tryParse(v);
        }
        return null;
    }

    private static String string(SceneExecutionContext c, String key, String f) {
        return c.step().parameters().has(key) ? c.step().parameters().get(key).getAsString() : f;
    }

    private static boolean bool(SceneExecutionContext c, String key, boolean f) {
        return c.step().parameters().has(key) ? c.step().parameters().get(key).getAsBoolean() : f;
    }

    private static int parameterInt(SceneExecutionContext c, String key, int f) {
        return c.step().parameters().has(key) ? c.step().parameters().get(key).getAsInt() : f;
    }

    private static int integer(SceneExecutionContext c, String key) {
        return Integer.parseInt(c.record().durableValues().get(key));
    }

    private static long durableLong(SceneExecutionContext c, String key, long fallback) {
        try {
            return Long.parseLong(
                    c.record().durableValues().getOrDefault(key, Long.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static ResourceLocation dimension(SceneExecutionContext c) {
        String actor = string(c, "anchor_actor", "");
        var binding = c.instance().actorBindings().get(actor);
        return binding != null && binding.lastDimension() != null
                ? binding.lastDimension()
                : ResourceLocation.tryParse(string(c, "dimension", "minecraft:overworld"));
    }

    private static BlockPos anchor(SceneExecutionContext c) {
        String actor = string(c, "anchor_actor", "");
        var binding = c.instance().actorBindings().get(actor);
        if (binding != null && binding.lastPosition() != null) return binding.lastPosition();
        if (c.step().parameters().has("x")
                && c.step().parameters().has("y")
                && c.step().parameters().has("z"))
            return new BlockPos(
                    c.step().parameters().get("x").getAsInt(),
                    c.step().parameters().get("y").getAsInt(),
                    c.step().parameters().get("z").getAsInt());
        return null;
    }
}
