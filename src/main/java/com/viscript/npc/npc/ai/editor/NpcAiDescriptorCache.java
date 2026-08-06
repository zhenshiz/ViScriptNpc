package com.viscript.npc.npc.ai.editor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Set;

/** Client-populated copy of the server-authoritative AI editor descriptors. */
public final class NpcAiDescriptorCache {
    private static CompoundTag descriptors = new CompoundTag();

    private NpcAiDescriptorCache() {
    }

    public static synchronized void set(CompoundTag payload) {
        descriptors = payload == null ? new CompoundTag() : payload.copy();
    }

    public static synchronized CompoundTag get() {
        return descriptors.copy();
    }

    public static synchronized Set<String> intentionIds() {
        Set<String> ids = new LinkedHashSet<>();
        var intentions = descriptors.getList("intentions", Tag.TAG_COMPOUND);
        for (int index = 0; index < intentions.size(); index++) {
            String id = intentions.getCompound(index).getString("id");
            if (!id.isBlank()) ids.add(id);
        }
        return Set.copyOf(ids);
    }

    public static synchronized Set<String> nodeIds(String kind) {
        Set<String> ids = new LinkedHashSet<>();
        var nodes = descriptors.getList("nodes", Tag.TAG_COMPOUND);
        for (int index = 0; index < nodes.size(); index++) {
            CompoundTag node = nodes.getCompound(index);
            if (kind.equals(node.getString("kind")) && !node.getString("id").isBlank()) {
                ids.add(node.getString("id"));
            }
        }
        return Set.copyOf(ids);
    }

    public static synchronized java.util.List<CompoundTag> intentionParameters(String intentionId) {
        var intentions = descriptors.getList("intentions", Tag.TAG_COMPOUND);
        for (int index = 0; index < intentions.size(); index++) {
            CompoundTag intention = intentions.getCompound(index);
            if (!intentionId.equals(intention.getString("id"))) continue;
            var parameters = intention.getList("parameters", Tag.TAG_COMPOUND);
            java.util.ArrayList<CompoundTag> result = new java.util.ArrayList<>();
            for (int parameter = 0; parameter < parameters.size(); parameter++) {
                result.add(parameters.getCompound(parameter).copy());
            }
            return java.util.List.copyOf(result);
        }
        return java.util.List.of();
    }

    public static synchronized java.util.List<CompoundTag> nodeParameters(String kind, String nodeId) {
        var nodes = descriptors.getList("nodes", Tag.TAG_COMPOUND);
        for (int index = 0; index < nodes.size(); index++) {
            CompoundTag node = nodes.getCompound(index);
            if (!kind.equals(node.getString("kind")) || !nodeId.equals(node.getString("id"))) continue;
            CompoundTag parameters = node.getCompound("parameters");
            java.util.ArrayList<CompoundTag> result = new java.util.ArrayList<>();
            for (String name : parameters.getAllKeys()) {
                CompoundTag descriptor = parameters.getCompound(name).copy();
                descriptor.putString("name", name);
                if (!descriptor.contains("type", Tag.TAG_STRING)) descriptor.putString("type", "attentionmind:string");
                result.add(descriptor);
            }
            result.sort(java.util.Comparator.comparing(value -> value.getString("name")));
            return java.util.List.copyOf(result);
        }
        return java.util.List.of();
    }

    public static synchronized Set<String> templateIds() {
        Set<String> ids = new LinkedHashSet<>();
        var templates = descriptors.getList("templates", Tag.TAG_COMPOUND);
        for (int index = 0; index < templates.size(); index++) {
            String id = templates.getCompound(index).getString("id");
            if (!id.isBlank()) ids.add(id);
        }
        return Set.copyOf(ids);
    }

    public static synchronized CompoundTag template(String templateId) {
        var templates = descriptors.getList("templates", Tag.TAG_COMPOUND);
        for (int index = 0; index < templates.size(); index++) {
            CompoundTag template = templates.getCompound(index);
            if (templateId.equals(template.getString("id"))) return template.copy();
        }
        return new CompoundTag();
    }
}
