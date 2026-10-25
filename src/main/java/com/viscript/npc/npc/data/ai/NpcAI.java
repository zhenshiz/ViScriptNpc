package com.viscript.npc.npc.data.ai;

import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.viscript.npc.gui.edit.page.NpcEditorPageIds;
import com.viscript.npc.npc.data.INpcData;
import lombok.Data;
import net.minecraft.resources.ResourceLocation;

@Data
@LDLRegister(name = "npc_ai", registry = INpcData.ID)
public class NpcAI implements INpcData {
    @com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted
    private com.viscript.npc.npc.ai.data.AiProgram program = new com.viscript.npc.npc.ai.data.AiProgram();
    /** 随 NPC 保存的行为定义快照，防止同名文件的后续修改影响其他标记。 */
    @com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted
    private java.util.Map<String, com.viscript.npc.npc.ai.data.AiProgram> actionDefinitions = new java.util.LinkedHashMap<>();
    @Configurable(name = "npcConfig.npcAI.enabled")
    private boolean enabled = true;

    /**
     * 优先解析该 NPC 已提交的行为快照，再查询服务端行为库。
     *
     * @param id 行为的逻辑文件标识
     * @return 行为定义，找不到时返回 {@code null}
     */
    public com.viscript.npc.npc.ai.data.AiProgram resolveAction(String id) {
        var saved = actionDefinitions.get(id);
        return saved == null ? com.viscript.npc.util.NpcActionFiles.load(id) : saved;
    }

    @Override
    public ResourceLocation getEditorPage() {
        return NpcEditorPageIds.AI;
    }
}
