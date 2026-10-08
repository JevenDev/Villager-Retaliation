package com.jvn.villagerretaliation.scene.encounter;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.util.WorldChunkCheckpoint;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class EncounterActorBirths {
    private static final String KEY = "villagerretaliation:encounter_actor_births";
    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, VillagerRetaliation.MOD_ID);
    static final Supplier<AttachmentType<Ledger>> BIRTHS = TYPES.register("encounter_actor_births",
            () -> AttachmentType.builder(Ledger::new).serialize(CompoundTag.CODEC.xmap(Ledger::load, Ledger::save)).build());

    private EncounterActorBirths() { }

    public static void register(IEventBus bus) { TYPES.register(bus); }

    static boolean born(ServerLevel level, EncounterActorSpawn receipt) throws IOException {
        var chunk = loaded(level, receipt);
        var ledger = chunk.getExistingDataOrNull(BIRTHS.get());
        if (ledger == null) throw new IOException("The native actor birth proof is missing. Restore its original chunk data.");
        var proof = ledger.match(receipt);
        if (proof == null) throw new IOException("The native actor birth reservation is missing. Restore its original chunk data.");
        return proof.born();
    }

    static void reserve(ServerLevel level, EncounterActorSpawn receipt) throws IOException {
        var chunk = loaded(level, receipt);
        var ledger = chunk.getData(BIRTHS);
        var proof = ledger.match(receipt);
        if (proof == null) {
            if (receipt.state() != EncounterActorSpawn.State.READY)
                throw new IOException("An existing actor reservation lost its native proof. Restore the original chunk data.");
            if (ledger.proofs.size() >= 65536) throw new IOException("Native actor birth capacity exceeded for this chunk.");
            ledger.proofs.put(receipt.operation(), new Proof(receipt.encounter(), receipt.entity(), receipt.signature(), false));
            chunk.setUnsaved(true);
        }
        persist(level, chunk, receipt, ledger);
    }

    static void commit(ServerLevel level, EncounterActorSpawn receipt) throws IOException {
        var chunk = loaded(level, receipt);
        var ledger = chunk.getExistingDataOrNull(BIRTHS.get());
        if (ledger == null || ledger.match(receipt) == null) throw new IOException("The native actor birth reservation is unavailable.");
        ledger.proofs.put(receipt.operation(), new Proof(receipt.encounter(), receipt.entity(), receipt.signature(), true));
        chunk.setUnsaved(true);
        persist(level, chunk, receipt, ledger);
    }

    private static LevelChunk loaded(ServerLevel level, EncounterActorSpawn receipt) throws IOException {
        if (!level.getServer().isSameThread() || !level.dimension().location().equals(receipt.dimension()))
            throw new IOException("Actor birth checkpoints require their original dimension on the server thread.");
        var chunk = level.getChunkSource().getChunkNow(receipt.anchor().getX() >> 4, receipt.anchor().getZ() >> 4);
        if (chunk == null) throw new IOException("Load the original actor reservation chunk before resuming this encounter.");
        return chunk;
    }

    private static void persist(ServerLevel level, LevelChunk chunk, EncounterActorSpawn receipt, Ledger ledger) throws IOException {
        var expected = ledger.save();
        WorldChunkCheckpoint.persist(level, receipt.anchor(), snapshot -> expected.equals(ledger.save())
                && expected.equals(snapshot.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).get(KEY)));
    }

    static final class Ledger {
        private final Map<UUID, Proof> proofs = new LinkedHashMap<>();
        private CompoundTag preserved;
        private String diagnostic = "";

        Proof match(EncounterActorSpawn receipt) throws IOException {
            if (!diagnostic.isBlank()) throw new IOException(diagnostic);
            var proof = proofs.get(receipt.operation());
            if (proof != null && (!proof.encounter().equals(receipt.encounter()) || !proof.entity().equals(receipt.entity())
                    || !proof.signature().equals(receipt.signature())))
                throw new IOException("The actor birth proof belongs to different saved entities. Restore matching scene and chunk data.");
            return proof;
        }

        CompoundTag save() {
            if (preserved != null) return preserved.copy();
            var tag = new CompoundTag();
            tag.putInt("Version", 1);
            var records = new ListTag();
            proofs.forEach((operation, proof) -> {
                var value = new CompoundTag();
                value.putUUID("Operation", operation);
                value.putUUID("Encounter", proof.encounter());
                value.putUUID("Entity", proof.entity());
                value.putUUID("Signature", proof.signature());
                value.putBoolean("Born", proof.born());
                records.add(value);
            });
            tag.put("Records", records);
            return tag;
        }

        static Ledger load(CompoundTag tag) {
            var ledger = new Ledger();
            try {
                EncounterActorSpawn.require(tag.getAllKeys().equals(java.util.Set.of("Version", "Records"))
                        && tag.contains("Version", Tag.TAG_INT) && tag.getInt("Version") == 1 && tag.get("Records") instanceof ListTag,
                        "unsupported actor birth proof");
                var list = (ListTag) tag.get("Records");
                EncounterActorSpawn.require(list.size() <= 65536 && (list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND), "invalid actor birth proof list");
                for (var raw : list) {
                    var value = (CompoundTag) raw;
                    EncounterActorSpawn.require(value.getAllKeys().equals(java.util.Set.of("Operation", "Encounter", "Entity", "Signature", "Born"))
                            && value.hasUUID("Operation") && value.hasUUID("Encounter") && value.hasUUID("Entity") && value.hasUUID("Signature")
                            && value.contains("Born", Tag.TAG_BYTE) && (value.getByte("Born") == 0 || value.getByte("Born") == 1), "invalid actor birth proof");
                    var proof = new Proof(value.getUUID("Encounter"), value.getUUID("Entity"), value.getUUID("Signature"), value.getBoolean("Born"));
                    EncounterActorSpawn.require(ledger.proofs.putIfAbsent(value.getUUID("Operation"), proof) == null, "duplicate actor birth proof");
                }
            } catch (IllegalArgumentException | IllegalStateException exception) {
                ledger.proofs.clear();
                ledger.preserved = tag.copy();
                ledger.diagnostic = "Native encounter actor birth proofs are unreadable. Restore compatible chunk data.";
            }
            return ledger;
        }
    }

    private record Proof(UUID encounter, UUID entity, UUID signature, boolean born) { }
}
