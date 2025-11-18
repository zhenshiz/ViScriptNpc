package com.viscript.npc.npc.ai.mind.intention;

import com.viscript.npc.npc.ai.mind.api.BaseIntention;
import com.viscript.npc.npc.ai.mind.api.IntentionPriority;
import com.viscript.npc.npc.ai.mind.api.IntentionType;
import net.minecraft.world.entity.Entity;

public abstract class SimpleIntention extends BaseIntention {
    public SimpleIntention(Entity origin, IntentionType type) {
        super(origin, type);
    }

    public SimpleIntention(Entity origin, IntentionType type, IntentionPriority priority) {
        super(origin, type, priority);
    }

    @Override
    public void hold() {
    }
}
