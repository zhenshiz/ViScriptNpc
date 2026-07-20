package com.viscript.npc.npc.data.ai.graph;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.GraphLogger;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.GraphNodeRegistry;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.IPort;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.data.ai.graph.node.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.joml.Vector2f;

import java.util.*;

public class NpcBehaviorGraph extends Graph {
    public static final GraphNodeRegistry NODE_REGISTRY = GraphNodeRegistry.create(ViScriptNpc.id("npc_behavior"), NpcBehaviorGraph.class);

    public static NpcBehaviorGraph createDefaultExample() {
        NpcBehaviorGraph graph = new NpcBehaviorGraph();
        graph.buildDefaultExample(null);
        return graph;
    }

    public static CompoundTag createDefaultGraphTag() {
        return createDefaultExample().graphModel.serializeNBT(Platform.getFrozenRegistry());
    }

    @Override
    protected CustomGraphModelImpl createGraphModel() {
        return new NpcBehaviorGraphModel(this);
    }

    @Override
    public List<Class<? extends Node>> getSupportNodes() {
        return NODE_REGISTRY.getNodeClasses();
    }

    @Override
    public List<TypeHandle> getSupportTypes() {
        return List.of(
                TypeHandles.BOOL,
                TypeHandles.INT,
                TypeHandles.FLOAT,
                TypeHandles.DOUBLE,
                TypeHandles.STRING,
                TypeHandles.ITEM,
                TypeHandles.BLOCK,
                TypeHandles.ENTITY_TYPE
        );
    }

    @Override
    public List<TypeHandle> getLibrarySupportTypes() {
        return List.of();
    }

    public void ensureDefaultRoot() {
        boolean hasRoot = getNodes().stream().anyMatch(RootNode.class::isInstance);
        if (!hasRoot) {
            graphModel.createNodeModel(new RootNode(), new Vector2f(300, 40));
        }
    }

    public void ensureDefaultExample() {
        List<Node> nodes = getBehaviorNodes();
        if (nodes.isEmpty()) {
            buildDefaultExample(null);
            return;
        }

        List<RootNode> roots = nodes.stream()
                .filter(RootNode.class::isInstance)
                .map(RootNode.class::cast)
                .toList();
        if (nodes.size() == 1 && roots.size() == 1 && connectedCount(roots.getFirst().getOutputPortById(NpcBehaviorNode.PORT_CHILD)) == 0) {
            buildDefaultExample(roots.getFirst());
            return;
        }

        ensureDefaultRoot();
    }

    private void buildDefaultExample(RootNode existingRoot) {
        RootNode root = existingRoot == null ? createNode(new RootNode(), 300, 40) : existingRoot;
        SelectorNode selector = createNode(new SelectorNode(), 300, 240);

        SequenceNode attackSequence = createNode(new SequenceNode(), -450, 440);
        FindNearestPlayerNode findNearestPlayer = createNode(new FindNearestPlayerNode(), -900, 660);
        option(findNearestPlayer, FindNearestPlayerNode.OPTION_RANGE, 16.0f);
        option(findNearestPlayer, FindNearestPlayerNode.OPTION_IGNORE_CREATIVE, false);
        IsTargetInRangeNode targetInRange = createNode(new IsTargetInRangeNode(), -600, 660);
        option(targetInRange, IsTargetInRangeNode.OPTION_RANGE, 2.5f);
        LookAtTargetNode lookAtTarget = createNode(new LookAtTargetNode(), -300, 660);
        MeleeAttackTargetNode attackTarget = createNode(new MeleeAttackTargetNode(), 0, 660);
        option(attackTarget, MeleeAttackTargetNode.OPTION_RANGE, 2.5f);
        option(attackTarget, MeleeAttackTargetNode.OPTION_COOLDOWN, 20);

        SequenceNode followSequence = createNode(new SequenceNode(), 450, 440);
        HasTargetNode hasTarget = createNode(new HasTargetNode(), 450, 660);
        FollowTargetNode followTarget = createNode(new FollowTargetNode(), 750, 660);
        option(followTarget, FollowTargetNode.OPTION_SPEED, 1.1f);
        option(followTarget, FollowTargetNode.OPTION_STOPPING_DISTANCE, 2.0f);

        IdleNode idle = createNode(new IdleNode(), 1050, 440);
        option(idle, IdleNode.OPTION_DURATION, 20);

        connect(root, NpcBehaviorNode.PORT_CHILD, selector);
        connect(selector, NpcBehaviorNode.PORT_CHILDREN, attackSequence);
        connect(selector, NpcBehaviorNode.PORT_CHILDREN, followSequence);
        connect(selector, NpcBehaviorNode.PORT_CHILDREN, idle);

        connect(attackSequence, NpcBehaviorNode.PORT_CHILDREN, findNearestPlayer);
        connect(attackSequence, NpcBehaviorNode.PORT_CHILDREN, targetInRange);
        connect(attackSequence, NpcBehaviorNode.PORT_CHILDREN, lookAtTarget);
        connect(attackSequence, NpcBehaviorNode.PORT_CHILDREN, attackTarget);

        connect(followSequence, NpcBehaviorNode.PORT_CHILDREN, hasTarget);
        connect(followSequence, NpcBehaviorNode.PORT_CHILDREN, followTarget);
    }

    private <T extends Node> T createNode(T node, float x, float y) {
        graphModel.createNodeModel(node, new Vector2f(x, y));
        return node;
    }

    private void connect(Node parent, String outputPortId, Node child) {
        IPort outputPort = parent.getOutputPortById(outputPortId);
        IPort inputPort = child.getInputPortById(NpcBehaviorNode.PORT_IN);
        if (outputPort instanceof PortModel outputPortModel && inputPort instanceof PortModel inputPortModel) {
            graphModel.createWire(outputPortModel, inputPortModel);
        }
    }

    private void option(Node node, String id, Object value) {
        if (node.getNodeOptionById(id) instanceof NodeOption option
                && option.getPortModel().createEmbeddedValueIfNeeded()
                && option.getPortModel().getEmbeddedValue() != null) {
            option.getPortModel().getEmbeddedValue().setDefaultValue(value);
            option.getPortModel().getEmbeddedValue().setValue(value);
        }
    }

    @Override
    public void onGraphChanged(GraphLogger logger) {
        super.onGraphChanged(logger);
        validateBehaviorTree(logger);
    }

    private void validateBehaviorTree(GraphLogger logger) {
        List<Node> nodes = getBehaviorNodes();
        List<RootNode> roots = nodes.stream()
                .filter(RootNode.class::isInstance)
                .map(RootNode.class::cast)
                .toList();
        if (roots.isEmpty()) {
            logger.error(validation("root_missing"));
        } else {
            if (roots.size() > 1) {
                logger.error(validation("root_multiple", roots.size()), roots.getFirst().getNodeModel());
            }
            validateSingleChild(logger, roots.getFirst(), true);
        }

        for (Node node : nodes) {
            if (!(node instanceof RootNode)) {
                validateParent(logger, node);
            }
            if (node instanceof SequenceNode || node instanceof SelectorNode) {
                validateComposite(logger, node);
            } else if (node instanceof InverterNode) {
                validateSingleChild(logger, node, false);
            }
        }
        validateCycles(logger, nodes);
    }

    private void validateParent(GraphLogger logger, Node node) {
        int parents = connectedCount(node.getInputPortById(NpcBehaviorNode.PORT_IN));
        if (parents == 0) {
            logger.warning(validation("parent_missing", node.getDisplayName()), node.getNodeModel());
        } else if (parents > 1) {
            logger.error(validation("parent_multiple", node.getDisplayName(), parents), node.getNodeModel());
        }
    }

    private void validateComposite(GraphLogger logger, Node node) {
        if (connectedCount(node.getOutputPortById(NpcBehaviorNode.PORT_CHILDREN)) == 0) {
            logger.warning(validation("children_missing", node.getDisplayName()), node.getNodeModel());
        }
    }

    private void validateSingleChild(GraphLogger logger, Node node, boolean root) {
        int children = connectedCount(node.getOutputPortById(NpcBehaviorNode.PORT_CHILD));
        if (children == 0) {
            logger.warning(validation(root ? "root_child_missing" : "child_missing", node.getDisplayName()), node.getNodeModel());
        } else if (children > 1) {
            logger.error(validation(root ? "root_child_multiple" : "child_multiple", node.getDisplayName(), children), node.getNodeModel());
        }
    }

    private void validateCycles(GraphLogger logger, List<Node> nodes) {
        Set<UUID> visited = new HashSet<>();
        Set<UUID> visiting = new HashSet<>();
        for (Node node : nodes) {
            if (hasCycle(node, visiting, visited, logger)) {
                return;
            }
        }
    }

    private boolean hasCycle(Node node, Set<UUID> visiting, Set<UUID> visited, GraphLogger logger) {
        UUID uid = node.getNodeModel().getUid();
        if (visiting.contains(uid)) {
            logger.error(validation("cycle", node.getDisplayName()), node.getNodeModel());
            return true;
        }
        if (!visited.add(uid)) {
            return false;
        }
        visiting.add(uid);
        for (Node child : children(node)) {
            if (hasCycle(child, visiting, visited, logger)) {
                return true;
            }
        }
        visiting.remove(uid);
        return false;
    }

    private List<Node> children(Node node) {
        ArrayList<Node> children = new ArrayList<>();
        addConnectedNodes(children, node.getOutputPortById(NpcBehaviorNode.PORT_CHILD));
        addConnectedNodes(children, node.getOutputPortById(NpcBehaviorNode.PORT_CHILDREN));
        return children;
    }

    private void addConnectedNodes(List<Node> nodes, IPort port) {
        for (IPort connectedPort : connectedPorts(port)) {
            if (connectedPort instanceof PortModel connectedPortModel
                    && connectedPortModel.getNodeModel() instanceof ICustomNodeModel customNodeModel
                    && customNodeModel.getNode() instanceof Node node) {
                nodes.add(node);
            }
        }
    }

    private int connectedCount(IPort port) {
        return connectedPorts(port).size();
    }

    private List<IPort> connectedPorts(IPort port) {
        if (!(port instanceof PortModel portModel)) {
            return List.of();
        }
        ArrayList<IPort> connectedPorts = new ArrayList<>();
        portModel.getConnectedPorts(connectedPorts);
        return connectedPorts;
    }

    private List<Node> getBehaviorNodes() {
        return getNodes().stream()
                .filter(Node.class::isInstance)
                .map(Node.class::cast)
                .toList();
    }

    private Component validation(String key, Object... args) {
        return Component.translatable("viscript_npc.ai.validation." + key, args);
    }
}
