package com.viscript.npc.npc.ai.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.networking.rpc.*;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.test.NpcTestSceneProject;
import com.viscript.npc.npc.*;
import com.viscript.npc.npc.ai.runtime.AiValidation;
import com.viscript.npc.npc.data.ai.NpcAI;
import com.viscript.npc.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** 测试快照仅作用于当前测试场地内的 NPC，不写入发布目录。 */
public final class NpcAiTestControl {
    public static final String OPEN = ViScriptNpc.MOD_ID + ":test_ai_draft";
    public static final String SNAPSHOT = ViScriptNpc.MOD_ID + ":ai_debug_snapshot";

    @RPCPacket(OPEN)
    public static void open(RPCSender sender, CompoundTag config) {
        if (sender.isServer()) return;
        ServerPlayer player = sender.asPlayer();
        if (!NpcAiTestLevelService.isAuthorized(player)) return;
        try {
            var dependencies = NpcAiPublication.readDependencies(config.getCompound(NpcAiPublication.DEPENDENCIES));
            NpcAI ai = new NpcAI(); ai.deserializeNBT(player.registryAccess(), config);
            AiValidation.requireValid(ai.getProgram(), dependencies::get);
            if (!NpcAiTestLevelService.enter(player)) return;
            var center = NpcAiTestLevelService.getArenaCenter(player);
            CustomNpc npc = NpcRegister.CUSTOM_NPC.get().create(player.serverLevel());
            if (center == null || npc == null) return;
            npc.readAdditionalSaveData(config);
            npc.moveTo(Vec3.atBottomCenterOf(center.above()).add(2, 0, 0));
            npc.getAiController().preview(ai.getProgram(), dependencies);
            config.getList("ai_breakpoints", net.minecraft.nbt.Tag.TAG_STRING)
                    .forEach(id -> npc.getAiController().debugger.breakpoints.add(id.getAsString()));
            player.serverLevel().addFreshEntity(npc);
            NpcTestSceneProject project = new NpcTestSceneProject(); project.initNewProject();
            CompoundTag tag = project.serializeNBT(player.registryAccess());
            tag.putLong(NpcAiTestEnvironment.ARENA_CENTER_TAG, center.asLong());
            tag.putInt("ai_selected_npc", npc.getId()); tag.putBoolean("ai_new_editor", true);
            tag.putBoolean("ai_detach_scene", config.getBoolean("ai_detach_scene"));
            ViScriptNpcServerUtil.openNpcEditor(player, tag);
        } catch (RuntimeException exception) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(exception.getMessage()), false);
        }
    }

    public static void control(ServerPlayer player, String action, CompoundTag payload) {
        var region = NpcAiTestLevelService.getArenaRegion(player);
        if (region == null || !(player.serverLevel().getEntity(payload.getInt("entityId")) instanceof CustomNpc npc)
                || !NpcAiTestEnvironment.isEditable(region, npc.getBoundingBox())) return;
        if (action.equals("ai_toggle")) {
            if (!npc.getNpcAI().isEnabled() || npc.getAiController().snapshot().paused) {
                npc.getNpcAI().setEnabled(true); npc.getAiController().resume();
            } else npc.getAiController().pause();
        }
        else if (action.equals("ai_run")) { npc.getNpcAI().setEnabled(true); npc.getAiController().resume(); }
        else if (action.equals("ai_stop")) npc.getAiController().pause();
        else if (action.equals("ai_step")) npc.getAiController().step();
        else if (action.equals("ai_step_tick")) npc.getAiController().stepTick();
        else if (action.equals("ai_breakpoint")) {
            var debug = npc.getAiController().debugger;
            debug.enabled = true;
            String id = payload.getString("nodeId");
            if (payload.getBoolean("enabled") && debug.breakpoints.size() < 1024) debug.breakpoints.add(id);
            else debug.breakpoints.remove(id);
        }
        else if (action.equals("ai_logging")) npc.getAiController().debugger.logging = payload.getBoolean("enabled");
        else if (action.equals("ai_clear_log")) npc.getAiController().debugger.events.clear();
        else if (action.equals("ai_target")) {
            if (payload.getInt("targetId") < 0) npc.setTarget(null);
            else if (player.serverLevel().getEntity(payload.getInt("targetId")) instanceof net.minecraft.world.entity.LivingEntity target
                    && target != npc && target.isAlive() && NpcAiTestEnvironment.isEditable(region, target.getBoundingBox())
                    && com.viscript.npc.compat.team.NpcFactionBridge.canHurt(npc, target)) npc.setTarget(target);
        }
        RPCPacketDistributor.rpcToPlayer(player, SNAPSHOT, npc.getAiController().snapshot().serializeNBT(player.registryAccess()));
    }

    @RPCPacket(SNAPSHOT)
    public static void receive(RPCSender sender, CompoundTag tag) {
        if (sender.isServer()) com.viscript.npc.gui.ai.AiClientDebug.accept(tag);
    }
}
