package io.github.robbieadi.gametap.layout

import kotlinx.serialization.json.Json

/** The JSON format of the layout file. */
val LayoutJson: Json = Json {
    // Default values are written out, so changing a default in code never changes a saved layout.
    encodeDefaults = true
    // A file written by a newer version can still load, minus the fields this version lacks.
    ignoreUnknownKeys = true
    classDiscriminator = "type"
}
