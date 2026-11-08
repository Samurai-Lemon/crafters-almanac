package com.samurailemon.craftersalmanac.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.samurailemon.craftersalmanac.config.ViewerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where the cards' textures come from. With "Match Texture Pack" on (the default) they're the game's textures as the
 * resource packs leave them. Off, they're the default ones, as if no pack were loaded: the game's own for vanilla
 * stations and the mod's own for modded ones. Those are read from the bottom of the resource stack (below every pack)
 * into copies of our own. Sprites normally come from the GUI atlas, which packs change, so with defaults they're drawn
 * from their own image files instead. The copies are dropped on resource reload (see CraftersAlmanac).
 */
public final class CardTextures {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Map<ResourceLocation, Optional<ResourceLocation>> DEFAULTS = new HashMap<>();

	private CardTextures() {
	}

	public static boolean matchPack() {
		return ViewerConfig.get().matchTexturePack;
	}

	/** The texture to draw: as the packs leave it, or with Match Texture Pack off, the default copy. */
	public static ResourceLocation texture(ResourceLocation texture) {
		if (matchPack()) return texture;
		return DEFAULTS.computeIfAbsent(texture, CardTextures::loadDefault).orElse(texture);
	}

	/** The texture's file to read (e.g. to measure a screen's frame), from the packs or the default. */
	public static Optional<Resource> resource(ResourceLocation texture) {
		ResourceManager resources = Minecraft.getInstance().getResourceManager();
		if (matchPack()) return resources.getResource(texture);
		List<Resource> stack = resources.getResourceStack(texture);
		return stack.isEmpty() ? Optional.empty() : Optional.of(stack.getFirst());
	}

	/**
	 * Draws part of a GUI sprite (by its name, like "container/slot"), the way {@link GuiGraphics#blitSprite} does: from
	 * the atlas as the packs leave it, or from the sprite's default image file.
	 */
	public static void sprite(GuiGraphics g, ResourceLocation sprite, int spriteWidth, int spriteHeight, int u, int v, int x, int y,
			int width, int height) {
		if (matchPack()) {
			g.blitSprite(sprite, spriteWidth, spriteHeight, u, v, x, y, width, height);
			return;
		}
		ResourceLocation file = sprite.withPath(path -> "textures/gui/sprites/" + path + ".png");
		g.blit(texture(file), x, y, u, v, width, height, spriteWidth, spriteHeight);
	}

	public static void clear() {
		for (Optional<ResourceLocation> copy : DEFAULTS.values()) {
			copy.ifPresent(Minecraft.getInstance().getTextureManager()::release);
		}
		DEFAULTS.clear();
	}

	private static Optional<ResourceLocation> loadDefault(ResourceLocation texture) {
		Optional<Resource> resource = resource(texture);
		if (resource.isEmpty()) return Optional.empty();
		try (InputStream in = resource.get().open()) {
			ResourceLocation copy = ResourceLocation.fromNamespaceAndPath("craftersalmanac", "default/" + texture.getNamespace() + "/" + texture.getPath());
			Minecraft.getInstance().getTextureManager().register(copy, new DynamicTexture(NativeImage.read(in)));
			return Optional.of(copy);
		} catch (IOException e) {
			LOGGER.warn("Couldn't read the default texture {}", texture, e);
			return Optional.empty();
		}
	}
}
