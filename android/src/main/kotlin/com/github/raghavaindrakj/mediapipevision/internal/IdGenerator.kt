package com.github.raghavaindrakj.mediapipevision.internal

import java.util.UUID

internal object IdGenerator {
    fun newSampleId(): String = UUID.randomUUID().toString()
}
