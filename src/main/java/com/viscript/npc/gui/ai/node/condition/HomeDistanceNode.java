package com.viscript.npc.gui.ai.node.condition;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.viscript.npc.gui.ai.AiGraph;
import com.viscript.npc.gui.ai.AiGraphNode;

@NodeAttribute(name = "viscript_npc:home_distance", group = "condition", graphTypes = AiGraph.class)
public final class HomeDistanceNode extends AiGraphNode {
    public HomeDistanceNode() { super("home_distance"); }
}
