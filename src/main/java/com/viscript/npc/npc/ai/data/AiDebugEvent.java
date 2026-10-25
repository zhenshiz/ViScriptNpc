package com.viscript.npc.npc.ai.data;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;

/** 有界执行日志中的一条节点事件。 */
public class AiDebugEvent implements IPersistedSerializable {
    @Persisted public long tick;
    @Persisted public String nodeId = "";
    @Persisted public String operation = "";
    @Persisted public String status = "";
    @Persisted public String message = "";
}
