package com.viscript.npc.npc.ai.mind.intention;

import com.viscript.npc.npc.ai.mind.api.IntentionPriority;
import lombok.Setter;
import net.minecraft.world.entity.Entity;

import java.util.function.BiConsumer;

public class RangeAttackIntention extends FollowIntention {
    @Setter
    protected BiConsumer<Entity, Entity> attackFunction;

    public RangeAttackIntention(Entity origin, Entity target, double distance, BiConsumer<Entity, Entity> attackFunction) {
        super(origin, target, distance, true);
        this.attackFunction = attackFunction;
    }

    public RangeAttackIntention(Entity origin, Entity target, double distance, BiConsumer<Entity, Entity> attackFunction, IntentionPriority priority) {
        super(origin, target, distance, true, priority);
        this.attackFunction = attackFunction;
    }

    @Override
    public boolean execute() {
        super.execute();
        if (this.target.isAttackable() && this.target.isAlive()) {
            attackFunction.accept(this.origin, this.target);
            return this.target.isAlive();
        }
        return true;
    }
}
