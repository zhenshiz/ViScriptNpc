package com.viscript.npc.util;

import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.ai.runtime.AiValidation;
import com.viscript.npc.npc.data.ai.NpcAI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/** 将完整 AI 快照应用到同一标记的已加载 NPC，不修改共享行为库文件。 */
public final class NpcAiUpdates {
    private NpcAiUpdates() {}

    /**
     * 完整校验成功后，替换服务器各维度中标记精确匹配的存活 NPC 的 AI。
     * 外观、生命值、位置和其他配置保持原值。
     *
     * @param server 拥有这些实体的服务器
     * @param marker NPC 标记，不允许为空白
     * @param snapshot AI 配置及其完整行为依赖包
     * @return 本次更新的 NPC 数量
     * @throws IllegalArgumentException 标记、树或任一依赖无效
     */
    public static int apply(MinecraftServer server, String marker, CompoundTag snapshot) {
        if (marker.isBlank()) throw new IllegalArgumentException("viscript_npc.ai.save.empty_marker");
        NpcAI ai = new NpcAI();
        ai.deserializeNBT(server.registryAccess(), snapshot);
        var dependencies = NpcAiPublication.readDependencies(snapshot.getCompound(NpcAiPublication.DEPENDENCIES));
        if (!ai.getProgram().kind.equals("tree")) throw new IllegalArgumentException("Expected NPC behavior tree");
        AiValidation.requireValid(ai.getProgram(), dependencies::get);
        int count = 0;
        for (var level : server.getAllLevels()) for (var entity : level.getAllEntities()) {
            if (entity instanceof CustomNpc npc && npc.isAlive() && marker.equals(npc.getNpcType())) {
                npc.getAiController().apply(ai.getProgram(), dependencies, ai.isEnabled());
                count++;
            }
        }
        return count;
    }
}
