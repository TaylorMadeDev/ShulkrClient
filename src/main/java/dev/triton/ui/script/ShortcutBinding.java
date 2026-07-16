package dev.triton.ui.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record ShortcutBinding(int key, int modifiers) {
	public static final int SHIFT = 1;
	public static final int CTRL = 2;
	public static final int ALT = 4;
	public static final int META = 8;
	public static final int UNBOUND = -1;

	public static ShortcutBinding unbound() {
		return new ShortcutBinding(UNBOUND, 0);
	}

	public boolean bound() {
		return key >= 0;
	}

	public boolean matches(int pressedKey, int pressedModifiers) {
		return bound() && key == pressedKey && modifiers == (pressedModifiers & (SHIFT | CTRL | ALT | META));
	}

	public String serialize() {
		if (!bound()) return "";
		List<String> parts = new ArrayList<>();
		if ((modifiers & CTRL) != 0) parts.add("Ctrl");
		if ((modifiers & ALT) != 0) parts.add("Alt");
		if ((modifiers & SHIFT) != 0) parts.add("Shift");
		if ((modifiers & META) != 0) parts.add("Meta");
		parts.add(Integer.toString(key));
		return String.join("+", parts);
	}

	public static ShortcutBinding parse(String value, ShortcutBinding fallback) {
		if (value == null) return fallback;
		if (value.isBlank()) return unbound();
		int modifiers = 0;
		int key = UNBOUND;
		try {
			for (String part : value.split("\\+")) {
				switch (part.trim().toLowerCase(Locale.ROOT)) {
					case "ctrl", "control" -> modifiers |= CTRL;
					case "alt", "option" -> modifiers |= ALT;
					case "shift" -> modifiers |= SHIFT;
					case "meta", "command", "cmd" -> modifiers |= META;
					default -> key = Integer.parseInt(part.trim());
				}
			}
			return key < 0 ? fallback : new ShortcutBinding(key, modifiers);
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}
}
