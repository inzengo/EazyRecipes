package nl.eazyrecipes.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/*
 * Sync-klaar datamodel (zie PLAN.md):
 * - elke rij heeft createdAt/updatedAt (epoch-millis) en deletedAt (soft-delete);
 * - entiteiten hebben een UUID als id; koppeltabellen een samengestelde sleutel van twee UUID's.
 */

/**
 * Wat de importer raadt als hoofdingrediënt. Sinds schema v2 bepaalt [RecipeEntity.categoryId] de soort; dit is alleen
 * nog de uitkomst van de gok, die op een standaardcategorie wordt gezet (zie [DefaultCategories]).
 */
enum class MainIngredient { VLEES, VIS, VEGA, VEGAN, ZOET }

/**
 * Soort recept (vlees, vis, … of iets eigens als "Soep"): naam, kleur en icoon kies je zelf.
 * [color] en [icon] zijn sleutels uit de vaste palet- en iconenlijst (`ui/theme/CategoryStyle.kt`), zodat het design klopt.
 */
@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    val icon: String,
    /** Volgorde van de tegels in de bibliotheek. */
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "recipe", indices = [Index("sourceUrl"), Index("updatedAt"), Index("categoryId")])
data class RecipeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String? = null,
    val sourceUrl: String? = null,
    /** Host zonder `www.`, bv. `leukerecepten.nl`. Voor het bron-filter. */
    val sourceSite: String? = null,
    /** Originele afbeelding bij de bron; [imagePath] is de lokale kopie. */
    val imageUrl: String? = null,
    val imagePath: String? = null,
    val servings: Int? = null,
    val yieldText: String? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val totalMinutes: Int? = null,
    val category: String? = null,
    val cuisine: String? = null,
    /** v1-veld; sinds v2 niet meer gebruikt (vervangen door [categoryId]). Blijft staan: een kolom weghalen vergt in SQLite een hele tabel-herbouw. */
    val mainIngredient: MainIngredient? = null,
    /**
     * Hoofdsoort: bepaalt kleur en icoon. Een recept kan in meer categorieën zitten (paella: vis én vlees), zie
     * [RecipeCategoryEntity]; deze staat daar ook in. `null` = geen soort. Geen foreign key: categorieën zijn soft-delete.
     */
    val categoryId: String? = null,
    val notes: String? = null,
    val favorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "ingredient",
    foreignKeys = [ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recipeId")],
)
data class IngredientEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val position: Int,
    /** De regel zoals de bron hem geeft ("200 gr kip"). Hoeveelheid/eenheid/naam komen later. */
    val raw: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "step",
    foreignKeys = [ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recipeId")],
)
data class StepEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val position: Int,
    val sectionName: String? = null,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * Een label ("tag"): komt mee bij importeren (`keywords`, `recipeCategory`, …) of maak je zelf.
 * In de app heten ze labels; je kunt ze hernoemen, samenvoegen, een kleur geven en verbergen.
 */
@Entity(tableName = "tag", indices = [Index("nameKey", unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    /** Opgeslagen zoals eerst gezien; uniek ongeacht hoofdletters via [nameKey]. */
    val name: String,
    @ColumnInfo(name = "nameKey") val nameKey: String = name.lowercase(),
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    /** Sleutel uit de categoriepalet, of `null` = neutraal grijs. (v3) */
    val color: String? = null,
    /** Verborgen labels staan niet op het receptscherm en in het filter, maar tellen wel mee bij zoeken. (v3) */
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
    /** Samengevoegd in dit label: een import met deze oude naam komt dan bij het nieuwe terecht. (v3) */
    val mergedInto: String? = null,
)

/** Recept ↔ categorie (een recept kan in meerdere zitten). Sinds schema v4. */
@Entity(
    tableName = "recipe_category",
    primaryKeys = ["recipeId", "categoryId"],
    foreignKeys = [ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("categoryId")],
)
data class RecipeCategoryEntity(
    val recipeId: String,
    val categoryId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** Label met het aantal (niet-verwijderde) recepten dat het heeft. */
data class LabelSummary(
    @Embedded val tag: TagEntity,
    val recipes: Int,
) {
    val id: String get() = tag.id
    val name: String get() = tag.name
}

@Entity(
    tableName = "recipe_tag",
    primaryKeys = ["recipeId", "tagId"],
    foreignKeys = [
        ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class RecipeTagEntity(
    val recipeId: String,
    val tagId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "collection")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "collection_recipe",
    primaryKeys = ["collectionId", "recipeId"],
    foreignKeys = [
        ForeignKey(CollectionEntity::class, ["id"], ["collectionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("recipeId")],
)
data class CollectionRecipeEntity(
    val collectionId: String,
    val recipeId: String,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** Een recept op de boodschappenlijst, met de porties waarvoor je inkoopt (`null` = zoals in het recept). Sinds v5. */
@Entity(
    tableName = "shopping_recipe",
    foreignKeys = [ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = ForeignKey.CASCADE)],
)
data class ShoppingRecipeEntity(
    @PrimaryKey val recipeId: String,
    val servings: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** Iets wat je zelf op de lijst zet ("wc-papier"). Sinds v5. */
@Entity(tableName = "shopping_item")
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val text: String,
    val checked: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** Een afgevinkte regel uit de recepten: ingrediënt + hoeveelheid (zie `ShoppingLine.checkKey`). Sinds v5. */
@Entity(tableName = "shopping_check")
data class ShoppingCheckEntity(
    @PrimaryKey val key: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * Zoekindex over titel, ingrediënten en tags. Wordt door [RecipeRepository] bijgewerkt bij elke wijziging.
 * `unicode61` haalt accenten weg, dus "creme" vindt "crème".
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["recipeId"])
@Entity(tableName = "recipe_fts")
data class RecipeFtsEntity(
    val recipeId: String,
    val title: String,
    val ingredients: String,
    val tags: String,
)

/** Recept met alles erbij, voor het detailscherm. Lijsten zijn nog niet gesorteerd; zie [RecipeRepository.get]. */
data class RecipeWithDetails(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<IngredientEntity>,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val steps: List<StepEntity>,
)
