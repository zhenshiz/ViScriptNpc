package com.viscript.npc.npc.ai.data;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;

/** 服务器发送给编辑器的被测 NPC 状态；不携带客户端可执行逻辑。 */
public class AiDebugSnapshot implements IPersistedSerializable {
    @Persisted public int entityId;
    @Persisted public java.util.List<String> activeNodes = new java.util.ArrayList<>();
    @Persisted public String phase = "";
    @Persisted public String status = "";
    @Persisted public String reason = "";
    @Persisted public String navigationStatus = "";
    @Persisted public int targetId = -1;
    @Persisted public java.util.Map<String, AiValue> variables = new java.util.LinkedHashMap<>();
    @Persisted public java.util.Map<String, AiValue> inputs = new java.util.LinkedHashMap<>();
    @Persisted public java.util.List<net.minecraft.world.phys.Vec3> path = new java.util.ArrayList<>();
    @Persisted public java.util.List<AiPerceptionVolume> perceptions = new java.util.ArrayList<>();
    @Persisted public double navigationQueueMs;
    @Persisted public double navigationSearchMs;
    @Persisted public boolean paused;
    @Persisted public boolean enabled;
    @Persisted public boolean logging;
    @Persisted public long tick;
    @Persisted public String breakpoint = "";
    @Persisted public boolean tickInProgress;
    @Persisted public String currentNode = "", currentOperation = "", currentAction = "";
    @Persisted public int currentDepth;
    @Persisted public String lastNode = "", lastOperation = "", lastAction = "", lastResult = "";
    @Persisted public java.util.Map<String, String> nodeStates = new java.util.LinkedHashMap<>();
    @Persisted public java.util.Map<String, AiValue> values = new java.util.LinkedHashMap<>();
    @Persisted public java.util.List<String> breakpoints = new java.util.ArrayList<>();
    @Persisted public java.util.List<AiDebugEvent> events = new java.util.ArrayList<>();
    @Persisted public java.util.List<AiDebugLine> lines = new java.util.ArrayList<>();
}
