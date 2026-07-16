package dev.triton.ui.script;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class ScriptSettingsParserTest {
	@Test
	void parsesSupportedTypesWithoutExecutingPython() {
		String source = """
				# ordinary comment
				# @setting radius slider min=1 max=16 step=1 default=8 label="Search radius"
				# @setting block block default=diamond_ore label="Block"
				# @setting enabled boolean default=true
				# @setting mode select options="safe,fast" default=safe
				# @setting message text default="Mining started"
				# @setting target coordinates default="1,64,-2"
				""";
		var result = ScriptSettingsParser.parse(source);
		assertTrue(result.issues().isEmpty());
		assertEquals(6, result.definitions().size());
		assertEquals("minecraft:diamond_ore", result.definitions().get(1).defaultValue());
		assertEquals(new ScriptSettingsParser.Coordinates(1, 64, -2), result.definitions().get(5).defaultValue());
	}

	@Test
	void reportsMalformedDuplicateAndFutureMetadataLines() {
		var result = ScriptSettingsParser.parse("# @setting value number default=2\n# @setting value text default=x\n# @setting v2 future text default=x\n# @setting bad unsupported default=x");
		assertEquals(1, result.definitions().size());
		assertEquals(java.util.List.of(2, 3, 4), result.issues().stream().map(ScriptSettingsParser.Issue::line).toList());
	}

	@Test
	void validatesRangesStepsSelectBlocksAndCoordinates() {
		var definitions = ScriptSettingsParser.parse("""
				# @setting radius slider min=1 max=10 step=1 default=5
				# @setting block block default=stone
				# @setting mode select options="safe,fast" default=safe
				# @setting target coordinates default="0,64,0"
				""").definitions();
		var validation = ScriptSettingsParser.validateValues(definitions, Map.of("radius", 5.5, "block", "bad block", "mode", "unsafe", "target", "x,y,z"));
		assertFalse(validation.valid());
		assertEquals(java.util.Set.of("radius", "block", "mode", "target"), validation.errors().keySet());
	}
}
