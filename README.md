# mediapipe-vision

On-device item enrollment and recognition for Android. Generates image embeddings with MediaPipe
Tasks Vision (MobileNetV3-Large) and stores/matches them with ObjectBox's HNSW vector index —
no server, no Python, no model training required.

## Usage

```kotlin
val store = EmbeddingStore()
store.initialize(context)

// Enroll a subject
store.createSubject(subjectId = "item-123", name = "Blue Mug")
store.createFeature(subjectId = "item-123", image = bitmap)

// Recognize against everything enrolled so far
val matches = store.recognize(image = queryBitmap, k = 5) // ranked by confidence, best first
matches.forEach { println("${it.name}: ${it.confidence}%") }

store.close()
```

`initialize`, `close`, and every read/write method are `suspend fun`s — call them from a
coroutine (e.g. `lifecycleScope.launch { ... }`).

Failures throw `EmbeddingStoreException` or `EmbeddingExtractorException` (both subtypes of
`EmbeddingException`) with a machine-readable `errorCode` — see `EmbeddingStoreErrorCodes` and
`EmbeddingExtractorErrorCodes`.

## Adding it to a project

Add the JitPack repository and the dependency:

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.raghava-indra-kj:mediapipe-vision:1.0.5")
}
```

## License

MIT — see [LICENSE](LICENSE).
