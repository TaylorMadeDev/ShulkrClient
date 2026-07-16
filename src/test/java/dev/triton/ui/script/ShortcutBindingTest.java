package dev.triton.ui.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ShortcutBindingTest {
	@Test
	void serializesParsesAndMatchesModifiersExactly() {
		var binding = new ShortcutBinding(82, ShortcutBinding.CTRL | ShortcutBinding.SHIFT);
		assertEquals("Ctrl+Shift+82", binding.serialize());
		assertEquals(binding, ShortcutBinding.parse(binding.serialize(), ShortcutBinding.unbound()));
		assertTrue(binding.matches(82, ShortcutBinding.CTRL | ShortcutBinding.SHIFT));
		assertFalse(binding.matches(82, ShortcutBinding.CTRL));
	}

	@Test
	void supportsClearingAndSafeFallback() {
		assertFalse(ShortcutBinding.parse("", new ShortcutBinding(1, 0)).bound());
		assertEquals(new ShortcutBinding(1, 0), ShortcutBinding.parse("broken", new ShortcutBinding(1, 0)));
	}
}
