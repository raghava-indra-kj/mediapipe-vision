# mediapipe-vision

On-device item learning and recognition for Android. Generates image embeddings with MediaPipe
Tasks Vision (MobileNetV3-Large) and stores/matches them with ObjectBox's HNSW vector index —
no server, no Python, no model training required.

## Usage

```kotlin
val vision = MediaPipeVision.initialize(context)

vision.learn(bitmap, id = "item-123", displayName = "Blue Mug")
val matches = vision.recognize(bitmap) // ranked by confidence, best first
```

## Adding it to a project

**Same machine, as a sibling folder** — add a composite build in `settings.gradle.kts`:

```kotlin
includeBuild("../mediapipe-vision")
```

**From anywhere, via JitPack** — add the repository and dependency:

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.raghava-indra-kj:mediapipe-vision:1.0.0")
}
```

## License

MIT — see [LICENSE](LICENSE).
