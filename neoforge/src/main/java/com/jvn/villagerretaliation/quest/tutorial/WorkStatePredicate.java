package com.jvn.villagerretaliation.quest.tutorial;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import com.jvn.villagerretaliation.interaction.HireContractStore;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;

/** Registered condition objective family. Missing/unloaded authority is UNKNOWN, never success. */
public record WorkStatePredicate(String tutorial, HiredVillagerRole role, String check, String parameter,
        ResourceLocation item, int count, boolean enabled, boolean itemTag) {
    public WorkStatePredicate(String tutorial,HiredVillagerRole role,String check,String parameter,ResourceLocation item,int count,boolean enabled){this(tutorial,role,check,parameter,item,count,enabled,false);}
    public record Result(ConditionOutcome outcome, String reason) {
        public boolean matches() { return outcome == ConditionOutcome.MATCHED; }
    }
    public static JsonObject schema() {
        return JsonParser.parseString("""
                {"type":"object","additionalProperties":false,
                 "required":["type","tutorial","role","check"],
                 "properties":{
                   "type":{"const":"work_state"},
                   "tutorial":{"type":"string","pattern":"^[a-z0-9_.-]+:[a-z0-9_./-]+$","maxLength":256},
                   "role":{"enum":["farming","fishing","mining","logging","hunting","animal_handling",
                      "brewing","craftsman","cook","smelter","courier","nitwit","combat","builder"]},
                   "check":{"enum":["eligible","assignment","area","storage","equipment","supplies","settings","outcome","completed","disposed","learned","task","route","selection","policy","quoted","blueprint"]},
                   "parameter":{"type":"string","minLength":1,"maxLength":128},
                   "item":{"type":"string","pattern":"^[a-z0-9_.-]+:[a-z0-9_./-]+$","maxLength":256},
                   "item_tag":{"type":"string","pattern":"^[a-z0-9_.-]+:[a-z0-9_./-]+$","maxLength":256},
                   "count":{"type":"integer","minimum":1,"maximum":65536},"enabled":{"type":"boolean"}},
                 "allOf":[
                   {"if":{"properties":{"check":{"enum":["equipment","supplies"]}}},"then":{"oneOf":[{"required":["item"]},{"required":["item_tag"]}]}},
                   {"if":{"properties":{"check":{"enum":["storage","settings","outcome","learned"]}}},"then":{"required":["parameter"]}},
                   {"if":{"properties":{"check":{"const":"storage"}}},"then":{"properties":{"parameter":{"enum":["supply","output"]}}}},
                   {"if":{"properties":{"check":{"const":"settings"}}},"then":{"properties":{"parameter":{"enum":["work_enabled","assigned_supplies","auto_deposit","farming_wheat"]}}}}
                 ]}
                """).getAsJsonObject();
    }
    public static WorkStatePredicate read(JsonObject json) {
        if (!QuestJsonSchema.validate(json, schema()).isEmpty()) return null;
        return new WorkStatePredicate(json.get("tutorial").getAsString(),
                HiredVillagerRole.bySerializedName(json.get("role").getAsString()), json.get("check").getAsString(),
                json.has("parameter") ? json.get("parameter").getAsString() : "",
                json.has("item") ? ResourceLocation.tryParse(json.get("item").getAsString()) : json.has("item_tag")?ResourceLocation.tryParse(json.get("item_tag").getAsString()):null,
                json.has("count") ? json.get("count").getAsInt() : 1,
                !json.has("enabled") || json.get("enabled").getAsBoolean(),json.has("item_tag"));
    }

    public Result evaluate(ServerLevel level, ServerPlayer learner) {
        if (level == null || learner == null) return unknown("Learner or world is unavailable.");
        var run = TutorialSavedData.get(level).find(learner.getUUID(), tutorial).orElse(null);
        if (run == null || !run.binding().role().equals(role.serializedName()))
            return unmet("Accept this personal job lesson and bind its worker first.");
        if(check.equals("blueprint")){
            for(int slot=0;slot<learner.getInventory().getContainerSize();slot++){
                var stack=learner.getInventory().getItem(slot);
                var preview=com.jvn.villagerretaliation.item.ConstructionBlueprintItem.previewData(stack).orElse(null);
                var marker=stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound("VillagerRetaliationTutorialBlueprint");
                if(preview!=null&&preview.jobId().equals(run.binding().task())&&preview.structureId().equals(TutorialBuilderPlan.TEMPLATE)
                        &&preview.dimension().location().toString().equals(run.binding().dimension())&&marker.hasUUID("Trial")&&marker.getUUID("Trial").equals(run.trial())&&marker.getBoolean("PreviewApplied"))return matched();
            }
            return unmet("Hold this lesson's Plains Accessory 1 blueprint, point at its intended ground and press Use. Then inspect its committed preview before asking me to check.");
        }
        if(check.equals("quoted"))return result(run.state()==TutorialRun.State.AWAITING_CHOICE&&run.snapshot().quote()!=null,"Ask for the current normal fee first.");
        if(check.equals("learned"))return result(run.snapshot().learned().contains(parameter),"Demonstrate the requested step, then ask me to check it.");
        if (check.equals("completed")) return result(run.completed(), "Complete the bound finite lesson.");
        if (check.equals("disposed")) return result(run.state() == TutorialRun.State.STOPPED || run.state() == TutorialRun.State.CONVERTED,
                "Choose Stop, or explicitly accept the current quoted normal fee.");
        if (check.equals("outcome")) {
            long quantity = run.snapshot().receipts().values().stream()
                    .filter(receipt -> receipt.permit().action().equals(parameter))
                    .mapToLong(receipt -> item == null ? receipt.permit().units() : receipt.permit().action().equals("deposit")
                            ? receipt.item().equals(item.toString())?receipt.quantity():0 : receipt.products().getOrDefault(item.toString(),0)).sum();
            return result(quantity >= count, "Waiting for the bound worker's committed " + parameter + " outcome.");
        }
        var dimension = ResourceLocation.tryParse(run.binding().dimension());
        var workerLevel = dimension == null ? null : level.getServer().getLevel(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimension));
        var entity = workerLevel == null ? null : workerLevel.getEntity(run.binding().worker());
        if (!(entity instanceof Villager worker) || !worker.isAlive())
            return unknown("The bound worker is unloaded or missing; resume or explicitly choose a replacement.");
        var contract = HireContractStore.load(worker).orElse(null);
        if (contract == null || !contract.isActive() || !contract.id().equals(run.binding().assignment())
                || !contract.owner().filter(learner.getUUID()::equals).isPresent() || contract.role() != role)
            return unmet("Restore this lesson's assignment; another worker or contract cannot count.");
        if (!(role==HiredVillagerRole.BUILDER?HiredVillagerRoles.canOfferBuilderService(workerLevel,worker):HiredVillagerRoles.availableContractRoles(workerLevel, worker).contains(role))
                || role == HiredVillagerRole.FARMING && worker.getVillagerData().getProfession() != VillagerProfession.FARMER)
            return unmet("This worker cannot execute the required job.");
        if (check.equals("assignment")) return matched();
        var session = HiredWorkSession.create(workerLevel, worker, role);
        return switch (check) {
            case "policy" -> result(TutorialSetupChecks.policy(worker,role,session.state()),"Inspect the exact role settings requested by this lesson.");
            case "selection" -> result(TutorialSetupChecks.selection(workerLevel,worker,role),"Configure the requested real recipe, filter or brewing order.");
            case "task" -> result(TutorialSetupChecks.task(workerLevel,worker,role),"Apply the requested finite Task to this worker.");
            case "route" -> result(session.context().route()!=null&&session.context().route().nodes().size()>=2,"Apply the ordered route to this worker.");
            case "area" -> result(com.jvn.villagerretaliation.interaction.HiredWorkArea.fromState(session.state(),worker.blockPosition()).explicitlyAssigned(), "Apply the area to the named tutor; a draft is only a preview.");
            case "storage" -> {
                var records = AssignedStorageService.assignedStorage(workerLevel, worker).stream()
                        .filter(record -> parameter.equals(record.purpose())).toList();
                if (records.isEmpty()) yield unmet("Select the chest, use the Clipboard on the tutor, then choose Assign Clipboard Storage.");
                boolean loaded = AssignedStorageService.hasLiveAssignedStorageForPurpose(workerLevel, worker, parameter);
                yield loaded ? matched() : unknown("The linked container is not currently accessible; load the site and check access.");
            }
            case "equipment" -> {
                int total = 0;
                for (var slot : EquipmentSlot.values()) {
                    ItemStack stack = worker.getItemBySlot(slot);
                    if (matchesItem(stack)) total += stack.getCount();
                }
                yield result(total >= count, "Equip the bound worker with the usable required item.");
            }
            case "supplies" -> {
                int total = 0;
                var inventory = session.inventory();
                for (int slot : inventory.supplySlots()) {
                    var stack = inventory.getItem(slot);
                    if (matchesItem(stack)) total += stack.getCount();
                }
                if(inventory.supplySlots().stream().noneMatch(slot->matchesItem(inventory.getItem(slot))) && !inventory.findTool(this::matchesItem).isEmpty())total++;
                if (session.context().useAssignedStorageForSupplies())
                    total += AssignedStorageService.countItems(worker, this::matchesItem);
                yield result(total >= count, "Put the required inputs in accessible assigned supplies or the worker's inventory.");
            }
            case "settings" -> {
                boolean actual = switch (parameter) {
                    case "work_enabled" -> session.state().getBoolean("Enabled");
                    case "assigned_supplies" -> session.context().useAssignedStorageForSupplies();
                    case "auto_deposit" -> session.context().autoDepositOutputs();
                    case "farming_wheat" -> HiredFarmingOptions.crop(session.state()) == HiredFarmingOptions.Crop.WHEAT
                            && HiredFarmingOptions.tillSoil(session.state()) && HiredFarmingOptions.plantCrops(session.state())
                            && HiredFarmingOptions.harvestCrops(session.state());
                    default -> false;
                };
                yield result(actual == enabled, "Inspect Job > Work Settings; change only the incorrect setting.");
            }
            default -> unmet("Unsupported work-state check.");
        };
    }
    private boolean matchesItem(ItemStack stack) {
        return !stack.isEmpty() && (itemTag?stack.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,item)):BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item))
                && (!stack.isDamageableItem() || stack.getDamageValue() < stack.getMaxDamage());
    }
    private static Result result(boolean success, String reason) { return success ? matched() : unmet(reason); }
    private static Result matched() { return new Result(ConditionOutcome.MATCHED, "Server-confirmed."); }
    private static Result unmet(String reason) { return new Result(ConditionOutcome.UNMET, reason); }
    private static Result unknown(String reason) { return new Result(ConditionOutcome.UNKNOWN, reason); }
}
