package com.viscript.npc.npc.ai.editor;

import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.CustomNodeModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphElement;
import org.jetbrains.annotations.Nullable;

public class FlowNodeModel extends CustomNodeModelImpl {
    public FlowNodeModel() {
        setCapability(com.lowdragmc.lowdraglib2.nodegraphtookit.model.Capabilities.COLORABLE, false);
    }

    @Override
    public @Nullable GraphElement<?> createElementUI() {
        return new NpcAiDebugNodeElement(this);
    }
}
