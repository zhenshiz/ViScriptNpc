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
    @Configurable(name = "npcConfig.npcAI.enabled")
    private boolean enabled = true;

    @Override
    public ResourceLocation getEditorPage() {
        return NpcEditorPageIds.AI;
    }
}
