package com.samurailemon.craftersalmanac.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.samurailemon.craftersalmanac.recipe.Card;
import com.samurailemon.craftersalmanac.recipe.Category;
import com.samurailemon.craftersalmanac.recipe.Display;
import com.samurailemon.craftersalmanac.recipe.Station;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Card frames cut from the station's own screen texture, so a card matches its station whatever resource pack is in
 * use (packs often colour each screen differently). The screen's frame is measured from the loaded texture: its size,
 * how thick each edge is (scanning in from the edge to the background colour), and a plain patch of its background.
 * It's then drawn nine-slice: corners as they are, edges and middle repeated. Stations without a screen texture we can
 * find (campfire, Oritech's machines, which build their screens from pieces) get the recipe book's panel instead.
 * Measurements are cached per texture and dropped on resource reload (see CraftersAlmanac). With "Match Texture Pack"
 * off, the same is done with the default textures instead of the packs' (see CardTextures).
 * <p>
 * Resource packs can skip all this by drawing the frame themselves: a GUI sprite at {@code craftersalmanac:card/frame}
 * frames every card, and {@code craftersalmanac:card/frame/<namespace>/<type>} one recipe type's (e.g.
 * {@code card/frame/minecraft/stonecutting}). Give it a nine-slice {@code .mcmeta} so it fits any size. A recipe type's
 * station file can also name the screen to measure ({@code "frame"}).
 */
public final class CardFrames {
	/** A frame measured from a screen texture, in the texture's own 256-wide coordinates (HD packs scale up). */
	private record Frame(ResourceLocation texture, int textureWidth, int textureHeight, int width, int height,
			int left, int top, int right, int bottom, int patchX, int patchY) {
	}

	private static final ResourceLocation PANEL = ResourceLocation.withDefaultNamespace("recipe_book/overlay_recipe");
	/** The panel as a frame of our own, for drawing it from its default image file: 32x32 with 4px edges. */
	private static final Frame DEFAULT_PANEL = new Frame(ResourceLocation.withDefaultNamespace("textures/gui/sprites/recipe_book/overlay_recipe.png"),
			32, 32, 32, 32, 4, 4, 4, 4, 4, 4);
	/** Vanilla GUI textures are 256 wide; HD packs use the same layout at a multiple of that. */
	private static final int VANILLA_SIZE = 256;
	/** The background patch repeated to fill a card's middle. */
	private static final int PATCH_WIDTH = 16;
	private static final int PATCH_HEIGHT = 8;
	/** Edges thicker than this aren't a frame we understand; use the panel. */
	private static final int MAX_EDGE = 12;
	private static final int MIN_SCREEN = 48;
	/** Where modded screen textures usually are, tried with the machine's and the recipe type's names. */
	private static final List<String> MODDED_SCREENS = List.of("textures/gui/container/%s.png", "textures/gui/%s.png",
			"textures/gui/container/%s_gui.png", "textures/gui/%s_gui.png");

	private static final Map<ResourceLocation, Optional<Frame>> FRAMES = new HashMap<>();
	private static final Map<Category, Optional<ResourceLocation>> MODDED = new HashMap<>();
	/** Which of the frame sprites a pack can add are there. */
	private static final Map<ResourceLocation, Boolean> PACK_SPRITES = new HashMap<>();
	private static final ResourceLocation PACK_FRAME = ResourceLocation.fromNamespaceAndPath("craftersalmanac", "card/frame");
	/** Whether FRAMES were measured from the packs' textures or the defaults; switching the setting measures again. */
	private static boolean measuredWithPack = true;

	private CardFrames() {
	}

	public static void clear() {
		FRAMES.clear();
		MODDED.clear();
		PACK_SPRITES.clear();
	}

	/**
	 * The texture the card's art comes from: the station's screen. Blast furnace and smoker recipes use their own
	 * screens, which have the furnace's layout.
	 */
	public static ResourceLocation artTexture(Display display) {
		if (display.card() != Card.FURNACE) return display.card().texture;
		if (display.category() == Category.BLASTING) return screen("blast_furnace");
		if (display.category() == Category.SMOKING) return screen("smoker");
		return Card.FURNACE.texture;
	}

	/** Draws a card's frame from its station's screen, or the recipe book's panel if there's none to use. */
	public static void draw(GuiGraphics g, Display display, int x, int y, int width, int height) {
		boolean matchPack = CardTextures.matchPack();
		if (matchPack != measuredWithPack) {
			FRAMES.clear();
			measuredWithPack = matchPack;
		}
		if (matchPack) {
			ResourceLocation packFrame = packFrame(display.category());
			if (packFrame != null) {
				g.blitSprite(packFrame, x, y, width, height);
				return;
			}
		}
		ResourceLocation screen = display.card() == Card.PLAIN ? plainScreen(display.category()) : artTexture(display);
		Frame frame = screen == null ? null : FRAMES.computeIfAbsent(screen, CardFrames::measure).orElse(null);
		if (frame == null && matchPack) {
			g.blitSprite(PANEL, x, y, width, height);
			return;
		}
		nineSlice(g, frame == null ? DEFAULT_PANEL : frame, x, y, width, height);
	}

	/** Corners as they are, edges and middle repeated to fit. */
	private static void nineSlice(GuiGraphics g, Frame frame, int x, int y, int width, int height) {
		int l = frame.left();
		int t = frame.top();
		int r = frame.right();
		int b = frame.bottom();
		int innerWidth = width - l - r;
		int innerHeight = height - t - b;
		// Corners.
		blit(g, frame, x, y, 0, 0, l, t);
		blit(g, frame, x + width - r, y, frame.width() - r, 0, r, t);
		blit(g, frame, x, y + height - b, 0, frame.height() - b, l, b);
		blit(g, frame, x + width - r, y + height - b, frame.width() - r, frame.height() - b, r, b);
		// Edges, each repeated from the screen's own (the screen is usually bigger than the card, so once is enough).
		tile(g, frame, x + l, y, innerWidth, t, l, 0, frame.width() - l - r, t);
		tile(g, frame, x + l, y + height - b, innerWidth, b, l, frame.height() - b, frame.width() - l - r, b);
		tile(g, frame, x, y + t, l, innerHeight, 0, t, l, frame.height() - t - b);
		tile(g, frame, x + width - r, y + t, r, innerHeight, frame.width() - r, t, r, frame.height() - t - b);
		// The middle, from the background patch.
		tile(g, frame, x + l, y + t, innerWidth, innerHeight, frame.patchX(), frame.patchY(), PATCH_WIDTH, PATCH_HEIGHT);
	}

	/** A frame sprite the resource pack added: one for this recipe type, else one for every card. */
	private static ResourceLocation packFrame(Category category) {
		ResourceLocation id = category.id();
		ResourceLocation own = PACK_FRAME.withSuffix("/" + id.getNamespace() + "/" + id.getPath());
		if (hasSprite(own)) return own;
		return hasSprite(PACK_FRAME) ? PACK_FRAME : null;
	}

	private static boolean hasSprite(ResourceLocation sprite) {
		return PACK_SPRITES.computeIfAbsent(sprite, name -> !Minecraft.getInstance().getGuiSprites().getSprite(name).contents().name()
				.equals(MissingTextureAtlasSprite.getLocation()));
	}

	/**
	 * The screen a plain card is framed like: the one its station file names, vanilla stations' own, or a modded
	 * machine's found by name.
	 */
	public static ResourceLocation plainScreen(Category category) {
		Station station = Station.of(category.typeId());
		if (station != null && station.frame() != null) return station.frame();
		if (category == Category.STONECUTTING) return screen("stonecutter");
		if (category == Category.SMITHING) return screen("smithing");
		if (category == Category.FUEL) return Card.FURNACE.texture;
		if (category.isVirtual() || category.type == null || category.typeId().getNamespace().equals("minecraft")) return null;
		return MODDED.computeIfAbsent(category, CardFrames::findModdedScreen).orElse(null);
	}

	/** Tries the machine's item name (cooking_pot) and then the recipe type's (cooking) in the usual places. */
	private static Optional<ResourceLocation> findModdedScreen(Category category) {
		ResourceManager resources = Minecraft.getInstance().getResourceManager();
		ResourceLocation type = category.typeId();
		ResourceLocation machine = BuiltInRegistries.ITEM.getKey(category.icon.getItem());
		for (ResourceLocation name : List.of(machine, type)) {
			if (!name.getNamespace().equals(type.getNamespace())) continue;
			for (String path : MODDED_SCREENS) {
				ResourceLocation candidate = ResourceLocation.fromNamespaceAndPath(name.getNamespace(), path.formatted(name.getPath()));
				if (resources.getResource(candidate).isPresent()) return Optional.of(candidate);
			}
		}
		return Optional.empty();
	}

	private static ResourceLocation screen(String name) {
		return ResourceLocation.withDefaultNamespace("textures/gui/container/" + name + ".png");
	}

	/**
	 * Measures a screen: its height down a column near the left edge and its width along a row near the bottom (away
	 * from the extra sprites screens keep beside or below themselves), each edge's thickness by scanning in to the
	 * background colour, and the most even patch of background along the top for the middle. Empty if it doesn't look
	 * like a framed screen.
	 */
	private static Optional<Frame> measure(ResourceLocation texture) {
		Optional<Resource> resource = CardTextures.resource(texture);
		if (resource.isEmpty()) return Optional.empty();
		try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
			int scale = Math.max(1, image.getWidth() / VANILLA_SIZE);
			int textureWidth = image.getWidth() / scale;
			int textureHeight = image.getHeight() / scale;
			Pixels pixels = (px, py) -> image.getPixelRGBA(px * scale, py * scale);

			int height = 0;
			while (height < textureHeight && opaque(pixels.at(10, height))) height++;
			if (height < MIN_SCREEN) return Optional.empty();
			int width = 0;
			while (width < textureWidth && opaque(pixels.at(width, height - 10))) width++;
			if (width < MIN_SCREEN) return Optional.empty();

			int background = pixels.at(width / 2, 10);
			int left = edge(pixels, background, 0, height / 2, 1, 0);
			int right = edge(pixels, background, width - 1, height / 2, -1, 0);
			int top = edge(pixels, background, width / 2, 0, 0, 1);
			int bottom = edge(pixels, background, width / 2, height - 1, 0, -1);
			if (Math.max(Math.max(left, right), Math.max(top, bottom)) > MAX_EDGE) return Optional.empty();
			// Each slice takes one row of background with it, so corners keep their inner shading.
			left++;
			right++;
			top++;
			bottom++;

			int patchY = top;
			int patchX = left;
			long best = Long.MAX_VALUE;
			for (int px = left; px + PATCH_WIDTH <= width - right; px += PATCH_WIDTH / 2) {
				long unevenness = unevenness(pixels, px, patchY);
				if (unevenness < best) {
					best = unevenness;
					patchX = px;
				}
			}
			return Optional.of(new Frame(texture, textureWidth, textureHeight, width, height, left, top, right, bottom, patchX, patchY));
		} catch (IOException | RuntimeException e) {
			return Optional.empty();
		}
	}

	private interface Pixels {
		/** ABGR, as NativeImage stores them. */
		int at(int x, int y);
	}

	private static boolean opaque(int abgr) {
		return (abgr >>> 24) != 0;
	}

	/** How many pixels in from (x, y), stepping by (dx, dy), before the background colour starts. */
	private static int edge(Pixels pixels, int background, int x, int y, int dx, int dy) {
		int count = 0;
		while (count <= MAX_EDGE && pixels.at(x + dx * count, y + dy * count) != background) count++;
		return count;
	}

	/** How much a patch's colours vary: icons drawn on the background stand out, plain background (even patterned) doesn't. */
	private static long unevenness(Pixels pixels, int x, int y) {
		long sum = 0;
		int first = pixels.at(x, y);
		for (int py = y; py < y + PATCH_HEIGHT; py++) {
			for (int px = x; px < x + PATCH_WIDTH; px++) {
				int pixel = pixels.at(px, py);
				for (int shift = 0; shift < 24; shift += 8) {
					sum += Math.abs((pixel >> shift & 0xFF) - (first >> shift & 0xFF));
				}
			}
		}
		return sum;
	}

	/** Part of the screen texture at its real size (in the texture's 256-wide coordinates). */
	private static void blit(GuiGraphics g, Frame frame, int x, int y, int u, int v, int width, int height) {
		if (width <= 0 || height <= 0) return;
		g.blit(CardTextures.texture(frame.texture()), x, y, u, v, width, height, frame.textureWidth(), frame.textureHeight());
	}

	/** Fills an area by repeating a piece of the screen texture. */
	private static void tile(GuiGraphics g, Frame frame, int x, int y, int width, int height, int u, int v, int pieceWidth, int pieceHeight) {
		if (pieceWidth <= 0 || pieceHeight <= 0) return;
		for (int dy = 0; dy < height; dy += pieceHeight) {
			for (int dx = 0; dx < width; dx += pieceWidth) {
				blit(g, frame, x + dx, y + dy, u, v, Math.min(pieceWidth, width - dx), Math.min(pieceHeight, height - dy));
			}
		}
	}
}
