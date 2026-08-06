package com.viscript.npc.npc.data.ai;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.viscript.npc.gui.edit.page.NpcEditorPageIds;
import com.viscript.npc.npc.data.INpcData;
import com.viscript.npc.ViScriptNpc;
import lombok.Data;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.thexeler.api.MindMachineConfig;

@Data
@LDLRegister(name = "npc_ai", registry = INpcData.ID)
public class NpcAI implements INpcData {
    @Configurable(name = "npcConfig.npcAI.enabled")
    @Persisted
    private boolean enabled = true;

    @Configurable(name = "npcConfig.npcAI.tickRate", tips = "npcConfig.npcAI.tickRate.tips")
    @ConfigNumber(range = {1, 100}, type = ConfigNumber.Type.INTEGER)
    @Persisted
    private int tickRate = 8;

    @Configurable(name = "npcConfig.npcAI.source")
    @Persisted
    private NpcAiSource source = NpcAiSource.EMBEDDED;

    @Persisted
    private CompoundTag embeddedGraph = new CompoundTag();

    @Persisted
    private CompoundTag embeddedFlow = createEmptyFlow();

    @Persisted
    private CompoundTag embeddedParameters = new CompoundTag();

    @Persisted
    private ResourceLocation templateId = ViScriptNpc.id("empty");

    @Persisted
    private CompoundTag templateParameterOverrides = new CompoundTag();

    @Persisted
    private String resolvedHash = "";

    @Persisted
    private String embeddedDependencyHash = "";

    @Override
    public ResourceLocation getEditorPage() {
        return NpcEditorPageIds.AI;
    }

    public MindMachineConfig toConfig() {
        return new MindMachineConfig(tickRate);
    }

    public boolean usesTemplate() {
        return source == NpcAiSource.TEMPLATE;
    }

    public void useEmbeddedFlow(CompoundTag flow) {
        source = NpcAiSource.EMBEDDED;
        embeddedFlow = flow == null || flow.isEmpty() ? createEmptyFlow() : flow.copy();
        embeddedParameters = new CompoundTag();
        templateParameterOverrides = new CompoundTag();
        resolvedHash = "";
    }

    public void useEmbeddedGraph(CompoundTag graph, CompoundTag compiledFlow) {
        useEmbeddedGraph(graph, compiledFlow,
                source == NpcAiSource.EMBEDDED ? embeddedParameters : new CompoundTag());
    }

    public void useEmbeddedGraph(CompoundTag graph, CompoundTag compiledFlow, CompoundTag parameters) {
        source = NpcAiSource.EMBEDDED;
        embeddedGraph = graph == null ? new CompoundTag() : graph.copy();
        embeddedFlow = compiledFlow == null || compiledFlow.isEmpty() ? createEmptyFlow() : compiledFlow.copy();
        embeddedParameters = parameters == null ? new CompoundTag() : parameters.copy();
        templateParameterOverrides = new CompoundTag();
        resolvedHash = "";
    }

    public void useTemplate(ResourceLocation id, CompoundTag overrides) {
        source = NpcAiSource.TEMPLATE;
        templateId = java.util.Objects.requireNonNull(id, "id");
        templateParameterOverrides = overrides == null ? new CompoundTag() : overrides.copy();
        embeddedDependencyHash = "";
    }

    private static CompoundTag createEmptyFlow() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 1);
        tag.put("rules", new net.minecraft.nbt.ListTag());
        return tag;
    }
}
