package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.conditions.ConditionContext;
import com.jvn.villagerretaliation.dialogue.conditions.ConditionEvaluator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

@EventBusSubscriber(modid = "villagerretaliation")
public final class SettlementJourneyEncounters {
    static final String OWNER = "VillagerRetaliationJourneyEncounter", GENERATION = "JourneyEncounterGeneration";
    private SettlementJourneyEncounters() { }

    static SettlementFoodRequest leader(ServerLevel level, SettlementFoodRequest member) {
        return member.caravan == null ? member : SettlementSavedData.get(level).aid().request(member.caravan);
    }
    static SettlementEncounterState active(ServerLevel level, SettlementFoodRequest member) {
        var leader = leader(level, member);
        var state = leader == null || leader.journey == null ? null : leader.journey.encounter;
        return state == null || state.complete() ? null : state;
    }
    static boolean holds(ServerLevel level, SettlementFoodRequest member) { return active(level, member) != null; }

    static void tick(ServerLevel level, SettlementFoodRequest request, long now) {
        if (request.caravan != null || request.journey == null) return;
        var journey = request.journey;
        var state = journey.encounter;
        if (state == null || state.complete()) {
            if (journey.ownsResident() && !journey.finished() && !request.recalled
                    && (journey.leg == SettlementCourierJourney.Leg.OUTBOUND || journey.leg == SettlementCourierJourney.Leg.HOMEBOUND)
                    && journey.route().steps().get(journey.step).mode() == com.jvn.villagerretaliation.diplomacy.route.DiplomaticRoute.Mode.WALK
                    && journey.boatCustody != SettlementCourierJourney.BoatCustody.LAUNCHED
                    && journey.blocker.isEmpty() && !journey.obstructed && now >= journey.encounterCooldown
                    && journey.remains.size() < 64) select(level, request, now);
            return;
        }
        if (state.lastTick == now) return;
        if (now - state.lastTick < 20) return;
        long elapsed = Math.clamp(now - state.lastTick, 0, 20);
        state.lastTick = now;
        var members = members(level, state);
        if (state.stage == SettlementEncounterState.Stage.RETIRING) {
            if (!SettlementAidCheckpoint.urgent(level, state.checkpointRevision)) return;
            cleanup(level, state);
            state.visible = false; state.stage = SettlementEncounterState.Stage.ACTIVE;
            SettlementCourierService.changed(level, request); return;
        }
        if (state.stage == SettlementEncounterState.Stage.DELAYED) {
            if (!SettlementAidCheckpoint.urgent(level, state.checkpointRevision)) return;
            cleanup(level, state);
            state.elapsed += elapsed;
            if (state.elapsed >= state.definition.delay()) {
                state.stage = SettlementEncounterState.Stage.COMPLETE;
                for (var member : members) {
                    member.journey.lastTick = now; member.journey.lastProgress = now;
                    if (member.journey.blocker.startsWith("encounter_")) SettlementCourierService.block(level, member, "");
                }
            }
            SettlementCourierService.changed(level, request); return;
        }
        boolean nearby = members.stream().anyMatch(member -> SettlementCourierService.nearby(level, member.journey.position, 96));
        boolean live = members.stream().anyMatch(member -> level.getEntity(member.journey.resident) instanceof Villager);
        if (state.visible && (!nearby || !live)) { retiring(level, request); return; }
        if (nearby && live) {
            if (!state.visible) {
                state.visible = true; SettlementCourierService.changed(level, request); state.checkpointRevision = journey.revision; return;
            }
            if (!SettlementAidCheckpoint.urgent(level, state.checkpointRevision)) return;
            boolean represented = materializeAttackers(level, request, members);
            captureAttackers(level, state);
            if (!represented) { SettlementCourierService.changed(level, request); return; }
        } else if (!live && !state.visible && members.stream().allMatch(member -> !member.journey.ownsResident()
                || member.journey.presence == SettlementCourierJourney.Presence.STORED)) {
            if (state.elapsed / state.definition.roundTicks() != (state.elapsed + elapsed) / state.definition.roundTicks())
                state.definition.behavior().advance(level, request, members);
        } else return;
        state.elapsed += elapsed;
        boolean defeated = state.definition.behavior().resolved(request);
        boolean lost = members.stream().noneMatch(member -> member.journey.ownsResident());
        if (lost || state.elapsed >= state.definition.duration() || defeated) resolve(level, request, members, now);
        else SettlementCourierService.changed(level, request);
    }
    static List<SettlementFoodRequest> members(ServerLevel level, SettlementEncounterState state) {
        var aid = SettlementSavedData.get(level).aid();
        return state.participants.stream().map(aid::request).filter(java.util.Objects::nonNull).filter(member -> member.journey != null).toList();
    }
    static void select(ServerLevel level, SettlementFoodRequest request, long now) {
        var journey = request.journey;
        if (now % 20 != 0) return;
        var group = SettlementCaravanService.group(level, request);
        var living = group.stream().filter(member -> member.journey.ownsResident()).toList();
        if (living.isEmpty() || living.stream().anyMatch(member -> member.journey.leg != journey.leg || member.journey.obstructed)) return;
        int guards = (int) living.stream().filter(member -> member.purpose == SettlementFoodRequest.Purpose.GUARD).count();
        long before = journey.encounterChecks;
        var eligible = new ArrayList<SettlementEncounterDefinition>();
        for (var definition : SettlementEncounterResources.get(level.getServer())) {
            String key = definition.id().toString();
            if (now < journey.encounterClocks.getOrDefault(key, 0L)
                    || journey.encounterDistance < journey.encounterClocks.getOrDefault(key + "/distance", (long) definition.distance())) continue;
            journey.encounterClocks.put(key, now + definition.interval());
            journey.encounterClocks.put(key + "/distance", journey.encounterDistance + definition.distance());
            journey.encounterChecks++;
            if (living.size() < definition.minimumParticipants() || guards > definition.maximumGuards()
                    || definition.route().equals("default") && journey.customRoute != null
                    || definition.route().equals("custom") && journey.customRoute == null
                    || !definition.leg().equals("any") && !definition.leg().equals(journey.leg.name().toLowerCase(java.util.Locale.ROOT))
                    || (1 - journey.timeFactor()) * 2 < definition.minimumDevelopment()
                    || (1 - journey.timeFactor()) * 2 > definition.maximumDevelopment()
                    || !matches(level, journey, definition.conditions())) continue;
            double risk = definition.behavior().hostile() ? definition.risk(journey.timeFactor(), guards) : 1;
            for (var modifier : definition.modifiers()) if (matches(level, journey, modifier.conditions())) risk *= modifier.factor();
            var random = new java.util.Random(journey.id.getMostSignificantBits() ^ journey.id.getLeastSignificantBits()
                    ^ journey.encounterChecks * 0x9e3779b97f4a7c15L ^ definition.id().hashCode());
            if (random.nextDouble() < Math.min(1, definition.chance() * risk)) eligible.add(definition);
        }
        if (!eligible.isEmpty()) {
            double total = eligible.stream().mapToDouble(SettlementEncounterDefinition::weight).sum();
            double roll = new java.util.Random(journey.id.getLeastSignificantBits() ^ journey.encounterChecks).nextDouble() * total;
            var chosen = eligible.getLast();
            for (var definition : eligible) { roll -= definition.weight(); if (roll < 0) { chosen = definition; break; } }
            start(level, request, chosen, living, now);
        }
        if (journey.encounterChecks != before) SettlementCourierService.changed(level, request);
    }
    static boolean matches(ServerLevel level, SettlementCourierJourney journey, List<DialogueCondition> conditions) {
        var context = new ConditionContext(null, journey.resident, null, null, null, Set.of(), (condition, trigger) -> {
            boolean matched;
            if (condition instanceof DialogueCondition.Dimension dimension) matched = dimension.dimensions().contains(journey.dimension);
            else if (condition instanceof DialogueCondition.Biome biome) {
                var position = journey.position;
                var holder = level.hasChunkAt(position) ? level.getBiome(position)
                        : level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(position.getX()),
                                net.minecraft.core.QuartPos.fromBlock(position.getY()), net.minecraft.core.QuartPos.fromBlock(position.getZ()),
                                level.getChunkSource().randomState().sampler());
                matched = holder.unwrapKey().map(key -> biome.biomes().contains(key.location())).orElse(false)
                        || biome.tags().stream().anyMatch(id -> holder.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME, id)));
            } else if (condition instanceof DialogueCondition.Time time) {
                long day = Math.floorMod(level.getDayTime(), 24000);
                matched = time.times().contains(day < 6000 ? DialogueContext.TimeOfDay.MORNING : day < 12000
                        ? DialogueContext.TimeOfDay.AFTERNOON : day < 14000 ? DialogueContext.TimeOfDay.EVENING : DialogueContext.TimeOfDay.NIGHT);
            } else if (condition instanceof DialogueCondition.Weather weather) matched = weather.states().contains(level.isThundering()
                    ? DialogueContext.WeatherState.THUNDER : level.isRaining() ? DialogueContext.WeatherState.RAIN : DialogueContext.WeatherState.CLEAR);
            else if (condition instanceof DialogueCondition.VillagerEquipment equipment) {
                var villager = context(level, journey);
                matched = villager != null && equipment.condition().matches(villager);
            } else return DialogueCondition.ConditionOutcome.UNKNOWN;
            return matched ? DialogueCondition.ConditionOutcome.MATCHED : DialogueCondition.ConditionOutcome.UNMET;
        });
        return ConditionEvaluator.all(context, null, conditions) == DialogueCondition.ConditionOutcome.MATCHED;
    }
    static boolean start(ServerLevel level, SettlementFoodRequest request, SettlementEncounterDefinition definition,
                         List<SettlementFoodRequest> members, long now) {
        var state = new SettlementEncounterState(UUID.randomUUID(), definition, request.journey.position, now);
        for (var member : members) state.participants.add(member.id());
        for (var attacker : definition.attackers()) for (int index = 0; index < attacker.count(); index++) {
            var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(attacker.entity());
            if (!(type.create(level) instanceof Mob mob)) {
                com.mojang.logging.LogUtils.getLogger().error("Journey encounter {} attacker {} is not a mob", definition.id(), attacker.entity());
                return false;
            }
            mob.moveTo(Vec3.atBottomCenterOf(state.center));
            var health = mob.getAttribute(Attributes.MAX_HEALTH); if (health != null) health.setBaseValue(attacker.health());
            var damage = mob.getAttribute(Attributes.ATTACK_DAMAGE); if (damage != null) damage.setBaseValue(attacker.damage());
            var armor = mob.getAttribute(Attributes.ARMOR); if (armor != null) armor.setBaseValue(attacker.armor());
            mob.setHealth((float) attacker.health()); mob.setPersistenceRequired();
            for (var entry : attacker.equipment().entrySet()) {
                var parsed = net.minecraft.world.item.ItemStack.STRICT_CODEC.parse(level.registryAccess()
                        .createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), entry.getValue());
                if (parsed.error().isPresent()) {
                    com.mojang.logging.LogUtils.getLogger().error("Journey encounter {} equipment: {}", definition.id(), parsed.error().get().message());
                    return false;
                }
                mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.byName(entry.getKey()), parsed.result().orElseThrow());
            }
            mob.getPersistentData().putUUID(OWNER, request.id()); mob.getPersistentData().putLong(GENERATION, state.generation);
            mob.getPersistentData().putDouble("EncounterDamage", attacker.damage());
            var snapshot = new CompoundTag(); mob.save(snapshot); state.attackers.add(snapshot);
        }
        request.journey.encounter = state; request.journey.encounterCooldown = now + 200;
        request.journey.encounterClocks.put(definition.id().toString(), now + definition.cooldown());
        for (var member : members) {
            member.journey.lastTick = now;
            SettlementCourierService.block(level, member, definition.behavior().hostile() ? "encounter_active" : "encounter_resting");
        }
        report(level, request, net.minecraft.network.chat.Component.translatable(definition.status()));
        SettlementCourierService.changed(level, request); return true;
    }
    static Villager context(ServerLevel level, SettlementCourierJourney journey) {
        var live = level.getEntity(journey.resident);
        if (live instanceof Villager villager) return villager;
        var villager = EntityType.VILLAGER.create(level);
        if (villager != null) {
            villager.load(journey.residentContext.copy()); villager.moveTo(Vec3.atBottomCenterOf(journey.position));
            equipment(villager);
        }
        return villager;
    }
    static void hold(ServerLevel level, SettlementFoodRequest request, Villager live, long now) {
        var journey = request.journey;
        if (live == null) {
            if (journey.presence == SettlementCourierJourney.Presence.PHYSICAL) {
                SettlementCourierService.block(level, request, "awaiting_native_courier"); return;
            }
            if (SettlementCourierService.nearby(level, journey.position, 80)) SettlementCourierService.materialize(level, request, now);
            return;
        }
        if (!SettlementCourierService.danger(live)) live.getNavigation().stop();
        SettlementCourierService.capture(journey, live);
        if (!SettlementCourierService.nearby(level, live.blockPosition(), 96)) {
            var state = active(level, request);
            if (state != null && (state.visible || state.stage == SettlementEncounterState.Stage.RETIRING)) return;
            clearTarget(live, state);
            journey.obstructed = false;
            if (!VillagerDownedService.isDowned(live)) SettlementCourierService.beginRetirement(level, request, live, now);
            else unloading(live, request);
        }
    }
    static void clearTarget(Villager villager, SettlementEncounterState state) {
        var hurtBy = villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HURT_BY_ENTITY).orElse(null);
        if (hurtBy != null && hurtBy.getPersistentData().hasUUID(OWNER)) {
            villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HURT_BY_ENTITY);
            villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HURT_BY);
        }
        if (villager.getLastHurtByMob() != null && villager.getLastHurtByMob().getPersistentData().hasUUID(OWNER))
            villager.setLastHurtByMob(null);
        var target = villager.getTarget();
        if (target != null && target.getPersistentData().hasUUID(OWNER)) {
            villager.setTarget(null);
            villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
            villager.setLastHurtByMob(null);
        }
    }
    static void unloading(Villager villager, SettlementFoodRequest request) {
        var journey = request.journey;
        clearTarget(villager, active((ServerLevel) villager.level(), request));
        SettlementCourierService.capture(journey, villager); journey.position = villager.blockPosition().immutable();
        journey.incarnation++; journey.obstructed = false; journey.presence = SettlementCourierJourney.Presence.RETIRING;
        SettlementCourierService.changed((ServerLevel) villager.level(), request);
    }
    static void retiring(ServerLevel level, SettlementFoodRequest leader) {
        var state = leader.journey.encounter;
        if (state == null || state.complete() || state.stage == SettlementEncounterState.Stage.RETIRING) return;
        captureAttackers(level, state); freeze(level, state);
        state.generation++; state.stage = SettlementEncounterState.Stage.RETIRING;
        SettlementCourierService.changed(level, leader); state.checkpointRevision = leader.journey.revision;
    }
    private static boolean materializeAttackers(ServerLevel level, SettlementFoodRequest request, List<SettlementFoodRequest> members) {
        var state = request.journey.encounter;
        var targets = members.stream().map(member -> level.getEntity(member.journey.resident))
                .filter(Villager.class::isInstance).map(Villager.class::cast).filter(Villager::isAlive)
                .filter(villager -> !VillagerDownedService.isDowned(villager)).toList();
        if (targets.isEmpty()) return state.attackers.isEmpty() || members.stream().allMatch(member -> !member.journey.ownsResident() || member.journey.downed);
        boolean represented = true;
        for (int index = 0; index < state.attackers.size(); index++) {
            var snapshot = state.attackers.get(index); if (snapshot.getFloat("Health") <= 0) continue;
            var found = SettlementCourierService.existing(level.getServer(), snapshot.getUUID("UUID"));
            Mob mob;
            if (found instanceof Mob actor) mob = actor;
            else {
                if (found != null) { represented = false; continue; }
                var entity = EntityType.loadEntityRecursive(snapshot.copy(), level, value -> value);
                if (!(entity instanceof Mob actor)) { represented = false; continue; }
                mob = actor;
                var position = SettlementEncounterRecovery.safePosition(level, state.center, mob, index, 8);
                if (position == null) { represented = false; continue; }
                mob.moveTo(Vec3.atBottomCenterOf(position));
                mob.getPersistentData().putLong(GENERATION, state.generation);
                if (!level.addFreshEntity(mob)) { represented = false; continue; }
            }
            var target = targets.get(index % targets.size());
            if (mob.getTarget() == null || !mob.getTarget().isAlive()) mob.setTarget(target);
            if (mob instanceof net.minecraft.world.entity.NeutralMob neutral) {
                neutral.setPersistentAngerTarget(target.getUUID()); neutral.setRemainingPersistentAngerTime(400);
            }
        }
        return represented;
    }
    static void captureAttackers(ServerLevel level, SettlementEncounterState state) {
        for (int index = 0; index < state.attackers.size(); index++) {
            var old = state.attackers.get(index);
            var live = level.getEntity(old.getUUID("UUID"));
            if (live instanceof LivingEntity actor && actor.getPersistentData().getLong(GENERATION) == state.generation) {
                var snapshot = new CompoundTag(); actor.saveWithoutId(snapshot); snapshot.putString("id", old.getString("id"));
                state.attackers.set(index, snapshot);
            }
        }
    }
    static void freeze(ServerLevel level, SettlementEncounterState state) {
        for (var snapshot : state.attackers) {
            var entity = level.getEntity(snapshot.getUUID("UUID"));
            if (entity instanceof Mob mob && mob.getPersistentData().hasUUID(OWNER)) {
                mob.setInvulnerable(true); mob.setNoAi(true); mob.setTarget(null); mob.getNavigation().stop();
            }
        }
    }
    static void cleanup(ServerLevel level, SettlementEncounterState state) {
        for (var snapshot : state.attackers) {
            var live = level.getEntity(snapshot.getUUID("UUID"));
            if (live != null && live.getPersistentData().hasUUID(OWNER)) live.discard();
        }
        var projectiles = new ArrayList<Entity>();
        for (var entity : level.getAllEntities()) if (entity instanceof net.minecraft.world.entity.projectile.Projectile
                && entity.getPersistentData().hasUUID(OWNER) && state.participants.contains(entity.getPersistentData().getUUID(OWNER))
                && entity.getPersistentData().getLong(GENERATION) < state.generation) projectiles.add(entity);
        projectiles.forEach(Entity::discard);
    }
    static void round(ServerLevel level, SettlementFoodRequest leader, List<SettlementFoodRequest> members) {
        var state = leader.journey.encounter;
        var villagers = members.stream().filter(member -> member.journey.ownsResident()).map(member -> context(level, member.journey))
                .filter(java.util.Objects::nonNull).filter(Villager::isAlive).filter(villager -> !VillagerDownedService.isDowned(villager)).toList();
        var attackers = new ArrayList<Mob>();
        for (var snapshot : state.attackers) if (snapshot.getFloat("Health") > 0) {
            var actor = EntityType.loadEntityRecursive(snapshot.copy(), level, value -> value);
            if (actor instanceof Mob mob) { equipment(mob); attackers.add(mob); }
        }
        if (villagers.isEmpty() || attackers.isEmpty()) return;
        for (var villager : villagers) {
            var target = attackers.stream().filter(Mob::isAlive).findFirst().orElse(null); if (target == null) break;
            backgroundHit(level, target, villager);
        }
        int offset = (int) (state.elapsed / state.definition.roundTicks());
        for (var attacker : attackers) if (attacker.isAlive()) {
            var candidates = villagers.stream().filter(Villager::isAlive).filter(villager -> !VillagerDownedService.isDowned(villager)).toList();
            if (candidates.isEmpty()) break;
            var target = candidates.get(Math.floorMod(offset++, candidates.size()));
            backgroundHit(level, target, attacker);
        }
        for (var villager : villagers) {
            var member = SettlementSavedData.get(level).aid().courier(villager.getUUID());
            if (member != null) { SettlementCourierService.capture(member.journey, villager); SettlementCourierService.changed(level, member); }
        }
        for (var attacker : attackers) for (int index = 0; index < state.attackers.size(); index++)
            if (state.attackers.get(index).getUUID("UUID").equals(attacker.getUUID())) {
                var snapshot = new CompoundTag(); attacker.saveWithoutId(snapshot);
                snapshot.putString("id", state.attackers.get(index).getString("id")); state.attackers.set(index, snapshot);
            }
    }
    static void backgroundHit(ServerLevel level, LivingEntity target, LivingEntity attacker) {
        try (var capture = new SettlementDeathCapture(target)) {
            target.hurt(level.damageSources().mobAttack(attacker), damage(attacker));
        }
    }
    static void equipment(LivingEntity entity) {
        ((com.jvn.villagerretaliation.mixin.LivingEntityCombatAccessor) entity).villagerretaliation$detectEquipmentUpdates();
    }
    static float damage(LivingEntity entity) {
        double base = entity.getAttribute(Attributes.ATTACK_DAMAGE) == null ? 1 : entity.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (entity.getAttribute(Attributes.ATTACK_DAMAGE) == null && entity.getPersistentData().contains("EncounterDamage"))
            base = entity.getPersistentData().getDouble("EncounterDamage");
        double[] weapon = {0};
        entity.getMainHandItem().forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE) && modifier.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE
                    && (entity.getAttribute(attribute) == null || !entity.getAttribute(attribute).hasModifier(modifier.id())))
                weapon[0] += modifier.amount();
        });
        return (float) Math.clamp(base + weapon[0], .5, 128);
    }
    static void resolve(ServerLevel level, SettlementFoodRequest request, List<SettlementFoodRequest> members, long now) {
        var state = request.journey.encounter;
        if (state == null || state.stage != SettlementEncounterState.Stage.ACTIVE) return;
        freeze(level, state);
        state.stage = SettlementEncounterState.Stage.DELAYED; state.elapsed = 0; state.generation++;
        state.visible = false;
        for (var member : members) {
            if (member.journey.ownsResident()) {
                if (state.definition.behavior().hostile()) SettlementEncounterRecovery.loseCargo(level, member, state, false);
                for (var action : state.definition.outcomes()) SettlementEncounterActions.get(action).apply(level, member, action.deepCopy());
                var live = level.getEntity(member.journey.resident);
                if (live instanceof Villager villager) {
                    clearTarget(villager, state); SettlementCourierService.capture(member.journey, villager);
                    if (member.journey.incarnation < Long.MAX_VALUE - 1) {
                        member.journey.incarnation++; member.journey.presence = SettlementCourierJourney.Presence.RETIRING;
                    }
                }
                member.journey.obstructed = false; member.journey.lastTick = now; member.journey.lastProgress = now;
                SettlementCourierService.block(level, member, "encounter_delayed");
            }
        }
        if (!request.journey.ownsResident()) for (var member : members) {
            if (!member.journey.ownsResident()) continue;
            member.recalled = true;
            if (member.journey.leg.ordinal() < SettlementCourierJourney.Leg.HOMEBOUND.ordinal()) {
                member.journey.leg = SettlementCourierJourney.Leg.HOMEBOUND; member.journey.step = 0; member.journey.detour = null;
                if (!SettlementCourierService.retry(level, member)) {
                    member.journey.obstructed = true; SettlementCourierService.block(level, member, "caravan_return_blocked");
                }
            }
            member.state = SettlementFoodRequest.State.RETURNING;
            SettlementCourierService.changed(level, member);
        }
        SettlementCourierService.changed(level, request); state.checkpointRevision = request.journey.revision;
        report(level, request, net.minecraft.network.chat.Component.translatable("villagerretaliation.encounter.resolved"));
    }
    static void report(ServerLevel level, SettlementFoodRequest request, net.minecraft.network.chat.Component message) {
        var home = SettlementSavedData.get(level).get(request.journey.home).orElse(null);
        if (home == null) return;
        for (var player : level.players()) if (home.permits(player.getUUID(), SettlementPermission.VIEW)) player.sendSystemMessage(message);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void join(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() != null && projectile.getOwner().getPersistentData().hasUUID(OWNER)) {
            projectile.getPersistentData().putUUID(OWNER, projectile.getOwner().getPersistentData().getUUID(OWNER));
            projectile.getPersistentData().putLong(GENERATION, projectile.getOwner().getPersistentData().getLong(GENERATION));
        }
        if (!(event.getLevel() instanceof ServerLevel level) || !event.getEntity().getPersistentData().hasUUID(OWNER)) return;
        var actor = event.getEntity();
        var leader = SettlementSavedData.get(level).aid().request(actor.getPersistentData().getUUID(OWNER));
        var state = leader == null || leader.journey == null ? null : leader.journey.encounter;
        if (state == null || state.stage != SettlementEncounterState.Stage.ACTIVE || !state.visible
                || actor.getPersistentData().getLong(GENERATION) != state.generation
                || !(actor instanceof net.minecraft.world.entity.projectile.Projectile)
                && state.attackers.stream().noneMatch(snapshot -> snapshot.getUUID("UUID").equals(actor.getUUID()) && snapshot.getFloat("Health") > 0)) {
            event.setCanceled(true); actor.discard();
        }
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !event.getEntity().getPersistentData().hasUUID(OWNER)
                || event.getEntity().getRemovalReason() != Entity.RemovalReason.UNLOADED_TO_CHUNK) return;
        var leader = SettlementSavedData.get(level).aid().request(event.getEntity().getPersistentData().getUUID(OWNER));
        if (leader != null && leader.journey != null && event.getEntity() instanceof LivingEntity actor) {
            var state = leader.journey.encounter;
            if (state != null && actor.getPersistentData().getLong(GENERATION) == state.generation) {
                for (int index = 0; index < state.attackers.size(); index++) if (state.attackers.get(index).getUUID("UUID").equals(actor.getUUID())) {
                    var snapshot = actor.saveWithoutId(new CompoundTag()); snapshot.putString("id", state.attackers.get(index).getString("id"));
                    state.attackers.set(index, snapshot);
                }
                retiring(level, leader);
            }
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void died(LivingDeathEvent event) {
        var actor = event.getEntity();
        if (!(actor.level() instanceof ServerLevel level) || !actor.getPersistentData().hasUUID(OWNER)) return;
        var leader = SettlementSavedData.get(level).aid().request(actor.getPersistentData().getUUID(OWNER));
        var state = leader == null || leader.journey == null ? null : leader.journey.encounter;
        if (state == null || actor.getPersistentData().getLong(GENERATION) != state.generation) return;
        for (var snapshot : state.attackers) if (snapshot.getUUID("UUID").equals(actor.getUUID())) snapshot.putFloat("Health", 0);
        SettlementCourierService.changed(level, leader);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void drops(LivingDropsEvent event) {
        if (event.getEntity().getPersistentData().hasUUID(OWNER)) { event.getDrops().clear(); event.setCanceled(true); }
    }
    @SubscribeEvent public static void experience(LivingExperienceDropEvent event) {
        if (event.getEntity().getPersistentData().hasUUID(OWNER)) event.setDroppedExperience(0);
    }
}
