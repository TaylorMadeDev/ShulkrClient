package dev.triton.ui.script;

import java.util.List;

public record ScriptSettingDefinition(
		String key,
		String label,
		Type type,
		Object defaultValue,
		String description,
		boolean required,
		Double min,
		Double max,
		Double step,
		List<String> options,
		int line,
		int version
) {
	public enum Type { NUMBER, SLIDER, TEXT, BOOLEAN, SELECT, BLOCK, COORDINATES }
}
