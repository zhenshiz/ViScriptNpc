package com.viscript.npc.gui.ai;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.*;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.ai.runtime.AiOperations;
import com.viscript.npc.npc.ai.runtime.AiValidation;
import com.viscript.npc.util.NpcActionFiles;
import net.minecraft.network.chat.Component;

import java.util.List;

/** 行为树与阶段流程共用画布基础，通过图上下文限制节点目录。 */
public final class AiGraph extends Graph {
    public static final GraphNodeRegistry REGISTRY = GraphNodeRegistry.create(ViScriptNpc.id("ai_graph"), AiGraph.class);
    public final String kind;
    public java.util.function.Function<String, com.viscript.npc.npc.ai.data.AiProgram> actionResolver = NpcActionFiles::loadLocal;
    public final java.util.Map<String, Component> nodeErrors = new java.util.HashMap<>();
    public AiGraph(String kind) { this.kind = kind; model().metadata.kind = kind; }
    public AiGraphModel model() { return (AiGraphModel) graphModel; }
    @Override protected CustomGraphModelImpl createGraphModel() { return new AiGraphModel(this); }
    @Override public List<Class<? extends Node>> getSupportNodes() {
        String prefix = ViScriptNpc.MOD_ID + ":";
        return REGISTRY.getNodeClasses().stream().filter(type -> {
            var attribute = type.getAnnotation(com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute.class);
            return AiGraphNode.class.isAssignableFrom(type) && attribute != null
                    && attribute.name().startsWith(prefix)
                    && AiOperations.supports(kind, attribute.name().substring(prefix.length()));
        }).toList();
    }
    @Override public List<TypeHandle> getSupportTypes() { return AiVariables.TYPES; }
    @Override public List<TypeHandle> getLibrarySupportTypes() { return AiVariables.TYPES; }
    @Override public java.util.Set<com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind> getSupportedSubgraphVariableKinds() {
        return kind != null && kind.equals("action")
                ? java.util.Set.of(com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind.INPUT) : java.util.Set.of();
    }
    @Override public void onGraphChanged(GraphLogger logger) {
        var program = model().compile();
        nodeErrors.clear();
        for (var issue : AiValidation.validate(program, actionResolver)) {
            Object context = graphModel.getNodeModels().stream().filter(java.util.Objects::nonNull)
                    .filter(n -> n.getUid().toString().equals(issue.nodeId())).findFirst().orElse(null);
            var message = Component.translatable("viscript_npc.ai.error." + issue.key(), issue.detail());
            nodeErrors.putIfAbsent(issue.nodeId(), message);
            logger.error(message, context);
        }
        var connected = program.nodes.stream().map(n -> n.id).collect(java.util.stream.Collectors.toSet());
        for (var node : graphModel.getNodeModels()) if (node instanceof AiNodeModel ai && !connected.contains(ai.getUid().toString()))
            logger.warning(Component.translatable("viscript_npc.ai.warning.disconnected"), ai);
    }
}
