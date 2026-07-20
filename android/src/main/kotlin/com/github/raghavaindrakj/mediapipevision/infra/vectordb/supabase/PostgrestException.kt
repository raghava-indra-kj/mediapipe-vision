package com.github.raghavaindrakj.mediapipevision.infra.vectordb.supabase

/** Thrown when PostgREST returns a structured Postgres error (e.g. a constraint violation). */
internal class PostgrestException(
    val pgCode: String?,
    message: String
) : Exception(message)
