package com.viscript.npc.npc.ai.editor;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.IPort;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.thexeler.AttentionMind;
import org.thexeler.api.IntentionPriority;
import org.thexeler.api.parameter.ParameterSchema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Compiles the editor graph into the server-side sequential flow format. */
public final class NpcFlowGraphCompiler {
    private NpcFlowGraphCompiler() {
    }

    public static CompoundTag compile(NpcFlowGraph graph) {
        CompoundTag result = new CompoundTag();
        result.putInt("version", 1);
        ListTag rules = new ListTag();
        ListTag errors = new ListTag();

        try {
            result.put("variables", compileVariables(graph));
        } catch (CompileException exception) {
            errors.add(StringTag.valueOf(exception.getMessage()));
        }

        for (INode node : graph.getNodes()) {
            if (!(node instanceof FlowNodes.Trigger trigger)) continue;
            try {
                CompoundTag rule = new CompoundTag();
                rule.putUUID("id", uid(trigger));
                rule.putString("trigger", stringOption(trigger, "trigger", "tick"));
                CompoundTag options = options(trigger, Set.of("trigger", "parameters_json"));
                options.put("parameters", jsonObjectTag(stringOption(trigger, "parameters_json", "{}")));
                rule.put("options", options);
                List<INode> roots = connected(trigger, FlowNode.PORT_CHILD);
                if (roots.size() > 1) {
                    throw new CompileException("Trigger nodes may only have one flow child");
                }
                rule.put("root", roots.isEmpty()
                        ? nodeTag(uid(trigger), "end", new CompoundTag(), List.of())
                        : compileNode(roots.getFirst(), new HashSet<>()));
                rules.add(rule);
            } catch (CompileException exception) {
                errors.add(StringTag.valueOf(exception.getMessage()));
            }
        }
        if (!errors.isEmpty()) {
            // Invalid updates are fail-closed: diagnostics remain visible, but no partial rules execute.
            rules.clear();
            result.put("errors", errors);
        }
        result.put("rules", rules);
        return result;
    }

    private static CompoundTag compileNode(INode node, Set<UUID> path) {
        UUID id = uid(node);
        if (!path.add(id)) throw new CompileException("Flow graph contains a cycle at " + id);
        try {
            String type;
            String output = null;
            Set<String> excluded = Set.of();
            if (node instanceof FlowNodes.Sequence) {
                type = "sequence";
                output = FlowNode.PORT_CHILDREN;
            } else if (node instanceof FlowNodes.Branch) {
                type = "branch";
                output = FlowNode.PORT_CHILDREN;
            } else if (node instanceof FlowNodes.Case) {
                // A case is a guarded sequential branch. Branch selection evaluates its options.
                type = "sequence";
                output = FlowNode.PORT_CHILDREN;
                excluded = Set.of("parameters_json");
            } else if (node instanceof FlowNodes.Delay) {
                type = "delay";
            } else if (node instanceof FlowNodes.Loop) {
                type = "loop";
                output = FlowNode.PORT_CHILD;
            } else if (node instanceof FlowNodes.Retry) {
                type = "retry";
                output = FlowNode.PORT_CHILD;
            } else if (node instanceof FlowNodes.SetVariable) {
                type = "set_variable";
                excluded = Set.of("value_json");
            } else if (node instanceof FlowNodes.Submit) {
                type = "submit";
                excluded = Set.of("parameters_json");
            } else if (node instanceof FlowNodes.End) {
                type = "end";
            } else if (node instanceof FlowNodes.Fail) {
                type = "fail";
            } else {
                throw new CompileException("Unsupported flow node " + node.getClass().getName());
            }

            List<CompoundTag> children = new ArrayList<>();
            if (output != null) {
                List<INode> connected = connected(node, output);
                if (FlowNode.PORT_CHILD.equals(output) && connected.size() > 1) {
                    throw new CompileException(node.getClass().getSimpleName() + " may only have one flow child");
                }
                if (node instanceof FlowNodes.Branch
                        && connected.stream().anyMatch(child -> !(child instanceof FlowNodes.Case))) {
                    throw new CompileException("Branch children must be Case nodes");
                }
                for (INode child : connected) children.add(compileNode(child, path));
            }
            CompoundTag compiledOptions = options(node, excluded);
            if (node instanceof FlowNodes.Case) {
                compiledOptions.put("parameters", jsonObjectTag(stringOption(node, "parameters_json", "{}")));
            }
            if (node instanceof FlowNodes.SetVariable) {
                compiledOptions.put("value", jsonTag(stringOption(node, "value_json", "null")));
            } else if (node instanceof FlowNodes.Submit) {
                CompoundTag parameters = jsonObjectTag(stringOption(node, "parameters_json", "{}"));
                validateSubmission(node, parameters);
                compiledOptions.put("parameters", parameters);
            }
            return nodeTag(id, type, compiledOptions, children);
        } finally {
            path.remove(id);
        }
    }

    private static CompoundTag nodeTag(UUID id, String type, CompoundTag options, List<CompoundTag> children) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("type", type);
        tag.put("options", options);
        ListTag childTags = new ListTag();
        childTags.addAll(children);
        tag.put("children", childTags);
        return tag;
    }

    private static List<INode> connected(INode node, String portId) {
        IPort output = node.getOutputPortById(portId);
        if (output == null) return List.of();
        List<IPort> ports = new ArrayList<>();
        output.getConnectedPorts(ports);
        List<INode> nodes = new ArrayList<>();
        for (IPort port : ports) {
            if (port instanceof PortModel model && model.getNodeModel() instanceof ICustomNodeModel custom
                    && custom.getNode() != null) {
                nodes.add(custom.getNode());
            }
        }
        return nodes;
    }

    private static CompoundTag options(INode node, Set<String> excluded) {
        CompoundTag tag = new CompoundTag();
        var nodeOptions = node.getNodeOptions();
        if (nodeOptions == null) return tag;
        for (INodeOption option : nodeOptions) {
            if (excluded.contains(option.getId())) continue;
            option.<String>tryGetValue(String.class).result().ifPresent(value -> tag.putString(option.getId(), value));
            option.<Integer>tryGetValue(Integer.class).result().ifPresent(value -> tag.putInt(option.getId(), value));
            option.<Float>tryGetValue(Float.class).result().ifPresent(value -> tag.putFloat(option.getId(), value));
            option.<Double>tryGetValue(Double.class).result().ifPresent(value -> tag.putDouble(option.getId(), value));
            option.<Boolean>tryGetValue(Boolean.class).result().ifPresent(value -> tag.putBoolean(option.getId(), value));
        }
        return tag;
    }

    private static CompoundTag compileVariables(NpcFlowGraph graph) {
        CompoundTag variables = new CompoundTag();
        for (INode node : graph.getNodes()) {
            if (!(node instanceof FlowNodes.Variable)) continue;
            String name = stringOption(node, "name", "").trim();
            if (name.isEmpty()) throw new CompileException("Variable name must not be empty");
            if (variables.contains(name)) throw new CompileException("Duplicate variable declaration '" + name + "'");
            String type = stringOption(node, "type", "string");
            if (!Set.of("boolean", "integer", "long", "number", "string", "resource_location").contains(type)) {
                throw new CompileException("Unsupported persistent variable type '" + type + "'");
            }
            CompoundTag declaration = new CompoundTag();
            declaration.putString("type", type);
            INodeOption persistent = node.getNodeOptionById("persistent");
            declaration.putBoolean("persistent", persistent != null
                    && persistent.<Boolean>tryGetValue(Boolean.class).result().orElse(false));
            JsonElement defaultValue = parseJson(stringOption(node, "default_json", "null"));
            if (!defaultValue.isJsonNull()) {
                Tag defaultTag = toTag(defaultValue);
                if ("long".equals(type) && defaultTag instanceof net.minecraft.nbt.NumericTag numeric) {
                    defaultTag = net.minecraft.nbt.LongTag.valueOf(numeric.getAsLong());
                }
                if (!typeCompatible(type, defaultTag)) {
                    throw new CompileException("Default value for variable '" + name
                            + "' is incompatible with type '" + type + "'");
                }
                if ("resource_location".equals(type)
                        && ResourceLocation.tryParse(defaultTag.getAsString()) == null) {
                    throw new CompileException("Default value for variable '" + name
                            + "' is not a resource location");
                }
                declaration.put("default", defaultTag);
            }
            variables.put(name, declaration);
        }
        return variables;
    }

    private static void validateSubmission(INode node, CompoundTag parameters) {
        ResourceLocation id = ResourceLocation.tryParse(stringOption(node, "intention", ""));
        if (id == null) throw new CompileException("Submit node has an invalid intention ID");
        try {
            IntentionPriority.valueOf(stringOption(node, "priority", "NORMAL"));
        } catch (IllegalArgumentException exception) {
            throw new CompileException("Submit node has an invalid priority");
        }
        ParameterSchema schema = AttentionMind.intentionTypes().get(id).map(type -> type.parameters())
                .orElseGet(() -> {
                    var asset = AttentionMind.intentionAssets().generation().assets().get(id);
                    return asset == null ? null : asset.parameters();
                });
        if (schema == null) throw new CompileException("Submit node references unknown intention '" + id + "'");
        for (String supplied : parameters.getAllKeys()) {
            if (!schema.definitions().containsKey(supplied)) {
                throw new CompileException("Submit node supplies unknown parameter '" + supplied + "' for " + id);
            }
        }
        schema.definitions().values().forEach(definition -> {
            if (definition.required() && definition.defaultValue().isEmpty() && !parameters.contains(definition.name())) {
                throw new CompileException("Submit node is missing required parameter '" + definition.name()
                        + "' for " + id);
            }
        });
    }

    private static CompoundTag jsonObjectTag(String source) {
        Tag tag = jsonTag(source);
        if (tag instanceof CompoundTag compound) return compound;
        throw new CompileException("Expected a JSON object");
    }

    private static Tag jsonTag(String source) {
        return toTag(parseJson(source));
    }

    private static JsonElement parseJson(String source) {
        try {
            return JsonParser.parseString(source);
        } catch (RuntimeException exception) {
            throw new CompileException("Invalid JSON option: " + exception.getMessage());
        }
    }

    private static boolean typeCompatible(String type, Tag value) {
        return switch (type) {
            case "boolean" -> value instanceof net.minecraft.nbt.ByteTag;
            case "integer" -> value instanceof net.minecraft.nbt.ByteTag
                    || value instanceof net.minecraft.nbt.ShortTag || value instanceof net.minecraft.nbt.IntTag;
            case "long" -> value instanceof net.minecraft.nbt.LongTag;
            case "number" -> value instanceof net.minecraft.nbt.NumericTag;
            case "string", "resource_location" -> value instanceof StringTag;
            default -> false;
        };
    }

    private static Tag toTag(JsonElement element) {
        if (element == null || element.isJsonNull()) return StringTag.valueOf("");
        if (element.isJsonObject()) {
            CompoundTag tag = new CompoundTag();
            element.getAsJsonObject().entrySet().forEach(entry -> tag.put(entry.getKey(), toTag(entry.getValue())));
            return tag;
        }
        if (element.isJsonArray()) {
            ListTag list = new ListTag();
            element.getAsJsonArray().forEach(value -> {
                if (!list.addTag(list.size(), toTag(value))) {
                    throw new CompileException("JSON arrays must contain one NBT-compatible value type");
                }
            });
            return list;
        }
        var primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) return net.minecraft.nbt.ByteTag.valueOf(primitive.getAsBoolean());
        if (primitive.isString()) return StringTag.valueOf(primitive.getAsString());
        Number number = primitive.getAsNumber();
        double value = number.doubleValue();
        return Math.rint(value) == value && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
                ? net.minecraft.nbt.IntTag.valueOf((int) value) : net.minecraft.nbt.DoubleTag.valueOf(value);
    }

    private static String stringOption(INode node, String id, String fallback) {
        INodeOption option = node.getNodeOptionById(id);
        return option == null ? fallback : option.<String>tryGetValue(String.class).result().orElse(fallback);
    }

    private static UUID uid(INode node) {
        return node.getNodeModel().getUid();
    }

    private static final class CompileException extends RuntimeException {
        private CompileException(String message) {
            super(message);
        }
    }
}
