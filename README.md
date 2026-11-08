# Crafter's Almanac

A client-side Fabric mod for Minecraft 1.21.1 that works like JEI/REI: browse every item and look up its
recipes and uses. The panel is drawn with the vanilla recipe book's own textures, so it looks like part of the game.

## Using it

Open your inventory or anything you make things in: a crafting table, furnace, smithing table, brewing stand, or a
modded machine. In storage (chests, barrels, shulker boxes, hoppers, dispensers, mods' chests and backpacks) the
almanac stays out of the way: press **R** or **U** over an item to open it there, and it closes with the chest.

The panel appears on the left, where the vanilla recipe book goes, and the inventory moves over so both are centered. Only one of the two is open at a time: opening ours closes the vanilla book.
The book and inventory together need 357 GUI pixels of width (376 in creative); on screens narrower than that the
panel doesn't open rather than cover the inventory.

| Action | How |
| --- | --- |
| Search items | Click the search box and type. `@modid` filters by mod and `#tag` by item tag, e.g. `@minecraft stairs` or `#logs`. While you search, items in the open inventory that don't match fade out |
| Recipes for an item | Left-click it in the panel, or hover any item and press **R** |
| Uses of an item | Right-click it in the panel, or hover any item and press **U** |
| Raw materials | Click the plain button on a crafting recipe's card, left of the **+** (or of the star where there's no **+**), called "Materials Calculator", to see everything it takes, down to raw materials. **−**/**+** change how many (shift for 10) |
| Favorite a recipe | Click the star in the corner of its card. The star tab (second from the top) lists your favorites. Each world and server has its own |
| Only show what you can make | The filter button next to the search box, like the vanilla book's "Showing Craftable" |
| Fill the crafting grid | Click the **+** on a recipe's card (shift-click for as many as you can). Works in the inventory, crafting table and furnaces |
| Switch recipe type | Click the tabs on the left (crafting, furnace, blast furnace, smoker, campfire, stonecutter, smithing, brewing, fuel, and modded machines) |
| Back | **Backspace** goes to the previous item; the compass tab goes back to the item list |
| Turn pages | Arrows, or the mouse wheel |
| Clear search | Right-click the search box |
| Sort your inventory or a chest | The small button above the top-right of your inventory rows (survival inventory) or of a storage grid (chests, barrels, shulker boxes, hoppers, dispensers). The inventory one leaves the hotbar alone; choose the order in the settings |
| Quick stack into a chest | The button left of a storage grid's sort button moves everything from your inventory that the chest (barrel, shulker box...) already has into it. Your hotbar stays as it is |
| Item details | Hold **Alt** over an item: what food restores, mining speed, durability, how armor compares with what you're wearing, compost chance, fuel time and stack size |
| Quick recipe | Hold **Shift** over an item to see its recipe beside the tooltip, with the station's name (Smithing Table, Cooking Pot...) along the top. Items with more than one recipe go through them every 2 seconds, with dots under the card showing how many there are |
| Cheat mode | With cheats allowed (the world's **Allow Cheats**, or operator on a server), a button shows in the bottom-right corner of the item list (green while on). Turn it on and click an item to get a stack of it (right-click for one), like the creative menu, without the "Gave ..." chat lines. **R** and **U** still show recipes and uses |
| See armor trims | Smithing trim recipes cycle through every armor piece and trim material, with an armor stand wearing the result |
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
- **Quick Stack Button**: the quick stack button in chests and other storage.
- **Item Details (Hold Alt)** and **Recipe Preview (Hold Shift)**: the extra tooltip details and the recipe preview,
  e.g. to turn off if another mod (like AppleSkin) already shows the same.
- **Match Texture Pack**: recipe cards use your resource pack's textures, each framed like its station's screen. Turn
  it off if a pack makes the cards look wrong; they then use the default textures, as if no pack were loaded.
- **Developer Mode**: for mod and resource pack makers. Recipe tabs show their recipe type, and Ctrl-clicking one writes
  a starter station file (see [COMPATIBILITY.md](COMPATIBILITY.md)).

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
- `gui/RecipeRenderer` draws each recipe on a card (`recipe/Card`), using only the game's own textures, so resource
  packs restyle the almanac along with the game. Every card is framed with its station's own screen frame
  (`gui/CardFrames`): the screen texture is measured when loaded (its size, each edge's thickness, an even patch of its
  background) and cut nine-slice to fit the card, so cards match packs that colour each screen differently. Modded
  machines' screens are found by name (e.g. `farmersdelight:textures/gui/cooking_pot.png`); stations without one get
  the recipe book's panel (`recipe_book/overlay_recipe`). With Match Texture Pack off, `gui/CardTextures` reads the
  default textures from under every resource pack instead and draws those. Crafting, cooking and brewing cards show the middle
  of the station's real screen texture (`crafting_table.png`, `furnace.png`, `blast_furnace.png`, `smoker.png`,
  `brewing_stand.png`) inside the frame,
  128 wide, with the recipe's items at the screens' own slot positions, animated with the screens' own sprites
  (`container/furnace/lit_progress`, `burn_progress`, `container/brewing_stand/brew_progress`, `bubbles`). Other
  recipe types get the frame alone, with vanilla's `container/slot` sprite and the furnace screen's result slot and
  arrow. Cheat mode's button is the furnace recipe book's filter button. The only art of our own is the star,
  `assets/craftersalmanac/textures/gui/sprites/favorite.png`, and the mod icon, `assets/craftersalmanac/icon.png`
  (128x128).
- `gui/BookColors` picks the heading text colour from the loaded book texture (dark page: light text, and the reverse).
- `recipe/ExtraRecipes` adds brewing (read from the game's brewing list through `PotionBrewingAccessor`, so modded
  potions are included) and furnace fuels as their own tabs. They aren't real recipes, so they can't be filled into a grid.
- `gui/Materials` is the raw materials calculator. It only expands crafting-table recipes (smelted items count as
  materials), skips packing recipes like iron block to ingots, and reuses leftovers from batch crafting.
- `gui/Craftable` works out which items you can make from your inventory for the filter button. It counts every
  recipe type wherever you are (so a stonecutter recipe counts even at a crafting table) and ignores fuel.
- `gui/Favorites` stores favorite recipe IDs in the config file for each world (by save folder) and server (by address),
  so they survive restarts and a new world starts with none.
- `gui/InventorySorter` sorts by clicking slots like a player would (so it works on any server): it combines
  stacks of the same item, then orders them with swaps.
- `gui/Cheats` gives items through `/give`, so cheat mode works on any server where you may use it.
- `gui/RecipeTransfer` fills the grid by sending the same packet as clicking a recipe in the vanilla book, so it works
  on any server. Like vanilla, the server only fills recipes the player has unlocked; the **+** is greyed out otherwise.

Modded screens: the panel appears on any screen built on vanilla's container screen, which covers most machines. A
modded screen with 27 or more slots of its own is taken to be storage and left alone (`ScreenSupport.isCraftingScreen`).
`gui/ScreenSupport` measures how far a screen's slots and buttons reach outside its container box (side tabs, upgrade
or accessory slots) and leaves room for them. The first time a screen like that opens, it lays itself out once more to
make room. Screens that still don't work can be switched off one by one; the list is saved as `disabledScreens`.

Modded recipes show up without any extra code. Each recipe type gets its own tab (`recipe/Category`), named from the
mod's translation if it has one, otherwise from the type ID, e.g. "Crushing (Create)", with the machine as its icon
where it can be found. `DisplayFactory` picks the layout from the recipe's class, so a mod's cooking or single-item
recipes look like vanilla's. Anything else is drawn as a grid of inputs, an arrow and the outputs.

`recipe/RecipeData` reads each modded recipe the way its mod saves it, so it finds what the recipe holds beyond its
ingredients: several outputs and their chances (shown as "50%" on the slot), time and XP (along the bottom of the
card), a tool or container it needs, and fluids (shown as their bucket). That's also what lets machines that keep
their outputs in a list of their own, like Oritech's, show up at all. Limits: energy isn't shown, outputs past three
rows aren't drawn (but are still searched), and recipes with more than 9 inputs are skipped.

A machine can also be drawn on its own GUI art, have its own field names read, or be hidden, with a station file
(`recipe/Station`); the Farmer's Delight Cooking Pot's is included. Resource packs can draw the card frames and set
the text colours themselves. All of this is plain resource files, described for mod and pack makers in
[COMPATIBILITY.md](COMPATIBILITY.md), along with Developer Mode, which writes a starter station file for any recipe tab.
