package com.viscript.npc.gui.ai.node.flow;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.viscript.npc.gui.ai.AiGraph;
import com.viscript.npc.gui.ai.AiGraphNode;

@NodeAttribute(name = "viscript_npc:finish_success", group = "action", graphTypes = AiGraph.class)
public final class FinishSuccessNode extends AiGraphNode {
    public FinishSuccessNode() { super("finish_success"); }
}
