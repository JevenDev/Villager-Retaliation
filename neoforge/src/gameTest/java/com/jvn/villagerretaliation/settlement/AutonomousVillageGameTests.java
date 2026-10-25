package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.*;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.storage.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("autonomous_village")
@PrefixGameTestTemplate(false)
public final class AutonomousVillageGameTests {
    private static AutonomousVillageRules.Rules rules(GameTestHelper h) {return AutonomousVillageRules.get(h.getLevel().getServer());}
    private static SettlementConstructionGameTests.Fixture fixture(GameTestHelper h) {
        var f=SettlementConstructionGameTests.fixture(h);var town=f.town();town.advance();town.advance();
        var state=new AutonomousVillageState();int y=SectionPos.blockToSectionCoord(town.center().getY());
        for(long plot:town.territory().plots()) for(int dy=-1;dy<=1;dy++) state.sections.add(SectionPos.asLong(net.minecraft.world.level.ChunkPos.getX(plot),y+dy,net.minecraft.world.level.ChunkPos.getZ(plot)));
        town.autonomous(state);state.ruler=f.builder().getUUID();state.surveyCursor=town.territory().plots().size();
        var graph=VillagerSocialGraphSavedData.get(h.getLevel());graph.ensureProfile(h.getLevel(),f.builder());
        VillageAllegianceRegistrySavedData.get(h.getLevel()).manageAutonomous(town.id(),town.center(),state.sections);
        return f;
    }
    private static Villager resident(GameTestHelper h,SettlementRecord town,boolean child) {
        var v=EntityType.VILLAGER.create(h.getLevel());v.setPos(town.center().getBottomCenter());v.setNoAi(true);v.setAge(child?-24000:0);
        VillageAllegianceApi.assignKnown(h.getLevel(),v,town.id(),AllegianceAssignmentSource.EXPLICIT_API);
        h.getLevel().addFreshEntity(v);VillagerSocialGraphSavedData.get(h.getLevel()).ensureProfile(h.getLevel(),v);return v;
    }
    @GameTest(template="empty") public static void sectionBoundariesAndStateSurviveReload(GameTestHelper h) {
        var f=fixture(h);var town=f.town();var registry=VillageAllegianceRegistrySavedData.get(h.getLevel());
        h.assertFalse(town.contains(town.center().above(96)),"natural villages retain vertical section boundaries");
        h.assertFalse(registry.resolveAt(h.getLevel(),town.center().above(96)).filter(town.id()::equals).isPresent(),"registry also preserves section semantics");
        var restored=SettlementRecord.load(town.save(h.getLevel().registryAccess()),h.getLevel().registryAccess(),()->{});
        h.assertValueEqual(restored.autonomous().sections,town.autonomous().sections,"exact footprint reloads");
        h.assertValueEqual(restored.autonomous().ruler,town.autonomous().ruler,"one ruler reloads");
        h.assertFalse(restored.contains(town.center().above(96)),"reload does not convert into player columns");f.builder().discard();h.succeed();
    }
    @GameTest(template="empty") public static void admissionUsesExistingBoundaryWithoutFreeClaims(GameTestHelper h) {
        try(var f=new AutonomousVillageAdmissionGameTests.Fixture(h)) {
            f.generatedAt(f.position);
            var village=f.registry.canonicalRecord(f.id).orElseThrow();
            var town=f.data.establishAutonomous(f.level,village).orElseThrow();
            var plots=village.footprintSections().stream().map(section->net.minecraft.world.level.ChunkPos.asLong(
                    SectionPos.x(section),SectionPos.z(section))).collect(java.util.stream.Collectors.toSet());
            h.assertValueEqual(town.territory().plots(),plots,"admission retains the generated village boundary");
            h.assertTrue(town.economy().balance().isZero(),"no starting money is minted");
            h.assertTrue(f.data.establishAutonomous(f.level,village).isEmpty(),"admission is idempotent");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void partnerThenAdultDescendantThenChild(GameTestHelper h) {
        var f=fixture(h);var town=f.town();var graph=VillagerSocialGraphSavedData.get(h.getLevel());
        var partner=resident(h,town,false);var child=resident(h,town,true);var adult=resident(h,town,false);
        graph.linkSpouses(h.getLevel(),f.builder(),partner);graph.linkParentsAndChild(h.getLevel(),f.builder(),partner,child);
        graph.linkParentsAndChild(h.getLevel(),f.builder(),partner,adult);
        var choices=new HashMap<UUID,Boolean>();choices.put(partner.getUUID(),true);choices.put(child.getUUID(),false);choices.put(adult.getUUID(),true);
        var random=net.minecraft.util.RandomSource.create(42);
        h.assertValueEqual(AutonomousVillageSuccession.choose(graph,f.builder().getUUID(),choices,random),partner.getUUID(),"living partner first");
        choices.remove(partner.getUUID());
        h.assertValueEqual(AutonomousVillageSuccession.choose(graph,f.builder().getUUID(),choices,random),adult.getUUID(),"adult descendant before child");
        choices.remove(adult.getUUID());
        h.assertValueEqual(AutonomousVillageSuccession.choose(graph,f.builder().getUUID(),choices,random),child.getUUID(),"child descendant is eligible without a regent");
        partner.discard();child.discard();adult.discard();f.builder().discard();h.succeed();
    }
    @GameTest(template="empty") public static void deathEventChoosesChildAndRejectsDeadRelatives(GameTestHelper h) {
        var f=fixture(h);var town=f.town();var graph=VillagerSocialGraphSavedData.get(h.getLevel());
        var partner=resident(h,town,false);var child=resident(h,town,true);graph.linkParentsAndChild(h.getLevel(),f.builder(),partner,child);
        graph.markDead(h.getLevel(),partner,"test");partner.discard();
        graph.markDead(h.getLevel(),f.builder(),"test");f.builder().discard();
        h.assertValueEqual(town.autonomous().ruler,child.getUUID(),"death hook immediately assigns the living child");
        graph.markDead(h.getLevel(),child,"test");child.discard();
        h.assertTrue(town.autonomous().ruler==null,"dead family does not fill vacant throne");h.succeed();
    }
    @GameTest(template="empty") public static void relativePriorityRandomAdultFallbackAndVacancy(GameTestHelper h) {
        var graph=new VillagerSocialGraphSavedData();var previous=EntityType.VILLAGER.create(h.getLevel());var parent=EntityType.VILLAGER.create(h.getLevel());
        var otherParent=EntityType.VILLAGER.create(h.getLevel());graph.linkParentsAndChild(h.getLevel(),parent,otherParent,previous);
        var stranger=UUID.randomUUID();var choices=Map.of(parent.getUUID(),true,stranger,true);
        h.assertValueEqual(AutonomousVillageSuccession.choose(graph,previous.getUUID(),choices,net.minecraft.util.RandomSource.create(3)),parent.getUUID(),"adult relative before resident fallback");
        var adults=Map.of(UUID.randomUUID(),true,UUID.randomUUID(),true,UUID.randomUUID(),false);var seen=new HashSet<UUID>();var random=net.minecraft.util.RandomSource.create(91);
        for(int i=0;i<100;i++) seen.add(AutonomousVillageSuccession.choose(graph,null,adults,random));
        h.assertValueEqual(seen.size(),2,"fallback samples multiple adults and never an unrelated child");
        h.assertTrue(AutonomousVillageSuccession.choose(graph,null,Map.of(UUID.randomUUID(),false),random)==null,"no eligible adult gives vacancy");h.succeed();
    }
    @GameTest(template="empty") public static void incomeIsCappedIdempotentAndDoesNotCatchUp(GameTestHelper h) {
        h.runAfterDelay(5,()-> {
        var f=fixture(h);var town=f.town();var rules=rules(h);town.economy().withdraw(town.economy().balance());town.economy().physicalIncome();
        town.removeDuty(f.builder().getUUID());town.removeArea(town.leader(),f.area().id());
        var farm=new SettlementArea(UUID.randomUUID(),SettlementAreaType.FARM,"Farm",f.origin(),f.origin().offset(3,2,3),5,true);
        h.assertTrue(town.putArea(town.leader(),farm),"farm registered");h.assertTrue(town.assignDuty(f.builder().getUUID(),farm.id()),"farm duty assigned");SettlementWorkforceService.reconcile(h.getLevel(),f.builder());h.getLevel().setDayTime(6000);
        town.autonomous().lastIncome=0;
        AutonomousVillageService.income(h.getLevel(),town,rules,2400);
        h.assertValueEqual(town.economy().pendingDeposit(),CurrencyAmount.of(1,1),"one loaded working resident earns one unit");
        AutonomousVillageService.income(h.getLevel(),town,rules,2400);
        h.assertValueEqual(town.economy().pendingDeposit(),CurrencyAmount.of(1,1),"same interval cannot pay twice");
        var restored=SettlementRecord.load(town.save(h.getLevel().registryAccess()),h.getLevel().registryAccess(),()->{});
        AutonomousVillageService.income(h.getLevel(),restored,rules,2400);
        h.assertValueEqual(restored.economy().pendingDeposit(),CurrencyAmount.of(1,1),"save/reload cannot pay interval twice");
        AutonomousVillageService.income(h.getLevel(),town,rules,240000);
        h.assertValueEqual(town.economy().pendingDeposit(),CurrencyAmount.of(1,1),"long absence pays no catch-up");
        town.autonomous().dailyIncome=rules.incomeCap();town.autonomous().incomeDay=10;
        AutonomousVillageService.income(h.getLevel(),town,rules,242400);
        h.assertValueEqual(town.economy().pendingDeposit(),CurrencyAmount.of(1,1),"daily income cap is enforced");
        f.builder().discard();h.succeed();
        });
    }
    @GameTest(template="empty") public static void pendingDepositsAreNeverSpendable(GameTestHelper h) {
        var economy=new SettlementEconomy(()->{});economy.bank.bind(h.getLevel().getServer(),h.getLevel().registryAccess());economy.physicalIncome();
        economy.credit(CurrencyAmount.of(19,2));
        h.assertTrue(economy.balance().isZero(),"absent storage has no spendable income");
        h.assertFalse(economy.withdraw(CurrencyAmount.of(1,1)),"pending cannot fund expansion or withdrawal");
        var restored=SettlementEconomy.load(economy.save(),()->{});
        h.assertValueEqual(restored.pendingDeposit(),CurrencyAmount.of(19,2),"pending exact amount survives reload");h.succeed();
    }
    @GameTest(template="empty") public static void treasuryReplaysStaleRegistrationAndDetachmentWithoutCopies(GameTestHelper h) {
        var f=fixture(h);var town=f.town();town.economy().withdraw(town.economy().balance());
        var bank=BankStorageGameTests.place(h,new BlockPos(1,2,1),StorageRegistry.SAFE.get());
        f.leader().setPos(bank.getBlockPos().getCenter());bank.issue(f.leader());
        bank.inventory().setItem(0,new ItemStack(Items.EMERALD,12));bank.inventory().setItem(53,new ItemStack(Items.DIAMOND,3));
        var before=bank.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(BankTreasury.register(f.leader(),bank),"register native safe");
        h.assertTrue(town.economy().withdraw(CurrencyAmount.of(5,1)),"spend registered funds");
        bank.loadWithComponents(before,h.getLevel().registryAccess());BankTreasury.reconcile(bank);
        h.assertValueEqual(bank.inventory().countItem(Items.EMERALD),7,"stale pre-registration chunk cannot restore spent currency");
        h.assertValueEqual(bank.inventory().getItem(53).getCount(),3,"noncurrency exact slots retained");
        var registered=bank.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(BankTreasury.unregister(f.leader(),bank),"leader unregisters");
        bank.inventory().removeItem(0,2);
        bank.loadWithComponents(registered,h.getLevel().registryAccess());BankTreasury.reconcile(bank);
        h.assertTrue(bank.treasury==null,"stale registered chunk cannot re-register detached stock");
        h.assertValueEqual(bank.inventory().countItem(Items.EMERALD),5,"detached inventory remains a single authoritative stock");
        h.assertTrue(town.economy().balance().isZero(),"detached stock is not spendable village money");
        bank.loadWithComponents(before,h.getLevel().registryAccess());BankTreasury.remove(bank);
        h.assertFalse(town.economy().bank.stocks.containsKey(bank.identity),"mining resolves authoritative owner even before a stale chunk reconciles");
        f.builder().discard();h.succeed();
    }
    @GameTest(template="empty") public static void physicalIncomeDepositAndBreachConserveFunds(GameTestHelper h) {
        var f=fixture(h);var town=f.town();town.economy().withdraw(town.economy().balance());town.economy().physicalIncome();
        var bank=BankStorageGameTests.place(h,new BlockPos(1,2,1),StorageRegistry.SAFE.get());f.leader().setPos(bank.getBlockPos().getCenter());bank.issue(f.leader());
        town.economy().credit(CurrencyAmount.of(10,1));h.assertTrue(BankTreasury.register(f.leader(),bank),"bank available for pending income");
        h.assertTrue(town.economy().pendingDeposit().isZero(),"pending becomes physical only once");
        h.assertValueEqual(town.economy().balance(),CurrencyAmount.of(10,1),"physical funds counted once");
        bank.hit();h.assertValueEqual(bank.automation().extractItem(0,4,false).getCount(),4,"breach permits public theft");
        town.economy().depositOverflow();h.assertValueEqual(town.economy().balance(),CurrencyAmount.of(6,1),"retry cannot recreate stolen funds");
        for(int i=0;i<54;i++)bank.inventory().setItem(i,new ItemStack(Items.STONE,64));town.economy().credit(CurrencyAmount.of(3,1));
        h.assertValueEqual(town.economy().pendingDeposit(),CurrencyAmount.of(3,1),"full storage visibly waits");
        BankTreasury.depositOverflow(bank);bank.inventory().setItem(0,ItemStack.EMPTY);BankTreasury.depositOverflow(bank);
        h.assertValueEqual(town.economy().pendingDeposit(),CurrencyAmount.of(3,1),"controller retries coalesce within the same interval");
        town.economy().depositOverflow();h.assertTrue(town.economy().pendingDeposit().isZero(),"explicit retry deposits pending funds once capacity exists");
        f.builder().discard();h.succeed();
    }
    @GameTest(template="empty") public static void plannerCadenceAndNeedsAreBounded(GameTestHelper h) {
        var f=fixture(h);var town=f.town();var rules=rules(h);
        h.assertValueEqual(AutonomousVillageService.need(town,4,16),SettlementAreaType.FARM,"food before improvements");
        h.assertTrue(AutonomousVillageService.need(town,0,16)==null,"empty village does not build");
        h.assertTrue(AutonomousVillageService.beginPlan(town,rules,100),"first observation allowed");
        long revision=town.revision();for(int i=0;i<1000;i++)h.assertFalse(AutonomousVillageService.beginPlan(town,rules,100),"no same-tick backlog work");
        h.assertValueEqual(town.revision(),revision,"skipped planning does not dirty saves");
        var restored=SettlementRecord.load(town.save(h.getLevel().registryAccess()),h.getLevel().registryAccess(),()->{});
        h.assertFalse(AutonomousVillageService.beginPlan(restored,rules,100),"cadence survives reload");f.builder().discard();h.succeed();
    }
    @GameTest(template="empty") public static void unloadedVillageAndBuildersDoNotSimulate(GameTestHelper h) {
        var f=fixture(h);var town=f.town();
        f.builder().discard();h.assertFalse(AutonomousVillageService.loaded(h.getLevel(),town),"no loaded resident means paused operation");
        var phantom=EntityType.VILLAGER.create(h.getLevel());phantom.setUUID(f.builder().getUUID());
        h.assertFalse(SettlementConstructionService.eligible(h.getLevel(),town,phantom),"saved resident contexts cannot build autonomously offscreen");
        var far=new BlockPos(29000000,70,29000000);h.assertFalse(h.getLevel().hasChunkAt(far),"test remote chunk is unavailable");
        h.assertFalse(town.contains(far),"remote unavailable site rejected without loading");h.assertFalse(h.getLevel().hasChunkAt(far),"no forced loading");h.succeed();
    }
    @GameTest(template="empty") public static void expansionRequiresNeedFundsAndClearClaim(GameTestHelper h) {
        var f=fixture(h);var town=f.town();var rules=rules(h);var state=town.autonomous();
        var original=town.economy().balance();int plots=town.territory().plots().size();
        h.assertFalse(AutonomousVillageService.expand(h.getLevel(),town,rules,0),"no sustained need no expansion");
        state.observations=rules.sustained();
        boolean claimed=false;
        // Test fixture loads a bounded ring; production never requests these chunks.
        for(long plot:town.territory().plots()) {var c=new net.minecraft.world.level.ChunkPos(plot);for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)h.getLevel().getChunk(c.x+dx,c.z+dz);}
        for(int i=0;i<plots*4 && !claimed;i++){state.siteCursor=i;claimed=AutonomousVillageService.expand(h.getLevel(),town,rules,0);}
        h.assertTrue(claimed,"eligible loaded adjacent claim succeeds");
        h.assertValueEqual(town.economy().balance(),original.subtract(CurrencyAmount.of(rules.expansionPrice(),1)),"claim spends exact configured price once");
        h.assertValueEqual(town.territory().plots().size(),plots+1,"one bounded expansion");
        h.assertFalse(AutonomousVillageService.expand(h.getLevel(),town,rules,0),"need resets after purchase");
        state.expansions=rules.expansions();state.observations=rules.sustained();h.assertFalse(AutonomousVillageService.expand(h.getLevel(),town,rules,0),"hard expansion cap");f.builder().discard();h.succeed();
    }
    @GameTest(template="empty") public static void takeoverPreservesFundsProjectsAndPlayerSettlementDefaults(GameTestHelper h) {
        var f=fixture(h);var town=f.town();var funds=town.economy().balance();var player=UUID.randomUUID();town.acceptAutonomousLeadership(player);
        h.assertTrue(town.autonomous()==null && town.leader().equals(player),"supported acceptance stops NPC planning");
        h.assertValueEqual(town.economy().balance(),funds,"takeover keeps treasury");
        h.assertTrue(town.permits(f.leader().getUUID(),SettlementPermission.BUILD),"in-flight civic authorities stay valid");
        var raw=town.save(h.getLevel().registryAccess());h.assertFalse(raw.contains("Autonomous"),"existing player settlements do not acquire NPC planning on load");f.builder().discard();h.succeed();
    }
    @GameTest(template="empty") public static void constructionWaitsForFullQuoteAndRejectsOutsideSections(GameTestHelper h) {
        var f=fixture(h);var town=f.town();var id=UUID.randomUUID();
        var project=SettlementProject.create(town,f.area().id(),f.builder().getUUID(),f.plan(),f.origin(),id,h.getLevel().getGameTime());
        project.reservePayment(f.plan().price());project.queue(town.leader());
        h.assertTrue(town.queueProject(town.leader(),project),"autonomous intent uses normal persisted queue");
        var before=town.economy().balance();
        h.assertFalse(SettlementConstructionMaterials.reserve(h.getLevel(),town,project,f.plan(),f.builder()),"missing full quote cannot reserve partial supplies");
        h.assertFalse(SettlementConstructionMaterials.reserved(project),"no phantom escrow");
        h.assertValueEqual(town.economy().balance(),before,"waiting project has not spent money");
        var outside=SettlementProject.create(town,f.area().id(),f.builder().getUUID(),f.plan(),f.origin().above(96),UUID.randomUUID(),h.getLevel().getGameTime());
        outside.queue(town.leader());h.assertFalse(town.queueProject(town.leader(),outside),"same XZ outside natural height boundary rejected");
        var restored=SettlementRecord.load(town.save(h.getLevel().registryAccess()),h.getLevel().registryAccess(),()->{});
        h.assertTrue(restored.project(id).pending(),"queued autonomous intent survives reload");f.builder().discard();h.succeed();
    }
    @GameTest(template="empty") public static void repairsKeepMatchingContainersAndRefuseAlterations(GameTestHelper h) {
        var f=fixture(h);var town=f.town();
        var source=SettlementProject.create(town,f.area().id(),f.builder().getUUID(),f.plan(),f.origin(),UUID.randomUUID(),h.getLevel().getGameTime());
        h.assertTrue(town.startProject(town.leader(),source),"known village-owned project");town.finishProject(source.id(),SettlementProject.Status.COMPLETED);
        for(var block:f.plan().blocks()) h.getLevel().setBlock(f.plan().worldPos(f.origin(),block),block.state(),2);
        var repair=SettlementProject.create(town,f.area().id(),f.builder().getUUID(),f.plan(),f.origin(),UUID.randomUUID(),h.getLevel().getGameTime());repair.repairOf(source.id());
        // Redstone/door update ordering can change authored states; clear every mismatch to model missing cells only.
        for(var block:f.plan().blocks()) {var pos=f.plan().worldPos(f.origin(),block);if(!com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner.sameSchematicState(h.getLevel().getBlockState(pos),block.state()))h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),2);}
        var target=f.plan().worldPos(f.origin(),f.plan().blocks().getFirst());h.getLevel().setBlock(target,Blocks.AIR.defaultBlockState(),2);
        h.assertTrue(AutonomousVillageMaintenance.safe(h.getLevel(),town,repair),"repair accepts only matching old blocks and missing cells");
        h.getLevel().setBlock(target,Blocks.CHEST.defaultBlockState(),2);
        h.assertFalse(AutonomousVillageMaintenance.safe(h.getLevel(),town,repair),"new container blocks repair instead of losing belongings");
        var restored=SettlementProject.load(repair.save());h.assertValueEqual(restored.repairOf(),source.id(),"repair provenance persists");f.builder().discard();h.succeed();
    }
    @GameTest(template="empty") public static void managementTableStillOffersEarnedLeadership(GameTestHelper h) {
        var f=fixture(h);var pos=f.origin();h.getLevel().setBlock(pos,com.jvn.villagerretaliation.block.VillagerRetaliationBlocks.SETTLEMENT_MANAGEMENT.get().defaultBlockState(),3);f.leader().setPos(pos.getCenter());
        h.assertValueEqual(SettlementFoundingService.found(f.leader(),pos).status(),SettlementFoundingService.Status.INDEPENDENT_VILLAGE,"autonomous record preserves existing management-table leadership route");
        h.assertTrue(SettlementLeadershipService.status(f.leader(),f.town().id()).isPresent(),"natural leadership requirements remain queryable");f.builder().discard();h.succeed();
    }
    @GameTest(template="empty") public static void sustainedPlannerCreatesOnlyOneSafeProject(GameTestHelper h) {
        var f=fixture(h);var town=f.town();var rules=rules(h);var state=town.autonomous();
        // The generated test world provides flat loaded candidate plots, never loaded by production planning.
        for(long plot:town.territory().plots()) {var c=new net.minecraft.world.level.ChunkPos(plot);h.getLevel().getChunk(c.x,c.z);}
        int attempts=town.territory().plots().size()+rules.sustained()+2;
        for(int i=0;i<attempts && town.projects().isEmpty();i++) {
            int before=state.siteCursor;AutonomousVillageService.plan(h.getLevel(),town,rules,1200L*i);
            h.assertTrue(state.siteCursor<=before+1,"one site candidate per planning observation");
            if(i<rules.sustained()-1) h.assertTrue(town.projects().isEmpty(),"transient need cannot commission");
        }
        h.assertValueEqual(town.projects().size(),1,"sustained need commissions an actual authored structure");
        var project=town.projects().values().iterator().next();h.assertTrue(town.owns(project),"entire site is inside natural boundaries");
        AutonomousVillageService.plan(h.getLevel(),town,rules,24000);
        h.assertValueEqual(town.projects().size(),1,"unfinished work prevents construction proliferation");
        h.assertFalse(SettlementConstructionMaterials.reserved(project),"missing resources leave a visible pending project");f.builder().discard();h.succeed();
    }
    @GameTest(template="empty") public static void autonomousTradeBuysRealFoodWithoutClerkIncomeDuplication(GameTestHelper h) {
        h.runAfterDelay(5,()-> {
            var f=SettlementTradeGameTests.fixture(h);var town=f.town();var state=new AutonomousVillageState();
            for(long plot:town.territory().plots()) for(int dy=-1;dy<=1;dy++) state.sections.add(SectionPos.asLong(net.minecraft.world.level.ChunkPos.getX(plot),SectionPos.blockToSectionCoord(town.center().getY())+dy,net.minecraft.world.level.ChunkPos.getZ(plot)));
            town.autonomous(state);town.economy().physicalIncome();h.getLevel().setDayTime(6000);
            var bank=BankStorageGameTests.place(h,new BlockPos(6,2,1),StorageRegistry.SAFE.get());f.leader().setPos(bank.getBlockPos().getCenter());bank.issue(f.leader());
            h.assertTrue(BankTreasury.register(f.leader(),bank),"physical treasury registered");town.economy().credit(CurrencyAmount.of(20,1));
            var rules=rules(h);town.economy().configureBudget(CurrencyAmount.of(rules.reserve(),1),CurrencyAmount.of(rules.dailyBudget(),1));
            AutonomousVillageService.configureTrade(town,rules);var before=town.economy().balance();
            var results=SettlementTradeService.process(h.getLevel(),town,2400);
            var order=town.economy().orders().values().stream().filter(o->o.item().equals(net.minecraft.resources.ResourceLocation.parse("minecraft:bread"))).findFirst().orElseThrow();
            var result=results.get(order.id());h.assertTrue(result.count()>0,"default food order can purchase within price and budget limits");
            h.assertValueEqual(f.depot().count(stack->stack.is(Items.BREAD)),result.count(),"purchase creates exactly the traded food quantity");
            h.assertTrue(town.economy().balance().compareTo(before)<0,"real treasury funds pay for food");
            var paid=town.economy().balance();h.assertTrue(SettlementTradeService.process(h.getLevel(),town,2400).isEmpty(),"trade interval cannot repeat");
            state.lastIncome=0;AutonomousVillageService.income(h.getLevel(),town,rules,2400);
            h.assertValueEqual(town.economy().balance(),paid,"trading clerk does not also receive abstract work income");
            h.assertTrue(town.economy().pendingDeposit().isZero(),"no hidden duplicate clerk credit");
            f.depot().setItem(10,new ItemStack(Items.IRON_BLOCK,2));bank.hit();bank.repairAfter=0;
            // Demand allocation advances through the normal bounded queue before repair can take supplies.
            SettlementAllocationService.request(h.getLevel(),town);
            for(int i=0;i<2000 && SettlementAllocationService.ready(h.getLevel(),town)==null;i++) SettlementSavedData.get(h.getLevel()).simulation.runPending();
            h.assertTrue(BankTreasury.repair(h.getLevel(),town,bank),"loaded permitted iron funds a repair");
            h.assertValueEqual(f.depot().getItem(10).getCount(),1,"repair consumes exactly one real iron block");
            h.assertValueEqual(bank.damage,0,"repair restores lock after durable intent");
            h.assertFalse(BankTreasury.repair(h.getLevel(),town,bank),"repeated repair cannot debit again");
            f.clerk().discard();h.succeed();
        });
    }
    @GameTest(template="empty") public static void completedFarmBecomesStaffableWithoutConstructionOverlap(GameTestHelper h) {
        var f=fixture(h);var town=f.town();town.removeDuty(f.builder().getUUID());town.removeArea(town.leader(),f.area().id());
        town.returnToAutomaticStaffing(town.leader(),f.builder().getUUID());
        var id=UUID.randomUUID();var area=SettlementConstructionService.workplace(h.getLevel(),town,f.plan(),f.origin(),id);
        AutonomousVillageService.assignBuilder(h.getLevel(),town,area);
        h.assertTrue(town.duties().containsKey(f.builder().getUUID()),"planner assigns a real builder staging duty");
        var project=SettlementProject.planned(town,area,f.builder().getUUID(),f.plan(),f.origin(),id,h.getLevel().getGameTime());
        h.assertTrue(town.startProject(town.leader(),project),"normal construction accepts staged builder");
        town.autonomous().projects.put(id,SettlementAreaType.FARM);town.finishProject(id,SettlementProject.Status.COMPLETED);
        h.getLevel().setBlockAndUpdate(f.origin(),Blocks.COMPOSTER.defaultBlockState());
        for(int i=0;i<town.territory().plots().size();i++) AutonomousVillageService.survey(h.getLevel(),town);
        h.assertTrue(town.areas().values().stream().noneMatch(a->a.type()==SettlementAreaType.FARM),"POI discovery does not steal completed project registration");
        AutonomousVillageService.complete(h.getLevel(),town);
        h.assertTrue(town.areas().values().stream().anyMatch(a->a.type()==SettlementAreaType.FARM),"completed farm registers despite former construction footprint");
        h.assertTrue(town.areas().values().stream().noneMatch(a->a.type()==SettlementAreaType.CONSTRUCTION),"temporary staging removed");
        h.assertFalse(town.manuallyStaffed(f.builder().getUUID()),"builder returns to automatic staffing");
        h.assertValueEqual(town.autonomous().completed,1,"completion counted once");
        AutonomousVillageService.complete(h.getLevel(),town);h.assertValueEqual(town.autonomous().completed,1,"completion replay is idempotent");
        f.builder().discard();h.succeed();
    }
    @GameTest(template="empty") public static void repairReceiptReplayDoesNotEraseLaterDamage(GameTestHelper h) {
        var f=fixture(h);var town=f.town();var level=h.getLevel();
        var bank=BankStorageGameTests.place(h,new BlockPos(1,2,1),StorageRegistry.CONTROLLER.get());f.leader().setPos(bank.getBlockPos().getCenter());bank.issue(f.leader());
        h.assertTrue(BankTreasury.register(f.leader(),bank),"native controller registered");bank.hit();bank.hit();
        h.assertFalse(BankTreasury.repair(level,town,bank),"cooldown prevents repair before materials are consumed");
        var stock=town.economy().bank.stocks.get(bank.identity);stock.repairId=UUID.randomUUID();stock.repairTarget=1;
        // Emulate a durable debit/repair intent replayed after an interrupted chunk save.
        BankTreasury.reconcile(bank);h.assertValueEqual(bank.damage,1,"persisted repair intent applies once");
        var receipt=bank.repairReceipt;bank.hit();BankTreasury.reconcile(bank);
        h.assertValueEqual(bank.damage,2,"retry does not erase damage after the recorded repair");
        h.assertValueEqual(bank.repairReceipt,receipt,"same repair identity is retained");
        var restored=SettlementRecord.load(town.save(level.registryAccess()),level.registryAccess(),()->{});
        h.assertValueEqual(restored.economy().bank.stocks.get(bank.identity).repairId,stock.repairId,"pending checkpoint intent survives reload");f.builder().discard();h.succeed();
    }
}
