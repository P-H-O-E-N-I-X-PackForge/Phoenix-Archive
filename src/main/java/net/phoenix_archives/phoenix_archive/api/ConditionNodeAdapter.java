package net.phoenix_archives.phoenix_archive.api;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ConditionNodeAdapter implements JsonSerializer<ConditionNode>, JsonDeserializer<ConditionNode> {

    private ConditionNodeAdapter() {}

    public static GsonBuilder register(GsonBuilder builder) {
        return builder.registerTypeAdapter(ConditionNode.class, new ConditionNodeAdapter());
    }

    @Override
    public JsonElement serialize(ConditionNode src, Type typeOfSrc, JsonSerializationContext ctx) {
        if (src instanceof ConditionNode.Leaf l) {
            JsonObject o = new JsonObject();
            o.addProperty("type", l.type());
            o.addProperty("value", l.value());
            return o;
        }
        if (src instanceof ConditionNode.And a) {
            JsonObject o = new JsonObject();
            o.add("and", serializeChildren(a.children(), ctx));
            return o;
        }
        if (src instanceof ConditionNode.Or or) {
            JsonObject o = new JsonObject();
            o.add("or", serializeChildren(or.children(), ctx));
            return o;
        }
        if (src instanceof ConditionNode.Not n) {
            JsonObject o = new JsonObject();
            o.add("not", serialize(n.child(), typeOfSrc, ctx));
            return o;
        }
        throw new JsonParseException("Unknown ConditionNode subtype: " + src);
    }

    private JsonArray serializeChildren(List<ConditionNode> children, JsonSerializationContext ctx) {
        JsonArray arr = new JsonArray();
        for (ConditionNode child : children) arr.add(serialize(child, ConditionNode.class, ctx));
        return arr;
    }

    @Override
    public ConditionNode deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx) {
        if (json == null || json.isJsonNull() || !json.isJsonObject()) return ConditionNode.EMPTY;
        JsonObject obj = json.getAsJsonObject();

        if (obj.has("and")) return new ConditionNode.And(deserializeChildren(obj.getAsJsonArray("and"), ctx));
        if (obj.has("or")) return new ConditionNode.Or(deserializeChildren(obj.getAsJsonArray("or"), ctx));
        if (obj.has("not")) return new ConditionNode.Not(deserialize(obj.get("not"), typeOfT, ctx));
        if (obj.has("type") && obj.has("value")) {
            return new ConditionNode.Leaf(obj.get("type").getAsString(), obj.get("value").getAsString());
        }

        List<ConditionNode> leaves = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                leaves.add(new ConditionNode.Leaf(entry.getKey(), entry.getValue().getAsString()));
            }
        }
        return leaves.isEmpty() ? ConditionNode.EMPTY : new ConditionNode.And(leaves);
    }

    private List<ConditionNode> deserializeChildren(JsonArray arr, JsonDeserializationContext ctx) {
        List<ConditionNode> out = new ArrayList<>(arr.size());
        for (JsonElement e : arr) out.add(deserialize(e, ConditionNode.class, ctx));
        return out;
    }
}
