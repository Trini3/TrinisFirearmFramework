package io.github.trini3.tff.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.tff.Tff;
import io.github.trini3.tff.platform.TffPlatform;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Server settings, read from {@code config/tff.json}. The file is written with the defaults the
 * first time, and read again whenever a world or server starts, so edits apply without
 * restarting the game. Fields left out keep their defaults and are added back to the file.
 *
 * @param headshotMultiplier damage multiplier for a bullet that hits a head (1 = no bonus)
 */
public record TffConfig(float headshotMultiplier) {
	public static final String FILE_NAME = "tff.json";
	public static final TffConfig DEFAULT = new TffConfig(1.5f);

	// Optional fields with a plain default would leave defaults out of the file, so wrap them in Optional.
	public static final Codec<TffConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.floatRange(0f, 100f).optionalFieldOf("headshot_multiplier").forGetter(c -> Optional.of(c.headshotMultiplier()))
	).apply(i, multiplier -> new TffConfig(multiplier.orElse(DEFAULT.headshotMultiplier()))));

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static volatile TffConfig current = DEFAULT;

	/** The settings in use. */
	public static TffConfig get() {
		return current;
	}

	/** Reads {@code tff.json} from the loader's config folder. */
	public static void load() {
		current = load(TffPlatform.INSTANCE.configDir().resolve(FILE_NAME));
		Tff.LOGGER.info("Headshot multiplier {}", current.headshotMultiplier());
	}

	/**
	 * Reads the config at {@code file}, creating it with the defaults if it is missing or empty. A
	 * file that can't be read keeps its contents and the defaults are used, so a typo never wipes a
	 * setting.
	 */
	public static TffConfig load(Path file) {
		if (!Files.exists(file)) {
			write(file, DEFAULT);
			return DEFAULT;
		}
		JsonElement json;
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			json = JsonParser.parseReader(reader);
		} catch (IOException | RuntimeException e) {
			Tff.LOGGER.warn("Could not read {}, using the default settings: {}", file, e.getMessage());
			return DEFAULT;
		}
		if (json.isJsonNull()) {
			// An empty file: start it over.
			write(file, DEFAULT);
			return DEFAULT;
		}
		var result = CODEC.parse(JsonOps.INSTANCE, json);
		if (result.isError()) {
			Tff.LOGGER.warn("Invalid {}, using the default settings: {}", file, result.error().map(e -> e.message()).orElse("?"));
			return DEFAULT;
		}
		TffConfig config = result.getOrThrow();
		JsonElement complete = CODEC.encodeStart(JsonOps.INSTANCE, config).getOrThrow();
		if (json.isJsonObject() && !json.getAsJsonObject().keySet().containsAll(complete.getAsJsonObject().keySet())) {
			// Add settings from a newer version, keeping the values that are there.
			JsonElement merged = json.deepCopy();
			complete.getAsJsonObject().entrySet().forEach(e -> {
				if (!merged.getAsJsonObject().has(e.getKey())) {
					merged.getAsJsonObject().add(e.getKey(), e.getValue());
				}
			});
			writeJson(file, merged);
		}
		return config;
	}

	private static void write(Path file, TffConfig config) {
		writeJson(file, CODEC.encodeStart(JsonOps.INSTANCE, config).getOrThrow());
	}

	private static void writeJson(Path file, JsonElement json) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(json) + "\n", StandardCharsets.UTF_8);
		} catch (IOException e) {
			Tff.LOGGER.warn("Could not write {}: {}", file, e.getMessage());
		}
	}
}
