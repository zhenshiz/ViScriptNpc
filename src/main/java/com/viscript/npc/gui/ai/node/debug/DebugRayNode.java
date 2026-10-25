package com.viscript.npc.gui.ai.node.debug;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.viscript.npc.gui.ai.AiGraph;
import com.viscript.npc.gui.ai.AiGraphNode;

@NodeAttribute(name = "viscript_npc:debug_ray", group = "debug", graphTypes = AiGraph.class)
public final class DebugRayNode extends AiGraphNode {
    public DebugRayNode() { super("debug_ray"); }
}
