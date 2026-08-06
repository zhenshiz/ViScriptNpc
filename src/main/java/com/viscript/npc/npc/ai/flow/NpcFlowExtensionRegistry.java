package com.viscript.npc.npc.ai.flow;

import com.viscript.npc.npc.CustomNpc;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.thexeler.AttentionMind;
import org.thexeler.api.parameter.ParameterDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class NpcFlowExtensionRegistry {
    private static final Map<ResourceLocation, Descriptor> TRIGGERS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, RegisteredCondition> CONDITIONS = new LinkedHashMap<>();

    private NpcFlowExtensionRegistry() {}

    public static synchronized void clearScriptExtensions() {
        TRIGGERS.clear();
        CONDITIONS.clear();
    }

    public static synchronized void registerTrigger(String id, CompoundTag parameters, CompoundTag display) {
        registerTrigger(id, parameters, List.of(), display, 1);
    }

    public static synchronized void registerTrigger(String id, CompoundTag parameters, List<String> capabilities,
                                                    CompoundTag display, int schemaVersion) {
        ResourceLocation key = ResourceLocation.parse(id);
        TRIGGERS.put(key, new Descriptor(key, "trigger", copy(parameters), capabilities, copy(display), schemaVersion));
    }

    public static synchronized void registerCondition(String id, CompoundTag parameters, CompoundTag display,
                                                      FlowCondition condition) {
        registerCondition(id, parameters, List.of(), display, 1, condition);
    }

    public static synchronized void registerCondition(String id, CompoundTag parameters, List<String> capabilities,
                                                      CompoundTag display, int schemaVersion,
                                                      FlowCondition condition) {
        ResourceLocation key = ResourceLocation.parse(id);
        CONDITIONS.put(key, new RegisteredCondition(new Descriptor(key, "condition", copy(parameters), capabilities,
                copy(display), schemaVersion),
                java.util.Objects.requireNonNull(condition, "condition")));
    }

    public static synchronized Optional<FlowCondition> condition(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        RegisteredCondition value = key == null ? null : CONDITIONS.get(key);
        return value == null ? Optional.empty() : Optional.of(value.condition());
    }

    public static synchronized Optional<String> fingerprint(ResourceLocation id) {
        Descriptor trigger = TRIGGERS.get(id);
        if (trigger != null) return Optional.of(NpcFlowHashing.hash(trigger.toTag()));
        RegisteredCondition condition = CONDITIONS.get(id);
        return condition == null ? Optional.empty()
                : Optional.of(NpcFlowHashing.hash(condition.descriptor().toTag()));
    }

    public static synchronized Map<ResourceLocation, String> fingerprints() {
        Map<ResourceLocation, String> result = new LinkedHashMap<>();
        TRIGGERS.forEach((id, descriptor) -> result.put(id, NpcFlowHashing.hash(descriptor.toTag())));
        CONDITIONS.forEach((id, condition) -> result.put(id,
                NpcFlowHashing.hash(condition.descriptor().toTag())));
        return Map.copyOf(result);
    }

    public static synchronized CompoundTag descriptors() {
        CompoundTag root = new CompoundTag();
        ListTag values = new ListTag();
        TRIGGERS.values().forEach(value -> values.add(value.toTag()));
        CONDITIONS.values().forEach(value -> values.add(value.descriptor().toTag()));
        root.put("nodes", values);
        Map<ResourceLocation, CompoundTag> intentionDescriptors = new LinkedHashMap<>();
        AttentionMind.intentionTypes().types().values().stream()
                .sorted(java.util.Comparator.comparing(type -> type.id().toString()))
                .forEach(type -> {
                    CompoundTag intention = new CompoundTag();
                    intention.putString("id", type.id().toString());
                    intention.putString("kind", "registered");
                    intention.putString("name", type.editorMetadata().nameKey());
                    intention.putString("description", type.editorMetadata().descriptionKey());
                    intention.putString("category", type.editorMetadata().category().toString());
                    ListTag capabilities = new ListTag();
                    type.requiredCapabilities().stream().map(Class::getName).sorted()
                            .forEach(value -> capabilities.add(net.minecraft.nbt.StringTag.valueOf(value)));
                    intention.put("capabilities", capabilities);
                    ListTag parameters = new ListTag();
                    type.parameters().definitions().values().forEach(definition ->
                            parameters.add(parameterDescriptor(definition)));
                    intention.put("parameters", parameters);
                    intentionDescriptors.put(type.id(), intention);
                });
        AttentionMind.intentionAssets().generation().assets().values().stream()
                .sorted(java.util.Comparator.comparing(asset -> asset.id().toString()))
                .forEach(asset -> {
                    CompoundTag intention = new CompoundTag();
                    intention.putString("id", asset.id().toString());
                    intention.putString("kind", "composite");
                    intention.putString("name", asset.metadata().nameKey());
                    intention.putString("description", asset.metadata().descriptionKey());
                    intention.putString("category", asset.metadata().category().toString());
                    ListTag capabilities = new ListTag();
                    asset.requiredCapabilities().stream().map(Class::getName).sorted()
                            .forEach(value -> capabilities.add(net.minecraft.nbt.StringTag.valueOf(value)));
                    intention.put("capabilities", capabilities);
                    ListTag parameters = new ListTag();
                    asset.parameters().definitions().values().forEach(definition ->
                            parameters.add(parameterDescriptor(definition)));
                    intention.put("parameters", parameters);
                    intentionDescriptors.put(asset.id(), intention);
                });
        ListTag intentions = new ListTag();
        intentionDescriptors.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> intentions.add(entry.getValue()));
        root.put("intentions", intentions);
        ListTag templates = new ListTag();
        NpcFlowTemplateRegistry.getInstance().snapshot().values().stream()
                .sorted(java.util.Comparator.comparing(value -> value.id().toString()))
                .forEach(template -> {
                    CompoundTag value = new CompoundTag();
                    value.putString("id", template.id().toString());
                    value.put("graph", template.graph());
                    value.put("flow", template.flow());
                    value.put("public_parameters", template.publicParameters());
                    value.putString("resolved_hash", template.resolvedHash());
                    templates.add(value);
                });
        root.put("templates", templates);
        return root;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static CompoundTag parameterDescriptor(ParameterDefinition<?> definition) {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", definition.name());
        tag.putString("type", definition.type().id().toString());
        tag.putBoolean("required", definition.required());
        definition.defaultValue().ifPresent(value ->
                tag.put("default", ((org.thexeler.api.parameter.ParameterType) definition.type()).encode(value)));
        return tag;
    }

    private static CompoundTag copy(CompoundTag value) {
        return value == null ? new CompoundTag() : value.copy();
    }

    @FunctionalInterface
    public interface FlowCondition {
        boolean test(CustomNpc npc, @Nullable LivingEntity target, CompoundTag options);
    }

    public record Descriptor(ResourceLocation id, String kind, CompoundTag parameters, List<String> capabilities,
                             CompoundTag display, int schemaVersion) {
        public Descriptor {
            parameters = copy(parameters);
            capabilities = capabilities == null ? List.of() : capabilities.stream()
                    .filter(java.util.Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty())
                    .distinct().sorted().toList();
            display = copy(display);
            if (!Set.of("trigger", "condition").contains(kind)) {
                throw new IllegalArgumentException("Unsupported flow extension kind '" + kind + "'");
            }
            if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be at least 1");
            validateParameters(id, parameters);
            for (String field : List.of("name", "description", "category")) {
                if (!display.contains(field, net.minecraft.nbt.Tag.TAG_STRING)
                        || display.getString(field).isBlank()) {
                    throw new IllegalArgumentException("Flow extension '" + id
                            + "' display metadata requires '" + field + "'");
                }
            }
        }

        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", id.toString());
            tag.putString("kind", kind);
            tag.putInt("schema_version", schemaVersion);
            tag.put("parameters", parameters.copy());
            ListTag requiredCapabilities = new ListTag();
            capabilities.forEach(value -> requiredCapabilities.add(net.minecraft.nbt.StringTag.valueOf(value)));
            tag.put("capabilities", requiredCapabilities);
            tag.put("display", display.copy());
            return tag;
        }
    }

    private static void validateParameters(ResourceLocation id, CompoundTag parameters) {
        Set<String> supported = Set.of("attentionmind:bool", "attentionmind:int", "attentionmind:long",
                "attentionmind:float", "attentionmind:double", "attentionmind:string",
                "attentionmind:resource_location", "attentionmind:position",
                "attentionmind:actor_reference", "attentionmind:item_reference");
        for (String name : parameters.getAllKeys()) {
            if (!parameters.contains(name, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Flow extension '" + id + "' parameter '" + name
                        + "' must be a descriptor compound");
            }
            CompoundTag parameter = parameters.getCompound(name);
            String type = parameter.getString("type");
            if (!supported.contains(type)) {
                throw new IllegalArgumentException("Flow extension '" + id + "' parameter '" + name
                        + "' has unsupported type '" + type + "'");
            }
            if (!parameter.contains("required", net.minecraft.nbt.Tag.TAG_BYTE)) {
                throw new IllegalArgumentException("Flow extension '" + id + "' parameter '" + name
                        + "' must declare required=true/false");
            }
        }
    }

    private record RegisteredCondition(Descriptor descriptor, FlowCondition condition) {}
}
