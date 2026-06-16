package com.viscript.npc.gui.edit.view;

import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.GraphEditorView;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.gui.edit.page.INpcEditorPage;
import com.viscript.npc.npc.data.ai.NpcAI;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public class NpcBehaviorGraphView extends GraphEditorView implements INpcEditorSlotView {
    @Nullable
    private NPCProject project;

    public NpcBehaviorGraphView() {
        super(NpcBehaviorDebugGraphView::new);
        setName(ViScriptNpc.MOD_ID + ".editor.view.ai_graph");
    }

    public void applyDebugSnapshot(CompoundTag snapshot) {
        if (graphView instanceof NpcBehaviorDebugGraphView debugGraphView) {
            debugGraphView.applyDebugSnapshot(snapshot);
        }
    }

    public void clearDebugSnapshot() {
        if (graphView instanceof NpcBehaviorDebugGraphView debugGraphView) {
            debugGraphView.clearDebugSnapshot();
        }
    }

    public void setSelectionListener(@Nullable BiConsumer<NpcBehaviorDebugGraphView, com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel> selectionListener) {
        if (graphView instanceof NpcBehaviorDebugGraphView debugGraphView) {
            debugGraphView.setSelectionListener(selectionListener);
        }
    }

    @Nullable
    public AbstractNodeModel getSelectedNode() {
        if (graphView instanceof NpcBehaviorDebugGraphView debugGraphView) {
            return debugGraphView.getSelectedNode();
        }
        return null;
    }

    @Override
    public void onViewSelected(NpcEditor editor, NPCProject project, INpcEditorPage page) {
        if (this.project != project || getGraph() == null) {
            if (this.project != null && this.project != project) {
                this.project.setBehaviorGraphSnapshotSupplier(null);
            }
            loadFromProject(project);
        }
        syncGraphToProject();
    }

    @Override
    public void screenTick() {
        super.screenTick();
        var modularUI = getModularUI();
        if (modularUI != null && (modularUI.getTickCounter() & 15) == 0) {
            syncGraphToProject();
        }
    }

    @Override
    public NpcBehaviorGraphView clear() {
        syncGraphToProject();
        if (project != null) {
            project.setBehaviorGraphSnapshotSupplier(null);
        }
        clearDebugSnapshot();
        project = null;
        super.clear();
        return this;
    }

    private void loadFromProject(NPCProject project) {
        NpcAI ai = project.npc.npcConfig.getNpcData(NpcAI.class);
        NpcBehaviorGraph graph = new NpcBehaviorGraph();
        if (ai != null) {
            CompoundTag graphTag = ai.getBehaviorGraph();
            if (!graphTag.isEmpty()) {
                graph.graphModel.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(), graphTag);
            }
        }
        graph.ensureDefaultRoot();
        loadGraph(graph, tag -> {
            NpcAI currentAi = project.npc.npcConfig.getNpcData(NpcAI.class);
            if (currentAi != null) {
                currentAi.setBehaviorGraph(tag);
            }
        });
        // GraphEditorView.loadGraph() clears the view first, so bind the project after it returns.
        this.project = project;
        project.setBehaviorGraphSnapshotSupplier(this::serializeGraph);
    }

    public void syncGraphToProject() {
        if (project == null || getGraph() == null) {
            return;
        }
        NpcAI ai = project.npc.npcConfig.getNpcData(NpcAI.class);
        if (ai != null) {
            ai.setBehaviorGraph(serializeGraph());
        }
    }
}
