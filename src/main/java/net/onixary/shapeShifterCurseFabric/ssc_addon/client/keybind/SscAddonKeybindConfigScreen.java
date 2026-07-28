package net.onixary.shapeShifterCurseFabric.ssc_addon.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonClientConfig;
import org.lwjgl.glfw.GLFW;

/**
 * 单个形态的「特殊键位」配置界面：
 * <ul>
 *   <li>开关：是否对该形态启用自定义键位（关=同步 SSC 的 G 键）；</li>
 *   <li>主技能键 / 副技能键：点击后按任意键/鼠标键绑定，按 ESC 解绑。</li>
 * </ul>
 * 改动即时写入 {@link SSCAddonClientConfig} 并持久化。
 */
public class SscAddonKeybindConfigScreen extends Screen {

	private final Screen parent;
	private final String formPath;
	private SSCAddonClientConfig.FormKeybind entry;

	private Button enableBtn;
	private Button primaryBtn;
	private Button secondaryBtn;

	/** 0=未监听，1=正在绑定主键，2=正在绑定副键 */
	private int listening = 0;

	public SscAddonKeybindConfigScreen(Screen parent, String formPath) {
		super(SscAddonSkillForms.displayName(formPath));
		this.parent = parent;
		this.formPath = formPath;
	}

	@Override
	protected void init() {
		SSCAddonClientConfig cfg = AutoConfig.getConfigHolder(SSCAddonClientConfig.class).getConfig();
		entry = cfg.formKeybinds.computeIfAbsent(formPath, k -> new SSCAddonClientConfig.FormKeybind());

		final int w = 220;
		final int h = 20;
		final int gap = 8;
		final int x = (width - w) / 2;
		int y = 50;

		enableBtn = Button.builder(Component.empty(), b -> {
			entry.enabled = !entry.enabled;
			save();
			updateLabels();
		}).size(w, h).pos(x, y).build();
		addRenderableWidget(enableBtn);
		y += h + gap;

		primaryBtn = Button.builder(Component.empty(), b -> {
			listening = 1;
			updateLabels();
		}).size(w, h).pos(x, y).build();
		addRenderableWidget(primaryBtn);
		y += h + gap;

		secondaryBtn = Button.builder(Component.empty(), b -> {
			listening = 2;
			updateLabels();
		}).size(w, h).pos(x, y).build();
		addRenderableWidget(secondaryBtn);
		y += h + gap * 2;

		addRenderableWidget(Button.builder(
						Component.translatable("text.ssc_addon.config.close"),
						b -> onClose())
				.size(w, h).pos(x, y).build());

		updateLabels();
	}

	private void updateLabels() {
		enableBtn.setMessage(Component.translatable("text.ssc_addon.keybind.enabled")
				.append(": ")
				.append(entry.enabled
						? Component.translatable("text.ssc_addon.keybind.on").withStyle(ChatFormatting.GREEN)
						: Component.translatable("text.ssc_addon.keybind.off").withStyle(ChatFormatting.RED)));

		primaryBtn.setMessage(Component.translatable("text.ssc_addon.keybind.primary")
				.append(": ")
				.append(listening == 1
						? Component.translatable("text.ssc_addon.keybind.listening").withStyle(ChatFormatting.YELLOW)
						: keyName(entry.primaryKey)));

		secondaryBtn.setMessage(Component.translatable("text.ssc_addon.keybind.secondary")
				.append(": ")
				.append(listening == 2
						? Component.translatable("text.ssc_addon.keybind.listening").withStyle(ChatFormatting.YELLOW)
						: keyName(entry.secondaryKey)));
	}

	private Component keyName(String translationKey) {
		if (translationKey == null || translationKey.isEmpty() || "key.keyboard.unknown".equals(translationKey)) {
			return Component.translatable("text.ssc_addon.keybind.unbound").withStyle(ChatFormatting.DARK_GRAY);
		}
		try {
			return InputConstants.getKey(translationKey).getDisplayName();
		} catch (Throwable t) {
			return Component.translatable("text.ssc_addon.keybind.unbound").withStyle(ChatFormatting.DARK_GRAY);
		}
	}

	private void setKey(int which, String translationKey) {
		if (which == 1) {
			entry.primaryKey = translationKey;
		} else if (which == 2) {
			entry.secondaryKey = translationKey;
		}
	}

	private void save() {
		AutoConfig.getConfigHolder(SSCAddonClientConfig.class).save();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (listening != 0) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				setKey(listening, "key.keyboard.unknown"); // ESC = 解绑
			} else {
				setKey(listening, InputConstants.getKey(keyCode, scanCode).getName());
			}
			listening = 0;
			save();
			updateLabels();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (listening != 0) {
			setKey(listening, InputConstants.Type.MOUSE.getOrCreate(button).getName());
			listening = 0;
			save();
			updateLabels();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseX, mouseY,  delta);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredString(this.font, this.title, this.width / 2, 22, 0xFFFFFF);
		// 副标题提示
		context.drawCenteredString(this.font,
				Component.translatable("text.ssc_addon.keybind.hint").withStyle(ChatFormatting.GRAY),
				this.width / 2, 34, 0xAAAAAA);
	}
}