package com.viscript.npc.gui.ai.node.action;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.viscript.npc.gui.ai.AiGraph;
import com.viscript.npc.gui.ai.AiGraphNode;

/** 在执行实例固定的中心附近寻找可站立巡逻点。 */
@NodeAttribute(name = "viscript_npc:patrol", group = "action", graphTypes = AiGraph.class)
public final class PatrolNode extends AiGraphNode {
    public PatrolNode() { super("patrol"); }
}
