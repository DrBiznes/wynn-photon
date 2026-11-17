package com.wynnphoton;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Hands install results from the installer (which runs before Minecraft starts, or on the watcher
 * thread) to the in-game toasts. Notices posted before the game can show them wait here. Loads no
 * Minecraft classes.
 */
public final class Notices {
	public enum Kind { SUCCESS, ERROR }

	public record Notice(Kind kind, String title, String message) {
	}

	private static final List<Notice> pending = new ArrayList<>();
	private static Consumer<Notice> sink;
	private static boolean enabled = true;

	private Notices() {
	}

	static synchronized void setEnabled(boolean on) {
		enabled = on;
	}

	public static synchronized void post(Notice notice) {
		if (!enabled) {
			return;
		}
		if (sink != null) {
			sink.accept(notice);
		} else {
			pending.add(notice);
		}
	}

	/** Called once the game can show toasts; delivers anything posted before that. */
	public static synchronized void connect(Consumer<Notice> target) {
		sink = target;
		pending.forEach(target);
		pending.clear();
	}

	static synchronized List<Notice> pendingForTest() {
		return List.copyOf(pending);
	}

	static synchronized void resetForTest() {
		pending.clear();
		sink = null;
		enabled = true;
	}
}
