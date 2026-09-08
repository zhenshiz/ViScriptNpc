package com.viscript.npc.npc.ai.flow;

import net.minecraft.resources.ResourceLocation;
import org.thexeler.AttentionMind;
import org.thexeler.api.asset.AssetHashing;
import org.thexeler.api.asset.CompositeNode;
import org.thexeler.api.intention.IntentionType;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class NpcIntentionDependencyGraph {
    private NpcIntentionDependencyGraph() {
    }

    public static Set<ResourceLocation> closure(Set<ResourceLocation> roots) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        ArrayDeque<ResourceLocation> pending = new ArrayDeque<>(roots);
        var assets = AttentionMind.intentionAssets().generation().assets();
        while (!pending.isEmpty()) {
            ResourceLocation id = pending.removeFirst();
            if (!result.add(id)) continue;
            var asset = assets.get(id);
            if (asset == null) continue;
            asset.nodes().values().stream().filter(CompositeNode.Invoke.class::isInstance)
                    .map(CompositeNode.Invoke.class::cast).map(CompositeNode.Invoke::intention)
                    .forEach(pending::addLast);
        }
        return Set.copyOf(result);
    }

    public static Set<ResourceLocation> affectedAssets(Set<ResourceLocation> changed) {
        Set<ResourceLocation> affected = new LinkedHashSet<>(changed);
        var assets = AttentionMind.intentionAssets().generation().assets();
        boolean found;
        do {
            found = false;
            for (var asset : assets.values()) {
                if (affected.contains(asset.id())) continue;
                boolean depends = asset.nodes().values().stream()
                        .filter(CompositeNode.Invoke.class::isInstance)
                        .map(CompositeNode.Invoke.class::cast)
                        .anyMatch(invoke -> affected.contains(invoke.intention()));
                if (depends) found |= affected.add(asset.id());
            }
        } while (found);
        return Set.copyOf(affected);
    }

    public static String fingerprint(ResourceLocation id) {
        return fingerprint(id, new HashMap<>(), new LinkedHashSet<>());
    }

    private static String fingerprint(ResourceLocation id, Map<ResourceLocation, String> memo,
                                      Set<ResourceLocation> path) {
        String cached = memo.get(id);
        if (cached != null) return cached;
        if (!path.add(id)) return "cycle:" + id;
        var asset = AttentionMind.intentionAssets().generation().assets().get(id);
        String value;
        if (asset != null) {
            StringBuilder source = new StringBuilder("asset:").append(asset.contentHash());
            asset.nodes().values().stream().filter(CompositeNode.Invoke.class::isInstance)
                    .map(CompositeNode.Invoke.class::cast).map(CompositeNode.Invoke::intention)
                    .distinct().sorted().forEach(dependency -> source.append('|').append(dependency).append('=')
                            .append(fingerprint(dependency, memo, path)));
            value = AssetHashing.sha256(source.toString());
        } else {
            value = AttentionMind.intentionTypes().get(id).map(NpcIntentionDependencyGraph::typeFingerprint)
                    .orElse("missing:" + id);
        }
        path.remove(id);
        memo.put(id, value);
        return value;
    }

    private static String typeFingerprint(IntentionType<?> type) {
        StringBuilder source = new StringBuilder("type:").append(type.id()).append('|')
                .append(type.schemaVersion()).append('|').append(type.editorMetadata().nameKey()).append('|')
                .append(type.editorMetadata().descriptionKey()).append('|').append(type.editorMetadata().category());
        type.requiredCapabilities().stream().map(Class::getName).sorted()
                .forEach(value -> source.append("|cap:").append(value));
        type.parameters().definitions().values().stream().sorted(java.util.Comparator.comparing(value -> value.name()))
                .forEach(definition -> {
                    source.append("|param:").append(definition.name()).append(':').append(definition.type().id())
                            .append(':').append(definition.required());
                    definition.defaultValue().ifPresent(value -> source.append(':').append(value));
                });
        return AssetHashing.sha256(source.toString());
    }
}
