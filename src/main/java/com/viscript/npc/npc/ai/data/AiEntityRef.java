package com.viscript.npc.npc.ai.data;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** 保存实体的维度和 UUID；解析仅访问当前维度内已加载的存活实体。 */
public class AiEntityRef implements IPersistedSerializable {
    @Persisted public String uuid = "";
    @Persisted public String dimension = "";

    public static AiEntityRef of(Entity entity) {
        var ref = new AiEntityRef();
        if (entity != null) { ref.uuid = entity.getUUID().toString(); ref.dimension = entity.level().dimension().location().toString(); }
        return ref;
    }
    public AiEntityRef copy() { var ref = new AiEntityRef(); ref.uuid = uuid; ref.dimension = dimension; return ref; }
    public Entity resolve(ServerLevel level) {
        if (!dimension.equals(level.dimension().location().toString())) return null;
        try { var entity = level.getEntity(java.util.UUID.fromString(uuid)); return entity != null && entity.isAlive() ? entity : null; }
        catch (IllegalArgumentException exception) { return null; }
    }
    @Override public String toString() { return uuid.isEmpty() ? "" : dimension + " / " + uuid; }
}
