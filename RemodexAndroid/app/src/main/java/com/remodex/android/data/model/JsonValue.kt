package com.remodex.android.data.model

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable(with = JsonValueSerializer::class)
sealed class JsonValue {
    data class StringValue(val value: String) : JsonValue()
    data class IntValue(val value: Long) : JsonValue()
    data class DoubleValue(val value: Double) : JsonValue()
    data class BoolValue(val value: Boolean) : JsonValue()
    data class ObjectValue(val value: Map<String, JsonValue>) : JsonValue()
    data class ArrayValue(val value: List<JsonValue>) : JsonValue()
    data object NullValue : JsonValue()

    val stringValue: String? get() = (this as? StringValue)?.value
    val intValue: Long? get() = (this as? IntValue)?.value
    val doubleValue: Double? get() = (this as? DoubleValue)?.value
    val boolValue: Boolean? get() = (this as? BoolValue)?.value
    val objectValue: Map<String, JsonValue>? get() = (this as? ObjectValue)?.value
    val arrayValue: List<JsonValue>? get() = (this as? ArrayValue)?.value
    val isNull: Boolean get() = this is NullValue

    operator fun get(key: String): JsonValue? = objectValue?.get(key)
    operator fun get(index: Int): JsonValue? = arrayValue?.getOrNull(index)

    fun toJsonElement(): JsonElement = when (this) {
        is StringValue -> JsonPrimitive(value)
        is IntValue -> JsonPrimitive(value)
        is DoubleValue -> JsonPrimitive(value)
        is BoolValue -> JsonPrimitive(value)
        is ObjectValue -> JsonObject(value.mapValues { it.value.toJsonElement() })
        is ArrayValue -> JsonArray(value.map { it.toJsonElement() })
        is NullValue -> JsonNull
    }

    companion object {
        fun from(element: JsonElement): JsonValue = when (element) {
            is JsonNull -> NullValue
            is JsonPrimitive -> when {
                element.isString -> StringValue(element.content)
                element.content == "true" || element.content == "false" -> BoolValue(element.boolean)
                element.content.contains('.') -> DoubleValue(element.double)
                else -> element.longOrNull?.let { IntValue(it) }
                    ?: DoubleValue(element.double)
            }
            is JsonObject -> ObjectValue(element.mapValues { from(it.value) })
            is JsonArray -> ArrayValue(element.map { from(it) })
        }

        fun string(value: String) = StringValue(value)
        fun int(value: Long) = IntValue(value)
        fun bool(value: Boolean) = BoolValue(value)
        fun obj(vararg pairs: Pair<String, JsonValue>) = ObjectValue(mapOf(*pairs))
        fun array(vararg items: JsonValue) = ArrayValue(items.toList())
        val nullValue = NullValue
    }
}

object JsonValueSerializer : KSerializer<JsonValue> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("JsonValue", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: JsonValue) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(value.toJsonElement())
    }

    override fun deserialize(decoder: Decoder): JsonValue {
        val jsonDecoder = decoder as JsonDecoder
        return JsonValue.from(jsonDecoder.decodeJsonElement())
    }
}
