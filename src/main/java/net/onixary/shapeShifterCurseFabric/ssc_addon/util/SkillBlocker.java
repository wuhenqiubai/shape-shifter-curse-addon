package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

import net.minecraft.server.level.ServerPlayer;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonConfig;

public class SkillBlocker {
    private static final String SKILL_BLOCKED_PREFIX = "ssc_skill_blocked:";
    
    private SkillBlocker() {} // Utility class
    
    public static boolean isSkillBlocked(ServerPlayer player, String form, String skill) {
        String tag = SKILL_BLOCKED_PREFIX + form + ":" + skill;
        if (player.getTags().contains(tag)) {
            return true;
        }
        
        String skillId = form + ":" + skill;
        return SSCAddonConfig.server().disabledSkills.contains(skillId);
    }
    
    public static void blockSkill(ServerPlayer player, String form, String skill) {
        String tag = SKILL_BLOCKED_PREFIX + form + ":" + skill;
        player.addTag(tag);
    }
    
    public static void unblockSkill(ServerPlayer player, String form, String skill) {
        String tag = SKILL_BLOCKED_PREFIX + form + ":" + skill;
        player.getTags().remove(tag);
    }
}