package com.jvn.villagerretaliation.quest.tutorial;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Dependency-free policy checks; native world mutation/reload tests are separate. */
public final class TutorialRunChecks {
    private TutorialRunChecks() { }
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static TutorialRun.Binding binding() {
        return new TutorialRun.Binding(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 0, "farming", "minecraft:overworld");
    }
    private static TutorialRun trial(TutorialRun.Binding binding) {
        var run = new TutorialRun(UUID.randomUUID(), "villagerretaliation:clipboard/farming", binding);
        check(run.activate(binding, new TutorialRun.Scope(
                Map.of("harvest", Set.of("crop"), "deposit", Set.of("chest")),
                Map.of("harvest", 1, "deposit", 3), Map.of("harvest", 1, "deposit", 3), "chest")), "activate");
        return run;
    }
    public static int run() {
        checks = 0;
        var b = binding(); var run = trial(b);
        check(run.reserve(binding(), UUID.randomUUID(), "harvest", "crop", 1) == null, "foreign actor rejected");
        check(run.reserve(b, UUID.randomUUID(), "harvest", "wrong", 1) == null, "wrong target rejected");
        check(run.reserve(b, UUID.randomUUID(), "harvest", "crop", 0) == null, "zero units rejected");
        var op = UUID.randomUUID(); var permit = run.reserve(b, op, "harvest", "crop", 1);
        check(permit != null, "reserve valid work");
        check(run.reserve(b, op, "harvest", "crop", 1) == null, "operation reservation replay rejected");
        check(run.reserve(b, UUID.randomUUID(), "harvest", "crop", 1) == null, "reserved budget enforced");
        run = TutorialRun.restore(run.snapshot());
        check(run.reserve(b, UUID.randomUUID(), "harvest", "crop", 1) == null, "unresolved reservation survives reload");
        check(!run.commit(new TutorialRun.Receipt(new TutorialRun.Permit(op, b, "harvest", "wrong", 1),
                "wheat", 3, "", 12)), "forged permit rejected");
        var harvest = new TutorialRun.Receipt(permit, "wheat", 3, "", 12);
        check(run.commit(harvest), "real harvest receipt accepted");
        check(!run.commit(harvest), "receipt replay rejected");
        var stale = run.reserve(b, UUID.randomUUID(), "deposit", "chest", 1);
        check(!run.commit(new TutorialRun.Receipt(stale, "old-player-wheat", 1, "chest", 13)), "pre-existing goods rejected");
        check(run.failed(stale), "nonmutating failure releases its reservation");
        var partial = run.reserve(b, UUID.randomUUID(), "deposit", "chest", 1);
        check(run.commit(new TutorialRun.Receipt(partial, "wheat", 1, "chest", 14)), "partial delivery");
        check(!run.completed(), "partial delivery is not complete");
        var rest = run.reserve(b, UUID.randomUUID(), "deposit", "chest", 2);
        check(!run.commit(new TutorialRun.Receipt(rest, "wheat", 2, "other-chest", 15)), "wrong destination rejected");
        check(run.commit(new TutorialRun.Receipt(rest, "wheat", 2, "chest", 15)), "remaining delivery");
        check(run.completed() && run.state() == TutorialRun.State.AWAITING_CHOICE, "freeze immediately at completion");
        check(run.reserve(b, UUID.randomUUID(), "harvest", "crop", 1) == null, "no free work pending choice");
        check(!run.reserveGrant("late-kit"), "no new grants after completion");
        var quote = new TutorialRun.Quote(UUID.randomUUID(), b.assignment(), b.generation(), b.role(),
                "minecraft:emerald", 1, 17, "elapsed game time; auto payment off");
        check(run.offer(quote), "offer actual price");
        var changed = new TutorialRun.Quote(UUID.randomUUID(), b.assignment(), b.generation(), b.role(),
                "minecraft:emerald", 1, 19, quote.terms());
        check(!run.accepts(quote.id(), changed), "changed price requires fresh consent");
        var currency = new TutorialRun.Quote(UUID.randomUUID(), b.assignment(), b.generation(), b.role(),
                "minecraft:diamond", 1, 17, quote.terms());
        check(!run.accepts(quote.id(), currency), "changed currency requires fresh consent");
        check(!run.accepts(UUID.randomUUID(), quote), "stale quote ID rejected");
        run = TutorialRun.restore(run.snapshot());
        check(run.accepts(quote.id(), quote), "consent quote survives reload");
        check(run.converted(quote.id(), quote, UUID.randomUUID()), "paid transaction receipt finalizes once");
        check(!run.converted(quote.id(), quote, UUID.randomUUID()), "double-click cannot convert twice");
        check(!run.stop(b), "late stop cannot cancel converted employment");
        check(run.completed(), "completion retained after paid continuation");

        var stop = trial(b);
        var p1 = stop.reserve(b, UUID.randomUUID(), "harvest", "crop", 1);
        stop.commit(new TutorialRun.Receipt(p1, "wheat", 3, "", 1));
        var p2 = stop.reserve(b, UUID.randomUUID(), "deposit", "chest", 3);
        stop.commit(new TutorialRun.Receipt(p2, "wheat", 3, "chest", 2));
        check(stop.stop(b), "stop choice accepted");
        check(stop.completed() && stop.state() == TutorialRun.State.STOPPED, "stop retains earned completion");
        check(!stop.stop(b), "stop is idempotent");

        var recovery = trial(b);
        check(recovery.reserveGrant("kit"), "reserve grant once");
        recovery = TutorialRun.restore(recovery.snapshot());
        check(!recovery.reserveGrant("kit"), "interrupted delivery does not regrant");
        check(recovery.deliveredGrant("kit"), "finish pending delivery");
        check(!recovery.deliveredGrant("kit"), "delivery replay rejected");
        var pending = recovery.reserve(b, UUID.randomUUID(), "harvest", "crop", 1);
        recovery.pause();
        check(!recovery.replaceWorker(b, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                b.dimension(), true), "unreconciled old work blocks replacement");
        check(recovery.failed(pending), "reconcile no mutation");
        check(recovery.replaceWorker(b, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                b.dimension(), true), "explicit validated replacement");
        check(recovery.binding().generation() == 1, "replacement increments generation");
        check(!recovery.commit(new TutorialRun.Receipt(pending, "wheat", 3, "", 3)), "late old worker receipt rejected");
        check(!recovery.reserveGrant("kit"), "replacement preserves grant ledger");
        check(!recovery.resume(recovery.binding(), false), "readiness required after replacement");
        check(recovery.resume(recovery.binding(), true), "resume remaining scope");
        check(!recovery.activate(recovery.binding(), recovery.scope()), "resume cannot reset work budget");
        var outstanding = recovery.reserve(recovery.binding(), UUID.randomUUID(), "harvest", "crop", 1);
        check(outstanding != null && !recovery.stop(recovery.binding()), "stop waits for unresolved work reconciliation");
        check(recovery.state() == TutorialRun.State.PAUSED, "stop with unresolved work pauses new actions");
        check(TutorialInstructionText.resolve("Press {Use}; physically right-click; hold {ControlModifier}; {Unknown}",
                token -> token.equals("Use") ? "G" : token.equals("ControlModifier") ? "Command" : null)
                .equals("Press G; physically right-click; hold Command; {Unknown}"), "remapped use preserves physical right-click and unknown literals");
        check(TutorialInstructionText.resolve("{Inventory} {Attack} {Sneak}", token -> "$" + token + "\\")
                .equals("$Inventory\\ $Attack\\ $Sneak\\"), "key names with replacement metacharacters remain literal");
        var all=new TutorialRun(UUID.randomUUID(),"test:all_products",b);
        check(all.activate(b,new TutorialRun.Scope(Map.of("mine",Set.of("one","two"),"deposit",Set.of("box")),Map.of("mine",2,"deposit",64),Map.of("mine",2),"box",true)),"all-products scope");
        var first=all.reserve(b,UUID.randomUUID(),"mine","one",1);all.commit(new TutorialRun.Receipt(first,"coal",1,"",1,Map.of("coal",1,"bonus",1)));
        check(all.reserve(b,UUID.randomUUID(),"mine","one",1)==null,"same ore node cannot be mined twice under a remaining global allowance");
        var second=all.reserve(b,UUID.randomUUID(),"mine","two",1);all.commit(new TutorialRun.Receipt(second,"coal",1,"",2));
        var coal=all.reserve(b,UUID.randomUUID(),"deposit","box",2);all.commit(new TutorialRun.Receipt(coal,"coal",2,"box",3));
        check(!all.completed(),"secondary real drops must also be delivered");
        var extra=all.reserve(b,UUID.randomUUID(),"deposit","box",1);all.commit(new TutorialRun.Receipt(extra,"bonus",1,"box",4));
        check(all.completed(),"all actual products delivered");
        var n=new TutorialRun.Binding(b.player(),b.mentor(),b.worker(),b.assignment(),b.task(),b.run(),0,"nitwit",b.dimension());
        var specialist=new TutorialRun(UUID.randomUUID(),"test:nitwit",n);specialist.activate(n,new TutorialRun.Scope(Map.of("report",Set.of(n.worker().toString())),Map.of("report",1),Map.of("report",1),""));specialist.pause();
        var replacement=UUID.randomUUID();check(specialist.replaceWorker(n,replacement,replacement,UUID.randomUUID(),n.task(),n.dimension(),true),"Nitwit replacement binds the same remaining allowance");
        specialist.resume(specialist.binding(),true);
        check(specialist.reserve(specialist.binding(),UUID.randomUUID(),"report",n.worker().toString(),1)==null,"old Nitwit cannot issue replacement report");
        check(specialist.reserve(specialist.binding(),UUID.randomUUID(),"report",replacement.toString(),1)!=null,"replacement can issue the one remaining report");
        System.out.println("TutorialRun: " + checks + " policy checks passed");
        return checks;
    }
}
