package com.viscript.npc.npc.data.ai.runtime;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class NpcBehaviorDebugSnapshot implements IPersistedSerializable {
    @Persisted
    private UUID npcUuid = NpcBehaviorProgram.EMPTY_UUID;
    @Persisted
    private UUID targetUuid = NpcBehaviorProgram.EMPTY_UUID;
    @Persisted
    private String gameObjectName = "";
    @Persisted
    private String behaviorName = "";
    @Persisted
    private String targetName = "";
    @Persisted
    private double targetDistance = -1.0D;
    @Persisted
    private float health;
    @Persisted
    private boolean navigationDone = true;
    @Persisted
    private String currentNode = "";
    @Persisted
    private String breakpointNode = "";
    @Persisted
    private boolean breakpointPaused;
    @Persisted
    private String lastFailureReason = "";
    @Persisted
    private long gameTime;
    @Persisted
    private List<String> visitOrder = new ArrayList<>();
    @Persisted
    private List<String> blackboardLines = new ArrayList<>();
    @Persisted
    private List<String> logs = new ArrayList<>();
    @Persisted
    private List<NpcBehaviorDebugNodeSnapshot> nodes = new ArrayList<>();
}
