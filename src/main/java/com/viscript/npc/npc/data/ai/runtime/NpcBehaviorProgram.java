package com.viscript.npc.npc.data.ai.runtime;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import lombok.Data;

import java.util.*;

@Data
public class NpcBehaviorProgram implements IPersistedSerializable {
    public static final int CURRENT_VERSION = 1;
    public static final UUID EMPTY_UUID = new UUID(0L, 0L);

    @Persisted
    private int version = CURRENT_VERSION;
    @Persisted
    private UUID rootNode = EMPTY_UUID;
    @Persisted
    private List<NpcBehaviorProgramNode> nodes = new ArrayList<>();

    public boolean isEmpty() {
        return EMPTY_UUID.equals(rootNode) || nodes == null || nodes.isEmpty();
    }

    public Map<UUID, NpcBehaviorProgramNode> createNodeMap() {
        Map<UUID, NpcBehaviorProgramNode> map = new LinkedHashMap<>();
        if (nodes != null) {
            for (NpcBehaviorProgramNode node : nodes) {
                map.put(node.getUid(), node);
            }
        }
        return map;
    }
}
