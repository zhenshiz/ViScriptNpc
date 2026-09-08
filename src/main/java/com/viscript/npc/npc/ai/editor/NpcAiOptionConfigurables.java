package com.viscript.npc.npc.ai.editor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.ITypeConfigurable;
import com.viscript.npc.util.ConfiguratorUtil;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class NpcAiOptionConfigurables {
    public static final ITypeConfigurable INTENTION_ID = searchable(NpcAiDescriptorCache::intentionIds);
    public static final ITypeConfigurable TRIGGER_ID = searchable(() -> withBuiltins("trigger"));
    public static final ITypeConfigurable CONDITION_ID = searchable(() -> withBuiltins("condition"));

    public static ITypeConfigurable flowParameterBindings(Supplier<String> intentionId) {
        return (value, type) -> IConfigurable.create(group -> {
            JsonObject initial = parseObject(value.getValue());
            Map<String, ParameterState> states = new LinkedHashMap<>();
            for (var descriptor : NpcAiDescriptorCache.intentionParameters(intentionId.get())) {
                String name = descriptor.getString("name");
                String parameterType = descriptor.getString("type");
                JsonElement supplied = initial.get(name);
                ParameterState state = ParameterState.create(parameterType, supplied, descriptor.get("default"));
                states.put(name, state);
            }
            Runnable commit = () -> {
                JsonObject result = new JsonObject();
                states.forEach((name, state) -> result.add(name, state.encoded()));
                value.setValue(result.toString());
            };
            states.forEach((name, state) -> {
                group.addConfigurator(ConfiguratorUtil.createStrArrSearchComponentConfigurator(
                        name + ".binding", Set.of("constant", "target", "variable", "parameter"),
                        () -> state.binding, binding -> {
                            state.binding = binding;
                            commit.run();
                        }));
                group.addConfigurator(new StringConfigurator(name + ".source", () -> state.source, source -> {
                    state.source = source;
                    commit.run();
                }, "", true));
                addConstantConfigurator(group, name, state, commit);
            });
        });
    }

    public static ITypeConfigurable intentionArguments(Supplier<String> intentionId) {
        return (value, type) -> IConfigurable.create(group -> {
            JsonObject initial = parseObject(value.getValue());
            Map<String, ExpressionState> states = new LinkedHashMap<>();
            for (var descriptor : NpcAiDescriptorCache.intentionParameters(intentionId.get())) {
                String name = descriptor.getString("name");
                states.put(name, ExpressionState.create(name, descriptor.getString("type"), initial.get(name),
                        descriptor.get("default")));
            }
            Runnable commit = () -> {
                JsonObject result = new JsonObject();
                states.forEach((name, state) -> result.add(name, state.encoded()));
                value.setValue(result.toString());
            };
            states.forEach((name, state) -> {
                group.addConfigurator(ConfiguratorUtil.createStrArrSearchComponentConfigurator(
                        name + ".expression", Set.of("constant", "parameter", "local"),
                        () -> state.mode, mode -> {
                            state.mode = mode;
                            commit.run();
                        }));
                group.addConfigurator(new StringConfigurator(name + ".source", () -> state.source, source -> {
                    state.source = source;
                    commit.run();
                }, "", true));
                addConstantConfigurator(group, name, () -> state.constant, state.type, updated -> {
                    state.constant = updated;
                    commit.run();
                });
            });
        });
    }

    public static ITypeConfigurable nodeParameters(Supplier<String> kind, Supplier<String> nodeId) {
        return (value, type) -> IConfigurable.create(group -> {
            JsonObject initial = parseObject(value.getValue());
            Map<String, JsonElement> states = new LinkedHashMap<>();
            Map<String, String> types = new LinkedHashMap<>();
            for (var descriptor : NpcAiDescriptorCache.nodeParameters(kind.get(), nodeId.get())) {
                String name = descriptor.getString("name");
                String parameterType = descriptor.getString("type");
                types.put(name, parameterType);
                states.put(name, initial.has(name) ? initial.get(name).deepCopy()
                        : ParameterState.defaultJson(parameterType, descriptor.get("default")));
            }
            Runnable commit = () -> {
                JsonObject result = new JsonObject();
                states.forEach(result::add);
                value.setValue(result.toString());
            };
            states.forEach((name, current) -> addConstantConfigurator(group, name, () -> states.get(name),
                    types.get(name), updated -> {
                states.put(name, updated);
                commit.run();
            }));
        });
    }

    public static ITypeConfigurable parameterSchemaJson() {
        return (value, type) -> IConfigurable.create(group -> {
            JsonObject initial = parseObject(value.getValue());
            Map<String, SchemaState> states = new LinkedHashMap<>();
            initial.entrySet().forEach(entry -> {
                if (entry.getValue().isJsonObject()) {
                    states.put(entry.getKey(), SchemaState.create(entry.getValue().getAsJsonObject()));
                }
            });
            Runnable commit = () -> {
                JsonObject result = new JsonObject();
                states.forEach((name, state) -> result.add(name, state.encoded()));
                value.setValue(result.toString());
            };
            group.addConfigurator(new StringConfigurator("schema.json", () -> {
                    Object current = value.getValue();
                    return current == null ? "" : current.toString();
                }, updated -> {
                    value.setValue(updated);
                    value.notifyValueChanged();
                }, "{}", true));
            states.forEach((name, state) -> {
                group.addConfigurator(ConfiguratorUtil.createStrArrSearchComponentConfigurator(name + ".type",
                        Set.of("attentionmind:bool", "attentionmind:int", "attentionmind:long",
                                "attentionmind:float", "attentionmind:double", "attentionmind:string",
                                "attentionmind:resource_location", "attentionmind:position",
                                "attentionmind:actor_reference", "attentionmind:item_reference"),
                        () -> state.type, updated -> {
                            state.type = updated;
                            commit.run();
                            value.notifyValueChanged();
                        }));
                group.addConfigurator(new BooleanConfigurator(name + ".required", () -> state.required, updated -> {
                    state.required = updated;
                    commit.run();
                }, false, true));
                group.addConfigurator(new BooleanConfigurator(name + ".default_enabled", () -> state.hasDefault,
                        updated -> {
                            state.hasDefault = updated;
                            commit.run();
                        }, false, true));
                addConstantConfigurator(group, name + ".default", () -> state.defaultValue, state.type, updated -> {
                    state.defaultValue = updated;
                    commit.run();
                });
            });
        });
    }

    private static ITypeConfigurable searchable(java.util.function.Supplier<java.util.Set<String>> values) {
        return (value, type) -> IConfigurable.create(group ->
            group.addConfigurator(ConfiguratorUtil.createStrArrSearchComponentConfigurator("",
                    values,
                    () -> {
                        String current = value.getValue();
                        return current == null ? "" : current;
                    }, selected -> {
                        value.setValue(selected);
                        value.notifyValueChanged();
                    })));
    }

    private static java.util.Set<String> withBuiltins(String kind) {
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>(NpcAiDescriptorCache.nodeIds(kind));
        if ("trigger".equals(kind)) {
            values.addAll(java.util.List.of("spawn", "tick", "hurt", "attack", "killed", "death", "interact",
                    "range_enter", "range_leave", "target_invalid"));
        } else {
            values.addAll(java.util.List.of("always", "alive", "target_alive", "line_of_sight", "hostile",
                    "friendly", "has_faction",
                    "distance", "health_below", "health_ratio_below", "target_health_below",
                    "target_health_ratio_below", "entity_type", "entity_tag",
                    "world", "dimension", "variable_true", "random"));
        }
        return java.util.Set.copyOf(values);
    }

    private static void addConstantConfigurator(com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup group,
                                                String name, ParameterState state, Runnable commit) {
        addConstantConfigurator(group, name, () -> state.constant, state.type, updated -> {
            state.constant = updated;
            commit.run();
        });
    }

    private static void addConstantConfigurator(com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup group,
                                                String name, Supplier<JsonElement> constant, String type,
                                                java.util.function.Consumer<JsonElement> update) {
        String path = typePath(type);
        if ("bool".equals(path)) {
            group.addConfigurator(new BooleanConfigurator(name,
                    () -> constant.get().isJsonPrimitive() && constant.get().getAsJsonPrimitive().isBoolean()
                            && constant.get().getAsBoolean(), updated -> {
                update.accept(new com.google.gson.JsonPrimitive(updated));
            }, false, true));
            return;
        }
        ConfigNumber.Type numberType = switch (path) {
            case "int" -> ConfigNumber.Type.INTEGER;
            case "long" -> ConfigNumber.Type.LONG;
            case "float" -> ConfigNumber.Type.FLOAT;
            case "double" -> ConfigNumber.Type.DOUBLE;
            default -> null;
        };
        if (numberType != null) {
            Number fallback = numberType == ConfigNumber.Type.FLOAT || numberType == ConfigNumber.Type.DOUBLE ? 0.0D : 0;
            NumberConfigurator configurator = new NumberConfigurator(name,
                    () -> constant.get().isJsonPrimitive() && constant.get().getAsJsonPrimitive().isNumber()
                            ? constant.get().getAsNumber() : fallback,
                    updated -> {
                        update.accept(new com.google.gson.JsonPrimitive(updated));
                    }, fallback, true).setType(numberType);
            group.addConfigurator(configurator);
            return;
        }
        group.addConfigurator(new StringConfigurator(name, () -> constantText(path, constant.get()), updated -> {
            update.accept(constantFromText(path, updated));
        }, "", true));
    }

    private static String constantText(String path, JsonElement constant) {
        if (Set.of("string", "resource_location", "item_reference").contains(path)
                && constant.isJsonPrimitive()) return constant.getAsString();
        return constant.toString();
    }

    private static JsonElement constantFromText(String path, String value) {
        if (Set.of("string", "resource_location", "item_reference").contains(path)) {
            return new com.google.gson.JsonPrimitive(value);
        }
        try {
            return JsonParser.parseString(value);
        } catch (RuntimeException ignored) {
            return new com.google.gson.JsonPrimitive(value);
        }
    }

    private static JsonObject parseObject(Object source) {
        try {
            JsonElement parsed = JsonParser.parseString(source == null ? "{}" : source.toString());
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException ignored) {
            return new JsonObject();
        }
    }

    private static String typePath(String id) {
        int separator = id.indexOf(':');
        return separator < 0 ? id : id.substring(separator + 1);
    }

    private static final class ParameterState {
        private final String type;
        private String binding;
        private String source;
        private JsonElement constant;

        private ParameterState(String type, String binding, String source, JsonElement constant) {
            this.type = type;
            this.binding = binding;
            this.source = source;
            this.constant = constant;
        }

        private static ParameterState create(String type, JsonElement supplied, Tag defaultValue) {
            if (supplied != null && supplied.isJsonObject() && supplied.getAsJsonObject().has("binding")) {
                JsonObject binding = supplied.getAsJsonObject();
                String mode = binding.get("binding").getAsString();
                String source = binding.has("name") ? binding.get("name").getAsString() : "";
                return new ParameterState(type, mode, source, defaultJson(type, defaultValue));
            }
            String defaultBinding = "actor_reference".equals(typePath(type)) && supplied == null ? "target" : "constant";
            return new ParameterState(type, defaultBinding, "",
                    supplied == null ? defaultJson(type, defaultValue) : supplied.deepCopy());
        }

        private JsonElement encoded() {
            if ("constant".equals(binding)) return constant.deepCopy();
            JsonObject result = new JsonObject();
            result.addProperty("binding", binding);
            if ("variable".equals(binding) || "parameter".equals(binding)) result.addProperty("name", source);
            return result;
        }

        private static JsonElement defaultJson(String type, Tag value) {
            if (value instanceof NumericTag numeric) {
                if ("bool".equals(typePath(type))) return new com.google.gson.JsonPrimitive(numeric.getAsByte() != 0);
                return new com.google.gson.JsonPrimitive(numeric.getAsNumber());
            }
            if (value instanceof StringTag string) return new com.google.gson.JsonPrimitive(string.getAsString());
            return switch (typePath(type)) {
                case "bool" -> new com.google.gson.JsonPrimitive(false);
                case "int", "long", "float", "double" -> new com.google.gson.JsonPrimitive(0);
                case "position", "actor_reference" -> new JsonObject();
                default -> new com.google.gson.JsonPrimitive("");
            };
        }
    }

    private static final class ExpressionState {
        private final String type;
        private String mode;
        private String source;
        private JsonElement constant;

        private ExpressionState(String type, String mode, String source, JsonElement constant) {
            this.type = type;
            this.mode = mode;
            this.source = source;
            this.constant = constant;
        }

        private static ExpressionState create(String name, String type, JsonElement supplied, Tag defaultValue) {
            JsonElement fallback = ParameterState.defaultJson(type, defaultValue);
            if (supplied != null && supplied.isJsonObject()) {
                JsonObject object = supplied.getAsJsonObject();
                if (object.has("ref")) {
                    String ref = object.get("ref").getAsString();
                    if (ref.startsWith("params.")) return new ExpressionState(type, "parameter",
                            ref.substring("params.".length()), fallback);
                    if (ref.startsWith("locals.")) return new ExpressionState(type, "local",
                            ref.substring("locals.".length()), fallback);
                }
                if (object.has("binding")) {
                    String binding = object.get("binding").getAsString();
                    String source = object.has("name") ? object.get("name").getAsString() : name;
                    return new ExpressionState(type, "variable".equals(binding) ? "local" : "parameter",
                            source, fallback);
                }
            }
            if (supplied != null) return new ExpressionState(type, "constant", "", supplied.deepCopy());
            if ("actor_reference".equals(typePath(type))) {
                return new ExpressionState(type, "parameter", name, fallback);
            }
            return new ExpressionState(type, "constant", "", fallback);
        }

        private JsonElement encoded() {
            if ("constant".equals(mode)) return constant.deepCopy();
            JsonObject result = new JsonObject();
            result.addProperty("ref", ("local".equals(mode) ? "locals." : "params.") + source);
            return result;
        }
    }

    private static final class SchemaState {
        private String type;
        private boolean required;
        private boolean hasDefault;
        private JsonElement defaultValue;

        private SchemaState(String type, boolean required, boolean hasDefault, JsonElement defaultValue) {
            this.type = type;
            this.required = required;
            this.hasDefault = hasDefault;
            this.defaultValue = defaultValue;
        }

        private static SchemaState create(JsonObject definition) {
            String type = definition.has("type") ? definition.get("type").getAsString() : "attentionmind:string";
            boolean required = definition.has("required") && definition.get("required").getAsBoolean();
            boolean hasDefault = definition.has("default");
            JsonElement defaultValue = hasDefault ? definition.get("default").deepCopy()
                    : ParameterState.defaultJson(type, null);
            return new SchemaState(type, required, hasDefault, defaultValue);
        }

        private JsonObject encoded() {
            JsonObject result = new JsonObject();
            result.addProperty("type", type);
            result.addProperty("required", required);
            if (hasDefault) result.add("default", defaultValue.deepCopy());
            return result;
        }
    }

    private NpcAiOptionConfigurables() {
    }
}
