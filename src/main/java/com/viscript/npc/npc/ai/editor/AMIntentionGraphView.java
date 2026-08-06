package com.viscript.npc.npc.ai.editor;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.GraphEditorView;
import com.viscript.npc.gui.edit.AMIntentionProject;
import net.minecraft.nbt.CompoundTag;

import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import java.util.stream.Collectors;

public class AMIntentionGraphView extends GraphEditorView {
    private final AMIntentionProject project;
    private final AMIntentionGraph graph = new AMIntentionGraph();

    public AMIntentionGraphView(AMIntentionProject project) {
        this.project = project;
        CompoundTag saved = project.getGraph();
        if (!saved.isEmpty()) graph.graphModel.deserializeNBT(Platform.getFrozenRegistry(), saved);
        loadGraph(graph, ignored -> project.setSnapshotSupplier(this::snapshot));
        project.setSnapshotSupplier(this::snapshot);
        project.setSelectedNodesSupplier(() -> getCurrentView().getSelected().stream()
                .filter(AbstractNodeModel.class::isInstance)
                .map(AbstractNodeModel.class::cast)
                .map(AbstractNodeModel::getUid)
                .collect(Collectors.toUnmodifiableSet()));
    }

    public AMIntentionProject.Snapshot snapshot() {
        return new AMIntentionProject.Snapshot(serializeGraph(), AMIntentionGraphCompiler.compile(graph));
    }
}
