package com.viscript.npc.npc.ai.data;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import net.minecraft.world.phys.Vec3;

/** 服务器计算的临时调试线段。 */
public class AiDebugLine implements IPersistedSerializable {
    @Persisted public String nodeId = "";
    @Persisted public Vec3 from = Vec3.ZERO;
    @Persisted public Vec3 to = Vec3.ZERO;
    @Persisted public long expires;
}
