package nl.eazyrecipes.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import nl.eazyrecipes.R
import nl.eazyrecipes.data.CategoryEntity
import nl.eazyrecipes.data.DefaultCategories
import nl.eazyrecipes.data.RecipeEntity

/**
 * Kleur uit de vaste palet. Elke [vivid] heeft ≥ 6:1 contrast met zwarte tekst (design-regel: zwart op fel),
 * [deep] is de donkere variant voor iconen/cijfers op wit of grijs. De eerste zes zijn de originele contextkleuren.
 */
data class PaletteColor(val key: String, val name: String, val vivid: Color, val deep: Color)

val CategoryPalette: List<PaletteColor> = listOf(
    PaletteColor("rood", "Rood", Color(0xFFFF5A4A), Color(0xFFC8261A)),
    PaletteColor("blauw", "Blauw", Color(0xFF4D9DFF), Color(0xFF1F5FCC)),
    PaletteColor("groen", "Groen", Color(0xFF3DD66F), Color(0xFF167A3B)),
    PaletteColor("mint", "Mint", Color(0xFF47E3BC), Color(0xFF0B7560)),
    PaletteColor("roze", "Roze", Color(0xFFFF8AD4), Color(0xFFB0237A)),
    PaletteColor("geel", "Geel", Color(0xFFFFC738), Color(0xFF8F5800)),
    PaletteColor("lavendel", "Lavendel", Color(0xFF9B8CFF), Color(0xFF5B45D6)),
    PaletteColor("oranje", "Oranje", Color(0xFFFF8A3D), Color(0xFFA84300)),
    PaletteColor("limoen", "Limoen", Color(0xFFB5E550), Color(0xFF4F6B00)),
    PaletteColor("aqua", "Aqua", Color(0xFF3FD0F0), Color(0xFF006F86)),
    PaletteColor("perzik", "Perzik", Color(0xFFFFA98A), Color(0xFFA3401F)),
    PaletteColor("paars", "Paars", Color(0xFFC08CFF), Color(0xFF6B2BB8)),
    PaletteColor("zand", "Zand", Color(0xFFE8C48A), Color(0xFF7A5418)),
    PaletteColor("koraal", "Koraal", Color(0xFFFF6F91), Color(0xFFB3123E)),
)

data class CategoryIcon(val key: String, @DrawableRes val res: Int)

/** Phosphor-iconen (MIT, fill) die bij eten passen; zie docs/licenses/Phosphor-MIT.txt. */
val CategoryIcons: List<CategoryIcon> = listOf(
    CategoryIcon("cow", R.drawable.ph_cow),
    CategoryIcon("fish", R.drawable.ph_fish),
    CategoryIcon("shrimp", R.drawable.ph_shrimp),
    CategoryIcon("leaf", R.drawable.ph_leaf),
    CategoryIcon("plant", R.drawable.ph_plant),
    CategoryIcon("carrot", R.drawable.ph_carrot),
    CategoryIcon("avocado", R.drawable.ph_avocado),
    CategoryIcon("pepper", R.drawable.ph_pepper),
    CategoryIcon("orange-slice", R.drawable.ph_orange_slice),
    CategoryIcon("egg", R.drawable.ph_egg),
    CategoryIcon("cheese", R.drawable.ph_cheese),
    CategoryIcon("bread", R.drawable.ph_bread),
    CategoryIcon("grains", R.drawable.ph_grains),
    CategoryIcon("bowl-food", R.drawable.ph_bowl_food),
    CategoryIcon("bowl-steam", R.drawable.ph_bowl_steam),
    CategoryIcon("cooking-pot", R.drawable.ph_cooking_pot),
    CategoryIcon("pizza", R.drawable.ph_pizza),
    CategoryIcon("hamburger", R.drawable.ph_hamburger),
    CategoryIcon("cake", R.drawable.ph_cake),
    CategoryIcon("cookie", R.drawable.ph_cookie),
    CategoryIcon("ice-cream", R.drawable.ph_ice_cream),
    CategoryIcon("popcorn", R.drawable.ph_popcorn),
    CategoryIcon("coffee", R.drawable.ph_coffee),
    CategoryIcon("wine", R.drawable.ph_wine),
    CategoryIcon("sun-horizon", R.drawable.ph_sun_horizon),
    CategoryIcon("moon-stars", R.drawable.ph_moon_stars),
    CategoryIcon("fire", R.drawable.ph_fire),
    CategoryIcon("snowflake", R.drawable.ph_snowflake),
    CategoryIcon("baby", R.drawable.ph_baby),
    CategoryIcon("chef-hat", R.drawable.ph_chef_hat),
)

fun paletteColor(key: String): PaletteColor = CategoryPalette.firstOrNull { it.key == key } ?: CategoryPalette.first()

@DrawableRes fun categoryIcon(key: String): Int = (CategoryIcons.firstOrNull { it.key == key } ?: CategoryIcons.first()).res

val CategoryEntity.context: ContextColor
    get() = paletteColor(color).let { p -> ContextColor(name, p.vivid, p.deep, categoryIcon(icon)) }

/** De categorieën van de gebruiker (op volgorde) met snelle opzoek per id. */
class CategorySet(val list: List<CategoryEntity>) {
    private val contexts = list.associate { it.id to it.context }
    operator fun get(id: String?): CategoryEntity? = id?.let { i -> list.firstOrNull { it.id == i } }

    /** Kleur/icoon van een categorie; onbekend of verwijderd = neutraal ([Eazy.Alles]). */
    fun context(id: String?): ContextColor = id?.let(contexts::get) ?: Eazy.Alles
}

/** Wordt in MainActivity gevuld uit de database; standaard (tests, previews) de vijf standaardsoorten. */
val LocalCategories = staticCompositionLocalOf { CategorySet(DefaultCategories.entities()) }

/** Kleur en icoon van dit recept. */
val RecipeEntity.context: ContextColor
    @Composable @ReadOnlyComposable get() = LocalCategories.current.context(categoryId)

/** Fel accent voor knoppen en vlakken; zonder soort de merkkleur (zwart vlak zou te zwaar zijn). */
val ContextColor.accent: Color get() = if (this == Eazy.Alles) Eazy.Brand else vivid
