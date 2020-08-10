package com.alext.utils.broadcastcenter

internal inline fun <K, V> Map<K, V>.forEachValue(action: (V) -> Unit) {
    for ((_, value) in this) {
        action(value)
    }
}

internal inline fun <K, V> Map<K, V>.forEachKey(action: (K) -> Unit) {
    for ((key, _) in this) {
        action(key)
    }
}