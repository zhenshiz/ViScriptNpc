package com.viscript.npc.gui.ai;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.viscript.npc.network.c2s.C2SPayload;
import com.viscript.npc.npc.ai.data.AiDebugSnapshot;
import com.viscript.npc.npc.ai.test.NpcAiTestEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/** 当前被测 NPC 的限频调试快照，供场景和图画布共同读取。 */
public final class AiClientDebug {
    public static AiDebugSnapshot latest = new AiDebugSnapshot();
    private static int selected = -1;
    private static long requestedTick = Long.MIN_VALUE;
    public static void accept(CompoundTag tag) {
        var snapshot = new AiDebugSnapshot(); snapshot.deserializeNBT(Platform.getFrozenRegistry(), tag);
        if (snapshot.entityId == selected) latest = snapshot;
    }
    public static void select(int id) {
        if (selected != id) { selected = id; latest = new AiDebugSnapshot(); requestedTick = Long.MIN_VALUE; }
    }
    public static void poll() {
        var level = Minecraft.getInstance().level;
        if (level == null || !NpcAiTestEnvironment.isTestLevel(level) || selected < 0) return;
        long tick = level.getGameTime();
        if (requestedTick != Long.MIN_VALUE && tick - requestedTick < 5) return;
        requestedTick = tick; command("ai_debug", selected);
    }
    public static void command(String command, int id) {
        command(command, id, new CompoundTag());
    }
    public static int selected() { return selected; }
    public static void command(String command, int id, CompoundTag payload) {
        if (id < 0) return;
        select(id);
        payload.putInt("entityId", id);
        RPCPacketDistributor.rpcToServer(C2SPayload.APPLY_NPC_AI_TEST_ACTION, command, payload);
    }
}
