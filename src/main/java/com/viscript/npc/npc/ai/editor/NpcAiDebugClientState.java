package com.viscript.npc.npc.ai.editor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** 客户端对最新权威 AI 调试快照的投影。 */
public final class NpcAiDebugClientState {
    private static Set<UUID> currentNodes = Set.of();
    private static Set<UUID> breakpoints = Set.of();

    private NpcAiDebugClientState() {
    }

    public static void update(CompoundTag payload) {
        CompoundTag snapshot = payload == null ? new CompoundTag() : payload.getCompound("snapshot");
        CompoundTag flow = snapshot.getCompound("flow");
        LinkedHashSet<UUID> current = new LinkedHashSet<>();
        var currentTags = flow.getList("current_nodes", Tag.TAG_COMPOUND);
        for (int index = 0; index < currentTags.size(); index++) {
            CompoundTag value = currentTags.getCompound(index);
            if (value.hasUUID("node")) current.add(value.getUUID("node"));
        }
        CompoundTag active = snapshot.getCompound("mind_debug").getCompound("active");
        parseNodeId(active.getString("current_node")).ifPresent(current::add);

        LinkedHashSet<UUID> savedBreakpoints = new LinkedHashSet<>();
        var breakpointTags = flow.getList("breakpoints", Tag.TAG_STRING);
        for (int index = 0; index < breakpointTags.size(); index++) {
            parseNodeId(breakpointTags.getString(index)).ifPresent(savedBreakpoints::add);
        }
        currentNodes = Set.copyOf(current);
        breakpoints = Set.copyOf(savedBreakpoints);
    }

    public static void clear() {
        currentNodes = Set.of();
        breakpoints = Set.of();
    }

    public static boolean isCurrent(UUID node) {
        return currentNodes.contains(node);
    }

    public static boolean isBreakpoint(UUID node) {
        return breakpoints.contains(node);
    }

    public static Set<UUID> breakpoints() {
        return breakpoints;
    }

    private static java.util.Optional<UUID> parseNodeId(String value) {
        if (value == null || value.isBlank()) return java.util.Optional.empty();
        String normalized = value.startsWith("n_") ? value.substring(2) : value;
        if (normalized.length() == 32) {
            normalized = normalized.substring(0, 8) + '-' + normalized.substring(8, 12) + '-'
                    + normalized.substring(12, 16) + '-' + normalized.substring(16, 20) + '-'
                    + normalized.substring(20);
        }
        try {
            return java.util.Optional.of(UUID.fromString(normalized));
        } catch (IllegalArgumentException ignored) {
            return java.util.Optional.empty();
        }
    }
}
