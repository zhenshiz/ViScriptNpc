package com.viscript.npc.gui.ai.node.tree;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.viscript.npc.gui.ai.AiGraph;
import com.viscript.npc.gui.ai.AiGraphNode;

@NodeAttribute(name = "viscript_npc:cooldown", group = "flow", graphTypes = AiGraph.class)
public final class CooldownNode extends AiGraphNode {
    public CooldownNode() { super("cooldown"); }
}
