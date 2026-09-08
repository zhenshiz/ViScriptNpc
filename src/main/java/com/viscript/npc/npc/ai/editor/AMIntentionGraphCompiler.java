package com.viscript.npc.npc.ai.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.IPort;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import net.minecraft.resources.ResourceLocation;
import org.thexeler.AttentionMind;
import org.thexeler.api.IntentionDiagnostic;
import org.thexeler.api.asset.AssetCompiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AMIntentionGraphCompiler {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private AMIntentionGraphCompiler() {
    }

    public static Compilation compile(AMIntentionGraph graph) {
        try {
            List<? extends INode> definitions = graph.getNodes().stream()
                    .filter(AMIntentionNodes.Definition.class::isInstance).toList();
            if (definitions.size() != 1) {
                return Compilation.failure("AM intention graph must contain exactly one Definition node");
            }
            INode definition = definitions.getFirst();
            ResourceLocation assetId = ResourceLocation.parse(stringOption(definition, "asset_id", ""));
            INode entry = requiredConnected(definition, "entry");

            JsonObject root = new JsonObject();
            root.addProperty("format_version", AssetCompiler.FORMAT_VERSION);
            JsonObject metadata = new JsonObject();
            metadata.addProperty("name", stringOption(definition, "name", ""));
            metadata.addProperty("description", stringOption(definition, "description", ""));
            metadata.addProperty("category", stringOption(definition, "category", ""));
            root.add("metadata", metadata);
            root.add("parameters", jsonObjectOption(definition, "parameters_json"));
            root.addProperty("entry", nodeId(entry));

            JsonObject nodes = new JsonObject();
            for (INode node : graph.getNodes()) {
                if (node instanceof AMIntentionNodes.Definition) continue;
                nodes.add(nodeId(node), compileNode(node));
            }
            root.add("nodes", nodes);

            Map<ResourceLocation, JsonElement> sources = new LinkedHashMap<>();
            AttentionMind.intentionAssets().generation().assets().forEach((id, asset) -> {
                if (!id.equals(assetId)) sources.put(id, JsonParser.parseString(asset.canonicalJson()));
            });
            sources.put(assetId, root);
            var result = new AssetCompiler(AttentionMind.intentionTypes(), AttentionMind.expressionQueries())
                    .compile(sources);
            if (!result.isSuccess()) {
                return new Compilation(assetId, "", diagnostics(result.diagnostics()));
            }
            return new Compilation(assetId, GSON.toJson(root), List.of());
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            return Compilation.failure(message);
        }
    }

    private static JsonObject compileNode(INode node) {
        JsonObject value = new JsonObject();
        if (node instanceof AMIntentionNodes.Invoke) {
            value.addProperty("type", "invoke");
            value.addProperty("intention", stringOption(node, "intention", ""));
            value.add("arguments", jsonObjectOption(node, "arguments_json"));
            int timeout = intOption(node, "timeout_ticks", 0);
            if (timeout > 0) value.addProperty("timeout_ticks", timeout);
            String guard = stringOption(node, "guard_json", "").trim();
            if (!guard.isEmpty()) value.add("guard", JsonParser.parseString(guard));
            value.addProperty("on_success", requiredConnectedId(node, "success"));
            value.addProperty("on_failure", requiredConnectedId(node, "failure"));
        } else if (node instanceof AMIntentionNodes.Condition) {
            value.addProperty("type", "condition");
            value.add("condition", jsonOption(node, "condition_json"));
            value.addProperty("on_true", requiredConnectedId(node, "true"));
            value.addProperty("on_false", requiredConnectedId(node, "false"));
        } else if (node instanceof AMIntentionNodes.Delay) {
            value.addProperty("type", "delay");
            value.addProperty("ticks", intOption(node, "ticks", 0));
            value.addProperty("next", requiredConnectedId(node, "next"));
        } else if (node instanceof AMIntentionNodes.SetLocal) {
            value.addProperty("type", "set");
            value.addProperty("name", stringOption(node, "name", ""));
            value.add("value", jsonOption(node, "value_json"));
            value.addProperty("next", requiredConnectedId(node, "next"));
        } else if (node instanceof AMIntentionNodes.Loop) {
            value.addProperty("type", "loop");
            value.addProperty("body", requiredConnectedId(node, "body"));
            value.addProperty("on_complete", requiredConnectedId(node, "complete"));
            value.addProperty("max_iterations", intOption(node, "max_iterations", 1));
        } else if (node instanceof AMIntentionNodes.Retry) {
            value.addProperty("type", "retry");
            value.addProperty("target", requiredConnectedId(node, "target"));
            value.addProperty("on_success", requiredConnectedId(node, "success"));
            value.addProperty("on_failure", requiredConnectedId(node, "failure"));
            value.addProperty("max_attempts", intOption(node, "max_attempts", 1));
            value.addProperty("delay_ticks", intOption(node, "delay_ticks", 0));
        } else if (node instanceof AMIntentionNodes.Complete) {
            value.addProperty("type", "complete");
        } else if (node instanceof AMIntentionNodes.Fail) {
            value.addProperty("type", "fail");
            value.addProperty("code", stringOption(node, "code", "INTENTION_FAILED"));
        } else {
            throw new IllegalArgumentException("Unsupported AM graph node " + node.getClass().getName());
        }
        return value;
    }

    private static INode requiredConnected(INode node, String portId) {
        IPort output = node.getOutputPortById(portId);
        if (output == null) throw new IllegalArgumentException("Missing output port '" + portId + "'");
        List<IPort> ports = new ArrayList<>();
        output.getConnectedPorts(ports);
        if (ports.size() != 1) {
            throw new IllegalArgumentException(node.getClass().getSimpleName()
                    + " output '" + portId + "' must have exactly one connection");
        }
        IPort port = ports.getFirst();
        if (port instanceof PortModel model && model.getNodeModel() instanceof ICustomNodeModel custom
                && custom.getNode() != null) {
            return custom.getNode();
        }
        throw new IllegalArgumentException("Output '" + portId + "' is connected to an invalid node");
    }

    private static String requiredConnectedId(INode node, String portId) {
        return nodeId(requiredConnected(node, portId));
    }

    private static String nodeId(INode node) {
        UUID id = node.getNodeModel().getUid();
        return "n_" + id.toString().replace("-", "");
    }

    private static String stringOption(INode node, String id, String fallback) {
        INodeOption option = node.getNodeOptionById(id);
        return option == null ? fallback : option.<String>tryGetValue(String.class).result().orElse(fallback);
    }

    private static int intOption(INode node, String id, int fallback) {
        INodeOption option = node.getNodeOptionById(id);
        return option == null ? fallback : option.<Integer>tryGetValue(Integer.class).result().orElse(fallback);
    }

    private static JsonElement jsonOption(INode node, String id) {
        String source = stringOption(node, id, "").trim();
        if (source.isEmpty()) throw new IllegalArgumentException("Option '" + id + "' requires JSON");
        return JsonParser.parseString(source);
    }

    private static JsonObject jsonObjectOption(INode node, String id) {
        JsonElement value = jsonOption(node, id);
        if (!value.isJsonObject()) throw new IllegalArgumentException("Option '" + id + "' must be a JSON object");
        return value.getAsJsonObject();
    }

    private static List<String> diagnostics(List<IntentionDiagnostic> diagnostics) {
        return diagnostics.stream().map(value -> value.stableCode() + ": " + value.message()
                + (value.path().isEmpty() ? "" : " [" + value.path() + "]")).toList();
    }

    public record Compilation(ResourceLocation assetId, String json, List<String> errors) {
        public Compilation {
            errors = List.copyOf(errors);
        }

        public static Compilation failure(String message) {
            return new Compilation(null, "", List.of(message));
        }

        public boolean isSuccess() {
            return assetId != null && !json.isBlank() && errors.isEmpty();
        }

        public String requireJson() {
            if (!isSuccess()) throw new IllegalStateException(String.join("\n", errors));
            return json;
        }
    }
}
