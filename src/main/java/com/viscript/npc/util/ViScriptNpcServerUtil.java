package com.viscript.npc.util;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.network.s2c.S2CPayload;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.thexeler.MindMachine;
import org.thexeler.AttentionMind;
import org.thexeler.api.IntentionPriority;
import org.thexeler.api.SubmissionResult;
import org.thexeler.api.script.ScriptIntentionDefinition;
import org.thexeler.api.world.MindActor;
import org.thexeler.api.world.MindEntityActor;
import com.viscript.npc.npc.ai.flow.NpcFlowExtensionRegistry;
import com.viscript.npc.npc.ai.flow.NpcAiDependencyIndex;
import com.viscript.npc.npc.ai.flow.NpcFlowTemplateRegistry;
import com.viscript.npc.npc.ai.flow.NpcIntentionDependencyGraph;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ViScriptNpcServerUtil {
    private static final List<ScriptIntentionDefinition> PENDING_SCRIPT_INTENTIONS = new ArrayList<>();
    private static Map<ResourceLocation, String> activeScriptFingerprints = Map.of();
    private static Map<ResourceLocation, String> preReloadFlowExtensionFingerprints = Map.of();
    private static boolean collectingScriptReload;

    @Info("服务端打开NPC编辑器")
    public static void openNpcEditor(ServerPlayer player, CompoundTag tag) {
        PlayerUIMenuType.openUI(player, NpcEditor.EDITOR_ID);
        RPCPacketDistributor.rpcToPlayer(player, S2CPayload.OPEN_NPC_EDITOR, tag);
        sendNpcAiDescriptors(player);
    }

    public static void sendNpcAiDescriptors(ServerPlayer player) {
        if (player != null) {
            RPCPacketDistributor.rpcToPlayer(player, S2CPayload.SEND_NPC_AI_DESCRIPTORS,
                    NpcFlowExtensionRegistry.descriptors());
        }
    }

    public static void broadcastNpcAiDescriptors(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        CompoundTag descriptors = NpcFlowExtensionRegistry.descriptors();
        server.getPlayerList().getPlayers().forEach(player -> RPCPacketDistributor.rpcToPlayer(player,
                S2CPayload.SEND_NPC_AI_DESCRIPTORS, descriptors));
    }

    @Info("生成NPC")
    public static Entity summonNpc(CompoundTag tag, Vec3 pos) {
        try {
            return SummonCommand.createEntity(Platform.getMinecraftServer().createCommandSourceStack(), (Holder.Reference<EntityType<?>>) NpcRegister.CUSTOM_NPC.getDelegate(), pos, tag, false);
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    @Info("将 MC 实体包装成 MindActor")
    public static MindActor wrap(LivingEntity entity) {
        return MindEntityActor.wrap(entity);
    }

    @Info("获取 NPC 的 MindMachine（未启用或客户端可能为 null）")
    public static MindMachine getMind(CustomNpc npc) {
        return npc.getMind();
    }

    @Info("按注册 ID 和强类型参数向 NPC 提交 Intention")
    public static SubmissionResult submitIntention(CustomNpc npc, String intentionId,
                                                    IntentionPriority priority, Map<String, ?> parameters) {
        MindMachine mind = npc.getMind();
        if (mind == null) {
            return new SubmissionResult.Rejected(java.util.List.of(
                    new org.thexeler.api.IntentionDiagnostic("MIND_UNAVAILABLE", "NPC mind is not available")));
        }
        return mind.submit(net.minecraft.resources.ResourceLocation.parse(intentionId), priority, parameters);
    }

    @Info("在 KubeJS/server reload 时原子替换脚本意图描述")
    public static synchronized void replaceScriptIntentions(Collection<ScriptIntentionDefinition> definitions) {
        if (collectingScriptReload) {
            PENDING_SCRIPT_INTENTIONS.clear();
            PENDING_SCRIPT_INTENTIONS.addAll(definitions);
        } else {
            applyScriptIntentions(List.copyOf(definitions));
        }
    }

    public static synchronized void beginScriptReload() {
        collectingScriptReload = true;
        PENDING_SCRIPT_INTENTIONS.clear();
        preReloadFlowExtensionFingerprints = NpcFlowExtensionRegistry.fingerprints();
        NpcFlowExtensionRegistry.clearScriptExtensions();
    }

    public static synchronized void finishScriptReload() {
        List<ScriptIntentionDefinition> definitions = List.copyOf(PENDING_SCRIPT_INTENTIONS);
        try {
            applyScriptIntentions(definitions);
        } finally {
            PENDING_SCRIPT_INTENTIONS.clear();
            collectingScriptReload = false;
        }
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        Map<ResourceLocation, String> currentFlowExtensions = NpcFlowExtensionRegistry.fingerprints();
        Set<ResourceLocation> changedFlowExtensions = new java.util.LinkedHashSet<>(
                preReloadFlowExtensionFingerprints.keySet());
        changedFlowExtensions.addAll(currentFlowExtensions.keySet());
        preReloadFlowExtensionFingerprints = Map.of();
        if (!changedFlowExtensions.isEmpty() && server != null) {
            Set<ResourceLocation> affected = new java.util.LinkedHashSet<>(changedFlowExtensions);
            affected.addAll(NpcFlowTemplateRegistry.getInstance().refreshResolvedHashes());
            NpcAiDependencyIndex.resetAffected(server, affected);
        }
        broadcastNpcAiDescriptors(server);
    }

    private static void applyScriptIntentions(List<ScriptIntentionDefinition> definitions) {
        Set<ResourceLocation> ids = new java.util.LinkedHashSet<>(activeScriptFingerprints.keySet());
        definitions.forEach(definition -> ids.add(definition.id()));
        AttentionMind.replaceScriptIntentions(definitions);
        Map<ResourceLocation, String> next = new java.util.LinkedHashMap<>();
        definitions.forEach(definition -> next.put(definition.id(),
                NpcIntentionDependencyGraph.fingerprint(definition.id())));
        Set<ResourceLocation> directlyChanged = new java.util.LinkedHashSet<>();
        ids.forEach(id -> {
            if (!java.util.Objects.equals(activeScriptFingerprints.get(id), next.get(id))) directlyChanged.add(id);
        });
        activeScriptFingerprints = Map.copyOf(next);
        if (directlyChanged.isEmpty()) return;
        Set<ResourceLocation> changed = new java.util.LinkedHashSet<>(
                NpcIntentionDependencyGraph.affectedAssets(directlyChanged));
        changed.addAll(NpcFlowTemplateRegistry.getInstance().refreshResolvedHashes());
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) NpcAiDependencyIndex.resetAffected(server, changed);
    }

    @Info("清空上一次 server reload 注册的流程扩展")
    public static void clearFlowExtensions() {
        NpcFlowExtensionRegistry.clearScriptExtensions();
    }

    @Info("注册服务端权威的自定义流程触发器描述")
    public static void registerFlowTrigger(String id, CompoundTag parameters, CompoundTag display) {
        NpcFlowExtensionRegistry.registerTrigger(id, parameters, display);
    }

    @Info("注册带能力和版本描述的服务端权威自定义流程触发器")
    public static void registerFlowTrigger(String id, CompoundTag parameters, List<String> capabilities,
                                           CompoundTag display, int schemaVersion) {
        NpcFlowExtensionRegistry.registerTrigger(id, parameters, capabilities, display, schemaVersion);
    }

    @Info("注册服务端权威的自定义流程条件")
    public static void registerFlowCondition(String id, CompoundTag parameters, CompoundTag display,
                                             NpcFlowExtensionRegistry.FlowCondition condition) {
        NpcFlowExtensionRegistry.registerCondition(id, parameters, display, condition);
    }

    @Info("注册带能力和版本描述的服务端权威自定义流程条件")
    public static void registerFlowCondition(String id, CompoundTag parameters, List<String> capabilities,
                                             CompoundTag display, int schemaVersion,
                                             NpcFlowExtensionRegistry.FlowCondition condition) {
        NpcFlowExtensionRegistry.registerCondition(id, parameters, capabilities, display, schemaVersion, condition);
    }

    @Info("触发 NPC 的自定义外层流程")
    public static void triggerFlow(CustomNpc npc, String trigger, LivingEntity target) {
        npc.triggerNpcFlow(trigger, target);
    }
}
