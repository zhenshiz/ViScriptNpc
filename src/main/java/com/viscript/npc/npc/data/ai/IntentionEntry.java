package com.viscript.npc.npc.data.ai;

import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.thexeler.api.IntentionPriority;
import org.thexeler.intention.base.IdleIntention;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IntentionEntry {
    @Configurable(name = "npcConfig.npcAI.intention.priority")
    @Persisted
    private IntentionPriority priority = IntentionPriority.NORMAL;

    @Configurable(name = "npcConfig.npcAI.intention.type")
    @Persisted
    private ResourceLocation type = IdleIntention.TYPE;

    @Configurable(name = "npcConfig.npcAI.intention.data")
    @Persisted
    private CompoundTag data = new CompoundTag();
}
