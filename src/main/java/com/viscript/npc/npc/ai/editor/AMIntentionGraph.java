package com.viscript.npc.npc.ai.editor;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.GraphNodeRegistry;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.viscript.npc.ViScriptNpc;

import java.util.List;

public class AMIntentionGraph extends Graph {
    public static final GraphNodeRegistry NODE_REGISTRY = GraphNodeRegistry.create(
            ViScriptNpc.id("am_intention"), AMIntentionGraph.class);

    protected CustomGraphModelImpl createGraphModel() { return new FlowGraphModel(this); }
    public List<Class<? extends Node>> getSupportNodes() { return NODE_REGISTRY.getNodeClasses(); }
    public List<TypeHandle> getSupportTypes() {
        return List.of(TypeHandles.BOOL, TypeHandles.INT, TypeHandles.FLOAT, TypeHandles.DOUBLE, TypeHandles.STRING);
    }
    public List<TypeHandle> getLibrarySupportTypes() { return List.of(); }
}
