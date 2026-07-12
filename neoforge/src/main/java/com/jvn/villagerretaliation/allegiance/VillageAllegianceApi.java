package com.jvn.villagerretaliation.allegiance;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/** Server-side integration API for durable village allegiance data. */
public final class VillageAllegianceApi {
    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

    private VillageAllegianceApi() {
    }

    /** Registers an external provider. Providers must return stable data and avoid world scans. */
    public static void registerProvider(Provider provider) {
        if (provider != null) {
            PROVIDERS.add(provider);
        }
    }

    /** Returns serialized entity data first, then a registered provider result. */
    public static Optional<VillageAllegianceData> get(Entity entity) {
        Optional<VillageAllegianceData> stored = VillageAllegianceEntityData.read(entity);
        return stored.isPresent() ? stored : providerData(entity);
    }

    /** Assigns an explicit allegiance payload without consulting physical village state. */
    public static void assign(Entity entity, VillageAllegianceData data) {
        VillageAllegianceEntityData.write(entity, data);
    }

    /** Assigns a known primary allegiance and registers its durable record. */
    public static VillageAllegianceData assignKnown(
            ServerLevel level,
            Entity entity,
            VillageAllegianceId id,
            AllegianceAssignmentSource source) {
        VillageAllegianceRegistrySavedData.get(level).ensureRecord(
                id, level.getGameTime(), level.dimension().location(), entity.blockPosition());
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceId canonical = registry.canonical(id).orElse(id);
        VillageAllegianceData data = VillageAllegianceData.known(
                canonical, source, AllegianceConfidence.AUTHORITATIVE, level.getGameTime(),
                level.dimension().location(), entity.blockPosition(), List.of());
        assign(entity, data);
        registry.removeResidentEverywhere(entity.getUUID());
        if (entity instanceof Villager villager) {
            registry.addOrUpdateResident(canonical, villager.getUUID(), !villager.isBaby(), level.getGameTime());
        }
        return data;
    }

    public static VillageAllegianceData assignUnaffiliated(
            ServerLevel level,
            Entity entity,
            AllegianceAssignmentSource source) {
        VillageAllegianceRegistrySavedData.get(level).removeResidentEverywhere(entity.getUUID());
        VillageAllegianceData data = VillageAllegianceData.unaffiliated(
                source, level.getGameTime(), level.dimension().location(), entity.blockPosition());
        assign(entity, data);
        return data;
    }

    /** Copies allegiance between replacement entities while retaining raw diagnostic IDs. */
    public static void copy(Entity source, Entity outcome) {
        VillageAllegianceEntityData.copy(source, outcome, AllegianceAssignmentSource.EXPLICIT_API);
    }

    /** Resolves an entity's primary ID through the current server-global alias graph. */
    public static Optional<VillageAllegianceId> canonicalPrimary(ServerLevel level, Entity entity) {
        return get(entity)
                .filter(VillageAllegianceData::isKnown)
                .flatMap(data -> VillageAllegianceRegistrySavedData.get(level).canonical(data.primary()));
    }

    public static Optional<VillageAllegianceRegistrySavedData.AllegianceRecord> village(
            ServerLevel level,
            Entity entity) {
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        return canonicalPrimary(level, entity).flatMap(registry::canonicalRecord);
    }

    public static Optional<VillageAllegianceRegistrySavedData.AllegianceRecord> villageAt(
            ServerLevel level,
            net.minecraft.core.BlockPos position) {
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        return registry.resolveAt(level, position).flatMap(registry::canonicalRecord);
    }

    public static String displayName(ServerLevel level, Entity entity) {
        return village(level, entity).map(VillageAllegianceRegistrySavedData.AllegianceRecord::displayName)
                .orElse("Wanderer");
    }

    static Optional<VillageAllegianceData> providerData(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        for (Provider provider : PROVIDERS) {
            Optional<VillageAllegianceData> data = provider.allegiance(entity);
            if (data != null && data.isPresent()) {
                return data;
            }
        }
        return Optional.empty();
    }

    public interface Provider {
        Optional<VillageAllegianceData> allegiance(Entity entity);
    }
}
