package io.github.trini3.tff.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Checks the shipped head files without starting the game (the game tests check that every mob id
 * in them exists): each one parses, and no mob is listed twice, which would leave the choice to
 * the file names.
 */
class HeadFilesTest {
	private static List<Path> headFiles() throws Exception {
		URL dir = Objects.requireNonNull(HeadFilesTest.class.getResource("/data/tff/tff/head"), "head folder");
		try (Stream<Path> files = Files.list(Path.of(dir.toURI()))) {
			return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
		}
	}

	@Test
	void everyFileParsesAndListsEachMobOnce() throws Exception {
		List<Path> files = headFiles();
		assertTrue(files.size() > 30, "head files found: " + files.size());
		Map<String, Path> listedIn = new HashMap<>();
		for (Path file : files) {
			JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			List<HeadDefinition.Box> boxes = HeadDefinition.Box.CODEC.listOf().parse(JsonOps.INSTANCE, json.get("boxes"))
				.getOrThrow(message -> new AssertionError(file.getFileName() + ": " + message));
			assertFalse(json.getAsJsonArray("entities").isEmpty(), file.getFileName() + " lists no mobs");
			for (JsonElement entity : json.getAsJsonArray("entities")) {
				String id = entity.getAsString();
				assertNull(listedIn.put(id, file), id + " is in " + listedIn.get(id) + " and " + file.getFileName());
			}
			for (HeadDefinition.Box box : boxes) {
				assertTrue(box.from().y() <= box.to().y(), file.getFileName() + ": a box is upside down");
			}
		}
	}

	@Test
	void zombiesAndPlayersHaveTheirHeadsOnTop() throws Exception {
		Map<String, JsonObject> byName = new HashMap<>();
		for (Path file : headFiles()) {
			byName.put(file.getFileName().toString(), JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
		}
		// A zombie's head is the top 8 px of its 31.2 px hitbox's model (24 to 32 px).
		HeadDefinition.Box zombie = HeadDefinition.Box.CODEC.parse(JsonOps.INSTANCE,
			byName.get("humanoid.json").getAsJsonArray("boxes").get(0)).getOrThrow();
		assertEquals(24f, zombie.from().y());
		assertTrue(byName.get("humanoid.json").getAsJsonArray("entities").toString().contains("minecraft:zombie"));
		assertTrue(byName.get("player.json").getAsJsonArray("entities").toString().contains("minecraft:player"));
		assertTrue(byName.get("no_head.json").getAsJsonArray("boxes").isEmpty(), "slimes and the like have no head");
	}
}
