package com.viscript.npc.npc;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.math.Range;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.mojang.blaze3d.MethodsReturnNonnullByDefault;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.event.neoforge.NpcEvent;
import com.viscript.npc.gui.edit.data.NpcConfig;
import com.viscript.npc.network.s2c.S2CPayload;
import com.viscript.npc.npc.data.INpcData;
import com.viscript.npc.npc.data.attributes.MeleeConfig;
import com.viscript.npc.npc.data.attributes.NpcAttributes;
import com.viscript.npc.npc.data.attributes.ResistanceConfig;
import com.viscript.npc.npc.data.basics.setting.NpcBasicsSetting;
import com.viscript.npc.npc.data.dynamic.model.NpcDynamicModel;
import com.viscript.npc.npc.data.inventory.LootTableConfig;
import com.viscript.npc.npc.data.inventory.NpcInventory;
import com.viscript.npc.npc.data.mod.integrations.NpcModIntegrations;
import com.viscript.npc.plugin.RegisterNpcEvent;
import com.viscript.npc.util.common.StrUtil;
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
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CustomNpc extends PathfinderMob implements RangedAttackMob {
    private static final Map<Class<? extends INpcData>, AttachmentType<? extends INpcData>> NPC_DATA_ATTACHMENTS = new HashMap<>();
    public static Set<String> lootTableKeys = Set.of();

    static {
        ViScriptNpc.executePluginMethod(plugin -> plugin.registerNpc(new RegisterNpcEvent()));
    }

    //披风用参数
    public double xCloakO;
    public double yCloakO;
    public double zCloakO;
    public double xCloak;
    public double yCloak;
    public double zCloak;
    public float oBob;
    public float bob;

//    // MindMachine
//    @Getter
//    MindMachine mind;

    public CustomNpc(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
//        mind = new MindMachine(this);
    }

    public static <T extends INpcData> void putNpcAttachment(Class<T> clazz, AttachmentType<T> attachmentType) {
        NPC_DATA_ATTACHMENTS.put(clazz, attachmentType);
        NpcConfig.NPC_DATA_CLASSES.add(clazz);
    }

    public static <T extends INpcData> AttachmentType<T> getNpcAttachment(Class<T> clazz) {
        // noinspection unchecked
        return (AttachmentType<T>) NPC_DATA_ATTACHMENTS.get(clazz);
    }

    @Override
    protected void registerGoals() {
/*        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(3, new RangedBowAttackGoal<>(this, 1.0F, 20, 15.0F));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Zombie.class, true));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0F));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));*/
    }

    @Override
    public void performRangedAttack(LivingEntity target, float v) {
/*        ItemStack weapon = this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, (item) -> item instanceof BowItem));
        ItemStack itemstack1 = this.getProjectile(weapon);
        AbstractArrow abstractarrow = ((ArrowItem) Items.ARROW).createArrow(level(), new ItemStack(Items.ARROW), this, weapon);
        Item var7 = weapon.getItem();
        if (var7 instanceof ProjectileWeaponItem weaponItem) {
            abstractarrow = weaponItem.customArrow(abstractarrow, itemstack1, weapon);
        }

        double d0 = target.getX() - this.getX();
        double d1 = target.getY(0.3333333333333333) - abstractarrow.getY();
        double d2 = target.getZ() - this.getZ();
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);
        abstractarrow.shoot(d0, d1 + d3 * (double)0.2F, d2, 1.6F, (float)(14 - this.level().getDifficulty().getId() * 4));
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        this.level().addFreshEntity(abstractarrow);*/
    }

    @Override
    public void tick() {
        super.tick();
        this.moveCloak();
        if (this.level() instanceof ServerLevel) {
            NeoForge.EVENT_BUS.post(new NpcEvent.Tick(this));
        }

        // MindMachine
//        mind.tick();
        if (level().isClientSide()) {
            Entity entity = getNpcDynamicModel().getEntity(this);
            try { // 某些生物的AI会导致奇怪的崩溃
                if (entity != null) entity.tick();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void aiStep() {
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

    public static void readNpcAttachments(CompoundTag tag, CustomNpc npc) {
        NPC_DATA_ATTACHMENTS.forEach((clazz, attachmentType) ->
                npc.getData(getNpcAttachment(clazz)).deserializeNBT(Platform.getFrozenRegistry(),
                        tag.getCompound(StrUtil.toCamelCase(clazz.getSimpleName()))));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        readNpcAttachments(compoundTag, this);
        updateNpcState();
    }

    public void updateNpcState() {
        NPC_DATA_ATTACHMENTS.forEach((clazz, a) -> syncData(getNpcAttachment(clazz)));
        //基础信息
        NpcBasicsSetting npcBasicsSetting = getNpcBasicsSetting();
        this.setAttributeBaseValue(Attributes.SCALE, npcBasicsSetting.getModeSize());
        this.setInvulnerable(npcBasicsSetting.isInvulnerable());
        this.setNoAi(npcBasicsSetting.isNoAI());
        this.setNoGravity(npcBasicsSetting.isNoGravity());

        //动态模型
        updateBoundingBox();

        //NPC属性
        NpcAttributes npcAttributes = getNpcAttributes();
        this.setAttributeBaseValue(Attributes.MAX_HEALTH, npcAttributes.getMaxHealth());
        this.setAttributeBaseValue(Attributes.MOVEMENT_SPEED, npcAttributes.getMovementSpeed());
        this.setAttributeBaseValue(Attributes.FOLLOW_RANGE, npcAttributes.getFollowRange());
        //近战属性
        this.setAttributeBaseValue(Attributes.ATTACK_DAMAGE, npcAttributes.getMeleeConfig().getAttackDamage());
        this.setAttributeBaseValue(Attributes.ATTACK_KNOCKBACK, npcAttributes.getMeleeConfig().getKnockback());
        this.setAttributeBaseValue(Attributes.KNOCKBACK_RESISTANCE, npcAttributes.getResistanceConfig().getKnockback());
        //防御
        this.setAttributeBaseValue(Attributes.ARMOR, npcAttributes.getDefenseConfig().getArmor());
        this.setAttributeBaseValue(Attributes.ARMOR_TOUGHNESS, npcAttributes.getDefenseConfig().getArmorToughness());

        //AI


        //联动模组
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
        if (npcAttributes.isImmuneToFire() && source.is(DamageTypeTags.IS_FIRE)) {
            return false;
        } else if (npcAttributes.isCanDrown() && source.is(DamageTypeTags.IS_DROWNING)) {
            return false;
        } else if (npcAttributes.isFallDamage() && source.is(DamageTypeTags.IS_FALL)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    public NpcBasicsSetting getNpcBasicsSetting() {
        return this.getData(getNpcAttachment(NpcBasicsSetting.class));
    }

    public NpcDynamicModel getNpcDynamicModel() {
        return this.getData(getNpcAttachment(NpcDynamicModel.class));
    }

    public NpcAttributes getNpcAttributes() {
        return this.getData(getNpcAttachment(NpcAttributes.class));
    }

    public NpcModIntegrations getNpcModIntegrations() {
        return this.getData(getNpcAttachment(NpcModIntegrations.class));
    }

    public NpcInventory getNpcInventory() {
        return this.getData(getNpcAttachment(NpcInventory.class));
    }

    public String getNpcType() {
        return this.getNpcBasicsSetting().getType();
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
    public void checkDespawn() {}

    @EventBusSubscriber(modid = ViScriptNpc.MOD_ID)
    public static class EventHandler {

        @SubscribeEvent
        public static void hurtAndAttack(LivingIncomingDamageEvent event) {
            if (event.getEntity().level().isClientSide) return;
            DamageSource source = event.getSource();
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
                npc.attack(npc, event.getEntity());
            }
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
                NeoForge.EVENT_BUS.post(new NpcEvent.Spawn(npc));
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
