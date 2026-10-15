package com.jvn.villagerretaliation.quest.tutorial;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.interaction.HireContract;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Headless checks of the native save/schema boundary; no world or client is launched. */
public final class TutorialPersistenceChecks {
    private static int checks;
    private static void check(boolean condition, String label) {
        checks++; if (!condition) throw new AssertionError(label);
    }
    public static int run() {
        checks = 0;
        UUID trial = UUID.randomUUID(), player = UUID.randomUUID();
        var contract = HireContract.tutorial(player, 1234, HiredVillagerRole.NITWIT, trial);
        check(contract.tutorialTrial() && contract.tutorialId().orElseThrow().equals(trial), "dedicated trial policy");
        check(contract.dailyCost() == 0 && contract.emeraldsPaid() == 0 && contract.durationDays() == 0, "no hire escrow");
        check(contract.endGameTime() == 1234, "trial does not simulate unlimited elapsed employment");
        contract.autoPayment(true);
        check(!contract.autoPayment(), "trial never auto renews");
        var binding = new TutorialRun.Binding(player, UUID.randomUUID(), UUID.randomUUID(), contract.id(),
                UUID.randomUUID(), UUID.randomUUID(), 0, "nitwit", "minecraft:overworld");
        var run = new TutorialRun(trial, "villagerretaliation:clipboard/nitwit", binding);
        var data = new TutorialSavedData();
        check(data.reserve(run), "reserve player/job globally");
        check(!data.reserve(new TutorialRun(UUID.randomUUID(), run.snapshot().tutorial(), binding)), "second NPC cannot create a second trial");
        run.activate(binding, new TutorialRun.Scope(Map.of("report", Set.of("worker")), Map.of("report", 1), Map.of("report", 1), ""));
        run.reserveGrant("shared_clipboard");
        var permit = run.reserve(binding, UUID.randomUUID(), "report", "worker", 1);
        var loaded = TutorialSavedData.load(data.save(new CompoundTag(), null), null);
        var restored = loaded.find(player, run.snapshot().tutorial()).orElseThrow();
        check(restored.binding().equals(binding) && restored.trial().equals(trial), "binding survives native NBT/Gson roundtrip");
        check(restored.snapshot().pending().get(permit.operation()).equals(permit), "pending operation survives roundtrip");
        check(!restored.reserveGrant("shared_clipboard"), "pending grant survives roundtrip");
        check(restored.commit(new TutorialRun.Receipt(permit, "actual_report_key", 0, "", 1240)), "reconciled report commits");
        check(restored.completed() && restored.state() == TutorialRun.State.AWAITING_CHOICE, "native persisted completion freezes work");
        var completed = TutorialSavedData.load(loaded.save(new CompoundTag(), null), null).find(player, run.snapshot().tutorial()).orElseThrow();
        check(completed.completed() && completed.committedUnits("report") == 1, "earned outcomes survive closing-dialogue delay");

        var corrupt = data.save(new CompoundTag(), null);
        corrupt.getList("Runs", 10).getCompound(0).putString("Snapshot", "{broken");
        var quarantined = TutorialSavedData.load(corrupt, null);
        check(quarantined.find(player, run.snapshot().tutorial()).isEmpty() && !quarantined.reserve(run), "corrupt entitlement fails closed");
        check(quarantined.save(new CompoundTag(), null).getList("Runs", 10).getCompound(0)
                .getString("Snapshot").equals("{broken"), "corrupt bytes retained for recovery");
        var future = data.save(new CompoundTag(), null); future.putInt("Version", 2);
        check(!TutorialSavedData.load(future, null).reserve(run), "future-version entitlement is not reset");

        var valid = JsonParser.parseString("""
                {"type":"work_state","tutorial":"villagerretaliation:clipboard/farming",
                 "role":"farming","check":"storage","parameter":"supply"}
                """).getAsJsonObject();
        check(WorkStatePredicate.read(valid) != null, "work-state descriptor parses");
        valid.addProperty("parameter", "payment");
        check(WorkStatePredicate.read(valid) == null, "tutorial cannot require payment storage");
        valid.addProperty("check", "supplies"); valid.remove("parameter");
        check(WorkStatePredicate.read(valid) == null, "supplies require an item selector");
        valid.addProperty("item", "minecraft:wheat_seeds"); valid.addProperty("count", 0);
        check(WorkStatePredicate.read(valid) == null, "zero quantity rejected");
        check(DialogueCondition.descriptors().stream().anyMatch(d -> d.id().equals("work_state")), "native editor discovers registered condition");
        authoredLessonsRoundTrip();
        System.out.println("Passed " + checks + " headless tutorial persistence/schema checks.");
        return checks;
    }
    private static void authoredLessonsRoundTrip(){
        try{
            var workspace=com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace.create(java.nio.file.Files.createTempDirectory("vr-clipboard-authoring-"));
            for(var role:HiredVillagerRole.values()){
                for(String checkName:java.util.List.of("assignment","area","storage","settings","policy","selection","task","route","outcome","completed","disposed"))
                    check(WorkStatePredicate.read(TutorialAuthoringPresets.condition(role,checkName))!=null,"valid native editor preset "+role+"/"+checkName);
                String base="data/villagerretaliation/quests/clipboard/clipboard_"+role.serializedName()+"/";
                for(String name:java.util.List.of("quest.json","locales/en_us.json")){
                    try(var reader=new java.io.InputStreamReader(TutorialPersistenceChecks.class.getClassLoader().getResourceAsStream(base+name),java.nio.charset.StandardCharsets.UTF_8)){
                        var json=JsonParser.parseReader(reader).getAsJsonObject();
                        workspace.add(new com.jvn.villagerretaliation.authoring.core.AuthoringDocument(name.equals("quest.json")?com.jvn.villagerretaliation.authoring.core.DefinitionKind.QUEST:com.jvn.villagerretaliation.authoring.core.DefinitionKind.RESOURCE,base+name,json));
                    }
                }
            }
            var errors=workspace.validate().stream().filter(d->d.severity()==com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic.Severity.ERROR).toList();
            check(errors.isEmpty(),"all fourteen authored lessons validate: "+errors.stream().limit(5).toList());
            var destination=java.nio.file.Files.createTempDirectory("vr-clipboard-export-").resolve("pack");workspace.exportPack(destination);
            var reopened=com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace.create(java.nio.file.Files.createTempDirectory("vr-clipboard-reimport-"));
            reopened.importPack(destination);
            check(reopened.documents().stream().filter(d->d.kind()==com.jvn.villagerretaliation.authoring.core.DefinitionKind.QUEST).count()==14,"native editor export/reimport retains every lesson");
            check(reopened.validate().stream().noneMatch(d->d.severity()==com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic.Severity.ERROR),"native editor roundtrip retains objective/action schemas");
        }catch(java.io.IOException error){throw new AssertionError("tutorial authoring roundtrip",error);}
    }

}
