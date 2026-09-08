package com.viscript.npc.npc.ai.editor;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.GraphEditorView;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.npc.data.ai.NpcAI;
import net.minecraft.nbt.CompoundTag;

import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import java.util.stream.Collectors;

public class NpcFlowGraphView extends GraphEditorView {
    private final NPCProject project;
    private final NpcFlowGraph graph;

    public NpcFlowGraphView(NPCProject project) {
        this.project = project;
        this.graph = new NpcFlowGraph();
        NpcAI ai = project.npc.getNpcData(NpcAI.class);
        if (!ai.getEmbeddedGraph().isEmpty()) {
            graph.graphModel.deserializeNBT(Platform.getFrozenRegistry(), ai.getEmbeddedGraph());
        }
        loadGraph(graph, this::saveGraph);
        project.setFlowEditorSnapshotSupplier(this::snapshot);
        project.setSelectedFlowNodesSupplier(() -> getCurrentView().getSelected().stream()
                .filter(AbstractNodeModel.class::isInstance)
                .map(AbstractNodeModel.class::cast)
                .map(AbstractNodeModel::getUid)
                .collect(Collectors.toUnmodifiableSet()));
    }

    public NPCProject.FlowEditorSnapshot snapshot() {
        return new NPCProject.FlowEditorSnapshot(serializeGraph(), NpcFlowGraphCompiler.compile(graph));
    }

    private void saveGraph(CompoundTag graphTag) {
        NPCProject.FlowEditorSnapshot snapshot = snapshot();
        project.npc.getNpcData(NpcAI.class).useEmbeddedGraph(graphTag, snapshot.compiledFlow());
    }
}
