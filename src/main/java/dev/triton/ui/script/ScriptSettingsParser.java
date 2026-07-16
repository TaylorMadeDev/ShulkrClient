package dev.triton.ui.script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScriptSettingsParser {
	private static final Pattern ANNOTATION = Pattern.compile("^\\s*#\\s*@setting(?:\\s+v(\\d+))?\\s+(.+)$");
	private static final Pattern KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
	private static final Pattern BLOCK = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
	private static final Set<String> PROPERTIES = Set.of("label", "default", "description", "required", "min", "max", "step", "options");

	private ScriptSettingsParser() {}

	public record Issue(int line, String message, String source) {}
	public record Result(int version, List<ScriptSettingDefinition> definitions, List<Issue> issues) {}
	public record Coordinates(double x, double y, double z) {}
	public record Validation(boolean valid, Map<String, Object> values, Map<String, String> errors) {}

	public static Result parse(String source) {
		List<ScriptSettingDefinition> definitions = new ArrayList<>();
		List<Issue> issues = new ArrayList<>();
		Set<String> keys = new HashSet<>();
		String[] lines = String.valueOf(source == null ? "" : source).split("\\R", -1);
		for (int index = 0; index < lines.length; index++) {
			Matcher matcher = ANNOTATION.matcher(lines[index]);
			if (!matcher.matches()) continue;
			int line = index + 1;
			try {
				int version = matcher.group(1) == null ? 1 : Integer.parseInt(matcher.group(1));
				if (version != 1) throw new IllegalArgumentException("Unsupported metadata version v" + version);
				List<String> tokens = tokenize(matcher.group(2));
				if (tokens.size() < 2) throw new IllegalArgumentException("Expected a setting key and type");
				String key = tokens.get(0);
				if (!KEY.matcher(key).matches()) throw new IllegalArgumentException("Setting key must be a Python-style identifier");
				if (keys.contains(key)) throw new IllegalArgumentException("Duplicate setting key: " + key);
				ScriptSettingDefinition.Type type;
				try { type = ScriptSettingDefinition.Type.valueOf(tokens.get(1).toUpperCase(Locale.ROOT)); }
				catch (IllegalArgumentException ignored) { throw new IllegalArgumentException("Unsupported setting type: " + tokens.get(1)); }
				Map<String, String> raw = new HashMap<>();
				for (int tokenIndex = 2; tokenIndex < tokens.size(); tokenIndex++) {
					String token = tokens.get(tokenIndex);
					int equals = token.indexOf('=');
					if (equals < 1) throw new IllegalArgumentException("Malformed property: " + token);
					String property = token.substring(0, equals);
					if (!PROPERTIES.contains(property)) throw new IllegalArgumentException("Unsupported property: " + property);
					raw.put(property, token.substring(equals + 1));
				}
				Double min = numberOrNull(raw.get("min"), "min");
				Double max = numberOrNull(raw.get("max"), "max");
				Double step = numberOrNull(raw.get("step"), "step");
				if (min != null && max != null && min > max) throw new IllegalArgumentException("min cannot be greater than max");
				if (step != null && step <= 0) throw new IllegalArgumentException("step must be greater than zero");
				List<String> options = type == ScriptSettingDefinition.Type.SELECT
						? List.of(raw.getOrDefault("options", "").split(",")).stream().map(String::trim).filter(value -> !value.isBlank()).toList()
						: List.of();
				if (type == ScriptSettingDefinition.Type.SELECT && options.isEmpty()) throw new IllegalArgumentException("select settings require options");
				boolean required = booleanValue(raw.getOrDefault("required", "false"), "required");
				String label = raw.getOrDefault("label", title(key));
				Object fallback = raw.containsKey("default") ? raw.get("default") : defaultFor(type, min, options);
				ScriptSettingDefinition partial = new ScriptSettingDefinition(key, label, type, null, raw.getOrDefault("description", ""), required, min, max, step, options, line, version);
				Object defaultValue = validate(partial, fallback);
				definitions.add(new ScriptSettingDefinition(key, label, type, defaultValue, partial.description(), required, min, max, step, options, line, version));
				keys.add(key);
			} catch (RuntimeException error) {
				issues.add(new Issue(line, error.getMessage(), lines[index].trim()));
			}
		}
		return new Result(1, List.copyOf(definitions), List.copyOf(issues));
	}

	public static Validation validateValues(List<ScriptSettingDefinition> definitions, Map<String, ?> input) {
		Map<String, Object> values = new HashMap<>();
		Map<String, String> errors = new HashMap<>();
		for (ScriptSettingDefinition definition : definitions) {
			Object raw = input != null && input.containsKey(definition.key()) ? input.get(definition.key()) : definition.defaultValue();
			try { values.put(definition.key(), validate(definition, raw)); }
			catch (IllegalArgumentException error) { errors.put(definition.key(), error.getMessage()); }
		}
		return new Validation(errors.isEmpty(), Map.copyOf(values), Map.copyOf(errors));
	}

	public static Object validate(ScriptSettingDefinition definition, Object value) {
		if ((value == null || String.valueOf(value).isBlank()) && definition.required()) throw new IllegalArgumentException(definition.label() + " is required");
		return switch (definition.type()) {
			case NUMBER, SLIDER -> validateNumber(definition, value);
			case BOOLEAN -> value instanceof Boolean bool ? bool : booleanValue(String.valueOf(value).toLowerCase(Locale.ROOT), definition.label());
			case SELECT -> {
				String selected = String.valueOf(value);
				if (!definition.options().contains(selected)) throw new IllegalArgumentException(definition.label() + " must be one of: " + String.join(", ", definition.options()));
				yield selected;
			}
			case BLOCK -> normalizeBlock(String.valueOf(value));
			case COORDINATES -> normalizeCoordinates(value);
			case TEXT -> String.valueOf(value == null ? "" : value);
		};
	}

	private static double validateNumber(ScriptSettingDefinition definition, Object value) {
		double number;
		try { number = value instanceof Number numeric ? numeric.doubleValue() : Double.parseDouble(String.valueOf(value)); }
		catch (NumberFormatException ignored) { throw new IllegalArgumentException(definition.label() + " must be a number"); }
		if (!Double.isFinite(number)) throw new IllegalArgumentException(definition.label() + " must be a finite number");
		if (definition.min() != null && number < definition.min()) throw new IllegalArgumentException(definition.label() + " must be at least " + definition.min());
		if (definition.max() != null && number > definition.max()) throw new IllegalArgumentException(definition.label() + " must be at most " + definition.max());
		if (definition.step() != null) {
			double units = (number - (definition.min() == null ? 0 : definition.min())) / definition.step();
			if (Math.abs(units - Math.rint(units)) > 1e-8) throw new IllegalArgumentException(definition.label() + " must use increments of " + definition.step());
		}
		return number;
	}

	public static String normalizeBlock(String value) {
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		if (!normalized.contains(":")) normalized = "minecraft:" + normalized;
		if (!BLOCK.matcher(normalized).matches()) throw new IllegalArgumentException("Enter a valid Minecraft identifier such as minecraft:diamond_ore");
		return normalized;
	}

	public static Coordinates normalizeCoordinates(Object value) {
		if (value instanceof Coordinates coordinates) return coordinates;
		if (value instanceof Map<?, ?> map) {
			try {
				return new Coordinates(Double.parseDouble(String.valueOf(map.get("x"))), Double.parseDouble(String.valueOf(map.get("y"))), Double.parseDouble(String.valueOf(map.get("z"))));
			} catch (NumberFormatException ignored) {
				throw new IllegalArgumentException("Coordinates require numeric X, Y, and Z values");
			}
		}
		String[] parts = String.valueOf(value).trim().split("[\\s,]+");
		if (parts.length != 3) throw new IllegalArgumentException("Coordinates require numeric X, Y, and Z values");
		try { return new Coordinates(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2])); }
		catch (NumberFormatException ignored) { throw new IllegalArgumentException("Coordinates require numeric X, Y, and Z values"); }
	}

	private static List<String> tokenize(String value) {
		List<String> tokens = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		char quote = 0;
		boolean escaped = false;
		for (char character : value.toCharArray()) {
			if (escaped) { current.append(character); escaped = false; continue; }
			if (character == '\\' && quote != 0) { escaped = true; continue; }
			if (quote != 0) { if (character == quote) quote = 0; else current.append(character); continue; }
			if (character == '\'' || character == '"') { quote = character; continue; }
			if (Character.isWhitespace(character)) { if (!current.isEmpty()) { tokens.add(current.toString()); current.setLength(0); } }
			else current.append(character);
		}
		if (quote != 0) throw new IllegalArgumentException("Unclosed quoted value");
		if (!current.isEmpty()) tokens.add(current.toString());
		return tokens;
	}

	private static Double numberOrNull(String value, String property) {
		if (value == null) return null;
		try { return Double.parseDouble(value); }
		catch (NumberFormatException ignored) { throw new IllegalArgumentException(property + " must be a number"); }
	}

	private static boolean booleanValue(String value, String property) {
		if ("true".equals(value)) return true;
		if ("false".equals(value)) return false;
		throw new IllegalArgumentException(property + " must be true or false");
	}

	private static Object defaultFor(ScriptSettingDefinition.Type type, Double min, List<String> options) {
		return switch (type) {
			case BOOLEAN -> false;
			case NUMBER, SLIDER -> min == null ? 0 : min;
			case SELECT -> options.getFirst();
			case COORDINATES -> "0,0,0";
			default -> "";
		};
	}

	private static String title(String key) {
		StringBuilder result = new StringBuilder();
		for (String part : key.split("_")) {
			if (part.isBlank()) continue;
			if (!result.isEmpty()) result.append(' ');
			result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return result.toString();
	}

}
