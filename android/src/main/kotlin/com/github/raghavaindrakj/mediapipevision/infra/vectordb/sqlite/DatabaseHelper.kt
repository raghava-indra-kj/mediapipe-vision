package com.github.raghavaindrakj.mediapipevision.infra.vectordb.sqlite

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Manages the SQLite database creation and schema versioning. */
internal class DatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, Db.NAME, null, Db.VERSION
) {

    init {
        // Enable WAL for concurrent reads.
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // Enforce foreign keys for ON DELETE CASCADE support.
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Create subjects table
        db.execSQL(
            """
            CREATE TABLE ${SubjectsTable.NAME} (
                ${SubjectsTable.COL_SUBJECT_ID} TEXT PRIMARY KEY,
                ${SubjectsTable.COL_NAME} TEXT NOT NULL,
                ${SubjectsTable.COL_CREATED_AT} INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // Create features table with foreign key to subjects
        db.execSQL(
            """
            CREATE TABLE ${FeaturesTable.NAME} (
                ${FeaturesTable.COL_FEATURE_ID} TEXT PRIMARY KEY,
                ${FeaturesTable.COL_SUBJECT_ID} TEXT NOT NULL
                    REFERENCES ${SubjectsTable.NAME}(${SubjectsTable.COL_SUBJECT_ID})
                    ON DELETE CASCADE,
                ${FeaturesTable.COL_VECTOR} BLOB NOT NULL
            )
            """.trimIndent()
        )

        // Index the foreign key for per-subject lookups.
        db.execSQL(
            """
            CREATE INDEX ${FeaturesTable.INDEX_SUBJECT_ID}
            ON ${FeaturesTable.NAME}(${FeaturesTable.COL_SUBJECT_ID})
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS ${FeaturesTable.NAME}")
        db.execSQL("DROP TABLE IF EXISTS ${SubjectsTable.NAME}")
        onCreate(db)
    }
}
