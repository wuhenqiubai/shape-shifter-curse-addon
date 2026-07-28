package net.onixary.shapeShifterCurseFabric.ssc_addon.client.screen;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseFabric.networking.BytePayload;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 白名单管理 GUI。三个 tab：
 * <ul>
 *   <li>所有玩家 tab：所有在线玩家，可增删（[+]/[−]）。</li>
 *   <li>已加入玩家 tab：仅列出白名单内的玩家，只能 [−]。该玩家的宠物/召唤物默认也被保护，但不在该 tab 重复显示。</li>
 *   <li>生物 tab：仅列出被友军标记单独标记的生物（含被单独标记的其他玩家的宠物），只能 [−]。</li>
 * </ul>
 * 顶部还有“默认/自定义”白名单模式切换按钮（影响是否按列表精确判定）。
 */
public class WhitelistManageScreen extends Screen {

	/** S2C 传过来的生物白名单条目。typeId 可能为 null（友军标记物品未写入物种信息时）。 */
	public record MobEntry(UUID uuid, String typeId) {}

	private enum Tab { ALL_PLAYERS, PLAYER_WL, MOB }

	private enum SortMode {
		DISTANCE("screen.ssc_addon.whitelist.mode.distance"),
		ALPHABETICAL("screen.ssc_addon.whitelist.mode.alphabetical"),
		ADDED_FIRST("screen.ssc_addon.whitelist.mode.added");
		final String key;
		SortMode(String key) { this.key = key; }
	}

	private static final int PANEL_WIDTH = 300;
	private static final int PANEL_HEIGHT = 240;
	private static final int ENTRY_HEIGHT = 22;
	private static final int LIST_VISIBLE_ROWS = 6;

	private Set<UUID> whitelist;
	private boolean customMode;
	private List<MobEntry> mobs;

	private Tab currentTab = Tab.ALL_PLAYERS;
	private EditBox searchField;
	private EditBox uuidField;
	private Button sortButton;
	private Button modeButton;
	private Button addUuidButton;
	private Button allPlayersTabButton;
	private Button playerWlTabButton;
	private Button mobTabButton;
	private SortMode sortMode = SortMode.DISTANCE;
	private int scrollOffset = 0;

	private List<PlayerInfo> filteredCache = Collections.emptyList();
	private String lastSearchText = "";
	private SortMode lastSortMode = null;
	private int lastWhitelistHash = 0;

	private Map<UUID, Integer> mobIndexInSpecies = new HashMap<>();

	private int listX, listY, listW;

	public WhitelistManageScreen(Set<UUID> whitelist) {
		this(whitelist, false, Collections.emptyList());
	}

	public WhitelistManageScreen(Set<UUID> whitelist, boolean customMode) {
		this(whitelist, customMode, Collections.emptyList());
	}

	public WhitelistManageScreen(Set<UUID> whitelist, boolean customMode, List<MobEntry> mobs) {
		super(Component.translatable("screen.ssc_addon.whitelist.title"));
		this.whitelist = whitelist != null ? new HashSet<>(whitelist) : new HashSet<>();
		this.customMode = customMode;
		this.mobs = mobs != null ? new ArrayList<>(mobs) : new ArrayList<>();
		recomputeMobIndices();
	}

	public void updateWhitelist(Set<UUID> newSet) {
		this.whitelist = new HashSet<>(newSet);
		invalidateFilterCache();
	}

	public void updateState(Set<UUID> newSet, boolean customMode) {
		updateState(newSet, customMode, this.mobs);
	}

	public void updateState(Set<UUID> newSet, boolean customMode, List<MobEntry> mobs) {
		this.whitelist = new HashSet<>(newSet);
		this.customMode = customMode;
		this.mobs = mobs != null ? new ArrayList<>(mobs) : new ArrayList<>();
		if (this.modeButton != null) this.modeButton.setMessage(getWhitelistModeButtonText());
		recomputeMobIndices();
		invalidateFilterCache();
		int max = Math.max(0, getCurrentListSize() - LIST_VISIBLE_ROWS);
		if (this.scrollOffset > max) this.scrollOffset = max;
	}

	private void recomputeMobIndices() {
		this.mobIndexInSpecies = new HashMap<>();
		LinkedHashMap<String, List<MobEntry>> grouped = new LinkedHashMap<>();
		for (MobEntry e : this.mobs) {
			String k = e.typeId() != null ? e.typeId() : "";
			grouped.computeIfAbsent(k, x -> new ArrayList<>()).add(e);
		}
		for (List<MobEntry> group : grouped.values()) {
			group.sort((a, b) -> a.uuid().toString().compareTo(b.uuid().toString()));
			int idx = 1;
			for (MobEntry e : group) this.mobIndexInSpecies.put(e.uuid(), idx++);
		}
	}

	private int getCurrentListSize() {
		return switch (this.currentTab) {
			case ALL_PLAYERS -> getFilteredEntries().size();
			case PLAYER_WL -> getPlayerWhitelistEntries().size();
			case MOB -> this.mobs.size();
		};
	}

	@Override
	protected void init() {
		int left = (this.width - PANEL_WIDTH) / 2;
		int top = (this.height - PANEL_HEIGHT) / 2;

		this.allPlayersTabButton = Button.builder(
						Component.translatable("screen.ssc_addon.whitelist.tab.all_players"),
						btn -> switchTab(Tab.ALL_PLAYERS))
				.bounds(left + 10, top + 22, 88, 18)
				.build();
		this.addRenderableWidget(this.allPlayersTabButton);
		this.playerWlTabButton = Button.builder(
						Component.translatable("screen.ssc_addon.whitelist.tab.player_wl"),
						btn -> switchTab(Tab.PLAYER_WL))
				.bounds(left + 102, top + 22, 88, 18)
				.build();
		this.addRenderableWidget(this.playerWlTabButton);
		this.mobTabButton = Button.builder(
						Component.translatable("screen.ssc_addon.whitelist.tab.mob"),
						btn -> switchTab(Tab.MOB))
				.bounds(left + 194, top + 22, 88, 18)
				.build();
		this.addRenderableWidget(this.mobTabButton);

		this.searchField = new EditBox(this.font, left + 10, top + 46, PANEL_WIDTH - 20, 18,
				Component.translatable("screen.ssc_addon.whitelist.search.placeholder"));
		this.searchField.setMaxLength(64);
		this.searchField.setResponder(s -> invalidateFilterCache());
		this.addWidget(this.searchField);

		this.sortButton = Button.builder(getSortButtonText(), btn -> {
					this.sortMode = SortMode.values()[(this.sortMode.ordinal() + 1) % SortMode.values().length];
					btn.setMessage(getSortButtonText());
					invalidateFilterCache();
				})
				.bounds(left + 10, top + 70, 120, 20)
				.build();
		this.addRenderableWidget(this.sortButton);

		this.modeButton = Button.builder(getWhitelistModeButtonText(), btn -> {
					this.customMode = !this.customMode;
					btn.setMessage(getWhitelistModeButtonText());
					sendModeToggle(this.customMode);
				})
				.bounds(left + 134, top + 70, 156, 20)
				.build();
		this.addRenderableWidget(this.modeButton);

		int uuidY = top + PANEL_HEIGHT - 50;
		this.uuidField = new EditBox(this.font, left + 10, uuidY, PANEL_WIDTH - 90, 18,
				Component.translatable("screen.ssc_addon.whitelist.uuid_input.placeholder"));
		this.uuidField.setMaxLength(36);
		this.addWidget(this.uuidField);

		this.addUuidButton = Button.builder(
						Component.translatable("screen.ssc_addon.whitelist.uuid_input.add"),
						btn -> tryAddByUuidInput())
				.bounds(left + PANEL_WIDTH - 75, uuidY - 1, 65, 20)
				.build();
		this.addRenderableWidget(this.addUuidButton);

		this.addRenderableWidget(Button.builder(
						Component.translatable("gui.done"),
						btn -> this.onClose())
				.bounds(left + PANEL_WIDTH / 2 - 50, top + PANEL_HEIGHT - 25, 100, 20)
				.build());

		this.listX = left + 10;
		this.listY = top + 94;
		this.listW = PANEL_WIDTH - 20;

		updateTabUiVisibility();
	}

	private void switchTab(Tab tab) {
		this.currentTab = tab;
		this.scrollOffset = 0;
		updateTabUiVisibility();
	}

	private void updateTabUiVisibility() {
		boolean isAllPlayers = this.currentTab == Tab.ALL_PLAYERS;
		// 搜索、排序、UUID 输入仅在“所有玩家” tab 可用
		this.searchField.setVisible(isAllPlayers);
		this.sortButton.visible = isAllPlayers;
		this.uuidField.setVisible(isAllPlayers);
		this.addUuidButton.visible = isAllPlayers;
		this.allPlayersTabButton.active = this.currentTab != Tab.ALL_PLAYERS;
		this.playerWlTabButton.active = this.currentTab != Tab.PLAYER_WL;
		this.mobTabButton.active = this.currentTab != Tab.MOB;
	}

	private Component getSortButtonText() {
		return Component.translatable("screen.ssc_addon.whitelist.mode.label",
				Component.translatable(this.sortMode.key));
	}

	private Component getWhitelistModeButtonText() {
		String key = this.customMode
				? "screen.ssc_addon.whitelist.wlmode.custom"
				: "screen.ssc_addon.whitelist.wlmode.default";
		return Component.translatable("screen.ssc_addon.whitelist.wlmode.label", Component.translatable(key));
	}

	private void sendModeToggle(boolean custom) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeByte(custom ? 1 : 0);
		ClientPlayNetworking.send(new BytePayload(BytePayload.id(SscAddonNetworking.PACKET_WHITELIST_GUI_MODE), buf));
	}

	private void invalidateFilterCache() {
		this.lastSortMode = null;
	}

	private List<PlayerInfo> getFilteredEntries() {
		if (this.minecraft == null || this.minecraft.getConnection() == null) return Collections.emptyList();
		String search = this.searchField != null ? this.searchField.getValue().trim().toLowerCase(Locale.ROOT) : "";
		int wlHash = this.whitelist.hashCode();
		if (search.equals(this.lastSearchText) && this.sortMode == this.lastSortMode && wlHash == this.lastWhitelistHash) {
			return this.filteredCache;
		}
		this.lastSearchText = search;
		this.lastSortMode = this.sortMode;
		this.lastWhitelistHash = wlHash;

		Collection<PlayerInfo> all = this.minecraft.getConnection().getOnlinePlayers();
		List<PlayerInfo> list = new ArrayList<>();
		for (PlayerInfo e : all) {
			GameProfile p = e.getProfile();
			if (p == null) continue;
			if (!search.isEmpty()) {
				String name = p.getName() == null ? "" : p.getName().toLowerCase(Locale.ROOT);
				String idStr = p.getId() == null ? "" : p.getId().toString().toLowerCase(Locale.ROOT);
				if (!name.contains(search) && !idStr.contains(search)) continue;
			}
			list.add(e);
		}

		switch (this.sortMode) {
			case DISTANCE -> list.sort((a, b) -> Double.compare(distanceTo(a), distanceTo(b)));
			case ALPHABETICAL -> list.sort((a, b) -> {
				String na = a.getProfile().getName() == null ? "" : a.getProfile().getName();
				String nb = b.getProfile().getName() == null ? "" : b.getProfile().getName();
				return na.compareToIgnoreCase(nb);
			});
			case ADDED_FIRST -> list.sort((a, b) -> {
				boolean aw = this.whitelist.contains(a.getProfile().getId());
				boolean bw = this.whitelist.contains(b.getProfile().getId());
				if (aw == bw) {
					String na = a.getProfile().getName() == null ? "" : a.getProfile().getName();
					String nb = b.getProfile().getName() == null ? "" : b.getProfile().getName();
					return na.compareToIgnoreCase(nb);
				}
				return aw ? -1 : 1;
			});
		}

		this.filteredCache = list;
		int maxOffset = Math.max(0, list.size() - LIST_VISIBLE_ROWS);
		if (this.scrollOffset > maxOffset) this.scrollOffset = maxOffset;
		return list;
	}

	private double distanceTo(PlayerInfo e) {
		if (this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null) return Double.MAX_VALUE;
		UUID uuid = e.getProfile().getId();
		if (uuid == null) return Double.MAX_VALUE;
		Player other = this.minecraft.level.getPlayerByUUID(uuid);
		if (other == null) return Double.MAX_VALUE;
		Vec3 a = this.minecraft.player.position();
		Vec3 b = other.position();
		return a.distanceToSqr(b);
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY,  delta);
		int left = (this.width - PANEL_WIDTH) / 2;
		int top = (this.height - PANEL_HEIGHT) / 2;

		ctx.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xC0101010);
		ctx.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF606060);

		ctx.drawCenteredString(this.font, this.title, this.width / 2, top + 8, 0xFFFFFFFF);

		Component countText = switch (this.currentTab) {
			case ALL_PLAYERS -> Component.translatable("screen.ssc_addon.whitelist.count", this.whitelist.size());
			case PLAYER_WL -> Component.translatable("screen.ssc_addon.whitelist.count.player_wl", getPlayerWhitelistEntries().size());
			case MOB -> Component.translatable("screen.ssc_addon.whitelist.count.mob", this.mobs.size());
		};
		int cw = this.font.width(countText);
		ctx.drawString(this.font, countText, left + PANEL_WIDTH - 10 - cw, top + PANEL_HEIGHT - 70, 0xFFCCCCCC);

		switch (this.currentTab) {
			case ALL_PLAYERS -> {
				this.searchField.render(ctx, mouseX, mouseY, delta);
				this.uuidField.render(ctx, mouseX, mouseY, delta);
				renderPlayerList(ctx, mouseX, mouseY);
			}
			case PLAYER_WL -> renderPlayerWhitelistList(ctx, mouseX, mouseY);
			case MOB -> renderMobList(ctx, mouseX, mouseY);
		}

		super.render(ctx, mouseX, mouseY, delta);
	}

	private void renderPlayerList(GuiGraphics ctx, int mouseX, int mouseY) {
		List<PlayerInfo> entries = getFilteredEntries();
		int listH = LIST_VISIBLE_ROWS * ENTRY_HEIGHT;
		ctx.fill(this.listX, this.listY, this.listX + this.listW, this.listY + listH, 0x80000000);

		if (entries.isEmpty()) {
			ctx.drawCenteredString(this.font,
					Component.translatable("screen.ssc_addon.whitelist.empty"),
					this.listX + this.listW / 2, this.listY + listH / 2 - 4, 0xFF888888);
		} else {
			int end = Math.min(entries.size(), this.scrollOffset + LIST_VISIBLE_ROWS);
			for (int i = this.scrollOffset; i < end; i++) {
				int rowY = this.listY + (i - this.scrollOffset) * ENTRY_HEIGHT;
				renderPlayerEntry(ctx, entries.get(i), rowY, mouseX, mouseY);
			}
		}
		renderScrollbar(ctx, entries.size(), listH);
	}

	/** “已加入白名单的玩家” tab 数据源：按 UUID 排序，白名单内全部条目（mob UUID 已被服务端从同步包剔除）。 */
	private List<UUID> getPlayerWhitelistEntries() {
		List<UUID> list = new ArrayList<>(this.whitelist);
		list.sort(java.util.Comparator.comparing(UUID::toString));
		return list;
	}

	private void renderPlayerWhitelistList(GuiGraphics ctx, int mouseX, int mouseY) {
		List<UUID> entries = getPlayerWhitelistEntries();
		int listH = LIST_VISIBLE_ROWS * ENTRY_HEIGHT;
		ctx.fill(this.listX, this.listY, this.listX + this.listW, this.listY + listH, 0x80000000);

		if (entries.isEmpty()) {
			ctx.drawCenteredString(this.font,
					Component.translatable("screen.ssc_addon.whitelist.empty.player_wl"),
					this.listX + this.listW / 2, this.listY + listH / 2 - 4, 0xFF888888);
		} else {
			int end = Math.min(entries.size(), this.scrollOffset + LIST_VISIBLE_ROWS);
			for (int i = this.scrollOffset; i < end; i++) {
				int rowY = this.listY + (i - this.scrollOffset) * ENTRY_HEIGHT;
				renderPlayerWhitelistEntry(ctx, entries.get(i), rowY, mouseX, mouseY);
			}
		}
		renderScrollbar(ctx, entries.size(), listH);
	}

	private void renderPlayerWhitelistEntry(GuiGraphics ctx, UUID uuid, int rowY, int mouseX, int mouseY) {
		// 行底色：全部已加入 → 淡绿
		ctx.fill(this.listX, rowY, this.listX + this.listW - 6, rowY + ENTRY_HEIGHT, 0x4030FF30);

		boolean hover = mouseX >= this.listX && mouseX < this.listX + this.listW - 6
				&& mouseY >= rowY && mouseY < rowY + ENTRY_HEIGHT;
		if (hover) ctx.fill(this.listX, rowY, this.listX + this.listW - 6, rowY + ENTRY_HEIGHT, 0x30FFFFFF);

		PlayerInfo online = this.minecraft != null && this.minecraft.getConnection() != null
				? this.minecraft.getConnection().getPlayerInfo(uuid)
				: null;

		// 头像：在线 → 取皮肤；离线 → 默认皮肤
		ResourceLocation skinTex = online != null ? online.getSkin().texture()
				: net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();
		if (skinTex != null) {
			ctx.blit(skinTex, this.listX + 3, rowY + 3, 16, 16, 8, 8, 8, 8, 64, 64);
			ctx.blit(skinTex, this.listX + 3, rowY + 3, 16, 16, 40, 8, 8, 8, 64, 64);
		}

		ctx.drawString(this.font, Component.literal("§a✔"), this.listX + 24, rowY + 7, 0xFFFFFFFF);

		String name = (online != null && online.getProfile() != null && online.getProfile().getName() != null)
				? online.getProfile().getName()
				: i18nStr("screen.ssc_addon.whitelist.entry.offline_name");
		ctx.drawString(this.font, name, this.listX + 36, rowY + 4, 0xFF55FF55);

		String sub = uuid.toString().substring(0, 8);
		ctx.drawString(this.font, Component.literal(sub).withStyle(ChatFormatting.DARK_GRAY),
				this.listX + 36, rowY + 13, 0xFF606060);

		int btnX = this.listX + this.listW - 30;
		int btnY = rowY + 3;
		boolean btnHover = mouseX >= btnX && mouseX < btnX + 22 && mouseY >= btnY && mouseY < btnY + 16;
		int bg = btnHover ? 0xFFAA3030 : 0xFF802020;
		ctx.fill(btnX, btnY, btnX + 22, btnY + 16, bg);
		ctx.renderOutline(btnX, btnY, 22, 16, 0xFF000000);
		ctx.drawCenteredString(this.font, "-", btnX + 11, btnY + 4, 0xFFFFFFFF);
	}

	private void renderMobList(GuiGraphics ctx, int mouseX, int mouseY) {
		int listH = LIST_VISIBLE_ROWS * ENTRY_HEIGHT;
		ctx.fill(this.listX, this.listY, this.listX + this.listW, this.listY + listH, 0x80000000);

		if (this.mobs.isEmpty()) {
			ctx.drawCenteredString(this.font,
					Component.translatable("screen.ssc_addon.whitelist.empty.mob"),
					this.listX + this.listW / 2, this.listY + listH / 2 - 4, 0xFF888888);
		} else {
			int end = Math.min(this.mobs.size(), this.scrollOffset + LIST_VISIBLE_ROWS);
			for (int i = this.scrollOffset; i < end; i++) {
				int rowY = this.listY + (i - this.scrollOffset) * ENTRY_HEIGHT;
				renderMobEntry(ctx, this.mobs.get(i), rowY, mouseX, mouseY);
			}
		}
		renderScrollbar(ctx, this.mobs.size(), listH);
	}

	private void renderScrollbar(GuiGraphics ctx, int total, int listH) {
		if (total > LIST_VISIBLE_ROWS) {
			int barX = this.listX + this.listW - 4;
			int barH = Math.max(8, (LIST_VISIBLE_ROWS * listH) / total);
			int barY = this.listY + (int) ((float) this.scrollOffset / (total - LIST_VISIBLE_ROWS) * (listH - barH));
			ctx.fill(barX, this.listY, barX + 4, this.listY + listH, 0x40FFFFFF);
			ctx.fill(barX, barY, barX + 4, barY + barH, 0xFFAAAAAA);
		}
	}

	private void renderPlayerEntry(GuiGraphics ctx, PlayerInfo entry, int rowY, int mouseX, int mouseY) {
		GameProfile profile = entry.getProfile();
		UUID uuid = profile.getId();
		boolean isSelf = this.minecraft != null && this.minecraft.player != null && uuid != null && uuid.equals(this.minecraft.player.getUUID());
		boolean inList = uuid != null && this.whitelist.contains(uuid);

		// 整行背景：已加入=淡绿，未加入=淡红，自己=灰
		int rowBg = isSelf ? 0x40808080 : (inList ? 0x4030FF30 : 0x40FF3030);
		ctx.fill(this.listX, rowY, this.listX + this.listW - 6, rowY + ENTRY_HEIGHT, rowBg);

		boolean hover = mouseX >= this.listX && mouseX < this.listX + this.listW - 6
				&& mouseY >= rowY && mouseY < rowY + ENTRY_HEIGHT;
		if (hover) ctx.fill(this.listX, rowY, this.listX + this.listW - 6, rowY + ENTRY_HEIGHT, 0x30FFFFFF);

		// 头像
		ResourceLocation skinTex = entry.getSkin().texture();
		if (skinTex != null) {
			ctx.blit(skinTex, this.listX + 3, rowY + 3, 16, 16, 8, 8, 8, 8, 64, 64);
			ctx.blit(skinTex, this.listX + 3, rowY + 3, 16, 16, 40, 8, 8, 8, 64, 64);
		}

		// √/× 状态图标
		if (!isSelf) {
			String mark = inList ? "§a✔" : "§c✘";
			ctx.drawString(this.font, Component.literal(mark), this.listX + 24, rowY + 7, 0xFFFFFFFF);
		}

		String name = profile.getName() != null ? profile.getName() : "?";
		int nameColor = isSelf ? 0xFF808080 : (inList ? 0xFF55FF55 : 0xFFFFAAAA);
		ctx.drawString(this.font, name, this.listX + 36, rowY + 4, nameColor);

		String sub;
		if (isSelf) {
			sub = i18nStr("screen.ssc_addon.whitelist.entry.self");
		} else {
			double d = distanceTo(entry);
			sub = d == Double.MAX_VALUE
					? i18nStr("screen.ssc_addon.whitelist.entry.offline")
					: i18nStr("screen.ssc_addon.whitelist.entry.distance", String.format(Locale.ROOT, "%.1f", Math.sqrt(d)));
		}
		ctx.drawString(this.font, Component.literal(sub).withStyle(ChatFormatting.GRAY),
				this.listX + 36, rowY + 13, 0xFFAAAAAA);

		if (!isSelf && uuid != null) {
			int btnX = this.listX + this.listW - 30;
			int btnY = rowY + 3;
			boolean btnHover = mouseX >= btnX && mouseX < btnX + 22 && mouseY >= btnY && mouseY < btnY + 16;
			int bg = btnHover ? (inList ? 0xFFAA3030 : 0xFF30AA30) : (inList ? 0xFF802020 : 0xFF208020);
			ctx.fill(btnX, btnY, btnX + 22, btnY + 16, bg);
			ctx.renderOutline(btnX, btnY, 22, 16, 0xFF000000);
			String label = inList ? "-" : "+";
			ctx.drawCenteredString(this.font, label, btnX + 11, btnY + 4, 0xFFFFFFFF);
		}
	}

	private void renderMobEntry(GuiGraphics ctx, MobEntry entry, int rowY, int mouseX, int mouseY) {
		// 行底色：淡绿（生物 tab 全部是已加入项）
		ctx.fill(this.listX, rowY, this.listX + this.listW - 6, rowY + ENTRY_HEIGHT, 0x4030FF30);

		boolean hover = mouseX >= this.listX && mouseX < this.listX + this.listW - 6
				&& mouseY >= rowY && mouseY < rowY + ENTRY_HEIGHT;
		if (hover) ctx.fill(this.listX, rowY, this.listX + this.listW - 6, rowY + ENTRY_HEIGHT, 0x30FFFFFF);

		// 头像：统一画对应物种的刷怪蛋（避免每帧遍历实体世界，省性能）；无 spawn egg 时回退占位
		net.minecraft.world.item.ItemStack egg = tryGetSpawnEgg(entry.typeId());
		if (!egg.isEmpty()) {
			ctx.renderItem(egg, this.listX + 3, rowY + 3);
		} else {
			ctx.fill(this.listX + 3, rowY + 3, this.listX + 19, rowY + 19, 0xFF303030);
			ctx.drawCenteredString(this.font, "?", this.listX + 11, rowY + 7, 0xFFAAAAAA);
		}

		// √ 标记（生物全是已加入）
		ctx.drawString(this.font, Component.literal("§a✔"), this.listX + 24, rowY + 7, 0xFFFFFFFF);

		Component speciesName = resolveSpeciesName(entry);
		Integer idx = this.mobIndexInSpecies.get(entry.uuid());
		String label = speciesName.getString() + (idx != null ? " #" + idx : "");
		ctx.drawString(this.font, label, this.listX + 36, rowY + 4, 0xFF55FF55);

		String shortUuid = entry.uuid().toString().substring(0, 8);
		ctx.drawString(this.font, Component.literal(shortUuid).withStyle(ChatFormatting.DARK_GRAY),
				this.listX + 36, rowY + 13, 0xFF606060);

		int btnX = this.listX + this.listW - 30;
		int btnY = rowY + 3;
		boolean btnHover = mouseX >= btnX && mouseX < btnX + 22 && mouseY >= btnY && mouseY < btnY + 16;
		int bg = btnHover ? 0xFFAA3030 : 0xFF802020;
		ctx.fill(btnX, btnY, btnX + 22, btnY + 16, bg);
		ctx.renderOutline(btnX, btnY, 22, 16, 0xFF000000);
		ctx.drawCenteredString(this.font, "-", btnX + 11, btnY + 4, 0xFFFFFFFF);
	}

	/** 根据 typeId 取对应的 spawn egg 物品；非原版/无 spawn egg 注册时返回空 */
	private net.minecraft.world.item.ItemStack tryGetSpawnEgg(String typeId) {
		if (typeId == null || typeId.isEmpty()) return net.minecraft.world.item.ItemStack.EMPTY;
		try {
			ResourceLocation id = ResourceLocation.parse(typeId);
			EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.get(id);
			if (t == null) return net.minecraft.world.item.ItemStack.EMPTY;
			net.minecraft.world.item.SpawnEggItem egg = net.minecraft.world.item.SpawnEggItem.byId(t);
			if (egg == null) return net.minecraft.world.item.ItemStack.EMPTY;
			return new net.minecraft.world.item.ItemStack(egg);
		} catch (Exception ignored) {
			return net.minecraft.world.item.ItemStack.EMPTY;
		}
	}

	private Component resolveSpeciesName(MobEntry entry) {
		if (entry.typeId() != null) {
			try {
				ResourceLocation id = ResourceLocation.parse(entry.typeId());
				EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.get(id);
				if (t != null) return t.getDescription();
			} catch (Exception ignored) {}
		}
		return Component.translatable("screen.ssc_addon.whitelist.entry.unknown_species");
	}

	private String i18nStr(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int listH = LIST_VISIBLE_ROWS * ENTRY_HEIGHT;
		if (mouseX >= this.listX && mouseX < this.listX + this.listW
				&& mouseY >= this.listY && mouseY < this.listY + listH) {
			int row = (int) ((mouseY - this.listY) / ENTRY_HEIGHT) + this.scrollOffset;
			int btnX = this.listX + this.listW - 30;
			int btnY = this.listY + (row - this.scrollOffset) * ENTRY_HEIGHT + 3;
			boolean hitBtn = mouseX >= btnX && mouseX < btnX + 22 && mouseY >= btnY && mouseY < btnY + 16;
			if (this.currentTab == Tab.ALL_PLAYERS) {
				List<PlayerInfo> entries = getFilteredEntries();
				if (row >= 0 && row < entries.size()) {
					UUID uuid = entries.get(row).getProfile().getId();
					if (uuid != null && this.minecraft != null && this.minecraft.player != null
							&& !uuid.equals(this.minecraft.player.getUUID()) && hitBtn) {
						if (this.whitelist.contains(uuid)) sendRemove(uuid); else sendAdd(uuid);
						return true;
					}
				}
			} else if (this.currentTab == Tab.PLAYER_WL) {
				List<UUID> entries = getPlayerWhitelistEntries();
				if (row >= 0 && row < entries.size() && hitBtn) {
					sendRemove(entries.get(row));
					return true;
				}
			} else {
				if (row >= 0 && row < this.mobs.size() && hitBtn) {
					sendMobRemove(this.mobs.get(row).uuid());
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int size = getCurrentListSize();
		int maxOffset = Math.max(0, size - LIST_VISIBLE_ROWS);
		this.scrollOffset = Mth.clamp(this.scrollOffset - (int) Math.signum(horizontalAmount), 0, maxOffset);
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.searchField != null && this.searchField.isFocused()) {
			return this.searchField.keyPressed(keyCode, scanCode, modifiers)
					|| this.searchField.canConsumeInput() || super.keyPressed(keyCode, scanCode, modifiers);
		}
		if (this.uuidField != null && this.uuidField.isFocused()) {
			return this.uuidField.keyPressed(keyCode, scanCode, modifiers)
					|| this.uuidField.canConsumeInput() || super.keyPressed(keyCode, scanCode, modifiers);
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (this.searchField != null && this.searchField.isFocused()) return this.searchField.charTyped(chr, modifiers);
		if (this.uuidField != null && this.uuidField.isFocused()) return this.uuidField.charTyped(chr, modifiers);
		return super.charTyped(chr, modifiers);
	}

	private void tryAddByUuidInput() {
		String input = this.uuidField.getValue().trim();
		if (input.isEmpty()) return;
		UUID uuid = parseUuidLoose(input);
		if (uuid == null) {
			if (this.minecraft != null && this.minecraft.player != null) {
				this.minecraft.player.displayClientMessage(Component.translatable("screen.ssc_addon.whitelist.uuid_input.invalid", input)
						.withStyle(ChatFormatting.RED), false);
			}
			return;
		}
		sendAdd(uuid);
		this.uuidField.setValue("");
	}

	private static UUID parseUuidLoose(String s) {
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException ignore) {
			String hex = s.replace("-", "");
			if (hex.length() == 32) {
				try {
					String dashed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
							+ hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
					return UUID.fromString(dashed);
				} catch (IllegalArgumentException ignore2) {
					return null;
				}
			}
			return null;
		}
	}

	private void sendAdd(UUID uuid) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeUUID(uuid);
		ClientPlayNetworking.send(new BytePayload(BytePayload.id(SscAddonNetworking.PACKET_WHITELIST_GUI_ADD), buf));
	}

	private void sendRemove(UUID uuid) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeUUID(uuid);
		ClientPlayNetworking.send(new BytePayload(BytePayload.id(SscAddonNetworking.PACKET_WHITELIST_GUI_REMOVE), buf));
	}

	private void sendMobRemove(UUID uuid) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeUUID(uuid);
		ClientPlayNetworking.send(new BytePayload(BytePayload.id(SscAddonNetworking.PACKET_WHITELIST_GUI_MOB_REMOVE), buf));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}