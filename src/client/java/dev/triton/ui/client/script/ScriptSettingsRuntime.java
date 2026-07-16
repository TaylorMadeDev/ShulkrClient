package dev.triton.ui.client.script;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import dev.triton.ui.script.ScriptSettingsParser;
import dev.triton.ui.client.config.FluxusConfig;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ScriptSettingsRuntime {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private ScriptSettingsRuntime() {}

	public record Prepared(String scriptId, String commandPath, boolean configured, int settingCount) {}

	public static Path scriptDirectory() {
		FluxusConfig config = FluxusConfig.load();
		if (!config.scriptFolderPath().isBlank()) {
			try { return Path.of(config.scriptFolderPath()).toAbsolutePath().normalize(); }
			catch (RuntimeException ignored) {}
		}
		return Minecraft.getInstance().gameDirectory.toPath().resolve("minescript").toAbsolutePath().normalize();
	}

	private static Path dataDirectory() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("shulkr-backend").normalize();
	}

	public static synchronized String stableId(Path script) throws IOException {
		Path root = scriptDirectory();
		Path normalized = script.toAbsolutePath().normalize();
		if (!normalized.startsWith(root.toAbsolutePath().normalize())) throw new IOException("Script is outside the Minescript directory");
		String relative = root.relativize(normalized).toString().replace('\\', '/');
		JsonObject registry = readObject(dataDirectory().resolve("script-registry.json"));
		if (!registry.has("version")) registry.addProperty("version", 1);
		JsonObject scripts = registry.has("scripts") && registry.get("scripts").isJsonObject() ? registry.getAsJsonObject("scripts") : new JsonObject();
		registry.add("scripts", scripts);
		if (!scripts.has(relative) || !scripts.get(relative).isJsonObject()) {
			JsonObject identity = new JsonObject();
			identity.addProperty("id", UUID.randomUUID().toString());
			identity.addProperty("installedAt", Instant.now().toString());
			identity.add("lastRunAt", com.google.gson.JsonNull.INSTANCE);
			scripts.add(relative, identity);
			writeObject(dataDirectory().resolve("script-registry.json"), registry);
		}
		return scripts.getAsJsonObject(relative).get("id").getAsString();
	}

	public static synchronized Path resolveScript(String id) throws IOException {
		JsonObject registry = readObject(dataDirectory().resolve("script-registry.json"));
		JsonObject scripts = registry.has("scripts") && registry.get("scripts").isJsonObject() ? registry.getAsJsonObject("scripts") : new JsonObject();
		for (Map.Entry<String, JsonElement> entry : scripts.entrySet()) {
			if (entry.getValue().isJsonObject() && id.equals(entry.getValue().getAsJsonObject().get("id").getAsString())) {
				Path candidate = scriptDirectory().resolve(entry.getKey()).normalize();
				if (candidate.startsWith(scriptDirectory()) && Files.isRegularFile(candidate)) return candidate;
			}
		}
		return null;
	}

	public static synchronized Prepared prepare(Path script) throws IOException {
		return prepare(script, null);
	}

	public static synchronized Prepared prepare(Path script, Path workingDirectory) throws IOException {
		Path root = scriptDirectory();
		Path normalized = script.toAbsolutePath().normalize();
		Path runDirectory = workingDirectory == null ? root : workingDirectory.toAbsolutePath().normalize();
		if (!Files.isDirectory(runDirectory)) throw new IOException("Working directory does not exist: " + runDirectory);
		String id = stableId(normalized);
		String source = Files.readString(normalized, StandardCharsets.UTF_8);
		ScriptSettingsParser.Result metadata = ScriptSettingsParser.parse(source);
		if (!metadata.issues().isEmpty()) throw new IOException("Script metadata error on line " + metadata.issues().getFirst().line() + ": " + metadata.issues().getFirst().message());
		String relative = root.relativize(normalized).toString().replace('\\', '/');
		if (metadata.definitions().isEmpty() && runDirectory.equals(root.toAbsolutePath().normalize())) {
			return new Prepared(id, stripExtension(relative), false, 0);
		}

		JsonObject allSettings = readObject(dataDirectory().resolve("script-settings.json"));
		Map<String, Object> saved = new LinkedHashMap<>();
		if (allSettings.has(id) && allSettings.get(id).isJsonObject()) {
			JsonObject record = allSettings.getAsJsonObject(id);
			if (record.has("values") && record.get("values").isJsonObject()) {
				saved = GSON.fromJson(record.get("values"), new TypeToken<Map<String, Object>>() {}.getType());
			}
		}
		ScriptSettingsParser.Validation validation = ScriptSettingsParser.validateValues(metadata.definitions(), saved);
		if (!validation.valid()) throw new IOException("Saved script settings are invalid: " + String.join(", ", validation.errors().values()));

		Path configDir = root.resolve("shulkr_config").normalize();
		Path runtimeDir = root.resolve("shulkr_runtime").normalize();
		Files.createDirectories(configDir);
		Files.createDirectories(runtimeDir);
		Path configFile = configDir.resolve(id + ".json").normalize();
		Path wrapperFile = runtimeDir.resolve(id + ".py").normalize();
		if (!configFile.startsWith(root) || !wrapperFile.startsWith(root)) throw new IOException("Runtime path escaped the Minescript directory");
		Map<String, Object> payload = Map.of("version", 1, "scriptId", id, "scriptPath", relative, "values", validation.values(),
				"workingDirectory", runDirectory.toString());
		atomicWrite(configFile, GSON.toJson(payload) + "\n");
		String wrapper = String.join("\n",
				"# Generated by Shulkr. Do not edit.",
				"import builtins, json, os, runpy",
				"_config_path = " + GSON.toJson(configFile.toString()),
				"_script_path = " + GSON.toJson(normalized.toString()),
				"with open(_config_path, 'r', encoding='utf-8') as _handle:",
				"    _payload = json.load(_handle)",
				"builtins.SHULKR_SETTINGS = _payload.get('values', {})",
				"builtins.shulkr_setting = lambda key, default=None: builtins.SHULKR_SETTINGS.get(key, default)",
				"os.environ['SHULKR_SCRIPT_SETTINGS_FILE'] = _config_path",
				"os.environ['SHULKR_SCRIPT_ID'] = _payload.get('scriptId', '')",
				"os.chdir(_payload.get('workingDirectory', os.path.dirname(_script_path)))",
				"runpy.run_path(_script_path, run_name='__main__')",
				"");
		atomicWrite(wrapperFile, wrapper);
		return new Prepared(id, stripExtension(root.relativize(wrapperFile).toString().replace('\\', '/')), !metadata.definitions().isEmpty(), metadata.definitions().size());
	}

	private static JsonObject readObject(Path file) throws IOException {
		if (!Files.exists(file)) return new JsonObject();
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
		} catch (RuntimeException error) {
			throw new IOException("Invalid JSON in " + file.getFileName(), error);
		}
	}

	private static void writeObject(Path file, JsonObject object) throws IOException {
		atomicWrite(file, GSON.toJson(object) + "\n");
	}

	private static void atomicWrite(Path file, String content) throws IOException {
		Files.createDirectories(file.getParent());
		Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
		try {
			Files.writeString(temporary, content, StandardCharsets.UTF_8);
			try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
			catch (IOException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
		} finally { Files.deleteIfExists(temporary); }
	}

	private static String stripExtension(String value) {
		int slash = value.lastIndexOf('/');
		int dot = value.lastIndexOf('.');
		return dot > slash ? value.substring(0, dot) : value;
	}
}
