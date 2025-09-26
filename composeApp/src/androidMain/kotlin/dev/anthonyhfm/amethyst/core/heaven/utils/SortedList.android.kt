package dev.anthonyhfm.amethyst.core.engine.utils

import java.util.*

actual class SortedList<K : Comparable<K>, V> actual constructor() {
    private val keyList = ArrayList<K>()
    private val valueList = ArrayList<V>()

    actual val size: Int get() = keyList.size
    actual val keys: List<K> get() = keyList
    actual val values: List<V> get() = valueList

    actual fun clear() {
        keyList.clear()
        valueList.clear()
    }

    actual fun containsKey(key: K): Boolean {
        return keyList.binarySearch(key) >= 0
    }

    actual operator fun get(key: K): V? {
        val index = keyList.binarySearch(key)
        return if (index >= 0) valueList[index] else null
    }

    actual operator fun set(key: K, value: V) {
        val index = keyList.binarySearch(key)
        if (index >= 0) {
            valueList[index] = value
        } else {
            val insertIndex = -(index + 1)
            keyList.add(insertIndex, key)
            valueList.add(insertIndex, value)
        }
    }

    actual fun remove(key: K): V? {
        val index = keyList.binarySearch(key)
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
}
