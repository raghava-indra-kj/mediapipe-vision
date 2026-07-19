package com.github.raghavaindrakj.mediapipevision.support

import java.util.UUID

internal object SampleIdGenerator {
    fun newSampleId(): String = UUID.randomUUID().toString()
}
