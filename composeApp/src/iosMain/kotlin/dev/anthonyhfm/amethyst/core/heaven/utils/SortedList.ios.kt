package dev.anthonyhfm.amethyst.core.engine.utils

/**
 * Shared implementation using Kotlin collections.
 * Simplified version using mutableListOf and binarySearch.
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

    actual fun containsKey(key: K): Boolean = binarySearch(key) >= 0

    actual operator fun get(key: K): V? {
        val idx = binarySearch(key)
        return if (idx >= 0) valueList[idx] else null
    }

    actual operator fun set(key: K, value: V) {
        val idx = binarySearch(key)
        if (idx >= 0) {
            valueList[idx] = value
        } else {
            val insertIndex = -(idx + 1)
            keyList.add(insertIndex, key)
            valueList.add(insertIndex, value)
        }
    }

    actual fun remove(key: K): V? {
        val idx = binarySearch(key)
        return if (idx >= 0) {
            keyList.removeAt(idx)
            valueList.removeAt(idx)
        } else null
    }

    actual fun isEmpty(): Boolean = keyList.isEmpty()

    actual fun getKeyAt(index: Int): K = keyList[index]
    actual fun getValueAt(index: Int): V = valueList[index]

    actual fun removeAt(index: Int) {
        keyList.removeAt(index)
        valueList.removeAt(index)
    }

    private fun binarySearch(key: K): Int = keyList.binarySearch(key)
}
