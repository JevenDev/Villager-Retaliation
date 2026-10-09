package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignCharacterDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.mixin.ServerLevelEntityManagerAccessor;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

public final class CampaignOwnedSpawns {
    private static final String OWNER_KEY = "VillagerRetaliationCampaignOwner";
    private static final Set<UUID> ROLLING_BACK = new HashSet<>();

    private CampaignOwnedSpawns() { }

    static CampaignCharacterService.Result resolve(MinecraftServer server, CampaignContentCatalog.Entry content,
            CampaignCharacterDefinition character, CampaignCharacterSavedData characters) {
        var definition = content.definition();
        var key = new CampaignCharacterBinding.Key(definition.id(), character.id());
        var data = CampaignSpawnSavedData.get(server.overworld());
        if (!data.diagnostic().isBlank()) return unavailable(data.diagnostic());
        var receipt = data.receipts().get(key);
        try {
            if (receipt == null) {
                if (characters.bindings().containsKey(key)) return unavailable("Campaign-owned character has no spawn receipt. Restore its original spawn save.");
                receipt = prepare(server, content, character, data);
                if (receipt == null) return unavailable("Campaign spawn needs a safe, loaded position near its bound location. Clear the obstruction or load that area and retry.");
            }
            var instance = CampaignSavedData.get(server.overworld()).instances().get(definition.id());
            var expected = receipt.binding();
            if (instance == null || !expected.instance().equals(instance.instanceId()) || expected.definitionRevision() != definition.revision()
                    || !expected.fingerprint().equals(definition.fingerprint())) return unavailable("Campaign spawn receipt is incompatible with its campaign instance or content.");
            for (var level : server.getAllLevels()) {
                var existing = level.getEntity(expected.entityId());
                if (existing != null && (!(existing instanceof Villager villager) || !owns(receipt, villager))) {
                    return unavailable("An entity with the reserved UUID does not belong to this campaign spawn.");
                }
            }
            var binding = characters.bind(expected);
            if (!binding.withLoss(false).equals(expected)) return unavailable("Campaign character binding conflicts with its spawn receipt. Restore the matching saves.");
            if (receipt.committed() || binding.lost()) return observe(server, characters, receipt);
            return materialize(server, characters, data, receipt);
        } catch (IOException | IllegalArgumentException failure) {
            return unavailable("Campaign spawn could not be saved: " + failure.getMessage());
        }
    }

    private static CampaignSpawnReceipt prepare(MinecraftServer server, CampaignContentCatalog.Entry content,
            CampaignCharacterDefinition character, CampaignSpawnSavedData data) throws IOException {
        var definition = content.definition();
        if (character.profession() != null && !BuiltInRegistries.VILLAGER_PROFESSION.containsKey(character.profession())) {
            throw new IOException("Campaign character profession is unavailable: " + character.profession());
        }
        var location = CampaignLocationService.resolve(server, definition.id(), character.location());
        if (!location.available()) throw new IOException(location.diagnostic());
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, location.binding().dimension()));
        var entity = EntityType.VILLAGER.create(level);
        if (entity == null) throw new IOException("Villager entity type could not create the campaign character.");
        BlockPos position = null;
        for (var candidate : BlockPos.withinManhattan(location.binding().position(), character.searchRadius(), 3, character.searchRadius())) {
            entity.moveTo(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5, 0.0F, 0.0F);
            if (safe(level, entity)) { position = candidate.immutable(); break; }
        }
        if (position == null) return null;
        var instance = CampaignSavedData.get(level).open(content, level.getGameTime());
        var operation = UUID.nameUUIDFromBytes((instance.instanceId() + "/spawn/" + character.id()).getBytes(StandardCharsets.UTF_8));
        var entityId = UUID.nameUUIDFromBytes((operation + "/entity").getBytes(StandardCharsets.UTF_8));
        entity.setUUID(entityId);
        entity.finalizeSpawn(level, level.getCurrentDifficultyAt(position), MobSpawnType.EVENT, null);
        entity.setAge(0);
        entity.setPersistenceRequired();
        if (character.profession() != null) entity.setVillagerData(entity.getVillagerData().setProfession(BuiltInRegistries.VILLAGER_PROFESSION.get(character.profession())));
        entity.setCustomName(Component.literal(content.localization().plainText("en_us", character.titleKey()).orElse(character.id())));
        var binding = new CampaignCharacterBinding(definition.id(), instance.instanceId(), definition.revision(), definition.fingerprint(),
                character.id(), character.source(), character.lossPolicy(), entityId, level.dimension().location(), position, level.getGameTime(), false);
        var owner = new CompoundTag();
        owner.putUUID("Instance", instance.instanceId()); owner.putUUID("Operation", operation);
        owner.putString("Campaign", definition.id().toString()); owner.putString("Role", character.id());
        entity.getPersistentData().put(OWNER_KEY, owner);
        var snapshot = new CompoundTag();
        if (!entity.save(snapshot)) throw new IOException("Campaign character could not be serialized before spawning.");
        return data.prepare(new CampaignSpawnReceipt(binding, operation, snapshot, -1L));
    }

    private static CampaignCharacterService.Result materialize(MinecraftServer server, CampaignCharacterSavedData characters,
            CampaignSpawnSavedData data, CampaignSpawnReceipt receipt) throws IOException {
        var binding = receipt.binding();
        var existing = CampaignCharacterService.observe(server, characters, binding);
        if (existing.entity() != null) {
            if (!owns(receipt, existing.entity())) return unavailable("An entity with the reserved UUID does not belong to this campaign spawn.");
            CampaignEntityCheckpoint.persist((ServerLevel) existing.entity().level(), existing.entity());
            data.commit(receipt.key(), receipt.operation(), server.overworld().getGameTime());
            return existing;
        }
        for (var dimension : server.getAllLevels()) {
            if (((ServerLevelEntityManagerAccessor) dimension).villagerretaliation$entityManager().isLoaded(binding.entityId())) {
                return unavailable("The reserved campaign character is loaded but unavailable. Its identity cannot be spawned again.");
            }
        }
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, binding.dimension()));
        var position = new ChunkPos(binding.position());
        if (level == null || level.getChunkSource().getChunkNow(position.x, position.z) == null || !level.areEntitiesLoaded(position.toLong())) {
            return unavailable("The prepared campaign spawn is waiting for its original chunk and entities to load.");
        }
        var loaded = EntityType.loadEntityRecursive(receipt.entity(), level, entity -> entity);
        if (!(loaded instanceof Villager villager) || !owns(receipt, villager)) return unavailable("The prepared campaign character snapshot is incompatible. Restore its spawn receipt.");
        if (!safe(level, villager)) return unavailable("Player edits or another entity obstruct the prepared campaign spawn. Clear that position and retry.");
        if (!level.addFreshEntity(villager)) return unavailable("The prepared campaign character could not enter the world. Its spawn receipt is retained.");
        try {
            if (villager.level() != level || !villager.blockPosition().equals(binding.position()) || !owns(receipt, villager)) {
                throw new IOException("Campaign character identity or position changed while it was being added.");
            }
            CampaignEntityCheckpoint.persist(level, villager);
            data.commit(receipt.key(), receipt.operation(), level.getGameTime());
            return new CampaignCharacterService.Result(characters.forEntity(binding.entityId()), villager, "");
        } catch (IOException | IllegalArgumentException failure) {
            ROLLING_BACK.add(binding.entityId());
            try { villager.discard(); }
            finally { ROLLING_BACK.remove(binding.entityId()); }
            throw failure;
        }
    }

    private static CampaignCharacterService.Result observe(MinecraftServer server, CampaignCharacterSavedData characters, CampaignSpawnReceipt receipt) {
        var result = CampaignCharacterService.observe(server, characters, characters.forEntity(receipt.binding().entityId()));
        if (result.entity() != null && !owns(receipt, result.entity())) return unavailable("Campaign character ownership is missing or conflicts with its spawn receipt.");
        return result;
    }

    static boolean rollingBack(UUID entity) { return ROLLING_BACK.contains(entity); }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() || !(event.getEntity() instanceof Villager villager) || !(event.getLevel() instanceof ServerLevel level)) return;
        var data = CampaignSpawnSavedData.get(level);
        var receipt = data.forEntity(villager.getUUID());
        if (receipt == null || receipt.committed() || !owns(receipt, villager)) return;
        try {
            var bound = CampaignCharacterSavedData.get(level).bind(receipt.binding());
            if (!bound.withLoss(false).equals(receipt.binding())) throw new IOException("Campaign spawn ownership conflicts with its character binding.");
            data.commit(receipt.key(), receipt.operation(), level.getGameTime());
        } catch (IOException | IllegalArgumentException failure) {
            event.setCanceled(true);
            LogUtils.getLogger().error("Could not finish the recovered campaign spawn {}. Its prepared receipt is retained.", receipt.operation(), failure);
        }
    }

    static boolean hasOwner(Villager entity) { return entity.getPersistentData().contains(OWNER_KEY); }

    static boolean owns(CampaignSpawnReceipt receipt, Villager entity) {
        if (!entity.getUUID().equals(receipt.binding().entityId())) return false;
        var owner = entity.getPersistentData().getCompound(OWNER_KEY);
        return owner.hasUUID("Instance") && receipt.binding().instance().equals(owner.getUUID("Instance"))
                && owner.hasUUID("Operation") && receipt.operation().equals(owner.getUUID("Operation"))
                && receipt.binding().campaign().toString().equals(owner.getString("Campaign")) && receipt.binding().role().equals(owner.getString("Role"));
    }

    private static boolean safe(ServerLevel level, Villager entity) {
        var box = entity.getBoundingBox();
        var min = BlockPos.containing(box.minX - 1, box.minY - 1, box.minZ - 1);
        var max = BlockPos.containing(box.maxX + 1, box.maxY + 1, box.maxZ + 1);
        if (level.isOutsideBuildHeight(min) || level.isOutsideBuildHeight(max)
                || !level.getWorldBorder().isWithinBounds(min) || !level.getWorldBorder().isWithinBounds(max)) return false;
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null || !level.areEntitiesLoaded(ChunkPos.asLong(x, z))) return false;
            }
        }
        var feet = entity.blockPosition();
        return level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty() && level.noCollision(entity);
    }

    private static CampaignCharacterService.Result unavailable(String diagnostic) { return new CampaignCharacterService.Result(null, null, diagnostic); }
}
