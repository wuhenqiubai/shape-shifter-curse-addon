package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.AllayJukeboxItem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.SkillBlocker;

import java.util.List;
import java.util.UUID;

/**
 * SP悦灵唱片机区域效果Tick逻辑
 * - 回血模式：每5秒给20格内白名单生物回1HP
 * - 加速模式：持续给20格内白名单生物+10%移速
 * - 每秒消耗1点充能
 * - 播放自定义音乐
 */
public class AllaySPJukebox {
    private AllaySPJukebox() {
        // Utility class
    }

    public static final double RANGE = 20.0;
    private static final ResourceLocation SPEED_MODIFIER_UUID = ResourceLocation.parse("a3b4c5d6-e7f8-9012-3456-789abcdef012");
    private static final ResourceLocation SPEED_MODIFIER_NAME = ResourceLocation.parse("allay_jukebox_speed");
    private static final double SPEED_BONUS = 0.10; // 10% speed

    // Track per-player: -1 = not playing, 0 = speed music, 1 = heal music
    private static final java.util.concurrent.ConcurrentHashMap<UUID, Integer> playerMusicState = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 玩家断线时清理音乐状态并移除速度加成，防止内存泄漏和其他玩家永久保留速度buff
     */
    public static void onPlayerDisconnect(ServerPlayer player) {
        Integer currentState = playerMusicState.remove(player.getUUID());
        if (currentState != null && currentState != -1) {
            removeSpeedFromAll(player);
        }
    }

    /**
     * 收集应当听到该唱片机音乐的玩家：持有者本人 + 追踪持有者的所有玩家（即附近能看到他的玩家）。
     * PlayerLookup.tracking 不含玩家自己，故手动加入持有者。
     * 修复：客机播放时其他人听不见（原先只发持有者）——改为向附近玩家范围广播，主客机一致。
     */
    private static java.util.Set<ServerPlayer> getAudience(ServerPlayer player) {
        java.util.Set<ServerPlayer> audience = new java.util.HashSet<>(PlayerLookup.tracking(player));
        audience.add(player);
        return audience;
    }

    /**
     * Stop all jukebox music for a player by sending StopSoundS2CPacket
     */
    public static void stopAllMusic(ServerPlayer player) {
        // 向持有者 + 附近所有玩家发停止包，保证范围广播的音乐对所有听众都能停下（主客机一致）
        for (ServerPlayer audience : getAudience(player)) {
            audience.connection.send(new ClientboundStopSoundPacket(SscAddon.ALLAY_HEAL_MUSIC_ID, SoundSource.RECORDS));
            audience.connection.send(new ClientboundStopSoundPacket(SscAddon.ALLAY_SPEED_MUSIC_ID, SoundSource.RECORDS));
            // 兜底：客机端按 ID 停止偶发不生效（流式音乐 SoundInstance 注册时序问题），再停整个 RECORDS 类别确保彻底停止（#1 关不掉）
            audience.connection.send(new ClientboundStopSoundPacket((ResourceLocation) null, SoundSource.RECORDS));
        }
        playerMusicState.put(player.getUUID(), -1);
    }

    /**
     * Stop old music and immediately play the new mode's music from the beginning
     */
    private static void switchMusic(ServerPlayer player, int newMode) {
        SoundEvent newSound = (newMode == AllayJukeboxItem.MODE_SPEED) ? SscAddon.ALLAY_SPEED_MUSIC_EVENT : SscAddon.ALLAY_HEAL_MUSIC_EVENT;
        Holder<SoundEvent> entry = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(newSound);
        // 所有听众用同一随机种子，保证范围内玩家听到的音乐起播一致
        long seed = player.getRandom().nextLong();
        // 向持有者 + 附近所有玩家广播：先停旧音乐（含 RECORDS 兜底防叠加/关不掉 #1），再以持有者位置播放新音乐（带距离衰减）
        for (ServerPlayer audience : getAudience(player)) {
            audience.connection.send(new ClientboundStopSoundPacket(SscAddon.ALLAY_HEAL_MUSIC_ID, SoundSource.RECORDS));
            audience.connection.send(new ClientboundStopSoundPacket(SscAddon.ALLAY_SPEED_MUSIC_ID, SoundSource.RECORDS));
            audience.connection.send(new ClientboundStopSoundPacket((ResourceLocation) null, SoundSource.RECORDS));
            // 音量 0.12（原 0.06 的 2 倍）；用实体跟踪音效包，让音乐声源跟随持有者移动（而非钉在释放位置）
            audience.connection.send(new ClientboundSoundEntityPacket(entry, SoundSource.RECORDS,
                    player, 0.25f, 1.0f, seed));
        }

        playerMusicState.put(player.getUUID(), newMode);
    }


    /**
     * Called every tick for each allay_sp player from the server tick event
     */
    public static void tick(ServerPlayer player) {
        if (SkillBlocker.isSkillBlocked(player, "allay", "jukebox_charge")) {
            return;
        }
        IForm currentForm = FormUtils.getCurrentForm(player);
        boolean isAllaySp = currentForm != null && currentForm.getFormID().equals(ResourceLocation.fromNamespaceAndPath("my_addon", "allay_sp"));

        // Check if cleanup is needed (if form changed OR item is missing/inactive)
        // Note: we check form first. If not Allay SP, we just cleanup and return.
        if (!isAllaySp) {
            Integer currentState = playerMusicState.getOrDefault(player.getUUID(), -1);
            if (currentState != -1) {
                stopAllMusic(player);
                removeSpeedFromAll(player);
            }
            return;
        }

        // Find jukebox item in inventory (should be in slot 1)
        ItemStack jukeboxStack = player.getInventory().getItem(1);
        
        // If item is missing, treat as inactive -> cleanup
        if (!jukeboxStack.is(SscAddon.ALLAY_JUKEBOX)) {
            Integer currentState = playerMusicState.getOrDefault(player.getUUID(), -1);
            if (currentState != -1) {
                stopAllMusic(player);
                removeSpeedFromAll(player);
            }
            return;
        }

        boolean isActive = AllayJukeboxItem.isActive(jukeboxStack);

        if (!isActive) {
            // Remove speed modifiers from all nearby entities when deactivated
            removeSpeedFromAll(player);
            // Stop music if it was playing
            Integer lastState = playerMusicState.get(player.getUUID());
            if (lastState != null && lastState != -1) {
                stopAllMusic(player);
            }
            return;
        }

        int charge = AllayJukeboxItem.getCharge(jukeboxStack);
        if (charge <= 0) {
            // Out of charge, deactivate
            AllayJukeboxItem.setActive(jukeboxStack, false);
            removeSpeedFromAll(player);
            stopAllMusic(player);
            return;
        }

        // Consume 1 charge per second (every 20 ticks)
        if (player.tickCount % 20 == 0) {
            AllayJukeboxItem.setCharge(jukeboxStack, charge - 1);
        }

        int mode = AllayJukeboxItem.getMode(jukeboxStack);

        // ===== Music: 每 tick 检测 mode 变化（不依赖实体扫描，保证切模即时） =====
        Integer lastMusicMode = playerMusicState.getOrDefault(player.getUUID(), -1);
        if (lastMusicMode != mode) {
            // Mode changed or first time: stop old, play new immediately
            switchMusic(player, mode);
        }

        // 性能优化：范围实体扫描(getEntitiesByClass) + buff 套用/移除 降频到每 5 tick 一次（原每 tick）。
        // applySpeedModifier 幂等（已有则跳过），回血本就每 60t、cleanup 本就每 40t（均为 5 的倍数，命中不变）；
        // 新进范围者最多晚 5t 获加速/被回血，肉眼不可察。
        if (player.age % 5 == 0) {
            List<LivingEntity> nearbyEntities = getNearbyWhitelistEntities(player);

            if (mode == AllayJukeboxItem.MODE_HEAL) {
                // Heal mode: every 3 seconds (60 ticks), heal 1 HP
                if (player.age % 60 == 0) {
                    for (LivingEntity entity : nearbyEntities) {
                        entity.heal(1.0f);
                    }
                }
                // Remove speed modifier when in heal mode
                removeSpeedFromAll(player);
            } else {
                // Speed mode: apply 10% speed modifier
                for (LivingEntity entity : nearbyEntities) {
                    applySpeedModifier(entity);
                }
                // Clean up speed modifiers from entities that moved out of range
                cleanupOutOfRangeEntities(player, nearbyEntities);
            }
        }
    }

    private static List<LivingEntity> getNearbyWhitelistEntities(ServerPlayer player) {
        AABB box = new AABB(
                player.getX() - RANGE, player.getY() - RANGE, player.getZ() - RANGE,
                player.getX() + RANGE, player.getY() + RANGE, player.getZ() + RANGE
        );

        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, box, entity -> {
            double dist = entity.distanceToSqr(player);
            if (dist > RANGE * RANGE) return false;
            if (entity == player) return true;
            // Use the allay whitelist
            return AllaySPGroupHeal.isInWhitelist(player, entity);
        });
    }

    private static void applySpeedModifier(LivingEntity entity) {
        AttributeInstance speedAttr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;

        AttributeModifier existing = speedAttr.getModifier(SPEED_MODIFIER_UUID);
        if (existing == null) {
            speedAttr.addTransientModifier(new AttributeModifier(
                    SPEED_MODIFIER_NAME,
                    SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
        }
    }

    private static void removeSpeedModifier(LivingEntity entity) {
        AttributeInstance speedAttr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;
        speedAttr.removeModifier(SPEED_MODIFIER_UUID);
    }

    private static void removeSpeedFromAll(ServerPlayer player) {
        AABB box = new AABB(
                player.getX() - RANGE - 10, player.getY() - RANGE - 10, player.getZ() - RANGE - 10,
                player.getX() + RANGE + 10, player.getY() + RANGE + 10, player.getZ() + RANGE + 10
        );
        List<LivingEntity> all = player.serverLevel().getEntitiesOfClass(LivingEntity.class, box, e -> true);
        for (LivingEntity entity : all) {
            removeSpeedModifier(entity);
        }
    }

    private static void cleanupOutOfRangeEntities(ServerPlayer player, List<LivingEntity> inRange) {
        // Every 2 seconds, clean up speed modifiers from entities that left range
        if (player.tickCount % 40 != 0) return;

        AABB bigBox = new AABB(
                player.getX() - RANGE - 20, player.getY() - RANGE - 20, player.getZ() - RANGE - 20,
                player.getX() + RANGE + 20, player.getY() + RANGE + 20, player.getZ() + RANGE + 20
        );
        List<LivingEntity> allNearby = player.serverLevel().getEntitiesOfClass(LivingEntity.class, bigBox, e -> true);
        for (LivingEntity entity : allNearby) {
            if (!inRange.contains(entity)) {
                removeSpeedModifier(entity);
            }
        }
    }
}