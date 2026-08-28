package network.protocol;

import org.json.JSONObject;

import java.util.Objects;

public class LanEnvelope {

    private final LanMessageType type;
    private final JSONObject payload;

    public LanEnvelope(LanMessageType type, JSONObject payload) {
        this.type = Objects.requireNonNull(type, "type is required");
        this.payload = payload != null ? payload : new JSONObject();
    }

    public LanMessageType getType() {
        return type;
    }

    public JSONObject getPayload() {
        return payload;
    }

    public JSONObject toJson() {
        JSONObject root = new JSONObject();
        root.put("type", type.name());
        root.put("payload", payload);
        return root;
    }

    public static LanEnvelope fromJson(JSONObject root) {
        if (root == null) {
            throw new IllegalArgumentException("envelope json is required");
        }
        String typeName = root.getString("type");
        LanMessageType type = LanMessageType.valueOf(typeName);
        JSONObject payload = root.optJSONObject("payload");
        return new LanEnvelope(type, payload != null ? payload : new JSONObject());
    }
}
