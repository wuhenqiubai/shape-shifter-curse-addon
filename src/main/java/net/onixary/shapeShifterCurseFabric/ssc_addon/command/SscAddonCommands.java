package net.onixary.shapeShifterCurseFabric.ssc_addon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseFabric.mana.ManaComponent;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.ConfigChangeManager;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpFrostStorm;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpMeleeAbility;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpTeleportAttack;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AnubisWolfSpSummonWolves;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AnubisWolfSpDeathDomain;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPJukebox;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPGroupHeal;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FrostBallEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.NovaSkillManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.palette.PaletteCodec;
import net.onixary.shapeShifterCurseFabric.player_form.skin.PlayerSkinComponent;
import net.onixary.shapeShifterCurseFabric.player_form.skin.RegPlayerSkinComponent;
import net.onixary.shapeShifterCurseFabric.util.FormTextureUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class SscAddonCommands {
	private static final Logger LOGGER = LoggerFactory.getLogger("SscAddon-Debug");
	private static final String SKILL_BLOCKED_PREFIX = "ssc_skill_blocked:";

	private SscAddonCommands() {
		// This utility class should not be instantiated
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("ssc_addon")
				.then(Commands.literal("set_mana")
						.requires(source -> source.hasPermission(2))
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("amount", IntegerArgumentType.integer(0))
										.executes(context -> setMana(context, EntityArgument.getPlayers(context, "targets"), IntegerArgumentType.getInteger(context, "amount")))
								)
						)
				)
				.then(Commands.literal("mark_owner")
						.requires(source -> source.hasPermission(2))
						.then(Commands.argument("targets", EntityArgument.entities())
								.executes(SscAddonCommands::markOwner)
						)
				)
				.then(Commands.literal("debug")
						.then(Commands.literal("form")
								.executes(SscAddonCommands::debugFormInfo)
						)
						.then(Commands.literal("mana")
								.executes(SscAddonCommands::debugMana)
						)
				)
				.then(Commands.literal("get_book")
						.requires(source -> source.hasPermission(2))
						// 使用字符串ID参数，支持任意书籍ID（不仅仅是数字）
						.then(Commands.argument("book_id", StringArgumentType.string())
								.suggests((context, builder) -> {
									// 自动补全：显示所有可用的书籍ID
									return SharedSuggestionProvider.suggest(
											net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.getBookIds(),
											builder
									);
								})
								.executes(SscAddonCommands::giveStoryBookById)
								.then(Commands.argument("language", StringArgumentType.string())
										.suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[]{"zh_cn", "en_us"}, builder))
										.executes(SscAddonCommands::giveStoryBookByIdWithLang)
								)
						)
				)
				.then(Commands.literal("list_books")
						.requires(source -> source.hasPermission(2))
						.executes(SscAddonCommands::listBooks)
						.then(Commands.argument("language", StringArgumentType.string())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[]{"zh_cn", "en_us"}, builder))
								.executes(SscAddonCommands::listBooksWithLang)
						)
				)
				.then(Commands.literal("reload_books")
						.requires(source -> source.hasPermission(2))
						.executes(SscAddonCommands::reloadBooks)
				)
				.then(Commands.literal("reload")
						.requires(source -> source.hasPermission(2))
						.executes(SscAddonCommands::reloadConfig)
				)
				// 玩家自助白名单 GUI（无 OP 限制，仅作用于调用者自己）
				.then(Commands.literal("my_whitelist")
						.executes(SscAddonCommands::openWhitelistGui)
				)
				// 朔望主/次技能触发（仅作用执行者本人、无 OP 限制，由 power 按键 execute_command 调用）
				.then(Commands.literal("nova")
						.then(Commands.literal("primary")
								.executes(ctx -> { ServerPlayer p = ctx.getSource().getPlayer(); if (p != null) NovaSkillManager.startCharge(p); return 1; }))
						.then(Commands.literal("secondary")
								.executes(ctx -> { ServerPlayer p = ctx.getSource().getPlayer(); if (p != null) NovaSkillManager.tryLeap(p); return 1; }))
				)
				.then(Commands.literal("skill")
						.requires(source -> source.hasPermission(2))
						.then(Commands.argument("form", StringArgumentType.word())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(
										Arrays.asList("snow_fox", "anubis_wolf", "allay", "axolotl", "wild_cat", "familiar_fox", "familiar_fox_red"), builder))
								.then(Commands.argument("skill", StringArgumentType.word())
										.suggests((context, builder) -> {
											String form = StringArgumentType.getString(context, "form");
											return SharedSuggestionProvider.suggest(getSkillsForForm(form), builder);
										})
										.then(Commands.argument("player", EntityArgument.player())
												.executes(SscAddonCommands::invokeSkillOnPlayer)
										)
										.executes(SscAddonCommands::invokeSkillOnSelf)
								)
						)
				)
				.then(Commands.literal("block")
						.requires(source -> source.hasPermission(2))
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("form", StringArgumentType.word())
										.suggests((context, builder) -> SharedSuggestionProvider.suggest(
												Arrays.asList("snow_fox", "anubis_wolf", "allay", "axolotl", "wild_cat", "familiar_fox", "familiar_fox_red"), builder))
										.then(Commands.argument("skill", StringArgumentType.word())
												.suggests((context, builder) -> {
													String form = StringArgumentType.getString(context, "form");
													return SharedSuggestionProvider.suggest(getSkillsForForm(form), builder);
												})
												.executes(SscAddonCommands::blockSkill)
										)
								)
						)
				)
				.then(Commands.literal("unblock")
						.requires(source -> source.hasPermission(2))
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("form", StringArgumentType.word())
										.suggests((context, builder) -> SharedSuggestionProvider.suggest(
												Arrays.asList("snow_fox", "anubis_wolf", "allay", "axolotl", "wild_cat", "familiar_fox", "familiar_fox_red"), builder))
										.then(Commands.argument("skill", StringArgumentType.word())
												.suggests((context, builder) -> {
													String form = StringArgumentType.getString(context, "form");
													return SharedSuggestionProvider.suggest(getSkillsForForm(form), builder);
												})
												.executes(SscAddonCommands::unblockSkill)
										)
								)
						)
				)
				.then(Commands.literal("list_blocks")
						.requires(source -> source.hasPermission(2))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(SscAddonCommands::listBlockedSkills)
						)
				)
				.then(Commands.literal("resistance")
						.requires(source -> source.hasPermission(2))
						.then(Commands.literal("get")
								.executes(ctx -> resistanceGet(ctx, ctx.getSource().getPlayer()))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> resistanceGet(ctx, EntityArgument.getPlayer(ctx, "player")))
								)
						)
						.then(Commands.literal("set")
								.then(Commands.argument("value", IntegerArgumentType.integer(0))
										.executes(ctx -> resistanceSet(ctx, ctx.getSource().getPlayer(), IntegerArgumentType.getInteger(ctx, "value")))
										.then(Commands.argument("player", EntityArgument.player())
												.executes(ctx -> resistanceSet(ctx, EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "value")))
										)
								)
						)
						.then(Commands.literal("add")
								.then(Commands.argument("delta", IntegerArgumentType.integer())
										.executes(ctx -> resistanceAdd(ctx, ctx.getSource().getPlayer(), IntegerArgumentType.getInteger(ctx, "delta")))
										.then(Commands.argument("player", EntityArgument.player())
												.executes(ctx -> resistanceAdd(ctx, EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "delta")))
										)
								)
						)
				)
				// ============== /ssc_addon mancianima_assault ==============
				// 控制玩家"今日是否可触发契灵敲钟袭击"的每日冷却（持久化）
				.then(Commands.literal("mancianima_assault")
						.requires(source -> source.hasPermission(2))
						.then(Commands.literal("reset")
								.executes(ctx -> mancianimaAssaultReset(ctx, ctx.getSource().getPlayer()))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> mancianimaAssaultReset(ctx, EntityArgument.getPlayer(ctx, "player")))
								)
						)
						.then(Commands.literal("lock")
								.executes(ctx -> mancianimaAssaultLock(ctx, ctx.getSource().getPlayer()))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> mancianimaAssaultLock(ctx, EntityArgument.getPlayer(ctx, "player")))
								)
						)
						.then(Commands.literal("status")
								.executes(ctx -> mancianimaAssaultStatus(ctx, ctx.getSource().getPlayer()))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> mancianimaAssaultStatus(ctx, EntityArgument.getPlayer(ctx, "player")))
								)
						)
				)
				// ============== /ssc_addon evolution ==============
				// SSCA 进化加点系统管理指令（框架）：unlock_all 全解锁 / reset 重置
				.then(Commands.literal("evolution")
						.requires(source -> source.hasPermission(2))
						.then(Commands.literal("unlock_all")
								.executes(ctx -> evolutionUnlockAll(ctx, ctx.getSource().getPlayer()))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> evolutionUnlockAll(ctx, EntityArgument.getPlayer(ctx, "player")))
								)
						)
						.then(Commands.literal("reset")
								.executes(ctx -> evolutionReset(ctx, ctx.getSource().getPlayer()))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> evolutionReset(ctx, EntityArgument.getPlayer(ctx, "player")))
								)
						)
				)
				// ============== /ssc_addon palette ==============
				// 形态配色「分享码」：导出当前配色为分享文本；apply 一键应用
				// 不加 .requires(permissionLevel) — 关闭作弊的存档/服务器内普通玩家也能使用；只对执行者本人生效（规则 #49）
				.then(Commands.literal("palette")
						.then(Commands.literal("export")
								.executes(ctx -> paletteExport(ctx, ctx.getSource().getPlayer()))
						)
						.then(Commands.literal("apply")
								.then(Commands.argument("code", StringArgumentType.greedyString())
										.executes(ctx -> paletteApply(ctx, ctx.getSource().getPlayer(), StringArgumentType.getString(ctx, "code")))
								)
						)
				)
		);
	}

	// ============== /ssc_addon palette ==============
	// 仅对执行者本人生效，禁止 target 参数；反馈消息全部走 lang key
	private static int paletteExport(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		if (player == null) { ctx.getSource().sendFailure(Component.translatable("ssc_addon.palette.only_self")); return 0; }
		PlayerSkinComponent skin = RegPlayerSkinComponent.SKIN_SETTINGS.get(player);
		FormTextureUtils.ColorSetting cs = skin.getFormColor();
		// 主包内部存 ABGR，导出转回 RGBA 让 apply 那边解析后能直接喂给 setFormColor(int RGBA, ...)
		int primary = FormTextureUtils.ABGR2RGBA(cs.getPrimaryColor());
		int accent1 = FormTextureUtils.ABGR2RGBA(cs.getAccentColor1());
		int accent2 = FormTextureUtils.ABGR2RGBA(cs.getAccentColor2());
		int eyeA = FormTextureUtils.ABGR2RGBA(cs.getEyeColorA());
		int eyeB = FormTextureUtils.ABGR2RGBA(cs.getEyeColorB());
		String code = PaletteCodec.encode(primary, accent1, accent2, eyeA, eyeB,
				cs.getPrimaryGreyReverse(), cs.getAccent1GreyReverse(), cs.getAccent2GreyReverse());

		MutableComponent codeText = Component.literal(code).setStyle(Style.EMPTY
				.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("ssc_addon.palette.export.copy_hover"))));
		ctx.getSource().sendSuccess(() -> Component.translatable("ssc_addon.palette.export.header").append(codeText), false);
		ctx.getSource().sendSuccess(() -> Component.translatable("ssc_addon.palette.export.hint"), false);
		return 1;
	}

	private static int paletteApply(CommandContext<CommandSourceStack> ctx, ServerPlayer player, String rawCode) {
		if (player == null) { ctx.getSource().sendFailure(Component.translatable("ssc_addon.palette.only_self")); return 0; }
		PaletteCodec.PaletteData data;
		try {
			data = PaletteCodec.decode(rawCode);
		} catch (PaletteCodec.DecodeException e) {
			ctx.getSource().sendFailure(Component.translatable("ssc_addon.palette.apply.failed",
					Component.translatable(e.langKey, e.args)));
			return 0;
		}
		PlayerSkinComponent skin = RegPlayerSkinComponent.SKIN_SETTINGS.get(player);
		skin.setFormColor(data.primaryRGBA(), data.accent1RGBA(), data.accent2RGBA(),
				data.eyeARGBA(), data.eyeBRGBA(),
				data.primaryGreyReverse(), data.accent1GreyReverse(), data.accent2GreyReverse());
		// 应用后自动开启 enableFormColor，避免玩家纳闷"为什么应用了没变化"
		skin.setEnableFormColor(true);
		// 触发 AutoSyncedComponent 同步，让其它客户端立即看到新配色
		RegPlayerSkinComponent.SKIN_SETTINGS.sync(player);
		ctx.getSource().sendSuccess(() -> Component.translatable("ssc_addon.palette.apply.success"), false);
		return 1;
	}

	// ============== /ssc_addon evolution ==============
	private static int evolutionUnlockAll(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		if (player == null) { ctx.getSource().sendFailure(Component.literal("目标玩家无效")); return 0; }
		EvolutionManager.unlockAll(player);
		ctx.getSource().sendSuccess(() -> Component.literal("已将 " + player.getName().getString() + " 的 SSCA 进化路线设为全解锁"), true);
		return 1;
	}

	private static int evolutionReset(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		if (player == null) { ctx.getSource().sendFailure(Component.literal("目标玩家无效")); return 0; }
		EvolutionManager.reset(player);
		ctx.getSource().sendSuccess(() -> Component.literal("已重置 " + player.getName().getString() + " 的 SSCA 进化数据"), true);
		return 1;
	}

	// ============== /ssc_addon mancianima_assault ==============
	private static final long MANCIANIMA_ASSAULT_COOLDOWN_TICKS = 24000L;

	private static int mancianimaAssaultReset(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		if (player == null) { ctx.getSource().sendFailure(Component.translatable("command.ssc_addon.common.no_target_player")); return 0; }
		net.minecraft.server.MinecraftServer srv = ctx.getSource().getServer();
		net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaAssaultState state =
				net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaAssaultState.get(srv);
		if (state.lastRoll.remove(player.getUUID()) != null) {
			state.setDirty();
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("command.ssc_addon.assault.reset", player.getName().getString()), true);
		return 1;
	}

	private static int mancianimaAssaultLock(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		if (player == null) { ctx.getSource().sendFailure(Component.translatable("command.ssc_addon.common.no_target_player")); return 0; }
		net.minecraft.server.MinecraftServer srv = ctx.getSource().getServer();
		net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaAssaultState state =
				net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaAssaultState.get(srv);
		state.lastRoll.put(player.getUUID(), srv.overworld().getGameTime());
		state.setDirty();
		ctx.getSource().sendSuccess(() -> Component.translatable("command.ssc_addon.assault.lock", player.getName().getString()), true);
		return 1;
	}

	private static int mancianimaAssaultStatus(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		if (player == null) { ctx.getSource().sendFailure(Component.translatable("command.ssc_addon.common.no_target_player")); return 0; }
		net.minecraft.server.MinecraftServer srv = ctx.getSource().getServer();
		net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaAssaultState state =
				net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaAssaultState.get(srv);
		Long last = state.lastRoll.get(player.getUUID());
		long now = srv.overworld().getGameTime();
		if (last == null || now - last >= MANCIANIMA_ASSAULT_COOLDOWN_TICKS) {
			ctx.getSource().sendSuccess(() -> Component.translatable("command.ssc_addon.assault.status.available", player.getName().getString()), false);
		} else {
			long remain = MANCIANIMA_ASSAULT_COOLDOWN_TICKS - (now - last);
			ctx.getSource().sendSuccess(() -> Component.translatable("command.ssc_addon.assault.status.cooldown", player.getName().getString(), remain, remain / 20), false);
		}
		return 1;
	}

	// ============== /ssc_addon resistance ==============
	private static int resistanceGet(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		if (player == null) { ctx.getSource().sendFailure(Component.translatable("command.ssc_addon.common.no_target_player")); return 0; }
		int cur = PowerUtils.getResourceValue(player, FormIdentifiers.MANCIANIMA_RESISTANCE);
		int max = PowerUtils.getResourceMax(player, FormIdentifiers.MANCIANIMA_RESISTANCE);
		ctx.getSource().sendSuccess(() -> Component.translatable("command.ssc_addon.resistance.get", player.getName().getString(), cur, max), false);
		return 1;
	}

	private static int resistanceSet(CommandContext<CommandSourceStack> ctx, ServerPlayer player, int value) {
		if (player == null) { ctx.getSource().sendFailure(Component.translatable("command.ssc_addon.common.no_target_player")); return 0; }
		int max = PowerUtils.getResourceMax(player, FormIdentifiers.MANCIANIMA_RESISTANCE);
		if (max <= 0) { ctx.getSource().sendFailure(Component.translatable("command.ssc_addon.resistance.no_power")); return 0; }
		int clamped = Math.max(0, Math.min(value, max));
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.MANCIANIMA_RESISTANCE, clamped);
		ctx.getSource().sendSuccess(() -> Component.translatable("command.ssc_addon.resistance.set", player.getName().getString(), clamped, max), true);
		return 1;
	}

	private static int resistanceAdd(CommandContext<CommandSourceStack> ctx, ServerPlayer player, int delta) {
		if (player == null) { ctx.getSource().sendFailure(Component.translatable("command.ssc_addon.common.no_target_player")); return 0; }
		int max = PowerUtils.getResourceMax(player, FormIdentifiers.MANCIANIMA_RESISTANCE);
		if (max <= 0) { ctx.getSource().sendFailure(Component.translatable("command.ssc_addon.resistance.no_power")); return 0; }
		int cur = PowerUtils.getResourceValue(player, FormIdentifiers.MANCIANIMA_RESISTANCE);
		int next = Math.max(0, Math.min(cur + delta, max));
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.MANCIANIMA_RESISTANCE, next);
		ctx.getSource().sendSuccess(() -> Component.translatable("command.ssc_addon.resistance.add", player.getName().getString(), cur, next, max), true);
		return 1;
	}

	private static int setMana(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets, int amount) {
		int count = 0;
		for (ServerPlayer player : targets) {
			boolean updated = false;

			int snowFoxMax = PowerUtils.getResourceMax(player, FormIdentifiers.SNOW_FOX_RESOURCE);
			if (snowFoxMax > 0) {
				int clamped = Math.min(amount, snowFoxMax);
				PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SNOW_FOX_RESOURCE, clamped);
				updated = true;
			}

			int allayMax = PowerUtils.getResourceMax(player, FormIdentifiers.ALLAY_MANA_RESOURCE);
			if (allayMax > 0) {
				int clamped = Math.min(amount, allayMax);
				PowerUtils.setResourceValueAndSync(player, FormIdentifiers.ALLAY_MANA_RESOURCE, clamped);
				updated = true;
			}

			int soulMax = PowerUtils.getResourceMax(player, FormIdentifiers.ANUBIS_WOLF_SP_SOUL_ENERGY);
			if (soulMax > 0) {
				int clamped = Math.min(amount, soulMax);
				net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AnubisWolfSpSoulEnergy.setEnergy(player, clamped);
				updated = true;
			}

			try {
				ManaComponent manaComponent = ManaUtils.getManaComponent(player);
				if (manaComponent != null) {
					double newVal = Math.min(amount, manaComponent.getMaxMana());
					manaComponent.setMana(newVal);
					updated = true;
				}
			} catch (Exception e) {
				LOGGER.debug("ManaComponent not available for {}", player.getName().getString(), e);
			}

			if (updated) {
				count++;
			}
		}
		final int finalCount = count;
		context.getSource().sendSuccess(() -> Component.translatable("command.ssc_addon.set_mana.result", finalCount, amount), true);
		return count;
	}

	private static int markOwner(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
		CommandSourceStack source = context.getSource();
		Entity attacker = source.getEntity();

		if (attacker instanceof ServerPlayer player) {
			UUID playerUUID = player.getUUID();
			for (Entity target : targets) {
				if (target instanceof LivingEntity livingTarget) {
					// Update ownership tag: remove old owner, set new owner
					livingTarget.getTags().removeIf(tag -> tag.startsWith("ssc_owner:"));
					livingTarget.addTag("ssc_owner:" + playerUUID.toString());
				}
			}
			return targets.size();
		}
		return 0;
	}

	private static int debugFormInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();

		// 获取玩家形态组件（getPlayerOrThrow 保证非空）
		PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(java.util.Objects.requireNonNull(player));

		// 准备调试信息（服务器日志保留原始英文，玩家聘天用可翻译版本）
		StringBuilder debugInfo = new StringBuilder();
		debugInfo.append("===== SSC_ADDON FORM DEBUG =====\n");
		player.displayClientMessage(Component.translatable("command.ssc_addon.debug_form.header").withStyle(ChatFormatting.AQUA), false);

		if (component == null) {
			debugInfo.append("PlayerFormComponent: NULL\n");
			player.displayClientMessage(Component.translatable("command.ssc_addon.debug_form.no_component").withStyle(ChatFormatting.AQUA), false);
		} else {
			IForm currentForm = component.nowForm;
			if (currentForm == null) {
				debugInfo.append("Current Form: NULL (no form active)\n");
				player.displayClientMessage(Component.translatable("command.ssc_addon.debug_form.no_form").withStyle(ChatFormatting.AQUA), false);
			} else {
				debugInfo.append("Form ID: ").append(currentForm.getFormID()).append("\n");
				player.displayClientMessage(Component.translatable("command.ssc_addon.debug_form.form_id", String.valueOf(currentForm.getFormID())).withStyle(ChatFormatting.AQUA), false);
				debugInfo.append("Form Class: ").append(currentForm.getClass().getName()).append("\n");
				player.displayClientMessage(Component.translatable("command.ssc_addon.debug_form.form_class", currentForm.getClass().getName()).withStyle(ChatFormatting.AQUA), false);
				debugInfo.append("Phase: ").append(currentForm.getFormTier()).append("\n");
				player.displayClientMessage(Component.translatable("command.ssc_addon.debug_form.phase", String.valueOf(currentForm.getFormTier())).withStyle(ChatFormatting.AQUA), false);
				debugInfo.append("Body Type: ").append(currentForm.getBodyType()).append("\n");
				player.displayClientMessage(Component.translatable("command.ssc_addon.debug_form.body_type", String.valueOf(currentForm.getBodyType())).withStyle(ChatFormatting.AQUA), false);
			}
		}
		debugInfo.append("================================");
		player.displayClientMessage(Component.translatable("command.ssc_addon.debug_form.footer").withStyle(ChatFormatting.AQUA), false);

		// 记录到服务器日志
		if (LOGGER.isInfoEnabled()) {
			LOGGER.info(debugInfo.toString());
		}

		return 1;
	}

	private static int debugMana(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		boolean foundMana = false;

		int snowFoxVal = PowerUtils.getResourceValue(player, FormIdentifiers.SNOW_FOX_RESOURCE);
		int snowFoxMax = PowerUtils.getResourceMax(player, FormIdentifiers.SNOW_FOX_RESOURCE);
		if (snowFoxMax > 0) {
			player.displayClientMessage(Component.translatable("command.ssc_addon.debug_mana.snow_fox", snowFoxVal, snowFoxMax).withStyle(ChatFormatting.AQUA), false);
			foundMana = true;
		}

		int allayVal = PowerUtils.getResourceValue(player, FormIdentifiers.ALLAY_MANA_RESOURCE);
		int allayMax = PowerUtils.getResourceMax(player, FormIdentifiers.ALLAY_MANA_RESOURCE);
		if (allayMax > 0) {
			player.displayClientMessage(Component.translatable("command.ssc_addon.debug_mana.allay", allayVal, allayMax).withStyle(ChatFormatting.AQUA), false);
			foundMana = true;
		}

		int soulVal = PowerUtils.getResourceValue(player, FormIdentifiers.ANUBIS_WOLF_SP_SOUL_ENERGY);
		int soulMax = PowerUtils.getResourceMax(player, FormIdentifiers.ANUBIS_WOLF_SP_SOUL_ENERGY);
		if (soulMax > 0) {
			player.displayClientMessage(Component.translatable("command.ssc_addon.debug_mana.anubis_wolf", soulVal, soulMax).withStyle(ChatFormatting.AQUA), false);
			foundMana = true;
		}

		try {
			ManaComponent manaComponent = ManaUtils.getManaComponent(player);
			if (manaComponent != null) {
				if (manaComponent.getManaTypeID() != null) {
					player.displayClientMessage(Component.translatable("command.ssc_addon.debug_mana.mana_type", manaComponent.getManaTypeID().toString()).withStyle(ChatFormatting.AQUA), false);
					player.displayClientMessage(Component.translatable("command.ssc_addon.debug_mana.mana", manaComponent.getMana(), manaComponent.getMaxMana()).withStyle(ChatFormatting.AQUA), false);
					foundMana = true;
				} else if (manaComponent.getMaxMana() > 0) {
					player.displayClientMessage(Component.translatable("command.ssc_addon.debug_mana.mana_notype", manaComponent.getMana(), manaComponent.getMaxMana()).withStyle(ChatFormatting.AQUA), false);
					foundMana = true;
				}
			}
		} catch (Exception e) {
			LOGGER.debug("ManaComponent not available for {}", player.getName().getString(), e);
		}

		if (!foundMana) {
			player.displayClientMessage(Component.translatable("command.ssc_addon.debug_mana.no_mana").withStyle(ChatFormatting.YELLOW), false);
		}
		return 1;
	}

	// ===== 新增：书籍命令方法 =====

	/**
	 * 通过书籍ID获取书籍（使用配置的默认语言）
	 */
	private static int giveStoryBookById(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		return giveStoryBookByIdInternal(context, null);
	}

	/**
	 * 通过书籍ID和指定语言获取书籍
	 */
	private static int giveStoryBookByIdWithLang(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String lang = StringArgumentType.getString(context, "language");
		return giveStoryBookByIdInternal(context, lang);
	}

	/**
	 * 内部方法：通过书籍ID获取书籍
	 */
	private static int giveStoryBookByIdInternal(CommandContext<CommandSourceStack> context, String language) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String bookId = StringArgumentType.getString(context, "book_id");

		net.minecraft.world.item.ItemStack book = net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.getStoryBookById(bookId, language);

		if (book.isEmpty()) {
			player.displayClientMessage(Component.translatable("command.ssc_addon.book.not_found", bookId).withStyle(ChatFormatting.RED), false);
			return 0;
		}

		// 获取书籍信息用于显示
		net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.BookData bookData =
				net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.getBookDataById(bookId, language);

		if (!player.getInventory().add(book)) {
			player.drop(book, false);
		}

		String bookTitle = bookData != null ? bookData.title : bookId;
		player.displayClientMessage(Component.translatable("command.ssc_addon.book.obtained", bookTitle).withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	/**
	 * 列出所有可用书籍（使用配置的默认语言）
	 */
	private static int listBooks(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		return listBooksInternal(context, null);
	}

	/**
	 * 列出所有可用书籍（指定语言）
	 */
	private static int listBooksWithLang(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String lang = StringArgumentType.getString(context, "language");
		return listBooksInternal(context, lang);
	}

	/**
	 * 内部方法：列出所有可用书籍
	 */
	private static int listBooksInternal(CommandContext<CommandSourceStack> context, String language) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();

		java.util.List<String> bookIds;
		if (language != null && !language.isEmpty()) {
			bookIds = net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.getBookIds(language);
		} else {
			bookIds = net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.getBookIds();
		}

		if (bookIds.isEmpty()) {
			player.displayClientMessage(Component.translatable("command.ssc_addon.book.list.empty").withStyle(ChatFormatting.YELLOW), false);
			return 0;
		}

		player.displayClientMessage(Component.translatable("command.ssc_addon.book.list.header").withStyle(ChatFormatting.GOLD), false);
		player.displayClientMessage(Component.translatable("command.ssc_addon.book.list.count", bookIds.size()).withStyle(ChatFormatting.AQUA), false);

		for (String bookId : bookIds) {
			net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.BookData bookData =
					net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.getBookDataById(bookId, language);

			if (bookData != null) {
				// 截断过长的标题
				String displayTitle = bookData.title;
				if (displayTitle.length() > 30) {
					displayTitle = displayTitle.substring(0, 27) + "...";
				}
				player.displayClientMessage(Component.translatable("command.ssc_addon.book.list.entry", bookId, displayTitle, bookData.author).withStyle(ChatFormatting.WHITE), false);
			} else {
				player.displayClientMessage(Component.translatable("command.ssc_addon.book.list.entry_failed", bookId).withStyle(ChatFormatting.RED), false);
			}
		}

		player.displayClientMessage(Component.translatable("command.ssc_addon.book.list.footer").withStyle(ChatFormatting.GOLD), false);
		player.displayClientMessage(Component.translatable("command.ssc_addon.book.list.hint").withStyle(ChatFormatting.GRAY), false);

		return bookIds.size();
	}

	/**
	 * 重新加载书籍配置
	 */
	private static int reloadBooks(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();

		try {
			net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.reloadBooks();
			int bookCount = net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.getBookCount();
			player.displayClientMessage(Component.translatable("command.ssc_addon.reload.books.success", bookCount).withStyle(ChatFormatting.GREEN), false);
			return 1;
		} catch (Exception e) {
			player.displayClientMessage(Component.translatable("command.ssc_addon.reload.books.fail", e.getMessage()).withStyle(ChatFormatting.RED), false);
			LOGGER.error("Failed to reload books", e);
			return 0;
		}
	}

	/**
	 * 重新加载模组配置
	 */
	private static int reloadConfig(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();

		try {
			ConfigChangeManager.notifyChange();
			player.displayClientMessage(Component.translatable("command.ssc_addon.reload.config.success").withStyle(ChatFormatting.GREEN), true);
			return 1;
		} catch (Exception e) {
			player.displayClientMessage(Component.translatable("command.ssc_addon.reload.config.fail", e.getMessage()).withStyle(ChatFormatting.RED), true);
			LOGGER.error("Failed to reload config", e);
			return 0;
		}
	}

	// ===== SP悦灵治疗白名单命令 =====
	// 旧的 /ssc_addon whitelist add/remove/list/clear 系列指令已被 my_whitelist GUI 取代，
	// 相关私有方法已删除以减少代码维护面。

	/**
	 * 打开玩家自助白名单 GUI（无 OP 限制，仅作用于调用者本人）。
	 * 服务端通过 S2C 包推送当前白名单数据给客户端，由客户端打开 WhitelistManageScreen。
	 * 控制台 / 命令方块调用会返回错误。
	 */
	private static int openWhitelistGui(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.translatable("command.ssc_addon.whitelist.gui.console_only"));
			return 0;
		}
		net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking.sendWhitelistSync(player);
		return 1;
	}

	private static List<String> getSkillsForForm(String form) {
		return switch (form) {
			case "snow_fox" -> Arrays.asList("melee_primary", "melee_secondary", "ranged_primary", "ranged_secondary", "frost_regen");
			case "anubis_wolf" -> Arrays.asList("summon_wolves", "death_domain", "soul_sand_heal", "wither_hunt", "soul_scrutiny");
			case "allay" -> Arrays.asList("jukebox_charge", "group_heal", "mana_regen");
			case "axolotl" -> Arrays.asList("natural_regen_boost", "rain_wetness");
			case "wild_cat" -> Arrays.asList("night_speed", "day_slow");
			case "familiar_fox" -> Arrays.asList("mana_regen");
			case "familiar_fox_red" -> Arrays.asList("red_mana_regen");
			default -> Collections.emptyList();
		};
	}

	private static boolean isSkillBlocked(ServerPlayer player, String form, String skill) {
		String tag = SKILL_BLOCKED_PREFIX + form + ":" + skill;
		return player.getTags().contains(tag);
	}

	private static void blockSkill(ServerPlayer player, String form, String skill) {
		String tag = SKILL_BLOCKED_PREFIX + form + ":" + skill;
		player.addTag(tag);
	}

	private static void unblockSkill(ServerPlayer player, String form, String skill) {
		String tag = SKILL_BLOCKED_PREFIX + form + ":" + skill;
		player.getTags().remove(tag);
	}

	private static List<String> getBlockedSkills(ServerPlayer player) {
		return player.getTags().stream()
				.filter(tag -> tag.startsWith(SKILL_BLOCKED_PREFIX))
				.map(tag -> tag.substring(SKILL_BLOCKED_PREFIX.length()))
				.toList();
	}

	private static int invokeSkillOnSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String form = StringArgumentType.getString(context, "form");
		String skill = StringArgumentType.getString(context, "skill");
		return invokeSkill(context, player, form, skill);
	}

	private static int invokeSkillOnPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		String form = StringArgumentType.getString(context, "form");
		String skill = StringArgumentType.getString(context, "skill");
		return invokeSkill(context, target, form, skill);
	}

	private static int blockSkill(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		String form = StringArgumentType.getString(context, "form");
		String skill = StringArgumentType.getString(context, "skill");

		if (isSkillBlocked(target, form, skill)) {
			context.getSource().sendSuccess(() -> Component.translatable(
					"command.ssc_addon.block_skill.already", form, skill, target.getName().getString()
			).withStyle(ChatFormatting.YELLOW), false);
			return 0;
		}

		blockSkill(target, form, skill);
		LOGGER.info("[SSC] Blocked " + form + "/" + skill + " for " + target.getName().getString());
		context.getSource().sendSuccess(() -> Component.translatable(
				"command.ssc_addon.block_skill.success", target.getName().getString(), form, skill
		).withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int unblockSkill(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		String form = StringArgumentType.getString(context, "form");
		String skill = StringArgumentType.getString(context, "skill");

		if (!isSkillBlocked(target, form, skill)) {
			context.getSource().sendSuccess(() -> Component.translatable(
					"command.ssc_addon.unblock_skill.not_blocked", form, skill, target.getName().getString()
			).withStyle(ChatFormatting.YELLOW), false);
			return 0;
		}

		unblockSkill(target, form, skill);
		LOGGER.info("[SSC] Unblocked " + form + "/" + skill + " for " + target.getName().getString());
		context.getSource().sendSuccess(() -> Component.translatable(
				"command.ssc_addon.unblock_skill.success", target.getName().getString(), form, skill
		).withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int listBlockedSkills(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		List<String> blockedSkills = getBlockedSkills(target);

		if (blockedSkills.isEmpty()) {
			context.getSource().sendSuccess(() -> Component.translatable(
					"command.ssc_addon.list_blocked.empty", target.getName().getString()
			).withStyle(ChatFormatting.YELLOW), false);
			return 0;
		}

		context.getSource().sendSuccess(() -> Component.translatable(
				"command.ssc_addon.list_blocked.header", target.getName().getString()
		).withStyle(ChatFormatting.GOLD), false);

		for (String blocked : blockedSkills) {
			context.getSource().sendSuccess(() -> Component.translatable(
					"command.ssc_addon.list_blocked.entry", blocked
			).withStyle(ChatFormatting.WHITE), false);
		}

		return blockedSkills.size();
	}

	private static int invokeSkill(CommandContext<CommandSourceStack> context, ServerPlayer target, String form, String skill) {
		CommandSourceStack source = context.getSource();
		String executorName = source.getTextName();

		if (isSkillBlocked(target, form, skill)) {
			LOGGER.warn("[SSC] Blocked skill invocation: " + form + "/" + skill + " on " + target.getName());
			source.sendFailure(Component.translatable("command.ssc_addon.ssc_test.skill_blocked", form, skill));
			return 0;
		}

	if ("snow_fox".equals(form)) {
            return invokeSnowFoxSkill(source, target, skill, executorName);
        } else if ("anubis_wolf".equals(form)) {
            return invokeAnubisWolfSkill(source, target, skill, executorName);
        } else if ("allay".equals(form)) {
            return invokeAllaySkill(source, target, skill, executorName);
        }

        LOGGER.warn("[SSC] Unknown form: " + form);
		source.sendFailure(Component.translatable("command.ssc_addon.ssc_test.unknown_form", form));
		return 0;
	}

	private static int invokeSnowFoxSkill(CommandSourceStack source, ServerPlayer target, String skill, String executorName) {
		return switch (skill) {
			case "melee_primary" -> {
				LOGGER.info("[SSC] Invoking snow_fox/melee_primary on " + target.getName().getString() + " by " + executorName);
				boolean success = SnowFoxSpMeleeAbility.execute(target);
				if (!success) {
					source.sendSuccess(() -> Component.translatable("command.ssc_addon.ssc_test.snow_fox.melee_primary.fail").withStyle(ChatFormatting.YELLOW), false);
				} else {
					source.sendSuccess(() -> Component.translatable("command.ssc_addon.ssc_test.snow_fox.melee_primary.success").withStyle(ChatFormatting.GREEN), false);
				}
				yield 1;
			}
			case "melee_secondary" -> {
				LOGGER.info("[SSC] Invoking snow_fox/melee_secondary on " + target.getName().getString() + " by " + executorName);
				boolean success = SnowFoxSpTeleportAttack.execute(target);
				if (!success) {
					source.sendSuccess(() -> Component.translatable("command.ssc_addon.ssc_test.snow_fox.melee_secondary.fail").withStyle(ChatFormatting.YELLOW), false);
				} else {
					source.sendSuccess(() -> Component.translatable("command.ssc_addon.ssc_test.snow_fox.melee_secondary.success").withStyle(ChatFormatting.GREEN), false);
				}
				yield 1;
			}
			case "ranged_primary" -> {
				LOGGER.info("[SSC] Invoking snow_fox/ranged_primary (frost_ball) on " + target.getName().getString() + " by " + executorName);
				boolean success = invokeSnowFoxFrostBall(target);
				if (!success) {
					source.sendSuccess(() -> Component.translatable("command.ssc_addon.ssc_test.snow_fox.ranged_primary.fail").withStyle(ChatFormatting.YELLOW), false);
				} else {
					source.sendSuccess(() -> Component.translatable("command.ssc_addon.ssc_test.snow_fox.ranged_primary.success").withStyle(ChatFormatting.GREEN), false);
				}
				yield success ? 1 : 0;
			}
			case "ranged_secondary" -> {
				LOGGER.info("[SSC] Invoking snow_fox/ranged_secondary (frost_storm) on " + target.getName().getString() + " by " + executorName);
				boolean success = SnowFoxSpFrostStorm.startCharging(target);
				if (!success) {
					source.sendSuccess(() -> Component.translatable("command.ssc_addon.ssc_test.snow_fox.ranged_secondary.fail").withStyle(ChatFormatting.YELLOW), false);
				} else {
					source.sendSuccess(() -> Component.translatable("command.ssc_addon.ssc_test.snow_fox.ranged_secondary.success").withStyle(ChatFormatting.GREEN), false);
				}
				yield 1;
			}
default -> {
                LOGGER.warn("[SSC] Unknown snow_fox skill: " + skill);
                source.sendFailure(Component.translatable("command.ssc_addon.ssc_test.unknown_skill", skill, "snow_fox"));
                yield 0;
            }
        };
    }

    private static int invokeAnubisWolfSkill(CommandSourceStack source, ServerPlayer target, String skill, String executorName) {
        return switch (skill) {
            case "summon_wolves" -> {
                LOGGER.info("[SSC] Invoking anubis_wolf/summon_wolves on " + target.getName().getString() + " by " + executorName);
                boolean success = AnubisWolfSpSummonWolves.execute(target);
                if (!success) {
                    source.sendSuccess(() -> Component.translatable("command.ssc_addon.ssc_test.anubis_wolf.summon_wolves.fail").withStyle(ChatFormatting.YELLOW), false);
                } else {
                    source.sendSuccess(() -> Component.translatable("command.ssc_addon.ssc_test.anubis_wolf.summon_wolves.success").withStyle(ChatFormatting.GREEN), false);
                }
                yield 1;
            }
            case "death_domain" -> {
                LOGGER.info("[SSC] Invoking anubis_wolf/death_domain on " + target.getName().getString() + " by " + executorName);
                boolean success = AnubisWolfSpDeathDomain.execute(target);
                if (!success) {
                    source.sendSuccess(() -> Component.translatable("command.ssc_addon.ssc_test.anubis_wolf.death_domain.fail").withStyle(ChatFormatting.YELLOW), false);
                } else {
                    source.sendSuccess(() -> Component.translatable("command.ssc_addon.ssc_test.anubis_wolf.death_domain.success").withStyle(ChatFormatting.GREEN), false);
                }
                yield 1;
            }
            default -> {
                LOGGER.warn("[SSC] Unknown anubis_wolf skill: " + skill);
                source.sendFailure(Component.translatable("command.ssc_addon.ssc_test.unknown_skill", skill, "anubis_wolf"));
                yield 0;
            }
        };
    }

	private static int invokeAllaySkill(CommandSourceStack source, ServerPlayer target, String skill, String executorName) {
		return switch (skill) {
			case "jukebox_charge" -> {
				LOGGER.info("[SSC] Invoking allay/jukebox_charge on " + target.getName().getString() + " by " + executorName);
				AllaySPJukebox.tick(target);
				source.sendSuccess(() -> Component.translatable("command.ssc_addon.ssc_test.allay.jukebox_charge.triggered").withStyle(ChatFormatting.AQUA), false);
				yield 1;
			}
			case "group_heal" -> {
				LOGGER.info("[SSC] Invoking allay/group_heal on " + target.getName().getString() + " by " + executorName);
				AllaySPGroupHeal.tick(target);
				source.sendSuccess(() -> Component.translatable("command.ssc_addon.ssc_test.allay.group_heal.triggered").withStyle(ChatFormatting.AQUA), false);
				yield 1;
			}
			default -> {
				LOGGER.warn("[SSC] Unknown allay skill: " + skill);
				source.sendFailure(Component.translatable("command.ssc_addon.ssc_test.unknown_skill", skill, "allay"));
				yield 0;
			}
		};
	}

	private static boolean invokeSnowFoxFrostBall(ServerPlayer player) {
		if (PowerUtils.getResourceValue(player, FormIdentifiers.SNOW_FOX_RANGED_PRIMARY_CD) > 0) {
			return false;
		}

		int manaCost = 10;
		int currentMana = PowerUtils.getResourceValue(player, FormIdentifiers.SNOW_FOX_RESOURCE);
		if (currentMana >= manaCost) {
			PowerUtils.changeResourceValueAndSync(player, FormIdentifiers.SNOW_FOX_RESOURCE, -manaCost);
		} else {
			return false;
		}
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SNOW_FOX_RANGED_PRIMARY_CD, 100);

		FrostBallEntity frostBall = new FrostBallEntity(player.level(), player);
		Vec3 lookDir = player.getLookAngle().normalize();
		Vec3 startPos = player.position().add(lookDir.scale(0.5));
		frostBall.setPos(startPos.x, startPos.y, startPos.z);
		frostBall.setDeltaMovement(lookDir.scale(3.0));
		player.level().addFreshEntity(frostBall);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5f, 1.2f);
		return true;
	}
}
