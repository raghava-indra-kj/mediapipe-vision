# mediapipe-vision

On-device item enrollment and recognition for Android. Generates image embeddings with MediaPipe
Tasks Vision (MobileNetV3-Large) and matches them with an on-device SQLite store — no server,
no Python, no model training required, no native dependencies.

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

## Storage

`recognize()` is backed by plain framework SQLite (`android.database.sqlite`) — exact linear-scan
cosine similarity, no native vector index, no third-party dependency. It works identically on
every device and ABI, including 32-bit-only hardware. The tradeoff is that search cost grows
linearly with the number of enrolled features — comfortably fast (low single-digit seconds) up to
a few thousand enrolled features, but not intended for very large catalogs.

## Adding it to a project

Add the JitPack repository and the dependency:

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.raghava-indra-kj:mediapipe-vision:1.0.7")
}
```

## License

MIT — see [LICENSE](LICENSE).
