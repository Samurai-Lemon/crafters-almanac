package com.samurailemon.craftersalmanac.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.Optional;

/**
 * Text colours a resource pack can set, in {@code assets/craftersalmanac/theme.json}, for when its textures make ours
 * hard to read: {@code {"card_text": "#404040", "card_subtext": "#707070", "chance_text": "#FFAA00",
 * "heading_text": "#404040"}}, each optional. The card colours follow "Match Texture Pack" (off, cards use the defaults
 * like their textures); the heading is on the book, which is always the pack's, and without a colour is picked from
 * the book's page (see BookColors). Read again on resource reload (see CraftersAlmanac).
 */
public final class Theme {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath("craftersalmanac", "theme.json");
	/** Vanilla's container label grey, its disabled-text grey, and its gold. */
	private static final int CARD_TEXT = 0x404040;
	private static final int CARD_SUBTEXT = 0x707070;
	private static final int CHANCE_TEXT = 0xFFFFAA00;

	private static JsonObject theme;

	private Theme() {
	}

	/** Text on a card: times, XP, names on favorites rows. */
	public static int cardText() {
		return CardTextures.matchPack() ? color("card_text", CARD_TEXT) : CARD_TEXT;
	}

	/** Secondary text on a card: the station under a favorite's name. */
	public static int cardSubtext() {
		return CardTextures.matchPack() ? color("card_subtext", CARD_SUBTEXT) : CARD_SUBTEXT;
	}

	/** The "50%" on outputs a recipe only sometimes gives. */
	public static int chanceText() {
		return CardTextures.matchPack() ? 0xFF000000 | color("chance_text", CHANCE_TEXT) : CHANCE_TEXT;
	}

	/** Headings and messages on the book's page. */
	public static int headingText() {
		return color("heading_text", BookColors.textColor());
	}

	public static void clear() {
		theme = null;
	}

	private static int color(String key, int fallback) {
		JsonElement value = theme().get(key);
		if (value == null || !value.isJsonPrimitive()) return fallback;
		try {
			return Integer.parseInt(value.getAsString().replace("#", ""), 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static JsonObject theme() {
		if (theme == null) {
			theme = new JsonObject();
			Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(FILE);
			if (resource.isPresent()) {
				try (Reader reader = resource.get().openAsReader()) {
					theme = JsonParser.parseReader(reader).getAsJsonObject();
				} catch (Exception e) {
					LOGGER.warn("Couldn't read {}", FILE, e);
				}
			}
		}
		return theme;
	}
}
