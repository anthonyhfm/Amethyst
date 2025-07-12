package dev.anthonyhfm.amethyst.core.heaven.utils

import java.util.*

/**
 * JVM implementation using ArrayList for maximum performance.
 * Array-based like C# SortedList for better cache locality.
 */
actual class SortedList<K : Comparable<K>, V> actual constructor() {
    private val keyList = mutableListOf<K>()
    private val valueList = mutableListOf<V>()

    actual val size: Int get() = keyList.size
    actual val keys: List<K> get() = keyList
    actual val values: List<V> get() = valueList

    actual fun clear() {
        keyList.clear()
        valueList.clear()
    }

    actual fun containsKey(key: K): Boolean {
        return binarySearch(key) >= 0
    }

    actual operator fun get(key: K): V? {
        val index = binarySearch(key)
        return if (index >= 0) valueList[index] else null
    }

    actual operator fun set(key: K, value: V) {
        val index = binarySearch(key)
        if (index >= 0) {
            // Key exists, update value
            valueList[index] = value
        } else {
            // Key doesn't exist, insert at correct position
            val insertIndex = -(index + 1)
            keyList.add(insertIndex, key)
            valueList.add(insertIndex, value)
        }
    }

    actual fun remove(key: K): V? {
        val index = binarySearch(key)
        return if (index >= 0) {
            keyList.removeAt(index)
            valueList.removeAt(index)
        } else {
            null
        }
    }

    actual fun isEmpty(): Boolean = keyList.isEmpty()

    actual fun getKeyAt(index: Int): K = keyList[index]
    actual fun getValueAt(index: Int): V = valueList[index]

    actual fun removeAt(index: Int) {
        keyList.removeAt(index)
        valueList.removeAt(index)
    }

    private fun binarySearch(key: K): Int {
        return Collections.binarySearch(keyList, key)
    }
}
