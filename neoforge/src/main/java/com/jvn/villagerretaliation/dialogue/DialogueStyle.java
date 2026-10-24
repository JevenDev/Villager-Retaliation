package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import java.util.List;

public record DialogueStyle(DialogueTextEffects effects, int overrides, Double speed,
        List<DialogueCue> onEnter, List<DialogueCue> onExit, boolean restorePose) {
    public static final int MAX_STYLE_CUES = 16;
    private static final List<String> FLAGS = List.of("italic", "bold", "underlined", "strikethrough", "obfuscated",
            "wavy", "shake", "pulse", "jump", "rainbow", "color", "gradient_start", "gradient_end");

    public DialogueStyle {
        java.util.Objects.requireNonNull(effects);
        onEnter = validateCues(onEnter);
        onExit = validateCues(onExit);
        if (overrides < 0 || overrides >= 1 << FLAGS.size()
                || speed != null && (!Double.isFinite(speed) || speed < 0.1 || speed > 10))
            throw new IllegalArgumentException("Invalid dialogue style");
    }

    public DialogueStyle(DialogueTextEffects effects, int overrides, Double speed) {
        this(effects, overrides, speed, List.of(), List.of(), false);
    }

    private static List<DialogueCue> validateCues(List<DialogueCue> cues) {
        cues = List.copyOf(cues);
        if (cues.size() > MAX_STYLE_CUES || cues.stream().anyMatch(cue -> cue.kind() == DialogueCue.Kind.SPEED
                || cue.kind() == DialogueCue.Kind.PUSH_POSE || cue.kind() == DialogueCue.Kind.POP_POSE
                || cue.particle() != null && cue.particle().toString().length() > 512
                || cue.sound() != null && cue.sound().id().toString().length() > 512))
            throw new IllegalArgumentException("Invalid dialogue style cues");
        return cues;
    }

    private static List<DialogueCue> readCues(JsonObject root, String field) {
        if (!root.has(field)) return List.of();
        var cues = new java.util.ArrayList<DialogueCue>();
        for (var value : root.getAsJsonArray(field)) {
            var cue = DialogueCue.parse(value.getAsString());
            if (cue == null) throw new IllegalArgumentException("Invalid dialogue style cue in " + field + ": " + value);
            cues.add(cue);
        }
        return cues;
    }

    public DialogueTextEffects apply(DialogueTextEffects base) {
        return new DialogueTextEffects(flag(0, base.italic(), effects.italic()), flag(1, base.bold(), effects.bold()),
                flag(2, base.underlined(), effects.underlined()), flag(3, base.strikethrough(), effects.strikethrough()),
                flag(4, base.obfuscated(), effects.obfuscated()), flag(5, base.wavy(), effects.wavy()),
                flag(6, base.shake(), effects.shake()), flag(7, base.pulse(), effects.pulse()),
                flag(8, base.jump(), effects.jump()), flag(9, base.rainbow(), effects.rainbow()),
                has(10) ? effects.color() : base.color(), has(11) ? effects.gradientStartColor() : base.gradientStartColor(),
                has(12) ? effects.gradientEndColor() : base.gradientEndColor(),
                has(5) ? effects.waveAnimation() : base.waveAnimation(), has(6) ? effects.shakeAnimation() : base.shakeAnimation(),
                has(7) ? effects.pulseAnimation() : base.pulseAnimation(), has(8) ? effects.jumpAnimation() : base.jumpAnimation());
    }

    private boolean has(int index) { return (overrides & 1 << index) != 0; }
    private boolean flag(int index, boolean inherited, boolean value) { return has(index) ? value : inherited; }

    public static boolean validTag(String tag) {
        return tag != null && tag.matches("[a-z][a-z0-9_]{0,63}") && !DialogueCue.isTag(tag)
                && !List.of("color", "gradient").contains(tag) && !DialogueTextEffects.fromTag(tag).active();
    }

    public static DialogueStyle read(JsonObject root) {
        var issues = QuestJsonSchema.validate(root, schema());
        if (!issues.isEmpty()) throw new IllegalArgumentException("Invalid dialogue style: " + issues);
        if (!validTag(root.get("tag").getAsString())) throw new IllegalArgumentException("Style tag is reserved or invalid");
        JsonObject effects = root.has("text_effects") ? root.getAsJsonObject("text_effects") : new JsonObject();
        int overrides = 0;
        for (int index = 0; index < FLAGS.size(); index++) if (effects.has(FLAGS.get(index))) overrides |= 1 << index;
        for (String field : FLAGS.subList(10, 13)) if (effects.has(field) && !effects.get(field).isJsonNull()) {
            var value = effects.get(field);
            if (value.getAsJsonPrimitive().isString() && DialogueTextEffects.parseColor(value.getAsString()) == null)
                throw new IllegalArgumentException("Invalid style color: " + field);
        }
        if (effects.has("gradient_start") != effects.has("gradient_end")
                || effects.has("gradient_start") && effects.get("gradient_start").isJsonNull() != effects.get("gradient_end").isJsonNull())
            throw new IllegalArgumentException("Set or clear both gradient colors together");
        return new DialogueStyle(DialogueTextEffects.read(root), overrides, root.has("speed") ? root.get("speed").getAsDouble() : null,
                readCues(root, "on_enter"), readCues(root, "on_exit"), root.has("restore_pose") ? root.get("restore_pose").getAsBoolean()
                        : root.has("on_enter") || root.has("on_exit"));
    }

    public static JsonObject schema() {
        var schema = JsonParser.parseString("""
                {"type":"object","additionalProperties":false,"required":["tag"],"properties":{
                  "tag":{"type":"string","pattern":"^[a-z][a-z0-9_]{0,63}$"},
                  "speed":{"type":"number","minimum":0.1,"maximum":10},
                  "restore_pose":{"type":"boolean"},
                  "on_enter":{"type":"array","maxItems":16,"items":{"type":"string","minLength":1,"maxLength":512}},
                  "on_exit":{"type":"array","maxItems":16,"items":{"type":"string","minLength":1,"maxLength":512}},
                  "text_effects":{"type":"object","additionalProperties":false,"properties":{}}
                }}
                """).getAsJsonObject();
        var properties = schema.getAsJsonObject("properties").getAsJsonObject("text_effects").getAsJsonObject("properties");
        for (String flag : FLAGS.subList(0, 10)) properties.add(flag, JsonParser.parseString("{\"type\":\"boolean\"}"));
        for (String flag : FLAGS.subList(5, 9)) {
            var options = new com.google.gson.JsonArray();
            options.add(JsonParser.parseString("{\"type\":\"boolean\"}"));
            options.add(com.jvn.villagerretaliation.dialogue.normal.DialogueTextAnimation.schema());
            var rule = new JsonObject(); rule.add("anyOf", options); properties.add(flag, rule);
        }
        for (String flag : FLAGS.subList(10, 13)) properties.add(flag, JsonParser.parseString("""
                {"anyOf":[{"type":"null"},{"type":"integer","minimum":0,"maximum":16777215},{"type":"string","minLength":1,"maxLength":32}]}
                """));
        return schema;
    }
}
