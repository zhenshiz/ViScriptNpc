package com.viscript.npc.npc.ai;

import com.viscript.npc.compat.team.NpcFactionBridge;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcProjectile;
import com.viscript.npc.npc.data.ai.attack.MeleeAttackData;
import com.viscript.npc.npc.data.ai.attack.RangedAttackData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

public final class NpcAttackExecutor {
    private NpcAttackExecutor() {
    }

    public static boolean melee(CustomNpc npc, LivingEntity target, MeleeAttackData data) {
        if (!canAttack(npc, target, data.range())) {
            return false;
        }
        npc.getLookControl().setLookAt(target, 30.0F, 30.0F);
        npc.swing(InteractionHand.MAIN_HAND);
        boolean accepted = data.damage() <= 0.0F || target.hurt(npc.damageSources().mobAttack(npc), data.damage());
        if (accepted) {
            if (data.knockback() > 0.0D) {
                target.knockback(data.knockback(), npc.getX() - target.getX(), npc.getZ() - target.getZ());
            }
            data.effect().apply(target);
            npc.setLastHurtMob(target);
        }
        return accepted;
    }

    public static boolean ranged(CustomNpc npc, LivingEntity target, RangedAttackData data) {
        if (!canAttack(npc, target, data.range())) {
            return false;
        }
        npc.getLookControl().setLookAt(target, 30.0F, 30.0F);
        boolean spawned = false;
        for (int i = 0; i < data.projectilesPerShot(); i++) {
            NpcProjectile projectile = new NpcProjectile(npc.level(), npc);
            projectile.configureFrom(data);
            double x = target.getX() - npc.getX();
            double y = target.getY(0.3333333333333333D) - projectile.getY();
            double z = target.getZ() - npc.getZ();
            double horizontal = Math.sqrt(x * x + z * z);
            projectile.shoot(x, y + horizontal * 0.2D, z, data.projectileSpeed(), data.inaccuracy());
            npc.level().addFreshEntity(projectile);
            spawned = true;
        }
        if (spawned) {
            npc.swing(InteractionHand.MAIN_HAND);
            npc.playSound(data.shootSoundEvent(), 1.0F, 1.0F / (npc.getRandom().nextFloat() * 0.4F + 0.8F));
        }
        return spawned;
    }

    private static boolean canAttack(CustomNpc npc, LivingEntity target, double range) {
        return target != null && target.isAlive() && target.isAttackable()
                && NpcFactionBridge.canHurt(npc, target)
                && npc.distanceToSqr(target) <= range * range;
    }
}
