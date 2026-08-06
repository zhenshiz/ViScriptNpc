package com.viscript.npc.npc.ai.flow;

import com.viscript.npc.npc.CustomNpc;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class NpcAiDependencyIndex {
    private static final Map<ResourceLocation, Set<UUID>> REVERSE = new HashMap<>();
    private static final Map<UUID, Set<ResourceLocation>> BY_NPC = new HashMap<>();
    private static String definitionFailure = "";

    private NpcAiDependencyIndex() {}

    public static synchronized void register(CustomNpc npc, Set<ResourceLocation> dependencies) {
        unregister(npc.getUUID());
        Set<ResourceLocation> keys = new HashSet<>(dependencies);
        if (npc.getNpcAI().usesTemplate()) keys.add(npc.getNpcAI().getTemplateId());
        BY_NPC.put(npc.getUUID(), Set.copyOf(keys));
        keys.forEach(key -> REVERSE.computeIfAbsent(key, ignored -> new HashSet<>()).add(npc.getUUID()));
    }

    public static synchronized void unregister(UUID npcId) {
        Set<ResourceLocation> keys = BY_NPC.remove(npcId);
        if (keys == null) return;
        for (ResourceLocation key : keys) {
            Set<UUID> ids = REVERSE.get(key);
            if (ids == null) continue;
            ids.remove(npcId);
            if (ids.isEmpty()) REVERSE.remove(key);
        }
    }

    public static void resetAffected(MinecraftServer server, Set<ResourceLocation> changed) {
        Set<UUID> affected = new HashSet<>();
        synchronized (NpcAiDependencyIndex.class) {
            changed.forEach(key -> affected.addAll(REVERSE.getOrDefault(key, Set.of())));
        }
        for (UUID id : affected) {
            for (var level : server.getAllLevels()) {
                if (level.getEntity(id) instanceof CustomNpc npc) {
                    npc.resetAiForDefinitionChange();
                    break;
                }
            }
        }
    }

    public static void invalidateAll(MinecraftServer server, String detail) {
        Set<UUID> affected;
        synchronized (NpcAiDependencyIndex.class) {
            definitionFailure = detail == null || detail.isBlank() ? "AI definition reload failed" : detail;
            affected = Set.copyOf(BY_NPC.keySet());
        }
        forEachLoaded(server, affected, npc -> npc.invalidateAiForDefinitionFailure(definitionFailure));
    }

    public static boolean recoverAll(MinecraftServer server) {
        Set<UUID> affected;
        synchronized (NpcAiDependencyIndex.class) {
            if (definitionFailure.isEmpty()) return false;
            definitionFailure = "";
            affected = Set.copyOf(BY_NPC.keySet());
        }
        forEachLoaded(server, affected, CustomNpc::resetAiForDefinitionChange);
        return true;
    }

    public static synchronized String definitionFailure() {
        return definitionFailure;
    }

    private static void forEachLoaded(MinecraftServer server, Set<UUID> ids,
                                      java.util.function.Consumer<CustomNpc> action) {
        for (UUID id : ids) {
            for (var level : server.getAllLevels()) {
                if (level.getEntity(id) instanceof CustomNpc npc) {
                    action.accept(npc);
                    break;
                }
            }
        }
    }

    public static synchronized void clear() {
        REVERSE.clear();
        BY_NPC.clear();
        definitionFailure = "";
    }
}
