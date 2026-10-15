package com.jvn.villagerretaliation.quest.tutorial;

import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** One ledger per learner and stable tutorial ID, stored in the overworld. */
public final class TutorialSavedData extends SavedData {
    private static final Gson GSON = new Gson();
    private static final String OWNER_COPIES="VillagerRetaliationTutorialEntitlements";
    private net.minecraft.server.MinecraftServer server;
    private final Map<String,Long> revisions=new LinkedHashMap<>();
    private final Map<String, TutorialRun> runs = new LinkedHashMap<>();
    private final Map<String, String> quarantined = new LinkedHashMap<>();

    public static TutorialSavedData get(ServerLevel level) {
        var data=level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(TutorialSavedData::new, TutorialSavedData::load), "villagerretaliation_job_tutorials");
        data.restoreOnlineOwnerCopies(level.getServer());return data;
    }
    /** Owner checkpoints share the player save with provision/payment receipts, preserving entitlement if world SavedData lags. */
    void restoreOnlineOwnerCopies(net.minecraft.server.MinecraftServer currentServer){
        this.server=currentServer;
        for(var player:currentServer.getPlayerList().getPlayers()){
            var copies=player.getPersistentData().getCompound(OWNER_COPIES);
            for(String tutorial:copies.getAllKeys()){
                String key=key(player.getUUID(),tutorial);var copy=copies.getCompound(tutorial);
                if(quarantined.containsKey(key)||copy.getLong("Revision")<=revisions.getOrDefault(key,0L))continue;
                try{
                    if(copy.getInt("Version")!=1)throw new IllegalArgumentException("Unknown owner checkpoint");
                    var saved=GSON.fromJson(copy.getString("Snapshot"),TutorialRun.Snapshot.class);
                    var run=TutorialRun.restore(saved);
                    if(!key.equals(key(run.binding().player(),run.snapshot().tutorial()))
                            ||runs.containsKey(key)&&!runs.get(key).trial().equals(run.trial()))throw new IllegalArgumentException("Entitlement identity conflict");
                    runs.put(key,run);revisions.put(key,copy.getLong("Revision"));super.setDirty();
                }catch(RuntimeException invalid){
                    runs.remove(key);quarantined.put(key,copy.getString("Snapshot"));super.setDirty();
                }
            }
        }
    }
    @Override public void setDirty(){
        super.setDirty();if(server==null)return;
        runs.forEach((key,run)->{
            var player=server.getPlayerList().getPlayer(run.binding().player());if(player==null)return;
            long revision=Math.addExact(revisions.getOrDefault(key,0L),1L);revisions.put(key,revision);
            var copies=player.getPersistentData().getCompound(OWNER_COPIES);var copy=new CompoundTag();
            copy.putInt("Version",1);copy.putLong("Revision",revision);copy.putString("Snapshot",GSON.toJson(run.snapshot()));
            copies.put(run.snapshot().tutorial(),copy);player.getPersistentData().put(OWNER_COPIES,copies);
        });
    }
    public void saveOwnerCheckpoints(){if(server!=null)server.getPlayerList().saveAll();}
    private static String key(UUID player, String tutorial) { return player + "/" + tutorial; }
    public Optional<TutorialRun> find(UUID player, String tutorial) {
        return Optional.ofNullable(runs.get(key(player, tutorial)));
    }
    public Optional<TutorialRun> trial(UUID trial) {
        return runs.values().stream().filter(run -> run.trial().equals(trial)).findFirst();
    }
    public java.util.List<TutorialRun> runs() { return java.util.List.copyOf(runs.values()); }
    public boolean reserve(TutorialRun run) {
        String key = key(run.binding().player(), run.snapshot().tutorial());
        if (quarantined.containsKey(key) || runs.putIfAbsent(key, run) != null) return false;
        setDirty(); return true;
    }

    public static TutorialSavedData load(CompoundTag root, HolderLookup.Provider registries) {
        var result = new TutorialSavedData();
        ListTag list = root.getList("Runs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var entry = list.getCompound(i);
            String key = entry.getString("Key"), json = entry.getString("Snapshot");
            if (root.getInt("Version") > 1 || result.quarantined.containsKey(key)) {
                result.runs.remove(key); result.quarantined.put(key, json); continue;
            }
            try {
                var snapshot = GSON.fromJson(json, TutorialRun.Snapshot.class);
                var run = TutorialRun.restore(snapshot);
                if (!key.equals(key(run.binding().player(), snapshot.tutorial())) || result.runs.containsKey(key))
                    throw new IllegalArgumentException("Tutorial key mismatch or duplicate");
                result.runs.put(key, run);result.revisions.put(key,Math.max(0,entry.getLong("Revision")));
            } catch (RuntimeException ex) {
                // Keep the original bytes and entitlement blocked instead of issuing a new kit/budget.
                result.runs.remove(key);
                result.quarantined.put(key, json);
            }
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        var list = new ListTag();
        runs.forEach((key, run) -> {var saved=entry(key,GSON.toJson(run.snapshot()));saved.putLong("Revision",revisions.getOrDefault(key,0L));list.add(saved);});
        quarantined.forEach((key, json) -> list.add(entry(key, json)));
        root.putInt("Version", 1); root.put("Runs", list); return root;
    }
    private static CompoundTag entry(String key, String json) {
        var entry = new CompoundTag(); entry.putString("Key", key); entry.putString("Snapshot", json); return entry;
    }
}
