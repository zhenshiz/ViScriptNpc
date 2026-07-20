package com.viscript.npc.npc;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.math.Range;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.mojang.blaze3d.MethodsReturnNonnullByDefault;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.compat.curios.NpcCuriosCompat;
import com.viscript.npc.compat.team.NpcFactionBridge;
import com.viscript.npc.event.neoforge.NpcEvent;
import com.viscript.npc.network.s2c.S2CPayload;
import com.viscript.npc.npc.data.INpcData;
import com.viscript.npc.npc.data.ai.IntentionEntry;
import com.viscript.npc.npc.data.ai.NpcAI;
import com.viscript.npc.npc.data.ai.runtime.NpcBehaviorDebugSnapshot;
import com.viscript.npc.npc.data.ai.runtime.NpcBehaviorRuntime;
import com.viscript.npc.npc.data.ai.runtime.NpcBehaviorTreeIntention;
import com.viscript.npc.npc.data.attributes.MeleeConfig;
import com.viscript.npc.npc.data.attributes.NpcAttributes;
import com.viscript.npc.npc.data.attributes.RangedConfig;
import com.viscript.npc.npc.data.attributes.ResistanceConfig;
import com.viscript.npc.npc.data.basics_setting.NpcBasicsSetting;
import com.viscript.npc.npc.data.inventory.LootTableConfig;
import com.viscript.npc.npc.data.inventory.NpcInventory;
import com.viscript.npc.npc.data.mod_integrations.NpcViScriptTeamIntegration;
import com.viscript.npc.npc.data.model.NpcDynamicModel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.thexeler.MindMachine;
import org.thexeler.api.IntentionPriority;
import org.thexeler.api.IntentionTypeRegistry;
import org.thexeler.api.MindMachineManager;
import org.thexeler.intention.BaseIntention;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CustomNpc extends PathfinderMob implements CrossbowAttackMob {
    protected final WaterBoundPathNavigation waterNavigation;
    protected final GroundPathNavigation groundNavigation;
    @Nullable
    private MindMachine mind;
    @Nullable
    private NpcBehaviorTreeIntention behaviorTreeIntention;
    private int mindConfigHash;
    public static Set<String> lootTableKeys = Set.of();
    public static Set<String> factionIds = Set.of();

    //披风用参数
    public double xCloakO;
    public double yCloakO;
    public double zCloakO;
    public double xCloak;
    public double yCloak;
    public double zCloak;
    public float oBob;
    public float bob;
    @Nullable
    private Quaternionf previewCameraOrientation;
    @Nullable
    private Vec3 commandPathTestTarget;

    public CustomNpc(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.waterNavigation = new WaterBoundPathNavigation(this, level);
        this.groundNavigation = new GroundPathNavigation(this, level);
    }

    public <T extends INpcData> T getNpcAttachment(Class<T> clazz) {
        return clazz.cast(this.getData(NpcAttachmentType.getAttachment(clazz)));
    }

    public void setPreviewCameraOrientation(@Nullable Quaternionf previewCameraOrientation) {
        this.previewCameraOrientation = previewCameraOrientation == null ? null : new Quaternionf(previewCameraOrientation);
    }

    @Nullable
    public Quaternionf getPreviewCameraOrientation() {
        return previewCameraOrientation;
    }

    @Override
    protected void registerGoals() {
        // Vanilla goals are intentionally empty. Server-side decision making is owned by AM MindMachine.
    }

    @Override
    public void setChargingCrossbow(boolean b) {
    }

    @Override
    public void onCrossbowAttackPerformed() {
        this.noActionTime = 0;
    }

    @Override
    public ItemStack getProjectile(ItemStack weapon) {
        if (weapon.getItem() instanceof ProjectileWeaponItem weaponItem) {
            var ammo = ProjectileWeaponItem.getHeldProjectile(this, weaponItem.getSupportedHeldProjectiles(weapon));
            return CommonHooks.getProjectile(this, weapon, ammo.isEmpty() ? new ItemStack(Items.ARROW) : ammo);
        } else return CommonHooks.getProjectile(this, weapon, ItemStack.EMPTY);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float v) {
    }

    public boolean shootRangedProjectile(LivingEntity target, int projectilesPerShot, float inaccuracy) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        RangedConfig rangedConfig = getNpcAttributes().getRangedConfig();
        int count = Mth.clamp(projectilesPerShot, 1, 64);
        float speed = Math.max(0.1F, rangedConfig.getSpeed());
        boolean spawned = false;
        for (int i = 0; i < count; i++) {
            NpcProjectile projectile = new NpcProjectile(level(), this);
            projectile.configureFrom(rangedConfig);
            double x = target.getX() - this.getX();
            double y = target.getY(0.3333333333333333D) - projectile.getY();
            double z = target.getZ() - this.getZ();
            double horizontal = Math.sqrt(x * x + z * z);
            projectile.shoot(x, y + horizontal * 0.2D, z, speed, Math.max(0.0F, inaccuracy));
            level().addFreshEntity(projectile);
            spawned = true;
        }
        if (spawned) {
            playSound(rangedConfig.getShootSoundEvent(), 1.0F, 1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
        }
        return spawned;
    }

    public void updateSwimming() { // 临时的
        if (!this.level().isClientSide) {
            if (this.isInWater()) {
                this.setSwimming(true);
                setPose(Pose.SWIMMING);
            } else {
                this.setSwimming(false);
                setPose(Pose.STANDING);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        NeoForge.EVENT_BUS.post(new NpcEvent.Tick(this));
        if (!level().isClientSide()) {
            if (tickCommandPathTestOverride()) {
                return;
            }
            if (mind != null) {
                mind.tick();
            }
        } else {
            this.moveCloak();
            Entity entity = getNpcDynamicModel().getEntity(this);
            try { // 某些生物的AI会导致奇怪的崩溃
                if (entity != null) entity.tick();
            } catch (Exception ignored) {
            }
        }
    }

    public boolean startCommandPathTest(Path path, Vec3 target, double speed) {
        commandPathTestTarget = target;
        getNavigation().stop();
        boolean moving = getNavigation().moveTo(path, speed);
        if (!moving) {
            commandPathTestTarget = null;
        }
        return moving;
    }

    public void stopCommandPathTest() {
        commandPathTestTarget = null;
        getNavigation().stop();
    }

    public boolean isCommandPathTestActive() {
        return commandPathTestTarget != null;
    }

    private boolean tickCommandPathTestOverride() {
        if (commandPathTestTarget == null) {
            return false;
        }
        if (!isAlive() || getNavigation().isDone() || distanceToSqr(commandPathTestTarget) <= 2.25D) {
            stopCommandPathTest();
            return false;
        }
        return true;
    }

    @Override
    public void aiStep() {
        updateSwingTime();
        super.aiStep();
        // 用于披风渲染
        this.oBob = this.bob;
        float f;
        if (this.onGround() && !this.isDeadOrDying() && !this.isSwimming()) {
            f = Math.min(0.1F, (float) this.getDeltaMovement().horizontalDistance());
        } else {
            f = 0.0F;
        }

        this.bob += (f - this.bob) * 0.4F;

        //是否在白天点燃
        if (this.isAlive()) {
            boolean flag = this.getNpcAttributes().isBurnInDay() && this.isSunBurnTick();
            if (flag) {
                ItemStack itemstack = this.getItemBySlot(EquipmentSlot.HEAD);
                if (itemstack.isEmpty()) this.igniteForSeconds(8);
                else if (itemstack.isDamageableItem()) {
                    itemstack.hurtAndBreak(this.random.nextInt(2), this, EquipmentSlot.HEAD);
                }
            }
        }

        if (!this.level().isClientSide) {
            //回血
            if (this.tickCount % 20 == 0 && this.getHealth() < this.getMaxHealth()) {
                if (this.getTarget() != null) {
                    this.heal(this.getNpcAttributes().getCombatRegenRate());
                } else {
                    this.heal(this.getNpcAttributes().getOutOfCombatRegenRate());
                }
            }
        }
    }

    public void updateBoundingBox() {
        NpcDynamicModel dynamicModel = this.getNpcDynamicModel();
        AABB aabb = dynamicModel.getAabb();
        this.bb = aabb.move(this.position().subtract(0, aabb.minY, 0));
        this.dimensions = EntityDimensions.scalable((float) aabb.getXsize(), (float) aabb.getYsize());
    }

    @Override
    protected AABB getAttackBoundingBox() {
        Entity entity = this.getVehicle();
        AABB aabb;
        if (entity != null) {
            AABB aabb1 = entity.getBoundingBox();
            AABB aabb2 = this.getBoundingBox();
            aabb = new AABB(Math.min(aabb2.minX, aabb1.minX), aabb2.minY, Math.min(aabb2.minZ, aabb1.minZ), Math.max(aabb2.maxX, aabb1.maxX), aabb2.maxY, Math.max(aabb2.maxZ, aabb1.maxZ));
        } else {
            aabb = this.getBoundingBox();
        }
        double attackRange = this.getNpcAttributes().getMeleeConfig().getAttackRange();

        return aabb.inflate(attackRange, 0.0F, attackRange);
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 motionMultiplier) {
        if (!state.is(Blocks.COBWEB) || !this.getNpcAttributes().isIgnoreCobweb()) {
            super.makeStuckInBlock(state, motionMultiplier);
        }
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effectInstance) {
        if (this.getNpcAttributes().isPotionImmune()) {
            return false;
        } else {
            return super.canBeAffected(effectInstance);
        }
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        if (level().isClientSide()) { // 仅用于客户端渲染，完全不影响实际物品槽位
            NpcInventory inventory = getNpcInventory();
            ItemStack stack = switch (slot) {
                case HEAD -> inventory.getHelmet();
                case CHEST -> inventory.getChestplate();
                case LEGS -> inventory.getLeggings();
                case FEET -> inventory.getBoots();
                case MAINHAND -> inventory.getMainHand();
                case OFFHAND -> inventory.getOffHand();
                case BODY -> ItemStack.EMPTY;
            };
            if (!stack.isEmpty()) return stack;
        }
        return super.getItemBySlot(slot);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (!tag.contains("neoforge:attachments")) NpcAttachmentType.getAttachmentClasses().forEach(clazz -> {
            try {
                INpcData npcAttachment = clazz.getConstructor().newInstance();
                npcAttachment.deserializeNBT(Platform.getFrozenRegistry(), tag);
                setData(NpcAttachmentType.getAttachment(clazz), npcAttachment);
            } catch (Exception ignored) {}
        });
        if (!level().isClientSide && tag.contains("mind")) {
            mind = null;
            behaviorTreeIntention = null;
            mindConfigHash = 0;
        }
        updateNpcState();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        if (mind != null) {
            compoundTag.put("mind", mind.serialize());
        }
    }

    public void updateNpcState() {
        //基础信息
        NpcBasicsSetting npcBasicsSetting = getNpcBasicsSetting();
        this.setAttributeBaseValue(Attributes.SCALE, npcBasicsSetting.getModeSize());
        this.setInvulnerable(npcBasicsSetting.isInvulnerable());
        this.setNoGravity(npcBasicsSetting.isNoGravity());

        //动态模型
        updateBoundingBox();

        //NPC属性
        NpcAttributes npcAttributes = getNpcAttributes();
        this.setAttributeBaseValue(Attributes.MAX_HEALTH, npcAttributes.getMaxHealth());
        this.setAttributeBaseValue(Attributes.MOVEMENT_SPEED, npcAttributes.getMovementSpeed());
        //近战属性
        this.setAttributeBaseValue(Attributes.ATTACK_DAMAGE, npcAttributes.getMeleeConfig().getAttackDamage());
        this.setAttributeBaseValue(Attributes.ATTACK_KNOCKBACK, npcAttributes.getMeleeConfig().getKnockback());
        this.setAttributeBaseValue(Attributes.KNOCKBACK_RESISTANCE, npcAttributes.getResistanceConfig().getKnockback());
        //防御
        this.setAttributeBaseValue(Attributes.ARMOR, npcAttributes.getDefenseConfig().getArmor());
        this.setAttributeBaseValue(Attributes.ARMOR_TOUGHNESS, npcAttributes.getDefenseConfig().getArmorToughness());

        //AI
        NpcAI npcAI = getNpcAI();
        this.setNoAi(!npcAI.isEnabled());
        syncMindState(npcAI);

        //联动模组
        NpcFactionBridge.applyConfiguredFaction(this);
        if (ViScriptNpc.isCuriosLoaded()) {
            NpcCuriosCompat.applyCurios(this);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND) {
        }

        return InteractionResult.PASS;
    }

    protected void dropFromLootTable(DamageSource source, boolean recentlyHit) {
        if (!(level() instanceof ServerLevel level)) return;
        NpcInventory npcInventory = this.getNpcInventory();
        switch (npcInventory.getLootTableType()) {
            case DATAPACK -> {
                if (!lootTableKeys.contains(npcInventory.getLootTable())) return;

                var key = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(npcInventory.getLootTable()));
                LootTable loottable = level.getServer().reloadableRegistries().getLootTable(key);
                var builder = new LootParams.Builder(level)
                        .withParameter(LootContextParams.THIS_ENTITY, this)
                        .withParameter(LootContextParams.ORIGIN, this.position())
                        .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                        .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
                        .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity());
                if (recentlyHit && this.lastHurtByPlayer != null) {
                    builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, this.lastHurtByPlayer).withLuck(this.lastHurtByPlayer.getLuck());
                }

                LootParams lootparams = builder.create(LootContextParamSets.ENTITY);
                loottable.getRandomItems(lootparams, this.getLootTableSeed(), this::spawnAtLocation);
            }
            case CUSTOM -> {
                for (LootTableConfig lootTableConfig : npcInventory.getLootTables()) {
                    ItemStack item = lootTableConfig.getItem();
                    if (item.isEmpty()) continue;
                    // 随机概率判断是否掉落
                    if (random.nextFloat() <= lootTableConfig.getChance()) this.spawnAtLocation(item);
                }
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        NpcAttributes npcAttributes = getNpcAttributes();
        if (npcAttributes.isCanDrown() && source.is(DamageTypeTags.IS_DROWNING)) {
            return false;
        } else if (npcAttributes.isFallDamage() && source.is(DamageTypeTags.IS_FALL)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    public NpcBasicsSetting getNpcBasicsSetting() {
        return getNpcAttachment(NpcBasicsSetting.class);
    }

    public NpcDynamicModel getNpcDynamicModel() {
        return getNpcAttachment(NpcDynamicModel.class);
    }

    public NpcAttributes getNpcAttributes() {
        return getNpcAttachment(NpcAttributes.class);
    }

    public NpcViScriptTeamIntegration getNpcViScriptTeamIntegration() {
        return getNpcAttachment(NpcViScriptTeamIntegration.class);
    }

    public NpcInventory getNpcInventory() {
        return getNpcAttachment(NpcInventory.class);
    }

    public NpcAI getNpcAI() {
        return getNpcAttachment(NpcAI.class);
    }

    @Nullable
    public MindMachine getMind() {
        return mind;
    }

    public void initMind() {
        if (this.level().isClientSide || mind != null) return;
        initMind(getNpcAI(), currentMindConfigHash(getNpcAI()));
    }

    private void initMind(NpcAI ai, int configHash) {
        if (this.level().isClientSide || mind != null) return;
        mind = MindMachineManager.getInstance().createInstance(this, ai.toConfig());
        mindConfigHash = configHash;
        if (ai.getBehaviorGraph() != null && !ai.getBehaviorGraph().isEmpty()) {
            behaviorTreeIntention = new NpcBehaviorTreeIntention(mind, ai);
            mind.addIntention(IntentionPriority.URGENT, behaviorTreeIntention);
        }
        for (IntentionEntry entry : ai.getIntentions()) {
            try {
                BaseIntention intention = IntentionTypeRegistry.deserialize(entry.getType(), mind, entry.getData());
                mind.addIntention(entry.getPriority(), intention);
            } catch (IllegalArgumentException ignored) {
                // Unknown intention type; keep the rest of the configured mind usable.
            }
        }
    }

    private void syncMindState(NpcAI ai) {
        if (this.level().isClientSide) return;
        if (!ai.isEnabled()) {
            clearMind();
            return;
        }

        int configHash = currentMindConfigHash(ai);
        if (mind == null) {
            initMind(ai, configHash);
        } else if (mindConfigHash != configHash) {
            clearMind();
            initMind(ai, configHash);
        }
    }

    private void clearMind() {
        if (mind != null) {
            MindMachineManager.getInstance().removeInstance(this);
            mind = null;
        }
        behaviorTreeIntention = null;
        mindConfigHash = 0;
    }

    public boolean isNpcBehaviorDebugPaused() {
        NpcBehaviorRuntime runtime = getNpcBehaviorRuntime();
        return runtime != null && runtime.isDebugPaused();
    }

    public void setNpcBehaviorDebugPaused(boolean paused) {
        NpcBehaviorRuntime runtime = getNpcBehaviorRuntime();
        if (runtime != null) {
            runtime.setDebugPaused(paused);
        }
    }

    public void continueNpcBehaviorDebug() {
        NpcBehaviorRuntime runtime = getNpcBehaviorRuntime();
        if (runtime != null) {
            runtime.continueDebug();
        }
    }

    public void stepNpcBehaviorDebug() {
        NpcBehaviorRuntime runtime = getNpcBehaviorRuntime();
        if (runtime != null) {
            runtime.stepDebugTickNow();
        }
    }

    public void stopNpcBehaviorDebug() {
        NpcBehaviorRuntime runtime = getNpcBehaviorRuntime();
        if (runtime != null) {
            runtime.stopDebug();
        }
    }

    public void setNpcBehaviorEditorDebugIgnoredPlayer(UUID playerUuid) {
        NpcBehaviorRuntime runtime = getNpcBehaviorRuntime();
        if (runtime != null) {
            runtime.setEditorDebugIgnoredPlayer(playerUuid);
        }
    }

    public void clearNpcBehaviorEditorDebugIgnoredPlayer(UUID playerUuid) {
        NpcBehaviorRuntime runtime = getNpcBehaviorRuntime();
        if (runtime != null) {
            runtime.clearEditorDebugIgnoredPlayer(playerUuid);
        }
    }

    public NpcBehaviorDebugSnapshot getNpcBehaviorDebugSnapshot() {
        NpcBehaviorRuntime runtime = getNpcBehaviorRuntime();
        return runtime == null ? new NpcBehaviorDebugSnapshot() : runtime.getLastDebugSnapshot();
    }

    @Nullable
    private NpcBehaviorRuntime getNpcBehaviorRuntime() {
        return behaviorTreeIntention == null ? null : behaviorTreeIntention.runtime();
    }

    private int currentMindConfigHash(NpcAI ai) {
        return Objects.hash(ai.isEnabled(), ai.getTickRate(), ai.getNavigation(), ai.getIntentions(),
                ai.getBehaviorGraph(), ai.getBehaviorProgram());
    }

    public String getNpcType() {
        return this.getNpcBasicsSetting().getNpcId();
    }

    private void setAttributeBaseValue(Holder<Attribute> attribute, double value) {
        AttributeInstance attributeInstance = this.getAttribute(attribute);
        if (attributeInstance != null) attributeInstance.setBaseValue(value);
    }

    @Override
    public void die(DamageSource source) {
        if (NeoForge.EVENT_BUS.post(new NpcEvent.Death(this, source)).isCanceled()) return;
        super.die(source);
    }

    @Override
    public void rideTick() {
        super.rideTick();
        this.oBob = this.bob;
        this.bob = 0.0F;
    }

    private void moveCloak() {
        this.xCloakO = this.xCloak;
        this.yCloakO = this.yCloak;
        this.zCloakO = this.zCloak;
        double d0 = this.getX() - this.xCloak;
        double d1 = this.getY() - this.yCloak;
        double d2 = this.getZ() - this.zCloak;
        double d3 = 10.0;
        if (d0 > 10.0) {
            this.xCloak = this.getX();
            this.xCloakO = this.xCloak;
        }

        if (d2 > 10.0) {
            this.zCloak = this.getZ();
            this.zCloakO = this.zCloak;
        }

        if (d1 > 10.0) {
            this.yCloak = this.getY();
            this.yCloakO = this.yCloak;
        }

        if (d0 < -10.0) {
            this.xCloak = this.getX();
            this.xCloakO = this.xCloak;
        }

        if (d2 < -10.0) {
            this.zCloak = this.getZ();
            this.zCloakO = this.zCloak;
        }

        if (d1 < -10.0) {
            this.yCloak = this.getY();
            this.yCloakO = this.yCloak;
        }

        this.xCloak += d0 * 0.25;
        this.zCloak += d2 * 0.25;
        this.yCloak += d1 * 0.25;
    }

    private void attack(CustomNpc npc, LivingEntity entity) {
        MeleeConfig.executeAdditionalEffects(npc, entity);
    }

    @Override
    public void checkDespawn() {
    }

    @EventBusSubscriber(modid = ViScriptNpc.MOD_ID)
    public static class EventHandler {

        @SubscribeEvent
        public static void hurtAndAttack(LivingIncomingDamageEvent event) {
            if (event.getEntity().level().isClientSide) return;
            DamageSource source = event.getSource();
            if (event.getEntity() instanceof LivingEntity target
                    && isNpcRelatedDamage(source, target)
                    && !NpcFactionBridge.canHurtFromSource(source, target)) {
                event.setCanceled(true);
                return;
            }
            //npc受伤
            if (event.getEntity() instanceof CustomNpc npc) {
                DamageContainer container = event.getContainer();
                float currentDamage = container.getNewDamage();
                ResistanceConfig resistanceConfig = npc.getNpcAttributes().getResistanceConfig();
                if (source.is(DamageTypeTags.IS_PROJECTILE)) {
                    currentDamage *= 1 - resistanceConfig.getProjectile();
                }
                if (source.is(DamageTypeTags.IS_EXPLOSION)) {
                    currentDamage *= 1 - resistanceConfig.getExplosion();
                }
                if (source.getDirectEntity() instanceof LivingEntity) {
                    currentDamage *= 1 - resistanceConfig.getMelee();
                }
                if (source.is(DamageTypeTags.IS_FIRE)) {
                    currentDamage *= 1 - Mth.clamp(resistanceConfig.getFire(), 0.0f, 1.0f);
                }
                container.setNewDamage(currentDamage);
                if (NeoForge.EVENT_BUS.post(new NpcEvent.Hurt(event)).isCanceled()) {
                    event.setCanceled(true);
                }
            }
            //npc攻击
            if (source.getEntity() instanceof CustomNpc npc) {
                if (NeoForge.EVENT_BUS.post(new NpcEvent.Attack(npc, event)).isCanceled()) {
                    event.setCanceled(true);
                }
                if (source.getDirectEntity() instanceof CustomNpc) {
                    npc.attack(npc, event.getEntity());
                }
            }
        }

        private static boolean isNpcRelatedDamage(DamageSource source, LivingEntity target) {
            return target instanceof CustomNpc
                    || source.getEntity() instanceof CustomNpc
                    || source.getDirectEntity() instanceof NpcProjectile;
        }

        @SubscribeEvent
        public static void hurt(LivingDamageEvent.Post event) {
            if (event.getEntity() instanceof CustomNpc npc) {
                //反弹伤害
                float reboundDamage = npc.getNpcAttributes().getDefenseConfig().getReboundDamage();
                if (reboundDamage > 0) {
                    DamageSource source = event.getSource();
                    if (source.getEntity() instanceof LivingEntity entity) {
                        entity.hurt(npc.damageSources().mobAttack(npc), reboundDamage);
                    }
                }
            }
        }

        @SubscribeEvent
        public static void npcInteract(PlayerInteractEvent.EntityInteract event) {
            if (event.getHand() == InteractionHand.MAIN_HAND && event.getEntity() instanceof ServerPlayer serverPlayer && event.getTarget() instanceof CustomNpc npc) {
                if (NeoForge.EVENT_BUS.post(new NpcEvent.Interact(npc, serverPlayer)).isCanceled()) {
                    event.setCanceled(true);
                }
            }
        }

        @SubscribeEvent
        public static void spawn(EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof CustomNpc npc) {
                if (!npc.level().isClientSide && npc.mind == null && npc.getNpcAI().isEnabled()) {
                    npc.initMind();
                }
                NeoForge.EVENT_BUS.post(new NpcEvent.Spawn(npc));
            }
        }

        @SubscribeEvent
        public static void despawn(EntityLeaveLevelEvent event) {
            if (event.getEntity() instanceof CustomNpc npc) {
                if (!event.getLevel().isClientSide && npc.mind != null) {
                    npc.clearMind();
                }
            }
        }

        @SubscribeEvent
        public static void killed(LivingDeathEvent event) {
            DamageSource source = event.getSource();
            if (source.getEntity() instanceof CustomNpc npc) {
                if (NeoForge.EVENT_BUS.post(new NpcEvent.Killed(npc, event.getEntity(), source)).isCanceled()) {
                    event.setCanceled(true);
                }
            }
        }


        @SubscribeEvent
        public static void changeTarget(LivingChangeTargetEvent event) {
            if (event.getEntity() instanceof CustomNpc npc) {
                if (NeoForge.EVENT_BUS.post(new NpcEvent.TargetEvent(npc, event)).isCanceled()) {
                    event.setCanceled(true);
                    return;
                }
                LivingEntity newTarget = event.getNewAboutToBeSetTarget();
                if (newTarget != null
                        && NpcFactionBridge.hasConfiguredFaction(npc)
                        && !NpcFactionBridge.shouldActivelyTarget(npc, newTarget)) {
                    event.setNewAboutToBeSetTarget(null);
                }
            }
        }

        @SubscribeEvent
        public static void onXpDrop(LivingExperienceDropEvent event) {
            if (event.getEntity() instanceof CustomNpc npc) {
                Range levelConfig = npc.getNpcInventory().getLevelRange();
                int random = Mth.randomBetweenInclusive(RandomSource.create(), levelConfig.getMin().intValue(), levelConfig.getMax().intValue());
                event.setDroppedExperience(random);
            }
        }

        @SubscribeEvent
        public static void setLootTableKeys(OnDatapackSyncEvent event) {
            if (event.getPlayer() == null) {
                MinecraftServer server = event.getPlayerList().getServer();
                setLootTableKeys(server);
                server.getPlayerList().getPlayers().forEach(EventHandler::sendLootTableKeys);
            } else sendLootTableKeys(event.getPlayer());
        }

        @SubscribeEvent
        public static void setLootTableKeys(ServerStartedEvent event) {
            setLootTableKeys(event.getServer());
        }

        private static void setLootTableKeys(MinecraftServer server) {
            lootTableKeys = server.reloadableRegistries().getKeys(Registries.LOOT_TABLE).stream().map(ResourceLocation::toString).collect(Collectors.toSet());
        }

        private static void sendLootTableKeys(ServerPlayer player) {
            if (player.server.getPlayerList().isOp(player.getGameProfile()))
                RPCPacketDistributor.rpcToPlayer(player, S2CPayload.SEND_LOOT_TABLES, lootTableKeys);
        }
    }
}
