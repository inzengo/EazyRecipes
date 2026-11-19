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

/** Bepaalt de contextkleur in de UI. "Snel" is geen hoofdingrediënt; dat volgt uit [RecipeEntity.totalMinutes]. */
enum class MainIngredient { VLEES, VIS, VEGA, VEGAN, ZOET }

@Entity(tableName = "recipe", indices = [Index("sourceUrl"), Index("updatedAt")])
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
    val mainIngredient: MainIngredient? = null,
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

@Entity(tableName = "tag", indices = [Index("nameKey", unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    /** Opgeslagen zoals eerst gezien; uniek ongeacht hoofdletters via [nameKey]. */
    val name: String,
    @ColumnInfo(name = "nameKey") val nameKey: String = name.lowercase(),
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

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
