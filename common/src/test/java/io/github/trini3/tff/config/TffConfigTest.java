package io.github.trini3.tff.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TffConfigTest {
	@TempDir
	Path dir;

	private Path file() {
		return this.dir.resolve("config").resolve(TffConfig.FILE_NAME);
	}

	private void write(String text) throws Exception {
		Files.createDirectories(this.file().getParent());
		Files.writeString(this.file(), text, StandardCharsets.UTF_8);
	}

	private JsonObject read() throws Exception {
		return JsonParser.parseString(Files.readString(this.file(), StandardCharsets.UTF_8)).getAsJsonObject();
	}

	@Test
	void missingFileIsCreatedWithTheDefaults() throws Exception {
		TffConfig config = TffConfig.load(this.file());
		assertEquals(1.5f, config.headshotMultiplier());
		assertEquals(1.5f, this.read().get("headshot_multiplier").getAsFloat(), "the default is written out");
	}

	@Test
	void valuesAreRead() throws Exception {
		this.write("{\"headshot_multiplier\": 2.25}");
		assertEquals(2.25f, TffConfig.load(this.file()).headshotMultiplier());
		assertEquals("{\"headshot_multiplier\": 2.25}", Files.readString(this.file()), "file left as it was");
	}

	@Test
	void oneTurnsHeadshotsOff() throws Exception {
		this.write("{\"headshot_multiplier\": 1}");
		assertEquals(1f, TffConfig.load(this.file()).headshotMultiplier());
	}

	@Test
	void brokenFileUsesTheDefaultsAndIsKept() throws Exception {
		this.write("{\"headshot_multiplier\": 2,,");
		assertEquals(TffConfig.DEFAULT, TffConfig.load(this.file()));
		assertEquals("{\"headshot_multiplier\": 2,,", Files.readString(this.file()));
	}

	@Test
	void outOfRangeValueUsesTheDefaultsAndIsKept() throws Exception {
		this.write("{\"headshot_multiplier\": -3}");
		assertEquals(TffConfig.DEFAULT, TffConfig.load(this.file()));
		assertEquals("{\"headshot_multiplier\": -3}", Files.readString(this.file()));
	}

	@Test
	void missingSettingsAreAddedBack() throws Exception {
		this.write("{\"something_else\": true}");
		assertEquals(TffConfig.DEFAULT, TffConfig.load(this.file()));
		JsonObject json = this.read();
		assertEquals(1.5f, json.get("headshot_multiplier").getAsFloat());
		assertTrue(json.get("something_else").getAsBoolean(), "other keys are kept");
	}

	@Test
	void emptyFileStartsOver() throws Exception {
		this.write("");
		assertEquals(TffConfig.DEFAULT, TffConfig.load(this.file()));
		assertEquals(1.5f, this.read().get("headshot_multiplier").getAsFloat());
	}
}
