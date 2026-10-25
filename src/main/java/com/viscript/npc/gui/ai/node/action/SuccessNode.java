package com.viscript.npc.gui.ai.node.action;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.viscript.npc.gui.ai.AiGraph;
import com.viscript.npc.gui.ai.AiGraphNode;

@NodeAttribute(name = "viscript_npc:success", group = "flow", graphTypes = AiGraph.class)
public final class SuccessNode extends AiGraphNode {
    public SuccessNode() { super("success"); }
}
