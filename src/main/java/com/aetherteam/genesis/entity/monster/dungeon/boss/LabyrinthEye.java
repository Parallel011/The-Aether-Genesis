package com.aetherteam.genesis.entity.monster.dungeon.boss;

import com.aetherteam.aether.Aether;
import com.aetherteam.aether.block.AetherBlocks;
import com.aetherteam.aether.entity.AetherBossMob;
import com.aetherteam.aether.entity.ai.goal.MostDamageTargetGoal;
import com.aetherteam.aether.entity.monster.dungeon.boss.BossNameGenerator;
import com.aetherteam.aether.event.AetherEventDispatch;
import com.aetherteam.aether.network.packet.clientbound.BossInfoPacket;
import com.aetherteam.genesis.client.GenesisSoundEvents;
import com.aetherteam.genesis.entity.projectile.CogProjectile;
import com.aetherteam.nitrogen.entity.BossRoomTracker;
import com.aetherteam.nitrogen.network.PacketRelay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class LabyrinthEye extends PathfinderMob implements AetherBossMob<LabyrinthEye>, Enemy, IEntityWithComplexSpawn {
    public static final EntityDataAccessor<Boolean> DATA_AWAKE_ID = SynchedEntityData.defineId(LabyrinthEye.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Component> DATA_BOSS_NAME_ID = SynchedEntityData.defineId(LabyrinthEye.class, EntityDataSerializers.COMPONENT);
    public static final EntityDataAccessor<Integer> DATA_BOSS_STAGE = SynchedEntityData.defineId(LabyrinthEye.class, EntityDataSerializers.INT);
    private static final Music MINIBOSS_MUSIC = new Music(GenesisSoundEvents.MUSIC_MINIBOSS, 0, 0, true);
    public static final Map<Block, Function<BlockState, BlockState>> DUNGEON_BLOCK_CONVERSIONS = Map.ofEntries(
            Map.entry(AetherBlocks.LOCKED_CARVED_STONE.get(), (blockState) -> AetherBlocks.CARVED_STONE.get().defaultBlockState()),
            Map.entry(AetherBlocks.LOCKED_SENTRY_STONE.get(), (blockState) -> AetherBlocks.SENTRY_STONE.get().defaultBlockState()),
            Map.entry(AetherBlocks.BOSS_DOORWAY_CARVED_STONE.get(), (blockState) -> Blocks.AIR.defaultBlockState()),
            Map.entry(AetherBlocks.TREASURE_DOORWAY_CARVED_STONE.get(), (blockState) -> AetherBlocks.SKYROOT_TRAPDOOR.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, blockState.getValue(HorizontalDirectionalBlock.FACING)))
    );

    /**
     * Goal for targeting in groups of entities
     */
    private MostDamageTargetGoal mostDamageTargetGoal;

    private final ServerBossEvent bossFight;
    private BossRoomTracker<LabyrinthEye> bronzeDungeon;

    private final boolean[] stageDone = new boolean[13];
    public int chatCooldown;
//    private int cappedAmount;

    public LabyrinthEye(EntityType<? extends PathfinderMob> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.bossFight = new ServerBossEvent(this.getBossName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        this.bossFight.setVisible(false);
        this.xpReward = XP_REWARD_BOSS;
        this.setPersistenceRequired();
        for (int i = 0; i < 12; i++)
            this.stageDone[i] = false;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        this.setBossName(BossNameGenerator.generateBossName(this.getRandom()).append(Component.translatable("gui.aether_genesis.labyrinth_eye.title")));
        this.moveTo(Mth.floor(this.getX()), this.getY(), Mth.floor(this.getZ()));
        return spawnData;
    }
    
    public static AttributeSupplier.Builder createMobAttributes() {
        return Monster.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 500.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.75)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new DoNothingGoal(this));
        this.targetSelector.addGoal(4, new ArrowAttackCogGoal(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoalBoss(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.mostDamageTargetGoal = new MostDamageTargetGoal(this);
        this.targetSelector.addGoal(1, this.mostDamageTargetGoal);
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, livingEntity -> this.isBossFight()));
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_AWAKE_ID, false);
        this.entityData.define(DATA_BOSS_NAME_ID, Component.literal("Labyrinth's Eye"));
        this.entityData.define(DATA_BOSS_STAGE, 13);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isAwake() || (this.getTarget() instanceof Player player && (player.isCreative() || player.isSpectator()))) {
            this.setTarget(null);
        }
        this.evaporate();
        if (this.getChatCooldown() > 0) {
            this.chatCooldown--;
        }
    }

    private void evaporate() {
        Pair<BlockPos, BlockPos> minMax = this.getDefaultBounds(this);
        AetherBossMob.super.evaporate(this, minMax.getLeft(), minMax.getRight(), (blockState) -> true);
    }

    @Override
    public void customServerAiStep() {
        super.customServerAiStep();
        this.bossFight.setProgress(this.getHealth() / this.getMaxHealth());
        this.trackDungeon();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Optional<LivingEntity> damageResult = this.canDamageLabyrinthEye(source);
        if (damageResult.isPresent()) {
            if (super.hurt(source, amount) && this.getHealth() > 0) {
                if (!this.isBossFight()) {
                    this.start();
                }
                if (!this.level().isClientSide() && source.getEntity() instanceof LivingEntity living) {
                    this.mostDamageTargetGoal.addAggro(living, amount); // AI goal for being hurt.

                    for (int stage = 0; stage < 13; stage++) { //todo
                        if (this.isBossStage(stage) && !this.stageDone[stage]) {
                            this.setStage(stage);
                            this.spawnLargeCog(this, stage);
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    private Optional<LivingEntity> canDamageLabyrinthEye(DamageSource source) {
        if (this.level().getDifficulty() != Difficulty.PEACEFUL) {
            if (source.getDirectEntity() instanceof LivingEntity attacker) {
                if (this.getDungeon() == null || this.getDungeon().isPlayerWithinRoomInterior(attacker)) { // Only allow damage within the boss room.
                    return Optional.of(attacker);
                } else {
                    this.sendTooFarMessage(attacker);
                }
            } else if (source.getDirectEntity() instanceof Projectile projectile) {
                if (projectile.getOwner() instanceof LivingEntity attacker) {
                    if (this.getDungeon() == null || this.getDungeon().isPlayerWithinRoomInterior(attacker)) { // Only allow damage within the boss room.
                        return Optional.of(attacker);
                    } else {
                        return this.sendTooFarMessage(attacker);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<LivingEntity> sendTooFarMessage(LivingEntity attacker) {
        if (!this.level().isClientSide() && attacker instanceof Player player) {
            if (this.getChatCooldown() <= 0) {
                this.displayTooFarMessage(player); // Too far from Slider
                this.setChatCooldown(15);
            }
        }
        return Optional.empty();
    }

    private boolean isBossStage(int stage) {
        float interval = this.getMaxHealth() / 13.0F;
        return this.getHealth() <= (this.getMaxHealth() - ((stage - 1) * interval))
                && this.getHealth() >= (this.getMaxHealth() - (stage * interval));

//        return switch (stage) {
//            case 1 -> (getHealth() <= getMaxHealth() && getHealth() >= getMaxHealth() * 0.9);
//            case 2 -> (getHealth() < getMaxHealth() * 0.9 && getHealth() >= getMaxHealth() * 0.8);
//            case 3 -> (getHealth() < getMaxHealth() * 0.8 && getHealth() >= getMaxHealth() * 0.725);
//            case 4 -> (getHealth() < getMaxHealth() * 0.65 && getHealth() >= getMaxHealth() * 0.575);
//            case 5 -> (getHealth() < getMaxHealth() * 0.575 && getHealth() >= getMaxHealth() * 0.5);
//            case 6 -> (getHealth() < getMaxHealth() * 0.45 && getHealth() >= getMaxHealth() * 0.4);
//            case 7 -> (getHealth() < getMaxHealth() * 0.4 && getHealth() >= getMaxHealth() * 0.35);
//            case 8 -> (getHealth() < getMaxHealth() * 0.35 && getHealth() >= getMaxHealth() * 0.3);
//            case 9 -> (getHealth() < getMaxHealth() * 0.3 && getHealth() >= getMaxHealth() * 0.25);
//            case 10 -> (getHealth() < getMaxHealth() * 0.25 && getHealth() >= getMaxHealth() * 0.2);
//            case 11 -> (getHealth() < getMaxHealth() * 0.2 && getHealth() >= getMaxHealth() * 0.15);
//            case 12 -> (getHealth() < getMaxHealth() * 0.15 && getHealth() >= getMaxHealth() * 0.1);
//            case 13 -> (getHealth() < getMaxHealth() * 0.1);
//            default -> false;
//        };
    }

    private void start() {
        this.setHealth(this.getMaxHealth());
        this.setAwake(true);
        this.setBossFight(true);
        if (this.getDungeon() != null) {
            this.closeRoom();
        }
        AetherEventDispatch.onBossFightStart(this, this.getDungeon());
    }

    public void reset() {
        this.setAwake(false);
        this.setBossFight(false);
        this.setTarget(null);
        if (this.getDungeon() != null) {
            this.setPos(this.getDungeon().originCoordinates());
            this.openRoom();
        }
        AetherEventDispatch.onBossFightStop(this, this.getDungeon());
    }

    @Override
    public void die(DamageSource source) {
        this.explode();
        if (this.level() instanceof ServerLevel) {
            this.bossFight.setProgress(this.getHealth() / this.getMaxHealth());
            if (this.getDungeon() != null) {
                this.getDungeon().grantAdvancements(source);
                this.tearDownRoom();
            }
        }
        super.die(source);
    }

    private void explode() {
        for (int i = 0; i < (this.getHealth() <= 0 ? 16 : 48); i++) {
            double x = this.position().x() + (double) (this.getRandom().nextFloat() - this.getRandom().nextFloat()) * 1.5;
            double y = this.getBoundingBox().minY + 1.75 + (double) (this.getRandom().nextFloat() - this.getRandom().nextFloat()) * 1.5;
            double z = this.position().z() + (double) (this.getRandom().nextFloat() - this.getRandom().nextFloat()) * 1.5;
            this.level().addParticle(ParticleTypes.POOF, x, y, z, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public void checkDespawn() { }

    @Nullable
    @Override
    public BlockState convertBlock(BlockState state) {
        return DUNGEON_BLOCK_CONVERSIONS.getOrDefault(state.getBlock(), (blockState) -> null).apply(state);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        PacketRelay.sendToPlayer(new BossInfoPacket.Display(this.bossFight.getId(), this.getId()), player);
        if (this.getDungeon() == null || this.getDungeon().isPlayerTracked(player)) {
            this.bossFight.addPlayer(player);
            AetherEventDispatch.onBossFightPlayerAdd(this, this.getDungeon(), player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        PacketRelay.sendToPlayer(new BossInfoPacket.Remove(this.bossFight.getId(), this.getId()), player);
        this.bossFight.removePlayer(player);
        AetherEventDispatch.onBossFightPlayerRemove(this, this.getDungeon(), player);
    }

    @Override
    public void onDungeonPlayerAdded(@javax.annotation.Nullable Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            this.bossFight.addPlayer(serverPlayer);
            AetherEventDispatch.onBossFightPlayerAdd(this, this.getDungeon(), serverPlayer);
        }
    }

    @Override
    public void onDungeonPlayerRemoved(@javax.annotation.Nullable Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            this.bossFight.removePlayer(serverPlayer);
            AetherEventDispatch.onBossFightPlayerRemove(this, this.getDungeon(), serverPlayer);
        }
    }

    public int getStage() {
        return this.entityData.get(DATA_BOSS_STAGE);
    }

    private void setStage(int stage) {
        this.entityData.set(DATA_BOSS_STAGE, stage);
    }

    public boolean isAwake() {
        return this.entityData.get(DATA_AWAKE_ID);
    }

    public void setAwake(boolean ready) {
        this.entityData.set(DATA_AWAKE_ID, ready);
    }

    @Override
    public Component getBossName() {
        return this.entityData.get(DATA_BOSS_NAME_ID);
    }

    @Override
    public void setBossName(Component component) {
        this.entityData.set(DATA_BOSS_NAME_ID, component);
        this.bossFight.setName(component);
    }

    @Override
    public BossRoomTracker<LabyrinthEye> getDungeon() {
        return this.bronzeDungeon;
    }

    @Override
    public void setDungeon(BossRoomTracker<LabyrinthEye> bossRoomTracker) {
        this.bronzeDungeon = bossRoomTracker;
    }

    @Override
    public boolean isBossFight() {
        return this.bossFight.isVisible();
    }

    @Override
    public void setBossFight(boolean isFighting) {
        this.bossFight.setVisible(isFighting);
    }

    /**
     * @return The {@link ResourceLocation} for this boss's health bar.
     */
    @Nullable
    @Override
    public ResourceLocation getBossBarTexture() {
        return new ResourceLocation(Aether.MODID, "boss_bar/slider");
    }

    /**
     * @return The {@link ResourceLocation} for this boss's health bar background.
     */
    @Nullable
    @Override
    public ResourceLocation getBossBarBackgroundTexture() {
        return new ResourceLocation(Aether.MODID, "boss_bar/slider_background");
    }

    /**
     * @return The {@link Music} for this boss's fight.
     */
    @Nullable
    @Override
    public Music getBossMusic() {
        return MINIBOSS_MUSIC;
    }

    /**
     * @return The {@link Integer} for the cooldown until another chat message can display.
     */
    public int getChatCooldown() {
        return this.chatCooldown;
    }

    /**
     * Sets the cooldown for when another chat message can display.
     *
     * @param cooldown The {@link Integer} cooldown.
     */
    public void setChatCooldown(int cooldown) {
        this.chatCooldown = cooldown;
    }


    @Override
    public int getDeathScore() {
        return this.deathScore;
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        this.setBossName(name);
    }

    @Override
    protected SoundEvent getDeathSound() {
        return GenesisSoundEvents.ENTITY_LABYRINTH_EYE_DEATH.get();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return GenesisSoundEvents.ENTITY_LABYRINTH_EYE_MOVE.get();
    }

    @Override
    protected float getJumpPower() {
        return 0.0F;
    }

    @Override
    public float getYRot() {
        return !this.isAwake() ? 0 : super.getYRot();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isNoGravity() {
        return !this.isAwake();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) { //todo stage data
        super.addAdditionalSaveData(tag);
        this.addBossSaveData(tag);
        tag.putBoolean("Awake", this.isAwake());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.readBossSaveData(tag);
        if (tag.contains("Awake")) {
            this.setAwake(tag.getBoolean("Awake"));
        }
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        CompoundTag tag = new CompoundTag();
        this.addBossSaveData(tag);
        buffer.writeNbt(tag);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf additionalData) {
        CompoundTag tag = additionalData.readNbt();
        if (tag != null) {
            this.readBossSaveData(tag);
        }
    }













    //todo


    public void spawnLargeCog(Entity entityToAttack, int stage) {
        if (this.stageDone[stage])
            return;
        CogProjectile cog = new CogProjectile(this.level(), this, true);
        cog.setYRot(this.getYRot());
        cog.setXRot(this.getXRot());
        double var3 = entityToAttack.position().x + entityToAttack.getMotionDirection().getStepX() - this.position().x;
        double var5 = entityToAttack.position().y + -this.getMotionDirection().getStepY();
        double var7 = entityToAttack.position().z + entityToAttack.getMotionDirection().getStepZ() - this.position().z;
        float var9 = (float) Math.sqrt(var3 * var3 + var7 * var7);
        if (!this.level().isClientSide()) {
            float distance = var9 * 0.075F;
            cog.shoot(var3, var5 + (var9 * 0.2F), var7, distance, 0.0F);
            this.playSound(GenesisSoundEvents.ENTITY_LABYRINTH_EYE_COG_LOSS.get(), 2.0F, 1.0F);
            this.playSound(SoundEvents.ITEM_BREAK, 0.8F, 0.8F + this.level().random.nextFloat() * 0.4F);
            this.level().addFreshEntity(cog);
        }
        stageDone[stage] = true;
    }

    public static class DoNothingGoal extends Goal {
        private final LabyrinthEye labyrinthEye;
        public DoNothingGoal(LabyrinthEye labyrinthEye) {
            this.labyrinthEye = labyrinthEye;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return !this.labyrinthEye.isBossFight();
        }

        @Override
        public void start() {
            this.labyrinthEye.setDeltaMovement(Vec3.ZERO);
            this.labyrinthEye.setPos(this.labyrinthEye.position().x,
                    this.labyrinthEye.position().y,
                    this.labyrinthEye.position().z);
        }
    }

    public static class ArrowAttackCogGoal extends Goal {
        private final LabyrinthEye labyrinthEye;

        public ArrowAttackCogGoal(LabyrinthEye labyrinthEye) {
            this.labyrinthEye = labyrinthEye;
        }

        @Override
        public boolean canUse() {
            return this.labyrinthEye.isBossFight() && this.labyrinthEye.random.nextInt(20) == 0;
        }

        @Override
        public void start() {
            Entity cog = new CogProjectile(this.labyrinthEye.level(), this.labyrinthEye, false);
            this.labyrinthEye.level().addFreshEntity(cog);
            cog.setPos(labyrinthEye.position());
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    public static class LookAtPlayerGoalBoss extends LookAtPlayerGoal{
        private final LabyrinthEye labyrinthEye;

        public LookAtPlayerGoalBoss(LabyrinthEye pMob, Class<? extends LivingEntity> pLookAtType, float pLookDistance) {
            super(pMob, pLookAtType, pLookDistance);
            this.labyrinthEye = pMob;
        }

        public boolean canUse() {
        return super.canUse() && this.labyrinthEye.isBossFight();
        }
    }
}