package com.viscript.npc.gui.ai.node.action;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.viscript.npc.gui.ai.AiGraph;
import com.viscript.npc.gui.ai.AiGraphNode;

@NodeAttribute(name = "viscript_npc:failure", group = "flow", graphTypes = AiGraph.class)
public final class FailureNode extends AiGraphNode {
    public FailureNode() { super("failure"); }
}
