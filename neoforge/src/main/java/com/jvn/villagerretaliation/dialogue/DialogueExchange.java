package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record DialogueExchange(ConversationDefinition.SpeakerKind speaker, String text, String textKey, com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Camera camera) {
    public DialogueExchange(ConversationDefinition.SpeakerKind speaker, String text, String textKey) { this(speaker, text, textKey, null); }
    public static JsonObject schema() {
        var schema = JsonParser.parseString("""
                {"type":"array","minItems":1,"maxItems":64,"items":{
                  "type":"object","additionalProperties":false,"required":["speaker","text"],"properties":{
                    "id":{"type":"string","minLength":1,"maxLength":256},
                    "speaker":{"type":"object","additionalProperties":false,"required":["type"],"properties":{
                      "type":{"enum":["role","player","narrator"]},"role":{"const":"provider"}
                    },"if":{"properties":{"type":{"const":"role"}}},"then":{"required":["role"]},
                    "else":{"not":{"required":["role"]}}},
                    "text":{"oneOf":[{"type":"string","minLength":1,"maxLength":512},
                      {"type":"object","additionalProperties":false,"required":["key"],"properties":{
                        "key":{"type":"string","minLength":1,"maxLength":256}}}]},
                    "text_key":{"type":"string","minLength":1,"maxLength":256}
                  }}}
                """).getAsJsonObject();
        schema.getAsJsonObject("items").getAsJsonObject("properties").add("camera", DialogueCamera.schema());
        return schema;
    }

    public static List<DialogueExchange> read(JsonObject data) {
        if (!data.has("exchanges")) return List.of();
        var issues = QuestJsonSchema.validate(data.get("exchanges"), schema());
        if (!issues.isEmpty()) throw new IllegalArgumentException("Invalid dialogue exchanges: " + issues);
        var result = new ArrayList<DialogueExchange>();
        for (var raw : data.getAsJsonArray("exchanges")) {
            var value = raw.getAsJsonObject();
            var kind = ConversationDefinition.SpeakerKind.valueOf(value.getAsJsonObject("speaker").get("type").getAsString().toUpperCase(Locale.ROOT));
            var text = value.get("text");
            String key = value.has("text_key") ? value.get("text_key").getAsString()
                    : text.isJsonObject() ? text.getAsJsonObject().get("key").getAsString() : "";
            result.add(new DialogueExchange(kind, text.isJsonPrimitive() ? text.getAsString() : "", key, value.has("camera") ? DialogueCamera.read(value) : DialogueCamera.read(data)));
        }
        return List.copyOf(result);
    }

    public DialogueSpeech resolve(DialogueRuntimeContext context, Map<String, String> replacements) {
        var entity = speaker == ConversationDefinition.SpeakerKind.PLAYER ? context.player()
                : speaker == ConversationDefinition.SpeakerKind.ROLE ? context.speaker() : null;
        String line = textKey.isBlank() ? text : context.message(textKey).orElse(text);
        line = DialogueTemplates.resolveTemplate(line, DialoguePlaceholders.merge(DialoguePlaceholders.base(context), replacements), context);
        return new DialogueSpeech(entity == null ? null : entity.getUUID(), entity == null ? -1 : entity.getId(),
                entity == null ? "" : context.displayName(entity), DialogueTextSegment.parse(line, DialogueTextEffects.NONE), camera);
    }

    public static List<DialogueSpeech> resolve(List<DialogueExchange> exchanges, DialogueRuntimeContext context, Map<String, String> replacements) {
        return exchanges.stream().map(exchange -> exchange.resolve(context, replacements)).filter(line -> !line.text().isBlank()).toList();
    }
}
