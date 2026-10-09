package com.jvn.villagerretaliation.campaign;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.campaign.content.CampaignScenario;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSavedData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder("campaign_scenarios")
public final class CampaignScenarioGameTests {
    private CampaignScenarioGameTests() { }

    @GameTestGenerator
    public static Collection<TestFunction> scenarios() throws IOException {
        net.minecraft.gametest.framework.StructureUtils.testStructuresDir = System.getProperty("villagerretaliation.gameteststructures");
        String source = System.getProperty("villagerretaliation.campaignScenarioPack", "");
        CampaignScenarioPack.require(!source.isBlank(), "Campaign scenario pack path is missing");
        var pack = CampaignScenarioPack.read(Path.of(source));
        String selected = System.getProperty("villagerretaliation.campaignScenario", "");
        var scenarios = pack.scenarios.stream().filter(value -> selected.isBlank() || value.id().toString().equals(selected)).toList();
        CampaignScenarioPack.require(!scenarios.isEmpty(), "No campaign scenario matches " + selected);
        int repeats = Integer.parseInt(System.getProperty("villagerretaliation.campaignScenarioRepeats", "1"));
        CampaignScenarioPack.require(repeats >= 1 && repeats <= 10, "Campaign scenario repeats must be between 1 and 10");
        var tests = new ArrayList<TestFunction>();
        for (var scenario : scenarios) for (int index = 1; index <= repeats; index++) {
            int repetition = index;
            String name = "campaignscenario." + scenario.id().getNamespace() + "/" + scenario.id().getPath() + "." + index;
            tests.add(new TestFunction(name, name, "campaign_scenarios:empty", 6600, 0L, true,
                    helper -> new Run(helper, pack, scenario, repetition).start()));
        }
        return List.copyOf(tests);
    }

    private static final class Run {
        private final GameTestHelper helper;
        private final CampaignScenarioPack pack;
        private final CampaignScenario scenario;
        private final int repetition;
        private final JsonArray trace = new JsonArray();
        private final long started = System.currentTimeMillis();
        private CampaignScenarioFixture fixture;
        private CampaignScenarioExecution execution;
        private int next;
        private boolean finished;

        Run(GameTestHelper helper, CampaignScenarioPack pack, CampaignScenario scenario, int repetition) {
            this.helper = helper;
            this.pack = pack;
            this.scenario = scenario;
            this.repetition = repetition;
        }

        void start() {
            helper.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo test) { }
                @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { if (!finished) finish("Scenario completed without executing all steps", false); }
                @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { if (!finished) finish("GameTest ended before the scenario completed: " + test.getError(), false); }
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) { }
            });
            try {
                fixture = CampaignScenarioFixture.open(helper, scenario, pack);
                execution = new CampaignScenarioExecution(fixture);
            } catch (IOException | RuntimeException exception) {
                finish(exception.toString(), true);
                return;
            }
            advance();
        }

        private void advance() {
            if (finished) return;
            String failure = "";
            try {
                while (next < scenario.steps().size()) {
                    var step = scenario.steps().get(next++);
                    var row = new JsonObject();
                    row.addProperty("id", step.id());
                    row.addProperty("pointer", step.pointer());
                    trace.add(row);
                    if (step instanceof CampaignScenario.Wait wait) {
                        row.addProperty("wait_ticks", wait.ticks());
                        helper.runAfterDelay(wait.ticks(), () -> { row.addProperty("passed", true); advance(); });
                        return;
                    }
                    execution.execute(step);
                    row.addProperty("passed", true);
                    if (!execution.lastDiagnostic.isBlank()) row.addProperty("diagnostic", execution.lastDiagnostic);
                }
            } catch (IOException | RuntimeException exception) {
                failure = scenario.source() + "#" + scenario.steps().get(next - 1).pointer() + ": " + exception;
                trace.get(trace.size() - 1).getAsJsonObject().addProperty("passed", false);
            }
            finish(failure, true);
        }

        private void finish(String failure, boolean signal) {
            if (finished) return;
            finished = true;
            var report = new JsonObject();
            report.addProperty("schema", "villagerretaliation:campaign_scenario_report/v1");
            report.addProperty("scenario", scenario.id().toString());
            report.addProperty("source", scenario.source().toString());
            report.addProperty("pack", pack.root.toString());
            report.addProperty("repetition", repetition);
            report.addProperty("elapsed_ms", System.currentTimeMillis() - started);
            report.add("steps", trace);
            if (fixture != null) {
                try {
                    var state = new JsonObject();
                    state.addProperty("campaign_instance", fixture.instance().instanceId().toString());
                    var outcomes = new JsonObject();
                    fixture.instance().outcomes().forEach((id, value) -> {
                        var outcome = new JsonObject();
                        outcome.addProperty("outcome", value.outcome());
                        outcome.addProperty("operation", value.operationId().toString());
                        outcome.addProperty("origin", value.origin().name());
                        outcomes.add(id, outcome);
                    });
                    state.add("outcomes", outcomes);
                    var conversations = new JsonObject();
                    ConversationSavedData.get(helper.getLevel()).snapshots().forEach((id, value) -> {
                        var session = new JsonObject();
                        session.addProperty("conversation", value.identity().conversation().toString());
                        session.addProperty("revision", value.revision());
                        session.addProperty("node", value.node());
                        session.addProperty("phase", value.phase().name());
                        session.addProperty("choices", value.choices().size());
                        session.addProperty("effect_receipts", value.receipts().size());
                        var checks = new JsonObject();
                        value.checks().forEach((key, check) -> {
                            var roll = new JsonObject();
                            roll.addProperty("request", check.request().toString());
                            roll.addProperty("die", check.die());
                            roll.addProperty("difficulty", check.difficulty());
                            roll.addProperty("success", check.success());
                            checks.add(key, roll);
                        });
                        session.add("checks", checks);
                        conversations.add(id.toString(), session);
                    });
                    state.add("conversations", conversations);
                    report.add("state", state);
                } catch (RuntimeException exception) {
                    failure += " Scenario inspection failed: " + exception;
                } finally {
                    try { fixture.close(); }
                    catch (IOException | RuntimeException exception) { failure += " Scenario cleanup failed: " + exception; }
                }
            }
            report.addProperty("passed", failure.isBlank());
            report.addProperty("diagnostic", failure);
            try {
                String directory = System.getProperty("villagerretaliation.campaignScenarioReports", "");
                CampaignScenarioPack.require(!directory.isBlank(), "Scenario report directory is missing");
                Path root = Path.of(directory).toAbsolutePath().normalize();
                Path file = root.resolve(scenario.id().getNamespace()).resolve(scenario.id().getPath() + "-" + repetition + ".json").normalize();
                CampaignScenarioPack.require(file.startsWith(root), "Scenario report left its output directory");
                Files.createDirectories(file.getParent());
                Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report) + System.lineSeparator());
            } catch (IOException | RuntimeException exception) { failure += " Scenario report failed: " + exception; }
            if (signal) {
                if (failure.isBlank()) helper.succeed();
                else helper.fail(failure);
            }
        }
    }
}
