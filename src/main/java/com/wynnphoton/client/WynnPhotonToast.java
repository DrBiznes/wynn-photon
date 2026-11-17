package com.wynnphoton.client;

import com.wynnphoton.Notices;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * A text-only toast in the top-right corner, drawn like vanilla's system toasts: a title (yellow,
 * or light red for errors) over a few wrapped lines. No icons, because the toast shows before
 * Wynncraft's resource pack loads and an item icon would look different once it does.
 */
final class WynnPhotonToast implements Toast {
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/system");
	private static final int MARGIN = 18;
	private static final int MAX_TEXT_WIDTH = 220;
	private static final int TITLE_COLOR = 0xFFFFFF00;
	private static final int ERROR_TITLE_COLOR = 0xFFFF7070;
	/** Vanilla's system toasts stay 5 s; ours stay longer, as they are easy to miss after loading. */
	private static final long SUCCESS_MILLIS = 12000;
	private static final long ERROR_MILLIS = 20000;

	private final Notices.Notice notice;
	private final Component title;
	private final List<FormattedCharSequence> lines;
	private final int width;
	private Visibility visibility = Visibility.SHOW;

	WynnPhotonToast(Font font, Notices.Notice notice) {
		this.notice = notice;
		this.title = Component.literal(notice.title());
		this.lines = font.split(Component.literal(notice.message()), MAX_TEXT_WIDTH);
		int textWidth = font.width(title);
		for (FormattedCharSequence line : lines) {
			textWidth = Math.max(textWidth, font.width(line));
		}
		this.width = Math.max(DEFAULT_WIDTH, textWidth + 2 * MARGIN);
	}

	@Override
	public int width() {
		return width;
	}

	@Override
	public int height() {
		return Math.max(SLOT_HEIGHT, 20 + lines.size() * 12);
	}

	@Override
	public Visibility getWantedVisibility() {
		return visibility;
	}

	@Override
	public void update(ToastManager manager, long fullyVisibleFor) {
		long shown = notice.kind() == Notices.Kind.ERROR ? ERROR_MILLIS : SUCCESS_MILLIS;
		visibility = fullyVisibleFor < shown * manager.getNotificationDisplayTimeMultiplier()
				? Visibility.SHOW : Visibility.HIDE;
	}

	@Override
	public void render(GuiGraphics graphics, Font font, long fullyVisibleFor) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, width(), height());
		int color = notice.kind() == Notices.Kind.ERROR ? ERROR_TITLE_COLOR : TITLE_COLOR;
		graphics.drawString(font, title, MARGIN, 7, color, false);
		for (int i = 0; i < lines.size(); i++) {
			graphics.drawString(font, lines.get(i), MARGIN, 18 + i * 12, 0xFFFFFFFF, false);
		}
	}
}
