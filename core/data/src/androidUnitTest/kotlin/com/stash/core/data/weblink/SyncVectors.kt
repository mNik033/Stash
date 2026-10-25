package com.stash.core.data.weblink

import com.stash.core.model.weblink.Hlc
import com.stash.core.model.weblink.SongRef
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.security.MessageDigest

/**
 * The shared sync-v1 vector files in `src/test/resources/sync/`: byte-for-byte copies of stash-player `src/lib/sync/fixtures/`
 * (docs/sync-v1.md "Test vectors"). Both repos pin the same SHA-256 (stash-player `src/lib/sync/vectors.test.ts`): change both
 * repos and both pins together, or neither.
 */
internal object SyncVectors {
    const val MERGE_SHA256 = "4568a4cc1c8ec6395a6560940005d316e09f8a7424f68b5fe7fa1f2b07f80198"
    const val CRYPTO_SHA256 = "b1a8f7a5a77ebc1212b8628c0906ef650c778634e809c5ee9439ccff116beb88"

    fun bytes(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/sync/$name")) { "missing test resource sync/$name" }.use { it.readBytes() }

    fun load(name: String): JsonElement = Json.parseToJsonElement(bytes(name).toString(Charsets.UTF_8))

    fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}

internal val JsonElement.s: String get() = jsonPrimitive.content
internal val JsonElement.sOrNull: String? get() = if (this is JsonNull) null else jsonPrimitive.content
internal val JsonElement.list: JsonArray get() = jsonArray
internal val JsonElement.l: Long get() = jsonPrimitive.long
internal val JsonElement.i: Int get() = jsonPrimitive.int
internal operator fun JsonElement.get(k: String): JsonElement = jsonObject[k] ?: JsonNull
internal fun JsonElement.has(k: String): Boolean = jsonObject[k].let { it != null && it !is JsonNull }

internal fun hlcOf(e: JsonElement): Hlc? = if (e is JsonNull) null else e.list.let { Hlc(it[0].l, it[1].i, it[2].s) }

internal fun songOf(o: JsonElement) = SongRef(o["title"].s, o["artist"].s, if (o.has("isrc")) o["isrc"].s else null)
