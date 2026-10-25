package com.viscript.npc.gui.ai.node.action;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.viscript.npc.gui.ai.AiGraph;
import com.viscript.npc.gui.ai.AiGraphNode;

@NodeAttribute(name = "viscript_npc:wait_ground", group = "action", graphTypes = AiGraph.class)
public final class WaitGroundNode extends AiGraphNode {
    public WaitGroundNode() { super("wait_ground"); }
}
