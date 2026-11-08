package com.samurailemon.craftersalmanac.config;

import com.google.gson.Gson;
import com.samurailemon.craftersalmanac.gui.InventorySorter;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Settings saved to config/craftersalmanac.json. */
public final class ViewerConfig {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("craftersalmanac.json");
	private static ViewerConfig instance;

	public boolean showViewerButton = true;
	public boolean hideVanillaRecipeButton = false;
	public boolean showRecipeIds = true;
	/** While searching, fade out inventory items that don't match. */
	public boolean highlightSearch = true;
	/** Remembers whether the panel was open, so it stays the way you left it. */
	public boolean panelOpen = true;
	/** The item list's filter button: only list items you can make from what you're carrying. */
	public boolean craftableOnly = false;
	/** Clicking items in the list gives them (for players allowed to use commands). */
	public boolean cheatMode = false;
	/** Favorite recipe IDs, oldest first, for each world (see Favorites.worldKey). */
	public Map<String, List<String>> worldFavorites = new HashMap<>();
	/** How the inventory sort button orders items; Off hides the button. */
	public InventorySorter.Mode sortMode = InventorySorter.Mode.CREATIVE;
	/** The quick stack button in chests and other storage. */
	public boolean quickStack = true;
	/** Food, tool, armor and other details in tooltips while Alt is held. */
	public boolean tooltipDetails = true;
	/** An item's recipes beside its tooltip while Shift is held. */
	public boolean recipePreview = true;
	/** Frame cards with their station's own screen (see CardFrames); off uses the recipe book's panel for all. */
	public boolean matchStationScreens = true;
	/** Class names of screens the viewer is switched off on. */
	public List<String> disabledScreens = new ArrayList<>();

	public static ViewerConfig get() {
		if (instance == null) instance = load();
		return instance;
	}

	private static ViewerConfig load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				ViewerConfig config = GSON.fromJson(reader, ViewerConfig.class);
				if (config != null) {
					if (config.worldFavorites == null) config.worldFavorites = new HashMap<>();
					if (config.disabledScreens == null) config.disabledScreens = new ArrayList<>();
					// Missing, or a mode name this version doesn't know.
					if (config.sortMode == null) config.sortMode = InventorySorter.Mode.CREATIVE;
					return config;
				}
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("Couldn't read {}, using defaults", FILE, e);
			}
		}
		return new ViewerConfig();
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(get(), writer);
			}
		} catch (IOException e) {
			LOGGER.warn("Couldn't save {}", FILE, e);
		}
	}
}
