package com.viscript.npc.npc.data.ai.graph;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.IPort;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortCapacity;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.viscript.npc.npc.data.ai.graph.node.NpcBehaviorNode;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class NpcBehaviorGraphModel extends CustomGraphModelImpl {
    public NpcBehaviorGraphModel(Graph graph) {
        super(graph);
    }

    @Override
    public NpcBehaviorCustomNodeModel createNodeModel(Node node, Vector2f position) {
        return createNodeWithType(NpcBehaviorCustomNodeModel.class, "", position, null,
                n -> n.initCustomNode(node), null);
    }

    @Override
    protected AbstractNodeModel createNodeFromDiscriminator(String type) {
        if ("custom".equals(type)) {
            return new NpcBehaviorCustomNodeModel();
        }
        return super.createNodeFromDiscriminator(type);
    }

    @Override
    public boolean canAssignTo(PortModel inputPort, PortModel outputPort) {
        if (!super.canAssignTo(inputPort, outputPort)) {
            return false;
        }
        if (isOverCapacity(inputPort, outputPort) || isOverCapacity(outputPort, inputPort)) {
            return false;
        }
        return !wouldCreateCycle(inputPort, outputPort);
    }

    private boolean isOverCapacity(PortModel port, PortModel otherPort) {
        if (port.getPortCapacity() != PortCapacity.SINGLE) {
            return false;
        }
        for (PortModel connectedPort : port.getConnectedPorts()) {
            if (connectedPort != otherPort) {
                return true;
            }
        }
        return false;
    }

    private boolean wouldCreateCycle(PortModel inputPort, PortModel outputPort) {
        if (!TypeHandles.EXECUTION_FLOW.equals(inputPort.getDataTypeHandle())
                || !TypeHandles.EXECUTION_FLOW.equals(outputPort.getDataTypeHandle())) {
            return false;
        }
        Node parent = nodeOf(outputPort);
        Node child = nodeOf(inputPort);
        if (parent == null || child == null) {
            return false;
        }
        return reaches(child, parent, new HashSet<>());
    }

    private boolean reaches(Node current, Node target, Set<UUID> visited) {
        UUID uid = current.getNodeModel().getUid();
        if (!visited.add(uid)) {
            return false;
        }
        if (uid.equals(target.getNodeModel().getUid())) {
            return true;
        }
        return reachesFromPort(current, NpcBehaviorNode.PORT_CHILD, target, visited)
                || reachesFromPort(current, NpcBehaviorNode.PORT_CHILDREN, target, visited);
    }

    private boolean reachesFromPort(Node current, String portId, Node target, Set<UUID> visited) {
        IPort outputPort = current.getOutputPortById(portId);
        if (!(outputPort instanceof PortModel portModel)) {
            return false;
        }
        ArrayList<IPort> connectedPorts = new ArrayList<>();
        portModel.getConnectedPorts(connectedPorts);
        for (IPort connectedPort : connectedPorts) {
            if (connectedPort instanceof PortModel connectedPortModel) {
                Node child = nodeOf(connectedPortModel);
                if (child != null && reaches(child, target, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Node nodeOf(PortModel portModel) {
        if (portModel.getNodeModel() instanceof ICustomNodeModel customNodeModel
                && customNodeModel.getNode() instanceof Node node) {
            return node;
        }
        return null;
    }
}
