package dev.triton.ui.client.module;

public final class BooleanModuleSetting extends ModuleSetting<Boolean> {
	public BooleanModuleSetting(String id, String label, String description, boolean value) {
		super(id, label, description, value);
	}

	public boolean enabled() {
		return Boolean.TRUE.equals(value());
	}
}
