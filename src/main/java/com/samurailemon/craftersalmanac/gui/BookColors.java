package com.samurailemon.craftersalmanac.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;

/**
 * Text colour for the recipe book's page: vanilla's dark label grey on a light page, near-white on a dark one.
 * Resource packs disagree on the page colour (vanilla's is dark, Better Than Vanilla's light), so it's read from the
 * loaded texture, and re-read after a resource reload (see CraftersAlmanac).
 */
public final class BookColors {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final ResourceLocation RECIPE_BOOK = ResourceLocation.withDefaultNamespace("textures/gui/recipe_book.png");
	/** A spot on the book's page, below the search box, where the item grid goes (in a 256x256 texture). */
	private static final int PAGE_X = 73;
	private static final int PAGE_Y = 100;
	private static final int VANILLA_SIZE = 256;
	private static Integer textColor;

	private BookColors() {
	}

	public static int textColor() {
		if (textColor == null) {
			textColor = 0x404040;
			try (InputStream in = Minecraft.getInstance().getResourceManager().open(RECIPE_BOOK)) {
				NativeImage image = NativeImage.read(in);
				// HD resource packs use bigger textures with the same layout, so scale the spot to fit.
				int page = image.getPixelRGBA(PAGE_X * image.getWidth() / VANILLA_SIZE, PAGE_Y * image.getHeight() / VANILLA_SIZE);
				image.close();
				// NativeImage pixels are ABGR.
				int brightness = (299 * (page & 0xFF) + 587 * (page >> 8 & 0xFF) + 114 * (page >> 16 & 0xFF)) / 1000;
				if (brightness < 128) textColor = 0xE0E0E0;
			} catch (IOException e) {
				LOGGER.warn("Couldn't read the recipe book texture to pick a text colour", e);
			}
		}
		return textColor;
	}

	public static void clear() {
		textColor = null;
	}
}
