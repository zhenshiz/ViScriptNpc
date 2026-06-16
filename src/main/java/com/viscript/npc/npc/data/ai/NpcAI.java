package com.viscript.npc.npc.data.ai;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.viscript.npc.gui.edit.page.NpcEditorPageIds;
import com.viscript.npc.npc.data.INpcData;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorGraph;
import com.viscript.npc.npc.data.ai.runtime.NpcBehaviorProgramCompiler;
import lombok.Data;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

@Data
@LDLRegister(name = "npc_ai", registry = INpcData.ID)
public class NpcAI implements INpcData {
    @Configurable(name = "npcConfig.npcAI.enabled")
    private boolean enabled = false;
    @Configurable(name = "npcConfig.npcAI.tickRate")
    @ConfigNumber(range = {1, 200}, type = ConfigNumber.Type.INTEGER)
    private int tickRate = 5;
    @Persisted
    private CompoundTag behaviorGraph = NpcBehaviorGraph.createDefaultGraphTag();
    @Persisted
    private CompoundTag behaviorProgram = new CompoundTag();

    @Override
    public ResourceLocation getEditorPage() {
        return NpcEditorPageIds.AI;
    }

    public CompoundTag getBehaviorGraph() {
        return behaviorGraph == null ? new CompoundTag() : behaviorGraph.copy();
    }

    public void setBehaviorGraph(CompoundTag behaviorGraph) {
        this.behaviorGraph = behaviorGraph == null ? new CompoundTag() : behaviorGraph.copy();
    }

    public CompoundTag getBehaviorProgram() {
        return behaviorProgram == null ? new CompoundTag() : behaviorProgram.copy();
    }

    public void setBehaviorProgram(CompoundTag behaviorProgram) {
        this.behaviorProgram = behaviorProgram == null ? new CompoundTag() : behaviorProgram.copy();
    }

    public CompoundTag compileBehaviorProgram(HolderLookup.Provider provider) {
        return NpcBehaviorProgramCompiler.compileToTag(getBehaviorGraph(), provider);
    }
}
