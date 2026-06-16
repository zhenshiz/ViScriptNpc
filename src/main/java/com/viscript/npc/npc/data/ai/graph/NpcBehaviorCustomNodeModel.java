package com.viscript.npc.npc.data.ai.graph;

import com.lowdragmc.lowdraglib2.nodegraphtookit.model.Capabilities;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.ChangeHint;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.ContextualMenuHelpers;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.ContextualMenuItem;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.GraphModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.CustomNodeModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.IPort;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.ModelState;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.viscript.npc.npc.data.ai.graph.node.SelectorNode;
import com.viscript.npc.npc.data.ai.graph.node.SequenceNode;
import com.viscript.npc.npc.data.ai.graph.node.NpcBehaviorNode;
import lombok.Getter;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class NpcBehaviorCustomNodeModel extends CustomNodeModelImpl {
    private static final int NO_USER_NODE_COLOR = 0;

    @Persisted
    @Getter
    private String comment = "";
    @Persisted
    @Getter
    private boolean breakpoint;
    @Persisted
    @Getter
    private NpcBehaviorAbortType abortType = NpcBehaviorAbortType.NONE;

    public NpcBehaviorCustomNodeModel() {
        setCapability(Capabilities.COLORABLE, false);
        clearNodeColor();
    }

    @Override
    public void setGraphModel(GraphModel value) {
        super.setGraphModel(value);
        clearNodeColor();
    }

    @Override
    public List<ContextualMenuItem> getContextualMenuItems() {
        return super.getContextualMenuItems().stream()
                .filter(item -> item != ContextualMenuHelpers.COLOR_ITEM)
                .toList();
    }

    @Override
    public int getElementColor() {
        return NO_USER_NODE_COLOR;
    }

    @Override
    public void setColor(int color) {
        clearNodeColor();
    }

    @Override
    public int getDefaultColor() {
        return NO_USER_NODE_COLOR;
    }

    @Override
    public boolean hasUserColor() {
        return false;
    }

    @Override
    public void resetColor() {
        clearNodeColor();
    }

    @Override
    public Component getTitle() {
        String alias = getName();
        if (alias != null && !alias.isBlank()) {
            return Component.literal(alias);
        }
        return super.getTitle();
    }

    @Override
    public Component getTooltip() {
        return comment == null || comment.isBlank() ? super.getTooltip() : Component.literal(comment);
    }

    public void setComment(String comment) {
        String next = comment == null ? "" : comment;
        if (Objects.equals(this.comment, next)) {
            return;
        }
        this.comment = next;
        markChanged(ChangeHint.STYLE);
    }

    public void setBreakpoint(boolean breakpoint) {
        if (this.breakpoint == breakpoint) {
            return;
        }
        this.breakpoint = breakpoint;
        markChanged(ChangeHint.STYLE);
    }

    public void setAbortType(NpcBehaviorAbortType abortType) {
        NpcBehaviorAbortType next = abortType == null ? NpcBehaviorAbortType.NONE : abortType;
        if (this.abortType == next) {
            return;
        }
        this.abortType = next;
        markChanged(ChangeHint.DATA);
    }

    public boolean supportsAbortType() {
        return getNode() instanceof SequenceNode || getNode() instanceof SelectorNode;
    }

    public boolean isDisabledByParent() {
        return isDisabledByParent(getNode(), new HashSet<>());
    }

    private void markChanged(ChangeHint hint) {
        GraphModel graphModel = getGraphModel();
        if (graphModel != null) {
            graphModel.getCurrentGraphChangeDescription().addChangedModel(this, hint);
        }
    }

    private void clearNodeColor() {
        elementColor = NO_USER_NODE_COLOR;
        userColor = false;
    }

    private static boolean isDisabledByParent(Node node, Set<UUID> visited) {
        if (node == null || node.getNodeModel() == null) {
            return false;
        }
        if (!visited.add(node.getNodeModel().getUid())) {
            return false;
        }
        IPort inputPort = node.getInputPortById(NpcBehaviorNode.PORT_IN);
        if (!(inputPort instanceof PortModel portModel)) {
            return false;
        }
        for (PortModel connectedPort : portModel.getConnectedPorts()) {
            if (!(connectedPort.getNodeModel() instanceof ICustomNodeModel customNodeModel)
                    || !(customNodeModel.getNode() instanceof Node parent)) {
                continue;
            }
            if (parent.getNodeModel().getState() == ModelState.DISABLED || isDisabledByParent(parent, visited)) {
                return true;
            }
        }
        return false;
    }
}
