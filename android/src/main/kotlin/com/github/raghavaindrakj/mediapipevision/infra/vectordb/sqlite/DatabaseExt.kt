package com.github.raghavaindrakj.mediapipevision.infra.vectordb.sqlite

import android.database.sqlite.SQLiteDatabase

/** Runs [block] inside a transaction, committing on success and always ending the transaction. */
internal inline fun <T> SQLiteDatabase.tx(block: () -> T): T {
    beginTransaction()
    try {
        return block().also { setTransactionSuccessful() }
    } finally {
        endTransaction()
    }
}
