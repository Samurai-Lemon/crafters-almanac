# Making your mod or resource pack work with Crafter's Almanac

Crafter's Almanac shows every recipe the game knows about. Most mods and resource packs work with it as they are. This
guide covers what you can add when you want more control. Everything here is plain resource files: no code, nothing to
depend on, and nothing breaks if the almanac isn't installed.

- [For resource pack makers](#for-resource-pack-makers)
- [For mod makers](#for-mod-makers)
- [Station files](#station-files)
- [Developer Mode](#developer-mode)

## For resource pack makers

The almanac draws its recipe cards from the game's own textures, so your pack restyles it automatically:

- The crafting, furnace, blast furnace, smoker and brewing cards show the middle of those screens
  (`textures/gui/container/crafting_table.png`, `furnace.png`, ...), with their own progress sprites
  (`container/furnace/burn_progress`, `container/brewing_stand/bubbles`, ...).
- Each card's frame is cut from its station's screen texture. The almanac measures the screen when it loads (its size,
  each edge's thickness, an even patch of background), so cards match packs that colour every screen differently.
- Plain cards use `container/slot` and the furnace screen's result slot and arrow. Cards without a screen of their own
  use `recipe_book/overlay_recipe`.
- The cheat mode button is the furnace recipe book's filter button (`recipe_book/furnace_filter_*`).
- The favorite star is the almanac's own: `assets/craftersalmanac/textures/gui/sprites/favorite.png` (9x9).

Players can turn off **Match Texture Pack** in the almanac's settings to get the default textures on the cards instead.

### Drawing the card frames yourself

If measuring your screens doesn't give the look you want (very rounded or decorated frames), add a frame sprite and the
almanac uses it instead:

| Sprite | Used for |
| --- | --- |
| `assets/craftersalmanac/textures/gui/sprites/card/frame.png` | every card |
| `assets/craftersalmanac/textures/gui/sprites/card/frame/<namespace>/<type>.png` | one recipe type, e.g. `card/frame/minecraft/stonecutting.png` or `card/frame/farmersdelight/cooking.png` |

Brewing and fuel aren't recipe types; their frames are `card/frame/craftersalmanac/brewing.png` and
`card/frame/craftersalmanac/fuel.png`.

Cards come in many sizes, so give the sprite a nine-slice `.mcmeta` next to it, like vanilla's GUI sprites:

```json
{
	"gui": {
		"scaling": {
			"type": "nine_slice",
			"width": 32,
			"height": 32,
			"border": 4
		}
	}
}
```

The crafting, furnace and brewing art is drawn 4 pixels inside the frame, so keep your border at 4 or less.

### Text colours

If your textures make the almanac's text hard to read, set the colours in `assets/craftersalmanac/theme.json`. Every
entry is optional:

```json
{
	"card_text": "#404040",
	"card_subtext": "#707070",
	"chance_text": "#FFAA00",
	"heading_text": "#404040"
}
```

| Key | Used for |
| --- | --- |
| `card_text` | times, XP and other text on cards, names on favorites rows |
| `card_subtext` | the station's name under a favorite |
| `chance_text` | the "50%" on outputs a recipe only sometimes gives |
| `heading_text` | headings and messages on the book's page. Without it, the almanac picks dark or light text from your recipe book texture |

The card colours apply while Match Texture Pack is on; with it off, cards use the default colours along with the default
textures.

## For mod makers

Your recipes show up in the almanac without anything from you. Each recipe type gets its own tab, named from your
translations (`recipe_type.<mod>.<type>` or `emi.category.<mod>.<type>`) and showing your machine as its icon (the
recipe's toast symbol, or an item named `<type>` or `<type>_block`).

The almanac reads each recipe through its serializer's codec, the same way the game saves it, and looks for the usual
field names:

| What | Field names it looks for |
| --- | --- |
| Outputs (several allowed, with `"chance"`) | `result`, `results`, `output`, `outputs`. An output can be an item stack, or `{"item": stack, "chance": 0.5}` |
| Time, in ticks | `cookingtime`, `cooking_time`, `cookTime`, `time`, `processingtime`, `processing_time`, `processingTime`, `duration` |
| XP | `experience`, `xp` |
| Things used besides the ingredients | `tool`, `container` (an ingredient or an item stack) |
| Fluids, shown as their bucket | `fluidInput`, `fluid_input`, `inputFluid`, `input_fluid`; `fluidOutputs`, `fluidOutput`, `fluid_outputs`, `fluid_output`, `outputFluid`, `output_fluid` |

So recipes whose `getResultItem` is empty still show up, as long as their outputs are in one of those fields.

If your recipes use other names, want to be drawn on your machine's own art, or shouldn't be listed at all, add a
station file.

## Station files

A station file describes one recipe type. Put it in your mod's resources (or a resource pack) at:

```
assets/<any namespace>/craftersalmanac/stations/<mod id>/<recipe type>.json
```

For the recipe type `farmersdelight:cooking` that's `.../stations/farmersdelight/cooking.json`. A resource pack replaces
a mod's file by using the same path, and a file in `config/craftersalmanac/stations/<mod id>/<recipe type>.json` replaces
both (handy while you're working on one: press F3+T to reload).

Every field is optional. This is the one the almanac ships for Farmer's Delight's Cooking Pot:

```json
{
	"icon": "farmersdelight:cooking_pot",
	"texture": "farmersdelight:textures/gui/jei/cooking_pot.png",
	"texture_size": [256, 256],
	"region": [0, 0, 116, 56],
	"inputs": [[1, 1], [19, 1], [37, 1], [1, 19], [19, 19], [37, 19]],
	"output": [95, 10],
	"slots": {
		"container": [63, 39]
	},
	"animations": [
		{ "texture": "farmersdelight:textures/gui/cooking_pot.png", "uv": [176, 15], "size": [24, 17], "at": [60, 9], "fill": "right" },
		{ "texture": "farmersdelight:textures/gui/cooking_pot.png", "uv": [176, 0], "size": [17, 15], "at": [18, 39] }
	]
}
```

### Drawing recipes on your machine's art

| Field | Meaning |
| --- | --- |
| `texture` | The texture holding the art, e.g. your JEI or REI background. |
| `texture_size` | Its size, `[width, height]`. Default `[256, 256]`. |
| `region` | The part to use: `[x, y, width, height]`. At most 116 wide so the card fits the book. |
| `inputs` | Where each ingredient goes, in the order of `getIngredients()` (empty ingredients skipped). Positions are where the 16x16 item is drawn, from the region's top-left. Recipes with more ingredients than positions get the generic card. |
| `output` | Where the (first) output goes. |
| `outputs` | Where further outputs go, in order. Outputs without a position aren't drawn but can still be searched. |
| `slots` | Other recipe fields to show, by field name: `{"container": [63, 39]}`. A field can hold an ingredient or an item stack. |
| `info` | Where the time and XP text goes, `[x, y]`. Default: along the bottom of the card. |
| `animations` | Parts of your art drawn over the region (see below). |

The positions are the same numbers you'd give JEI's `addSlot`, so you can copy them from your JEI plugin. The almanac
frames the card and adds the favorite and fill buttons itself.

Each animation draws `size` pixels from `uv` in its `texture` (with an optional `texture_size`, default the station's)
at `at`. `fill` makes it fill over the recipe's time (10 seconds if the recipe has none): `right` (left to right),
`left`, `up` (bottom to top) or `down`. Leave it out, or use `none`, to always show the piece in full, like a lit
heat indicator.

### Other fields

| Field | Meaning |
| --- | --- |
| `fields` | Your recipe's own field names, tried before the usual ones: `{"time": ["processing_ticks"], "outputs": ["products"]}`. The keys are `outputs`, `time`, `xp`, `extra_inputs`, `fluid_inputs` and `fluid_outputs`. |
| `icon` | The item shown on the recipe type's tab, e.g. `"mymod:crusher"`. |
| `frame` | The screen texture to frame plain cards with, if the almanac can't find yours by name (it tries `textures/gui/container/<name>.png`, `textures/gui/<name>.png` and the same with `_gui`, for your machine's and the recipe type's names). |
| `hidden` | `true` to leave the recipe type out of the almanac, e.g. internal or duplicate types. |

A file can use any of these on its own; a file with only `fields` or `hidden` keeps the generic card.

## Developer Mode

Turn on **Developer Mode** in the almanac's settings. Recipe tabs then show their recipe type in their tooltip, and
Ctrl-clicking a tab writes a starter station file for it to `config/craftersalmanac/stations/<mod id>/<recipe type>.json`,
filled in with what the almanac found:

- the icon and the screen texture it frames the cards with,
- where the generic card puts the ingredients and outputs,
- every field your recipe has (`_recipe_fields`), to fill `fields` from.

Point `texture` and `region` at your art, move the positions to your slots, and press F3+T to see the result. Fields
starting with `_` are ignored. When it looks right, copy the file into your mod or pack.
