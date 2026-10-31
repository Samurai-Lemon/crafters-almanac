# Crafter's Almanac

A client-side Fabric mod for Minecraft 1.21.1 that works like JEI/REI: browse every item and look up its
recipes and uses. The panel is drawn with the vanilla recipe book's own textures, so it looks like part of the game.

## Using it

Open any inventory or container. The panel appears on the left, where the vanilla recipe book goes, and the
inventory moves over so both are centered. Only one of the two is open at a time: opening ours closes the vanilla book.
The book and inventory together need 357 GUI pixels of width (376 in creative); on screens narrower than that the
panel doesn't open rather than cover the inventory.

| Action | How |
| --- | --- |
| Search items | Click the search box and type. `@modid` filters by mod and `#tag` by item tag, e.g. `@minecraft stairs` or `#logs`. While you search, items in the open inventory that don't match fade out |
| Recipes for an item | Left-click it in the panel, or hover any item and press **R** |
| Uses of an item | Right-click it in the panel, or hover any item and press **U** |
| Raw materials | Click the plain button on a crafting recipe's card, left of the **+** (or of the star where there's no **+**), called "Materials Calculator", to see everything it takes, down to raw materials. **−**/**+** change how many (shift for 10) |
| Favorite a recipe | Click the star in the corner of its card. The star tab (second from the top) lists your favorites |
| Only show what you can make | The filter button next to the search box, like the vanilla book's "Showing Craftable" |
| Fill the crafting grid | Click the **+** on a recipe's card (shift-click for as many as you can). Works in the inventory, crafting table and furnaces |
| Switch recipe type | Click the tabs on the left (crafting, furnace, blast furnace, smoker, campfire, stonecutter, smithing, brewing, fuel, and modded machines) |
| Back | **Backspace** goes to the previous item; the compass tab goes back to the item list |
| Turn pages | Arrows, or the mouse wheel |
| Clear search | Right-click the search box |
| Sort your inventory or a chest | The small button above the top-right of your inventory rows (survival inventory) or of a storage grid (chests, barrels, shulker boxes, hoppers, dispensers). The inventory one leaves the hotbar alone; choose the order in the settings |
| Hide / show panel | **O**, or click the almanac button (it uses the vanilla recipe book art) |
| Settings | Right-click the almanac button, or **Config** in Mod Menu |
| Turn the almanac off on a screen it doesn't suit | **Show on This Screen** in the settings (open them from that screen), or bind **Turn Almanac Off/On for This Screen** in Controls |

You can rebind every key under Controls → Key Binds → Crafter's Almanac.

## Settings

Saved to `config/craftersalmanac.json`.

- **Almanac Button**: the button that shows/hides the almanac. It sits next to the green
  recipe book button (in screens without one, above the container's top-right corner).
- **Hide Vanilla Recipe Book**: hides the green recipe book button; the almanac button takes its spot.
- **Recipe IDs in Tooltips**: shows `minecraft:oak_planks`-style IDs when hovering a recipe's result.
- **Highlight Search in Inventory**: fades out inventory items that don't match the search.
- **Sort Inventory**: how the sort buttons order items: Creative Menu Order, Name, Mod, Amount, or Off (hides the buttons).

## Building

You need JDK 21 (for example `brew install --cask temurin@21`).

```sh
./gradlew build      # mod jar ends up in build/libs/
./gradlew runClient  # launch a dev Minecraft with the mod loaded
```

To play with it, put `build/libs/crafters-almanac-<version>.jar` and
[Fabric API](https://modrinth.com/mod/fabric-api) into your `.minecraft/mods` folder.

## How it works

- `recipe/RecipeIndex` reads the recipes the server syncs to the client (1.21.1 still sends all of them)
  and turns each one into a `Display`: slots with positions plus a few extras like cook time.
- `recipe/ItemList` uses the creative search tab, so it includes variants such as potions and enchanted books, plus any
  modded item that isn't in a creative tab.
- `gui/ScreenShift` makes room for the panel by drawing the whole container screen shifted right (with the mouse
  shifted back), rather than changing the screen's position. Many screens, vanilla's chest included, work out their own
  position, so moving it would split their background from their slots; shifting the whole screen keeps every screen,
  vanilla or modded, in one piece.
- `gui/ViewerPanel` is a widget that gets added to every container screen. It uses the same texture offsets as
  vanilla's `RecipeBookComponent` (book background, tabs, page arrows, slot buttons).
- `gui/RecipeRenderer` draws each recipe on a card (`recipe/Card`). Crafting, cooking and brewing use the art cards
  `card_crafting`, `card_furnace` and `card_brewing` (176x78, slots drawn in at vanilla's own screen positions), drawn
  128 wide by keeping their borders and the middle around the art. The animations use lit copies of the cards' own
  shapes (`card_furnace_flame`, `card_furnace_arrow`, `card_brewing_arrow`, `card_brewing_bubbles`) so they line up
  exactly; remake them if you redraw those parts of a card. Other recipe types get a plain card built from the furnace card's
  border, slots and arrow. The star is `favorite.png`. All are in `assets/craftersalmanac/textures/gui/sprites/`.
  The mod icon is `assets/craftersalmanac/icon.png` (128x128).
- `gui/BookColors` picks the heading text colour from the loaded book texture (dark page: light text, and the reverse).
- `recipe/ExtraRecipes` adds brewing (read from the game's brewing list through `PotionBrewingAccessor`, so modded
  potions are included) and furnace fuels as their own tabs. They aren't real recipes, so they can't be filled into a grid.
- `gui/Materials` is the raw materials calculator. It only expands crafting-table recipes (smelted items count as
  materials), skips packing recipes like iron block to ingots, and reuses leftovers from batch crafting.
- `gui/Craftable` works out which items you can make from your inventory for the filter button. It counts every
  recipe type wherever you are (so a stonecutter recipe counts even at a crafting table) and ignores fuel.
- `gui/Favorites` stores favorite recipe IDs in the config file, so they survive restarts and work across worlds.
- `gui/InventorySorter` sorts by clicking slots like a player would (so it works on any server): it combines
  stacks of the same item, then orders them with swaps.
- `gui/RecipeTransfer` fills the grid by sending the same packet as clicking a recipe in the vanilla book, so it works
  on any server. Like vanilla, the server only fills recipes the player has unlocked; the **+** is greyed out otherwise.

Modded screens: the panel appears on any screen built on vanilla's container screen, which covers most machines.
`gui/ScreenSupport` measures how far a screen's slots and buttons reach outside its container box (side tabs, upgrade
or accessory slots) and leaves room for them. The first time a screen like that opens, it lays itself out once more to
make room. Screens that still don't work can be switched off one by one; the list is saved as `disabledScreens`.

Modded recipes show up without any extra code. Each recipe type gets its own tab (`recipe/Category`), named from the
mod's translation if it has one, otherwise from the type ID, e.g. "Crushing (Create)". `DisplayFactory` picks the
layout from the recipe's class, so a mod's cooking or single-item recipes look like vanilla's, and anything else is
drawn as a grid of inputs, an arrow and the output. Limits: only item ingredients and one output are shown (no
fluids, energy, chances or extra outputs), and recipes with more than 9 inputs are skipped.
