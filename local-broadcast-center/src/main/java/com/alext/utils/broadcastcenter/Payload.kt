package com.alext.utils.broadcastcenter

import android.os.Bundle
import android.os.Parcelable
import com.google.gson.Gson
import com.google.gson.JsonIOException
import java.io.Serializable

/**
 * A Wrapper of payload object
 *
 * @property value a non-null payload object with generic type [T]
 */
class Payload<T : Any> constructor(val value: T) {

    internal fun asBundle(): Bundle {
        val bundle = Bundle()
        when (value) {
            // Use the related put of Bundle for some regular type of value
            is String -> {
                bundle.putString(KEY_PAYLOAD, value)
            }
            is Int -> {
                bundle.putInt(KEY_PAYLOAD, value)
            }
            is Long -> {
                bundle.putLong(KEY_PAYLOAD, value)
            }
            is Float -> {
                bundle.putFloat(KEY_PAYLOAD, value)
            }
            is Double -> {
                bundle.putDouble(KEY_PAYLOAD, value)
            }
            is Boolean -> {
                bundle.putBoolean(KEY_PAYLOAD, value)
            }
            is Byte -> {
                bundle.putByte(KEY_PAYLOAD, value)
            }
            is Char -> {
                bundle.putChar(KEY_PAYLOAD, value)
            }
            is Parcelable -> {
                bundle.putParcelable(KEY_PAYLOAD, value)
            }
            is Serializable -> {
                bundle.putSerializable(KEY_PAYLOAD, value)
            }
            is Bundle -> {
                bundle.putBundle(KEY_PAYLOAD, value)
            }
            else -> {
                // Other type will be converted to Json then stored in bundle as String
                try {
                    val asJson = Gson().toJson(value)
                    bundle.putBoolean(KEY_AS_JSON, true)
                    bundle.putString(KEY_PAYLOAD, asJson)
                    bundle.putSerializable(KEY_CLASS_TYPE, value::class.java)
                } catch (e: JsonIOException) {
                    // Throw a exception if payload can't be parsed
                    throw IllegalArgumentException("This type of value is not supported")
                }
            }
        }
        return bundle
    }

    companion object {
        private const val UNIQUE_PREFIX = "LocalBroadcastCenter"
        internal const val KEY_PAYLOAD = "${UNIQUE_PREFIX}_Payload"
        internal const val KEY_AS_JSON = "${UNIQUE_PREFIX}_Payload_As_Json"
        internal const val KEY_CLASS_TYPE = "${UNIQUE_PREFIX}_Payload_ClassType"
    }
}
