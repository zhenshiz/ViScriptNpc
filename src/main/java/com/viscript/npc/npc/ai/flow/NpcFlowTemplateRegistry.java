package com.viscript.npc.npc.ai.flow;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.viscript.npc.npc.data.ai.NpcAI;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.thexeler.api.asset.AssetHashing;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class NpcFlowTemplateRegistry {
    public static final String DIRECTORY = "npc_ai_templates";
    private static final NpcFlowTemplateRegistry INSTANCE = new NpcFlowTemplateRegistry();

    private volatile Map<ResourceLocation, Template> templates = Map.of();

    private NpcFlowTemplateRegistry() {}

    public static NpcFlowTemplateRegistry getInstance() {
        return INSTANCE;
    }

    public Optional<Template> get(ResourceLocation id) {
        return Optional.ofNullable(templates.get(id));
    }

    public Map<ResourceLocation, Template> snapshot() {
        return templates;
    }

    public Resolved resolve(NpcAI ai) {
        if (!ai.usesTemplate()) {
            Template embedded = compile(ResourceLocation.fromNamespaceAndPath("viscript_npc", "embedded"),
                    ai.getEmbeddedFlow());
            return new Resolved(embedded.flow(), ai.getEmbeddedParameters(), embedded.resolvedHash(),
                    embedded.dependencies());
        }
        Template template = templates.get(ai.getTemplateId());
        if (template == null) throw new IllegalArgumentException("Missing NPC AI template '" + ai.getTemplateId() + "'");
        CompoundTag parameters = template.publicParameters().copy();
        CompoundTag overrides = ai.getTemplateParameterOverrides();
        for (String name : overrides.getAllKeys()) {
            Tag expected = parameters.get(name);
            Tag supplied = overrides.get(name);
            if (expected == null) {
                throw new IllegalArgumentException("Template '" + template.id()
                        + "' has no public parameter '" + name + "'");
            }
            if (supplied == null || !samePublicParameterType(expected, supplied)) {
                throw new IllegalArgumentException("Template parameter override '" + name
                        + "' has incompatible type");
            }
            parameters.put(name, supplied.copy());
        }
        return new Resolved(template.flow(), parameters, template.resolvedHash(), template.dependencies());
    }

    public synchronized Set<ResourceLocation> reload(ResourceManager resources) {
        Map<ResourceLocation, CompoundTag> loaded = new LinkedHashMap<>();
        resources.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).forEach((file, resource) -> {
            try (Reader reader = resource.openAsReader()) {
                String path = file.getPath().substring((DIRECTORY + "/").length(), file.getPath().length() - 5);
                loaded.put(ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path),
                        (CompoundTag) toTag(JsonParser.parseReader(reader)));
            } catch (Exception exception) {
                throw new IllegalArgumentException("Unable to load NPC AI template " + file, exception);
            }
        });
        return replace(loaded);
    }

    public synchronized Set<ResourceLocation> replace(Map<ResourceLocation, CompoundTag> sources) {
        Map<ResourceLocation, Template> next = new LinkedHashMap<>();
        sources.forEach((id, source) -> next.put(id, compile(id, source)));
        Set<ResourceLocation> changed = new LinkedHashSet<>(templates.keySet());
        changed.addAll(next.keySet());
        changed.removeIf(id -> templates.containsKey(id) && next.containsKey(id)
                && templates.get(id).resolvedHash().equals(next.get(id).resolvedHash()));
        templates = Map.copyOf(next);
        return Set.copyOf(changed);
    }

    public synchronized Set<ResourceLocation> refreshResolvedHashes() {
        Map<ResourceLocation, Template> next = new LinkedHashMap<>();
        Set<ResourceLocation> changed = new LinkedHashSet<>();
        templates.forEach((id, template) -> {
            CompoundTag source = new CompoundTag();
            source.put("graph", template.graph());
            source.put("flow", template.flow());
            source.put("public_parameters", template.publicParameters());
            Template refreshed = compile(id, source);
            next.put(id, refreshed);
            if (!template.resolvedHash().equals(refreshed.resolvedHash())) changed.add(id);
        });
        templates = Map.copyOf(next);
        return Set.copyOf(changed);
    }

    private Template compile(ResourceLocation id, CompoundTag source) {
        CompoundTag flow = source.contains("flow", Tag.TAG_COMPOUND) ? source.getCompound("flow") : source;
        validateFlow(id, flow);
        CompoundTag graph = source.getCompound("graph");
        Set<ResourceLocation> dependencies = new LinkedHashSet<>();
        collectDependencies(flow, dependencies);
        dependencies = new LinkedHashSet<>(NpcIntentionDependencyGraph.closure(dependencies));
        List<String> hashes = new ArrayList<>();
        dependencies.stream().sorted().forEach(dependency -> {
            hashes.add(dependency + "=" + NpcFlowExtensionRegistry.fingerprint(dependency)
                    .orElseGet(() -> NpcIntentionDependencyGraph.fingerprint(dependency)));
        });
        CompoundTag runtimeContent = new CompoundTag();
        runtimeContent.put("flow", flow.copy());
        runtimeContent.put("public_parameters", source.getCompound("public_parameters").copy());
        String contentHash = NpcFlowHashing.hash(runtimeContent);
        String resolvedHash = AssetHashing.sha256(contentHash + "|" + String.join("|", hashes));
        return new Template(id, graph, flow.copy(), source.getCompound("public_parameters"), contentHash,
                resolvedHash, Set.copyOf(dependencies));
    }

    private static void validateFlow(ResourceLocation id, CompoundTag flow) {
        ListTag compileErrors = flow.getList("errors", Tag.TAG_STRING);
        if (!compileErrors.isEmpty()) {
            throw new IllegalArgumentException("NPC AI template '" + id + "' contains compile errors: "
                    + compileErrors.getString(0));
        }
        if (!flow.contains("rules", Tag.TAG_LIST) || !flow.getList("rules", Tag.TAG_COMPOUND).equals(flow.get("rules"))) {
            throw new IllegalArgumentException("NPC AI template '" + id + "' must contain a compound rules list");
        }
        ListTag rules = flow.getList("rules", Tag.TAG_COMPOUND);
        for (int index = 0; index < rules.size(); index++) {
            CompoundTag rule = rules.getCompound(index);
            if (!rule.hasUUID("id") || rule.getString("trigger").isBlank()
                    || !rule.contains("root", Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("NPC AI template '" + id + "' has an invalid rule at index " + index);
            }
            validateNode(id, rule.getCompound("root"), 0);
        }
    }

    private static void validateNode(ResourceLocation templateId, CompoundTag node, int depth) {
        if (depth > 1024) throw new IllegalArgumentException("NPC AI template '" + templateId + "' is too deeply nested");
        if (!node.hasUUID("id")) throw new IllegalArgumentException("NPC AI template '" + templateId + "' has a node without an ID");
        String type = node.getString("type");
        if (!Set.of("sequence", "branch", "delay", "loop", "retry", "set_variable", "submit", "end", "fail")
                .contains(type)) {
            throw new IllegalArgumentException("NPC AI template '" + templateId + "' has unknown node type '" + type + "'");
        }
        if (!node.contains("options", Tag.TAG_COMPOUND)
                || !node.contains("children", Tag.TAG_LIST)
                || !node.getList("children", Tag.TAG_COMPOUND).equals(node.get("children"))) {
            throw new IllegalArgumentException("NPC AI template '" + templateId + "' has malformed node " + node.getUUID("id"));
        }
        if ("submit".equals(type)) {
            ResourceLocation intention = ResourceLocation.tryParse(node.getCompound("options").getString("intention"));
            if (intention == null || (org.thexeler.AttentionMind.intentionTypes().get(intention).isEmpty()
                    && !org.thexeler.AttentionMind.intentionAssets().generation().assets().containsKey(intention))) {
                throw new IllegalArgumentException("NPC AI template '" + templateId
                        + "' references unknown intention '" + intention + "'");
            }
        }
        ListTag children = node.getList("children", Tag.TAG_COMPOUND);
        for (int index = 0; index < children.size(); index++) {
            validateNode(templateId, children.getCompound(index), depth + 1);
        }
    }

    private static CompoundTag emptyFlow() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 1);
        tag.put("rules", new ListTag());
        return tag;
    }

    private static void collectDependencies(Tag tag, Set<ResourceLocation> output) {
        if (tag instanceof CompoundTag compound) {
            if ("submit".equals(compound.getString("type"))) {
                ResourceLocation id = ResourceLocation.tryParse(compound.getCompound("options").getString("intention"));
                if (id != null) output.add(id);
            }
            if (compound.contains("trigger", Tag.TAG_STRING)) {
                ResourceLocation id = ResourceLocation.tryParse(compound.getString("trigger"));
                if (id != null) output.add(id);
            }
            if (compound.contains("condition", Tag.TAG_STRING)) {
                ResourceLocation id = ResourceLocation.tryParse(compound.getString("condition"));
                if (id != null) output.add(id);
            }
            compound.getAllKeys().forEach(key -> collectDependencies(compound.get(key), output));
        } else if (tag instanceof ListTag list) {
            list.forEach(value -> collectDependencies(value, output));
        }
    }

    private static boolean samePublicParameterType(Tag expected, Tag supplied) {
        if (expected instanceof NumericTag && supplied instanceof NumericTag) {
            return (expected instanceof ByteTag) == (supplied instanceof ByteTag);
        }
        return expected.getId() == supplied.getId();
    }

    private static Tag toTag(JsonElement element) {
        if (element == null || element.isJsonNull()) return StringTag.valueOf("");
        if (element.isJsonObject()) {
            CompoundTag tag = new CompoundTag();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                tag.put(entry.getKey(), toTag(entry.getValue()));
            }
            return tag;
        }
        if (element.isJsonArray()) {
            ListTag list = new ListTag();
            element.getAsJsonArray().forEach(value -> list.add(toTag(value)));
            return list;
        }
        var primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) return ByteTag.valueOf(primitive.getAsBoolean());
        if (primitive.isString()) return StringTag.valueOf(primitive.getAsString());
        Number number = primitive.getAsNumber();
        double value = number.doubleValue();
        return Math.rint(value) == value && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
                ? IntTag.valueOf((int) value) : DoubleTag.valueOf(value);
    }

    public record Template(ResourceLocation id, CompoundTag graph, CompoundTag flow, CompoundTag publicParameters,
                           String contentHash, String resolvedHash, Set<ResourceLocation> dependencies) {
        public Template {
            graph = graph.copy();
            flow = flow.copy();
            publicParameters = publicParameters.copy();
            dependencies = Set.copyOf(dependencies);
        }
    }

    public record Resolved(CompoundTag flow, CompoundTag parameters, String resolvedHash,
                           Set<ResourceLocation> dependencies) {
        public Resolved {
            flow = flow.copy();
            parameters = parameters.copy();
            dependencies = Set.copyOf(dependencies);
        }
    }
}
