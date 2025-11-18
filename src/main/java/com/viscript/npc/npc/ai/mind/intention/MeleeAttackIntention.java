package com.viscript.npc.npc.ai.mind.intention;

import com.viscript.npc.npc.ai.mind.api.IntentionPriority;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class MeleeAttackIntention extends FollowIntention {
    @Getter
    @Setter
    protected float damage;

    public MeleeAttackIntention(Entity origin, Entity target) {
        super(origin, target);
    }

    public MeleeAttackIntention(Entity origin, Entity target, IntentionPriority priority) {
        super(origin, target, priority);
    }

    @Override
    public boolean execute() {
        super.execute();
        if (this.target.isAttackable() && this.target.isAlive()) {
            if (this.origin.position().distanceTo(this.target.position()) < this.distance) {
                if (origin instanceof LivingEntity livingEntity) {
                    target.hurt(origin.damageSources().mobAttack(livingEntity), this.damage);
                } else {
                    target.hurt(origin.damageSources().generic(), this.damage);
                }
            }
            return this.target.isAlive();
        }
        return true;
    }
}
