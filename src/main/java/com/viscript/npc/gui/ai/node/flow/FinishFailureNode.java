package com.viscript.npc.gui.ai.node.flow;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.viscript.npc.gui.ai.AiGraph;
import com.viscript.npc.gui.ai.AiGraphNode;

@NodeAttribute(name = "viscript_npc:finish_failure", group = "action", graphTypes = AiGraph.class)
public final class FinishFailureNode extends AiGraphNode {
    public FinishFailureNode() { super("finish_failure"); }
}
