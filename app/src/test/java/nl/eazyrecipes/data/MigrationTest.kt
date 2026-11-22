package nl.eazyrecipes.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * De echte database op de telefoon is v1. Deze test bouwt een v1-database precies volgens het bewaarde schema
 * (`app/schemas/.../1.json`), vult hem, en opent hem daarna met de huidige app (v2 + migratie). Room controleert
 * dan zelf of het resultaat exact overeenkomt met het v2-schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val name = "migratie-test.db"

    private fun createV1() {
        val schema = Json.parseToJsonElement(File("schemas/nl.eazyrecipes.data.EazyDatabase/1.json").readText()).jsonObject["database"]!!.jsonObject
        context.deleteDatabase(name)
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null)
        schema["entities"]!!.jsonArray.forEach { e ->
            val table = e.jsonObject["tableName"]!!.jsonPrimitive.content
            db.execSQL(e.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e.jsonObject["indices"]?.jsonArray?.forEach { i ->
                db.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        schema["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }

        fun recipe(id: String, title: String, main: String?) = ContentValues().apply {
            put("id", id); put("title", title); put("mainIngredient", main); put("favorite", 0); put("createdAt", 1L); put("updatedAt", 1L)
        }
        db.insert("recipe", null, recipe("r1", "Kip kerrie", "VLEES"))
        db.insert("recipe", null, recipe("r2", "Linzensoep", "VEGAN"))
        db.insert("recipe", null, recipe("r3", "Iets zonder soort", null))
        db.insert("recipe", null, recipe("r4", "Taart", "ZOET"))
        db.insert("recipe", null, recipe("r5", "Oude zalm", "VIS").apply { put("deletedAt", 2L) })
        db.insert("ingredient", null, ContentValues().apply {
            put("id", "i1"); put("recipeId", "r1"); put("position", 0); put("raw", "200 gr kipfilet"); put("createdAt", 1L); put("updatedAt", 1L)
        })
        db.insert("tag", null, ContentValues().apply {
            put("id", "t1"); put("name", "Makkelijk"); put("nameKey", "makkelijk"); put("createdAt", 1L); put("updatedAt", 1L)
        })
        db.insert("recipe_tag", null, ContentValues().apply {
            put("recipeId", "r1"); put("tagId", "t1"); put("createdAt", 1L); put("updatedAt", 1L)
        })
        db.execSQL("INSERT INTO recipe_fts (recipeId, title, ingredients, tags) VALUES ('r1', 'Kip kerrie', '200 gr kipfilet', '')")
        db.version = 1
        db.close()
    }

    @Test
    fun `v1 naar v3 - standaardcategorieen erbij, recepten houden soort en labels`() = runBlocking {
        createV1()
        val db = Room.databaseBuilder(context, EazyDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = RecipeRepository(db)
            assertEquals(listOf("vlees", "vis", "vega", "vegan", "zoet"), repo.observeCategories().first().map { it.id })
            val byId = repo.observeLibrary().first().associateBy { it.id }
            assertEquals("vlees", byId.getValue("r1").categoryId)
            assertEquals("vegan", byId.getValue("r2").categoryId)
            assertNull(byId.getValue("r3").categoryId)
            assertEquals("zoet", byId.getValue("r4").categoryId)
            assertEquals("verwijderde recepten blijven weg", null, byId["r5"])
            db.query("SELECT COUNT(*) FROM category", null).use { it.moveToFirst(); assertEquals(5, it.getInt(0)) }
            // Labels (v3): bestaande tag blijft, zichtbaar en zonder kleur.
            with(repo.observeLabels().first().single()) { assertEquals("Makkelijk", name); assertEquals(1, recipes); assertNull(tag.color); assertEquals(false, tag.hidden) }
            // De rest is intact: ingrediënten en zoekindex.
            assertEquals(listOf("200 gr kipfilet"), repo.get("r1")!!.ingredients.map { it.raw })
            assertEquals(listOf("r1"), repo.search("kipf").map { it.id })
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
