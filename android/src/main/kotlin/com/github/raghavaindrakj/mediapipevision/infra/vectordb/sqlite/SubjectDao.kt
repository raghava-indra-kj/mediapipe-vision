package com.github.raghavaindrakj.mediapipevision.infra.vectordb.sqlite

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.github.raghavaindrakj.mediapipevision.domain.vectordb.Subject

/** DAO for the subjects table. */
internal class SubjectDao(private val db: SQLiteDatabase) {

    /** Returns true if a subject with the given ID exists. */
    fun exists(subjectId: String): Boolean {
        val cursor = db.rawQuery(
            "SELECT 1 FROM ${SubjectsTable.NAME} WHERE ${SubjectsTable.COL_SUBJECT_ID} = ? LIMIT 1", arrayOf(subjectId)
        )
        return cursor.use { it.moveToFirst() }
    }

    /** Inserts a new subject and returns it. */
    fun insert(subjectId: String, name: String, createdAt: Long): Subject {
        val values = ContentValues().apply {
            put(SubjectsTable.COL_SUBJECT_ID, subjectId)
            put(SubjectsTable.COL_NAME, name)
            put(SubjectsTable.COL_CREATED_AT, createdAt)
        }
        val rowId = db.insert(SubjectsTable.NAME, null, values)
        check(rowId != -1L) { "Failed to insert subject $subjectId" }
        return Subject(subjectId, name, featureCount = 0, createdAt = createdAt)
    }

    /** Updates the subject name. Returns the number of affected rows. */
    fun update(subjectId: String, name: String): Int {
        val values = ContentValues().apply { put(SubjectsTable.COL_NAME, name) }
        return db.update(
            SubjectsTable.NAME, values, "${SubjectsTable.COL_SUBJECT_ID} = ?", arrayOf(subjectId)
        )
    }

    /** Deletes a subject. Returns the number of affected rows. */
    fun delete(subjectId: String): Int {
        return db.delete(
            SubjectsTable.NAME, "${SubjectsTable.COL_SUBJECT_ID} = ?", arrayOf(subjectId)
        )
    }

    /** Returns a subject by ID, or null if not found. */
    fun findById(subjectId: String): Subject? {
        val cursor = db.rawQuery(
            "$SELECT_WITH_COUNT WHERE s.${SubjectsTable.COL_SUBJECT_ID} = ?", arrayOf(subjectId)
        )
        return cursor.use { if (it.moveToFirst()) readSubject(it) else null }
    }

    /** Returns all subjects. */
    fun findAll(): List<Subject> {
        val cursor = db.rawQuery(SELECT_WITH_COUNT, null)
        val list = mutableListOf<Subject>()
        cursor.use {
            while (it.moveToNext()) list.add(readSubject(it))
        }
        return list
    }

    /** Returns the total number of subjects. */
    fun count(): Int {
        val cursor = db.rawQuery("SELECT COUNT(*) FROM ${SubjectsTable.NAME}", null)
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /** Maps the current cursor row to a Subject. */
    private fun readSubject(cursor: Cursor): Subject {
        return Subject(
            subjectId = cursor.getString(cursor.getColumnIndexOrThrow(SubjectsTable.COL_SUBJECT_ID)),
            name = cursor.getString(cursor.getColumnIndexOrThrow(SubjectsTable.COL_NAME)),
            featureCount = cursor.getInt(cursor.getColumnIndexOrThrow(ALIAS_FEATURE_COUNT)),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(SubjectsTable.COL_CREATED_AT))
        )
    }

    private companion object {
        const val ALIAS_FEATURE_COUNT = "feature_count"

        /** Selects a subject row with its live feature count. */
        val SELECT_WITH_COUNT = """
            SELECT s.${SubjectsTable.COL_SUBJECT_ID},
                   s.${SubjectsTable.COL_NAME},
                   s.${SubjectsTable.COL_CREATED_AT},
                   (SELECT COUNT(*) FROM ${FeaturesTable.NAME} f
                    WHERE f.${FeaturesTable.COL_SUBJECT_ID} = s.${SubjectsTable.COL_SUBJECT_ID}) AS $ALIAS_FEATURE_COUNT
            FROM ${SubjectsTable.NAME} s
        """.trimIndent()
    }
}
