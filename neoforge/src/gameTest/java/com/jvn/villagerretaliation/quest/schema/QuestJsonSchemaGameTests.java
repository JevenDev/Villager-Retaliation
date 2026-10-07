package com.jvn.villagerretaliation.quest.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class QuestJsonSchemaGameTests {
    private QuestJsonSchemaGameTests() {
    }

    @GameTest(template = "empty", batch = "quest_schema")
    public static void scalarTypesDoNotCoerceAuthoredValues(GameTestHelper helper) {
        for (String type : List.of("object", "array", "string", "boolean", "integer", "number", "null")) {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", type);
            List<String> types = List.of("object", "array", "string", "boolean", "integer", "number", "null");
            List<String> values = List.of("{}", "[]", "\"1\"", "true", "1", "1.5", "null");
            for (int index = 0; index < types.size(); index++) {
                boolean valid = type.equals(types.get(index)) || type.equals("number") && types.get(index).equals("integer");
                helper.assertValueEqual(QuestJsonSchema.validate(json(values.get(index)), schema).isEmpty(), valid,
                        type + " accepts only its declared JSON type for " + values.get(index));
            }
        }
        helper.assertTrue(validate("1e2", "{\"type\":\"integer\"}").isEmpty(), "integral exponent is an integer");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_schema")
    public static void constraintsRetainEscapedSourcePointers(GameTestHelper helper) {
        String schema = """
                {"type":"object","additionalProperties":false,"required":["a/b~c"],
                 "properties":{"a/b~c":{"type":"integer","minimum":1,"maximum":20}}}
                """;
        reject(helper, "{}", schema, "/a~1b~0c", "required");
        reject(helper, "{\"a/b~c\":21}", schema, "/a~1b~0c", "range");
        reject(helper, "{\"a/b~c\":0}", schema, "/a~1b~0c", "range");
        reject(helper, "{\"a/b~c\":10,\"unexpected\":true}", schema, "/unexpected", "unsupported");
        helper.assertTrue(validate("{\"a/b~c\":20}", schema).isEmpty(), "numeric bounds include their endpoints");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_schema")
    public static void textUsesJsonSchemaPatternAndUnicodeLength(GameTestHelper helper) {
        helper.assertTrue(validate("\"prefix_topic_suffix\"", "{\"type\":\"string\",\"pattern\":\"topic\"}").isEmpty(),
                "unanchored JSON Schema patterns search within the value");
        helper.assertTrue(validate("\"\uD83C\uDFB2\"", "{\"type\":\"string\",\"minLength\":1,\"maxLength\":1}").isEmpty(),
                "supplementary characters count as one Unicode code point");
        reject(helper, "\"ab\"", "{\"type\":\"string\",\"maxLength\":1}", "", "length");
        reject(helper, "\"\"", "{\"type\":\"string\",\"minLength\":1}", "", "length");
        reject(helper, "\"abc\"", "{\"type\":\"string\",\"pattern\":\"^[0-9]+$\"}", "", "match");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_schema")
    public static void arraysValidateBoundsUniqueValuesAndEachItem(GameTestHelper helper) {
        String schema = """
                {"type":"array","minItems":1,"maxItems":3,"uniqueItems":true,"items":{"type":"integer"}}
                """;
        reject(helper, "[]", schema, "", "at least");
        reject(helper, "[1,2,3,4]", schema, "", "exceeds");
        reject(helper, "[1,1]", schema, "/1", "duplicate");
        reject(helper, "[1,\"2\"]", schema, "/1", "expected integer");
        helper.assertTrue(validate("[1,2,3]", schema).isEmpty(), "ordered unique array is accepted");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_schema")
    public static void localReferencesAndTypedAdditionalPropertiesResolve(GameTestHelper helper) {
        String schema = """
                {"type":"object","additionalProperties":{"$ref":"#/$defs/die~1result~0"},
                 "$defs":{"die/result~":{"type":"integer","minimum":1,"maximum":20}}}
                """;
        helper.assertTrue(validate("{\"persuade\":20}", schema).isEmpty(), "escaped local reference resolves");
        reject(helper, "{\"persuade\":21}", schema, "/persuade", "range");
        reject(helper, "{\"persuade\":\"20\"}", schema, "/persuade", "integer");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_schema")
    public static void conditionalBranchesAndNegationDoNotLeakOtherBranchErrors(GameTestHelper helper) {
        String schema = """
                {"type":"object","if":{"required":["check"]},
                 "then":{"required":["success","failure"]},"else":{"required":["next"]},
                 "not":{"required":["check","next"]}}
                """;
        helper.assertTrue(validate("{\"next\":\"end\"}", schema).isEmpty(), "direct transition has no check requirements");
        helper.assertTrue(validate("{\"check\":\"appeal\",\"success\":\"yes\",\"failure\":\"no\"}", schema).isEmpty(),
                "check transition has no direct destination requirement");
        reject(helper, "{\"check\":\"appeal\",\"success\":\"yes\"}", schema, "/failure", "required");
        reject(helper, "{\"check\":\"appeal\",\"next\":\"end\"}", schema, "", "combination");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_schema")
    public static void alternativesAndCombinedRulesRequireActualMatches(GameTestHelper helper) {
        String one = "{\"oneOf\":[{\"required\":[\"next\"]},{\"required\":[\"outcome\"]}]}";
        helper.assertTrue(validate("{\"next\":\"end\"}", one).isEmpty(), "one matching alternative is accepted");
        reject(helper, "{\"next\":\"end\",\"outcome\":\"success\"}", one, "", "exactly one");
        reject(helper, "{}", one, "/next", "required");
        String any = "{\"anyOf\":[{\"const\":1},{\"enum\":[1,2]}]}";
        helper.assertTrue(validate("1", any).isEmpty(), "anyOf accepts more than one match");
        reject(helper, "3", any, "", "unsupported");
        String all = "{\"allOf\":[{\"type\":\"integer\",\"minimum\":1},{\"maximum\":20}]}";
        reject(helper, "0", all, "", "range");
        reject(helper, "21", all, "", "range");
        helper.assertTrue(validate("10", all).isEmpty(), "allOf checks every rule");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_schema")
    public static void recursiveConditionsRetainTheirDeepSourcePointer(GameTestHelper helper) {
        String schema = """
                {"$ref":"#/$defs/condition","$defs":{"condition":{"type":"object",
                 "if":{"required":["not"]},"then":{"properties":{"not":{"$ref":"#/$defs/condition"}}},
                 "else":{"required":["value"],"properties":{"value":{"type":"boolean"}}}}}}
                """;
        helper.assertTrue(validate("{\"not\":{\"not\":{\"value\":true}}}", schema).isEmpty(), "recursive condition resolves");
        reject(helper, "{\"not\":{\"not\":{\"value\":1}}}", schema, "/not/not/value", "boolean");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_schema")
    public static void validationLimitsCannotTurnFailedConditionsIntoSuccess(GameTestHelper helper) {
        String recursive = """
                {"if":{"$ref":"#/$defs/loop"},"then":false,"else":true,
                 "$defs":{"loop":{"$ref":"#/$defs/loop"}}}
                """;
        reject(helper, "{}", recursive, "", "nesting");
        String negated = "{\"not\":{\"$ref\":\"#/$defs/loop\"},\"$defs\":{\"loop\":{\"$ref\":\"#/$defs/loop\"}}}";
        reject(helper, "{}", negated, "", "nesting");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_schema")
    public static void diagnosticsAndValidationWorkRemainBounded(GameTestHelper helper) {
        JsonArray input = new JsonArray();
        for (int index = 0; index < 1000; index++) input.add("bad");
        var issues = QuestJsonSchema.validate(input, json("{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}").getAsJsonObject());
        helper.assertValueEqual(issues.size(), 128, "large invalid content does not produce unbounded diagnostics");
        JsonArray large = new JsonArray();
        for (int index = 0; index < 100_010; index++) large.add(index);
        issues = QuestJsonSchema.validate(large, json("{\"items\":true}").getAsJsonObject());
        helper.assertTrue(issues.size() == 1 && issues.getFirst().message().contains("work limit"), "large valid input also has a work budget");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_schema")
    public static void unsupportedSchemaRulesAndMissingReferencesAreNeverIgnored(GameTestHelper helper) {
        for (String schema : List.of("{\"format\":\"uuid\"}", "{\"$ref\":\"https://example.invalid/schema\"}",
                "{\"$ref\":\"#/$defs/missing\"}", "{\"if\":false,\"then\":{\"unknown\":true}}")) {
            boolean rejected = false;
            try {
                validate("{}", schema);
            } catch (IllegalArgumentException exception) {
                rejected = true;
            }
            helper.assertTrue(rejected, "unsupported schema rejects before data evaluation: " + schema);
        }
        helper.succeed();
    }

    private static void reject(GameTestHelper helper, String input, String schema, String pointer, String message) {
        var issues = validate(input, schema);
        helper.assertTrue(issues.stream().anyMatch(issue -> issue.pointer().equals(pointer) && issue.message().contains(message)),
                "diagnostic at " + pointer + " contains " + message + ": " + issues);
    }

    private static List<QuestJsonSchema.Issue> validate(String input, String schema) {
        return QuestJsonSchema.validate(json(input), json(schema).getAsJsonObject());
    }

    private static JsonElement json(String value) {
        return JsonParser.parseString(value);
    }
}
