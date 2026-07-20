package com.viscript.npc.npc;

import com.viscript.npc.compat.team.NpcFactionBridge;
import com.viscript.npc.npc.data.attributes.RangedConfig;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class NpcProjectile extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> DATA_GRAVITY = SynchedEntityData.defineId(NpcProjectile.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_VISUAL_SCALE = SynchedEntityData.defineId(NpcProjectile.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> DATA_TRAIL_TYPE = SynchedEntityData.defineId(NpcProjectile.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_HIT_SOUND = SynchedEntityData.defineId(NpcProjectile.class, EntityDataSerializers.STRING);
    private double damage = 2;
    private double knockback;
    private float explosionPower;
    private boolean explosionBreaksBlocks;
    private String additionalEffect = RangedConfig.AdditionalEffects.NONE.getSerializedName();
    private String debuffEffect = ResourceLocation.withDefaultNamespace("slowness").toString();
    private int effectSeconds;
    private int effectAmplifier;

    public NpcProjectile(EntityType<? extends NpcProjectile> entityType, Level level) {
        super(entityType, level);
    }

    public NpcProjectile(Level level, LivingEntity shooter) {
        super(NpcRegister.CUSTOM_NPC_PROJECTILE.get(), shooter, level);
    }

    public void configureFrom(RangedConfig config) {
        this.damage = Math.max(0.0D, config.getDamage());
        this.knockback = Math.max(0.0D, config.getKnockback());
        this.explosionPower = Math.max(0.0F, config.getExplosionPower());
        this.explosionBreaksBlocks = config.isExplosionBreaksBlocks();
        this.additionalEffect = config.getAdditionalEffects().getSerializedName();
        this.debuffEffect = config.getDebuffEffect();
        this.effectSeconds = config.getSeconds().intValue();
        this.effectAmplifier = config.getAmplifier().intValue();
        this.setItem(config.getProjectileItemForDisplay());
        this.setAffectedByGravity(config.isAffectedByGravity());
        this.setVisualScale(config.getVisualScale());
        this.setTrailType(config.getTrailType());
        this.setHitSound(config.getHitSound());
    }

    public float getVisualScale() {
        return Math.max(0.1F, this.getEntityData().get(DATA_VISUAL_SCALE));
    }

    private void setVisualScale(float visualScale) {
        this.getEntityData().set(DATA_VISUAL_SCALE, Math.max(0.1F, visualScale));
    }

    private void setAffectedByGravity(boolean affectedByGravity) {
        this.getEntityData().set(DATA_GRAVITY, affectedByGravity);
    }

    private boolean isAffectedByGravity() {
        return this.getEntityData().get(DATA_GRAVITY);
    }

    private void setTrailType(RangedConfig.TrailType trailType) {
        this.getEntityData().set(DATA_TRAIL_TYPE, trailType.getSerializedName());
    }

    private RangedConfig.TrailType getTrailType() {
        return RangedConfig.TrailType.bySerializedName(this.getEntityData().get(DATA_TRAIL_TYPE));
    }

    private void setHitSound(String hitSound) {
        this.getEntityData().set(DATA_HIT_SOUND, hitSound == null ? "" : hitSound);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.ARROW;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_GRAVITY, true);
        builder.define(DATA_VISUAL_SCALE, 1.0F);
        builder.define(DATA_TRAIL_TYPE, RangedConfig.TrailType.NONE.getSerializedName());
        builder.define(DATA_HIT_SOUND, "minecraft:entity.arrow.hit");
    }

    @Override
    protected double getDefaultGravity() {
        return isAffectedByGravity() ? 0.03D : 0.0D;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide && this.tickCount > 1) {
            spawnTrailParticle();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 3) {
            ParticleOptions particle = new ItemParticleOption(ParticleTypes.ITEM, this.getItem());
            for (int i = 0; i < 8; i++) {
                this.level().addParticle(
                        particle,
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        ((double) this.random.nextFloat() - 0.5D) * 0.08D,
                        ((double) this.random.nextFloat() - 0.5D) * 0.08D,
                        ((double) this.random.nextFloat() - 0.5D) * 0.08D
                );
            }
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide) {
            return;
        }
        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        if (owner instanceof LivingEntity livingOwner
                && target instanceof LivingEntity livingTarget
                && !NpcFactionBridge.canHurt(livingOwner, livingTarget)) {
            return;
        }
        DamageSource source = owner instanceof LivingEntity livingOwner
                ? this.damageSources().mobProjectile(this, livingOwner)
                : this.damageSources().thrown(this, owner);
        boolean accepted = damage <= 0.0D || target.hurt(source, (float) damage);
        if (accepted && owner instanceof LivingEntity livingOwner) {
            livingOwner.setLastHurtMob(target);
        }
        if (accepted && target instanceof LivingEntity livingTarget) {
            applyKnockback(livingTarget, owner);
            applyAdditionalEffect(livingTarget);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            playHitSound();
            explodeIfNeeded();
            this.level().broadcastEntityEvent(this, (byte) 3);
            this.discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putDouble("Damage", damage);
        compound.putDouble("Knockback", knockback);
        compound.putBoolean("AffectedByGravity", isAffectedByGravity());
        compound.putFloat("VisualScale", getVisualScale());
        compound.putString("TrailType", this.getEntityData().get(DATA_TRAIL_TYPE));
        compound.putString("HitSound", this.getEntityData().get(DATA_HIT_SOUND));
        compound.putFloat("ExplosionPower", explosionPower);
        compound.putBoolean("ExplosionBreaksBlocks", explosionBreaksBlocks);
        compound.putString("AdditionalEffect", additionalEffect);
        compound.putString("DebuffEffect", debuffEffect);
        compound.putInt("EffectSeconds", effectSeconds);
        compound.putInt("EffectAmplifier", effectAmplifier);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("Damage", 99)) damage = compound.getDouble("Damage");
        if (compound.contains("Knockback", 99)) knockback = compound.getDouble("Knockback");
        if (compound.contains("AffectedByGravity")) setAffectedByGravity(compound.getBoolean("AffectedByGravity"));
        if (compound.contains("VisualScale", 99)) setVisualScale(compound.getFloat("VisualScale"));
        if (compound.contains("TrailType", 8)) this.getEntityData().set(DATA_TRAIL_TYPE, compound.getString("TrailType"));
        if (compound.contains("HitSound", 8)) this.getEntityData().set(DATA_HIT_SOUND, compound.getString("HitSound"));
        if (compound.contains("ExplosionPower", 99)) explosionPower = compound.getFloat("ExplosionPower");
        if (compound.contains("ExplosionBreaksBlocks")) explosionBreaksBlocks = compound.getBoolean("ExplosionBreaksBlocks");
        if (compound.contains("AdditionalEffect", 8)) additionalEffect = compound.getString("AdditionalEffect");
        if (compound.contains("DebuffEffect", 8)) debuffEffect = compound.getString("DebuffEffect");
        if (compound.contains("EffectSeconds", 99)) effectSeconds = compound.getInt("EffectSeconds");
        if (compound.contains("EffectAmplifier", 99)) effectAmplifier = compound.getInt("EffectAmplifier");
    }

    private void spawnTrailParticle() {
        ParticleOptions particle = switch (getTrailType()) {
            case NONE -> null;
            case SMOKE -> ParticleTypes.SMOKE;
            case FLAME -> ParticleTypes.FLAME;
            case MAGIC -> ParticleTypes.WITCH;
            case CRIT -> ParticleTypes.CRIT;
        };
        if (particle == null) {
            return;
        }
        Vec3 movement = this.getDeltaMovement();
        this.level().addParticle(
                particle,
                this.getX() - movement.x() * 0.25D,
                this.getY() - movement.y() * 0.25D,
                this.getZ() - movement.z() * 0.25D,
                0.0D,
                0.0D,
                0.0D
        );
    }

    private void playHitSound() {
        this.playSound(
                RangedConfig.soundOrDefault(this.getEntityData().get(DATA_HIT_SOUND), SoundEvents.ARROW_HIT),
                1.0F,
                1.0F / (this.random.nextFloat() * 0.2F + 0.9F)
        );
    }

    private void explodeIfNeeded() {
        if (explosionPower <= 0.0F) {
            return;
        }
        Entity owner = this.getOwner();
        DamageSource source = owner instanceof LivingEntity livingOwner
                ? this.damageSources().explosion(this, livingOwner)
                : this.damageSources().explosion(this, this);
        this.level().explode(
                this,
                source,
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                explosionPower,
                false,
                explosionBreaksBlocks ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE
        );
    }

    private void applyKnockback(LivingEntity target, Entity owner) {
        if (knockback <= 0.0D) {
            return;
        }
        double x;
        double z;
        if (owner != null) {
            x = owner.getX() - target.getX();
            z = owner.getZ() - target.getZ();
        } else {
            Vec3 movement = this.getDeltaMovement();
            x = -movement.x();
            z = -movement.z();
        }
        target.knockback(knockback, x, z);
    }

    private void applyAdditionalEffect(LivingEntity target) {
        switch (RangedConfig.AdditionalEffects.bySerializedName(additionalEffect)) {
            case FIRE -> target.igniteForSeconds((float) effectSeconds);
            case POTION -> {
                ResourceLocation effectKey = ResourceLocation.tryParse(debuffEffect);
                if (effectKey != null) {
                    BuiltInRegistries.MOB_EFFECT.getHolder(effectKey).ifPresent(effect ->
                            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, effectSeconds * 20, effectAmplifier, false, false)));
                }
            }
        }
    }
}
