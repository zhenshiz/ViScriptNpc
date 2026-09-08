package com.viscript.npc.npc.ai.editor;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import org.joml.Vector2f;

public class FlowGraphModel extends CustomGraphModelImpl {
    public FlowGraphModel(Graph graph) { super(graph); }
    public FlowNodeModel createNodeModel(Node node, Vector2f position) {
        return createNodeWithType(FlowNodeModel.class, "", position, null, value -> value.initCustomNode(node), null);
    }
    protected AbstractNodeModel createNodeFromDiscriminator(String type) {
        return "custom".equals(type) ? new FlowNodeModel() : super.createNodeFromDiscriminator(type);
    }
}
