package com.samurailemon.craftersalmanac;

import com.mojang.blaze3d.platform.InputConstants;
import com.samurailemon.craftersalmanac.config.ViewerConfig;
import com.samurailemon.craftersalmanac.gui.BookColors;
import com.samurailemon.craftersalmanac.gui.InventorySorter;
import com.samurailemon.craftersalmanac.gui.ScreenShift;
import com.samurailemon.craftersalmanac.gui.ScreenSupport;
import com.samurailemon.craftersalmanac.gui.SortButton;
import com.samurailemon.craftersalmanac.gui.ViewerButton;
import com.samurailemon.craftersalmanac.gui.ViewerPanel;
import com.samurailemon.craftersalmanac.gui.ViewerState;
import com.samurailemon.craftersalmanac.mixin.AbstractContainerScreenAccessor;
import com.samurailemon.craftersalmanac.mixin.ImageButtonAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.lang.ref.WeakReference;

public class CraftersAlmanac implements ClientModInitializer {
	private static final String CATEGORY = "key.categories.craftersalmanac";
	private static final KeyMapping SHOW_RECIPES = key("recipes", GLFW.GLFW_KEY_R);
	private static final KeyMapping SHOW_USES = key("uses", GLFW.GLFW_KEY_U);
	private static final KeyMapping BACK = key("back", GLFW.GLFW_KEY_BACKSPACE);
	private static final KeyMapping TOGGLE = key("toggle", GLFW.GLFW_KEY_O);
	/** Unbound by default: it's rarely needed and shouldn't clash with other mods' keys. */
	private static final KeyMapping DISABLE_ON_SCREEN = key("disable_on_screen", GLFW.GLFW_KEY_UNKNOWN);

	/** The last screen we attached to, to tell a newly opened screen from a re-init of the same one (resize). */
	private static WeakReference<AbstractContainerScreen<?>> lastScreen = new WeakReference<>(null);
	/** Screens get at most one automatic re-layout, in case a mod's widgets move whenever the container does. */
	private static boolean relaidOut;

	private static KeyMapping key(String name, int defaultKey) {
		return new KeyMapping("key.craftersalmanac." + name, InputConstants.Type.KEYSYM, defaultKey, CATEGORY);
	}

	@Override
	public void onInitializeClient() {
		KeyBindingHelper.registerKeyBinding(SHOW_RECIPES);
		KeyBindingHelper.registerKeyBinding(SHOW_USES);
		KeyBindingHelper.registerKeyBinding(BACK);
		KeyBindingHelper.registerKeyBinding(TOGGLE);
		KeyBindingHelper.registerKeyBinding(DISABLE_ON_SCREEN);
		// Resource packs may change the recipe book's page colour, so pick the text colour again.
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return ResourceLocation.fromNamespaceAndPath("craftersalmanac", "book_colors");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				BookColors.clear();
			}
		});

		// Fabric recreates per-screen events on every init (including resizes), so we re-attach each time.
		// Skip screens that replaced themselves during init: in creative, the survival inventory swaps itself for
		// the creative one, and attaching to it too would leave a hidden panel that steals our search typing.
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof AbstractContainerScreen<?> container && client.screen == screen) attach(container);
		});
	}

	private static void attach(AbstractContainerScreen<?> screen) {
		if (screen != lastScreen.get()) {
			lastScreen = new WeakReference<>(screen);
			relaidOut = false;
			ViewerState.reset();
		}
		// Registered even on disabled screens, so the key can switch the viewer back on.
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, scancode, modifiers) -> {
			if (!DISABLE_ON_SCREEN.matches(key, scancode)) return true;
			toggleDisabled(screen);
			return false;
		});
		if (ScreenSupport.isDisabled(screen)) return;

		// Measure before adding our own widgets. The first time a screen with extras is seen, lay it out again
		// so the panel makes room for them.
		if (ScreenSupport.measure(screen) && ViewerState.isShowing() && !relaidOut) {
			relaidOut = true;
			ViewerState.relayout();
		}

		ViewerConfig config = ViewerConfig.get();
		ImageButton vanillaButton = findVanillaRecipeButton(screen);
		boolean hideVanilla = vanillaButton != null && config.hideVanillaRecipeButton;
		if (hideVanilla) {
			// If the vanilla book was left open, close it the same way its button would, so the container slides back.
			if (screen instanceof RecipeUpdateListener listener && listener.getRecipeBookComponent().isVisible()) {
				vanillaButton.onPress();
			}
			vanillaButton.visible = false;
			vanillaButton.active = false;
		}
		if (config.showViewerButton) {
			Screens.getButtons(screen).add(new ViewerButton(screen, vanillaButton, hideVanilla));
		}

		// A sort button for the player's inventory (survival only: creative moves items its own way), or for
		// storage like chests, barrels and shulker boxes. Not machines, where sorting would mix up their slots.
		if (screen instanceof InventoryScreen) {
			Screens.getButtons(screen).add(new SortButton(screen, InventorySorter.Target.PLAYER));
		} else if (InventorySorter.isStorage(screen.getMenu())) {
			Screens.getButtons(screen).add(new SortButton(screen, InventorySorter.Target.CONTAINER));
		}

		ViewerPanel panel = new ViewerPanel(screen);
		Screens.getButtons(screen).add(panel);
		ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, delta) -> panel.renderTooltip(graphics, mouseX, mouseY));
		ScreenMouseEvents.allowMouseRelease(screen).register((s, mouseX, mouseY, button) -> {
			if (!ViewerState.consumeRelease()) return true;
			screen.setDragging(false);
			return false;
		});
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, scancode, modifiers) -> handleKey(screen, panel, key, scancode, modifiers));
	}

	/** Returns false to stop the screen from also handling the key. Typing in our search box is handled by KeyboardHandlerMixin. */
	private static boolean handleKey(AbstractContainerScreen<?> screen, ViewerPanel panel, int key, int scancode, int modifiers) {
		// Don't steal letters from the screen's own text boxes (anvil, creative search).
		if (screen.children().stream().anyMatch(child -> child instanceof EditBox box && box.isVisible() && box.isFocused())) {
			return true;
		}

		if (TOGGLE.matches(key, scancode)) {
			ViewerState.toggle();
			return false;
		}
		boolean uses = SHOW_USES.matches(key, scancode);
		if (uses || SHOW_RECIPES.matches(key, scancode)) {
			return !ViewerState.show(hoveredStack(screen, panel), uses);
		}
		if (BACK.matches(key, scancode) && (ViewerState.view != null || ViewerState.materials != null)) {
			ViewerState.back();
			return false;
		}
		return true;
	}

	private static void toggleDisabled(AbstractContainerScreen<?> screen) {
		boolean disabled = ScreenSupport.toggleDisabled(screen);
		Minecraft minecraft = Minecraft.getInstance();
		SystemToast.addOrUpdate(minecraft.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
				Component.translatable(disabled ? "craftersalmanac.screen_disabled" : "craftersalmanac.screen_enabled"),
				Component.literal(screen.getClass().getSimpleName()));
		ViewerState.relayout();
	}

	/** The green recipe book button in the inventory, crafting table and furnaces. */
	private static ImageButton findVanillaRecipeButton(AbstractContainerScreen<?> screen) {
		for (AbstractWidget widget : Screens.getButtons(screen)) {
			if (widget instanceof ImageButton button && ((ImageButtonAccessor) button).rbv$getSprites() == RecipeBookComponent.RECIPE_BUTTON_SPRITES) {
				return button;
			}
		}
		return null;
	}

	private static ItemStack hoveredStack(AbstractContainerScreen<?> screen, ViewerPanel panel) {
		Minecraft minecraft = Minecraft.getInstance();
		// The raw mouse position, moved back by the screen's shift to match the screen's own layout.
		double mouseX = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth()
				- ScreenShift.of(screen);
		double mouseY = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
		ItemStack stack = panel.stackAt(mouseX, mouseY);
		if (!stack.isEmpty()) return stack;

		Slot slot = ((AbstractContainerScreenAccessor) screen).rbv$getHoveredSlot();
		return slot != null && slot.hasItem() ? slot.getItem() : ItemStack.EMPTY;
	}
}
