package com.viscript.npc.gui.ai;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.viscript.npc.network.c2s.C2SPayload;
import com.viscript.npc.npc.ai.data.AiProgram;
import com.viscript.npc.npc.ai.runtime.AiExamples;
import com.viscript.npc.util.NpcActionFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

/** 当前服务器提供的行为功能文件快照；切换连接后清除旧服务器的数据。 */
public final class AiServerActions {
    private static ClientPacketListener connection;
    private static final Map<String, AiProgram> actions = new TreeMap<>();
    private static final Map<String, Request> pending = new HashMap<>();
    private record Request(Map<String, AiProgram> entries, Runnable updated) {}

    private static void checkConnection() {
        var current = Minecraft.getInstance().getConnection();
        if (connection == current) return;
        connection = current;
        actions.clear();
        pending.clear();
    }

    public static List<String> ids() {
        checkConnection();
        return List.copyOf(actions.keySet());
    }

    public static List<String> choices() {
        var result = new TreeSet<>(ids());
        AiExamples.NAMES.forEach(name -> result.add("examples/" + name));
        return List.copyOf(result);
    }

    public static AiProgram load(String id) {
        checkConnection();
        var program = actions.get(id);
        if (program != null) return program;
        return NpcActionFiles.isBuiltin(id) ? AiExamples.action(id.substring(9)) : null;
    }

    public static void request(Runnable updated) {
        checkConnection();
        if (connection == null) { updated.run(); return; }
        String requestId = UUID.randomUUID().toString();
        pending.put(requestId, new Request(new TreeMap<>(), updated));
        RPCPacketDistributor.rpcToServer(C2SPayload.REQUEST_NPC_ACTION_LIBRARY, requestId);
    }

    /**
     * 接收一个服务端行为定义，并在完整列表到达后替换当前快照。
     *
     * @param requestId 请求标识
     * @param fileId 行为功能文件的逻辑标识；完成消息中为空
     * @param definition 行为定义的 NBT 数据
     * @param complete 是否已发送全部行为
     */
    public static void receive(String requestId, String fileId, CompoundTag definition, boolean complete) {
        checkConnection();
        var request = pending.get(requestId);
        if (request == null) return;
        if (!complete) {
            var program = new AiProgram();
            program.deserializeNBT(Platform.getFrozenRegistry(), definition);
            request.entries.put(fileId, program);
            return;
        }
        pending.remove(requestId);
        actions.clear();
        actions.putAll(request.entries);
        request.updated.run();
    }
}
