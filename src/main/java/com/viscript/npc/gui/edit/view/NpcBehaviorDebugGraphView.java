package com.viscript.npc.gui.edit.view;

import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphView;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.ModelElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.Model;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.ModelState;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorCustomNodeModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public class NpcBehaviorDebugGraphView extends GraphView {
    private final Map<UUID, DebugNodeState> debugStates = new HashMap<>();
    @Nullable
    private BiConsumer<NpcBehaviorDebugGraphView, AbstractNodeModel> selectionListener;
    @Nullable
    private AbstractNodeModel lastNotifiedSelection;

    @Override
    public GraphView loadGraph(@Nullable Graph graph) {
        debugStates.clear();
        lastNotifiedSelection = null;
        GraphView result = super.loadGraph(graph);
        notifySelectionChanged();
        return result;
    }

    @Override
    public void clearGraph() {
        super.clearGraph();
        debugStates.clear();
        lastNotifiedSelection = null;
        notifySelectionChanged();
    }

    @Override
    public @Nullable ModelElement createAndAddModelElement(@Nullable Model model) {
        if (model instanceof AbstractNodeModel nodeModel) {
            ModelElement element = getModelElement(model);
            if (element != null) {
                return element;
            }
            var elementUI = new NpcBehaviorNodeElement(nodeModel);
            if (addElement(elementUI)) {
                elementUI.doCompleteUpdate();
            }
            return elementUI;
        }
        return super.createAndAddModelElement(model);
    }

    public void applyDebugSnapshot(CompoundTag snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            clearDebugSnapshot();
            return;
        }

        Map<UUID, DebugNodeState> nextStates = new HashMap<>();
        String currentNode = snapshot.getString("currentNode");
        String breakpointNode = snapshot.getString("breakpointNode");
        ListTag nodes = snapshot.getList("nodes", Tag.TAG_COMPOUND);
        for (Tag tag : nodes) {
            if (!(tag instanceof CompoundTag nodeTag)) {
                continue;
            }
            UUID nodeId = parseUuid(nodeTag.getString("nodeId"));
            if (nodeId == null) {
                continue;
            }
            nextStates.put(nodeId, new DebugNodeState(
                    nodeTag.getString("status"),
                    nodeTag.getInt("visitIndex"),
                    nodeId.toString().equals(currentNode),
                    nodeTag.getBoolean("breakpoint"),
                    nodeId.toString().equals(breakpointNode)
            ));
        }

        if (!debugStates.equals(nextStates)) {
            debugStates.clear();
            debugStates.putAll(nextStates);
        }
    }

    public void clearDebugSnapshot() {
        debugStates.clear();
    }

    public void setSelectionListener(@Nullable BiConsumer<NpcBehaviorDebugGraphView, AbstractNodeModel> selectionListener) {
        this.selectionListener = selectionListener;
        notifySelectionChanged();
    }

    @Override
    public void endBatchSelection() {
        super.endBatchSelection();
        notifySelectionChanged();
    }

    @Override
    protected TreeBuilder.Menu createMenu(float mouseX, float mouseY) {
        TreeBuilder.Menu menu = super.createMenu(mouseX, mouseY);
        AbstractNodeModel selectedNode = selectedNode();
        if (selectedNode instanceof NpcBehaviorCustomNodeModel behaviorNode) {
            menu.crossLine();
            menu.leaf(behaviorNode.isBreakpoint()
                    ? "viscript_npc.ai.context.clear_breakpoint"
                    : "viscript_npc.ai.context.set_breakpoint", () -> behaviorNode.setBreakpoint(!behaviorNode.isBreakpoint()));
            menu.leaf(selectedNode.getState() == ModelState.DISABLED
                    ? "viscript_npc.ai.context.enable_node"
                    : "viscript_npc.ai.context.disable_node", () -> selectedNode.setState(
                            selectedNode.getState() == ModelState.DISABLED ? ModelState.ENABLED : ModelState.DISABLED));
        }
        return menu;
    }

    @Override
    public void screenTick() {
        super.screenTick();
        notifySelectionChangedIfNeeded();
    }

    @Nullable
    public DebugNodeState getDebugState(UUID nodeId) {
        return debugStates.get(nodeId);
    }

    @Nullable
    public AbstractNodeModel getSelectedNode() {
        return selectedNode();
    }

    @Nullable
    private AbstractNodeModel selectedNode() {
        if (getSelected().size() != 1) {
            return null;
        }
        Model model = getSelected().iterator().next();
        return model instanceof AbstractNodeModel nodeModel ? nodeModel : null;
    }

    private void notifySelectionChanged() {
        lastNotifiedSelection = selectedNode();
        if (selectionListener != null) {
            selectionListener.accept(this, lastNotifiedSelection);
        }
    }

    private void notifySelectionChangedIfNeeded() {
        AbstractNodeModel selectedNode = selectedNode();
        if (selectedNode != lastNotifiedSelection) {
            notifySelectionChanged();
        }
    }

    @Nullable
    private static UUID parseUuid(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record DebugNodeState(String status, int visitIndex, boolean current, boolean breakpoint, boolean breakpointHit) {
        public boolean visible() {
            return breakpointHit || status != null && !status.isBlank();
        }

        public boolean terminal() {
            return "SUCCESS".equals(status) || "FAILURE".equals(status) || "ABORTED".equals(status);
        }
    }
}
