package dev.anthonyhfm.amethyst.core.heaven.utils

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.darwin.*

/**
 * iOS implementation using native Foundation collections for maximum performance.
 * Leverages NSMutableArray's optimized C implementation and binary search.
 */
actual class SortedList<K : Comparable<K>, V> actual constructor() {
    // Use NSMutableArray for native iOS performance
    private val keyArray = NSMutableArray()
    private val valueArray = NSMutableArray()

    actual val size: Int get() = keyArray.count.toInt()

    actual val keys: List<K>
        get() = (0 until size).map {
            @Suppress("UNCHECKED_CAST")
            keyArray.objectAtIndex(it.toULong()) as K
        }

    actual val values: List<V>
        get() = (0 until size).map {
            @Suppress("UNCHECKED_CAST")
            valueArray.objectAtIndex(it.toULong()) as V
        }

    actual fun clear() {
        keyArray.removeAllObjects()
        valueArray.removeAllObjects()
    }

    actual fun containsKey(key: K): Boolean {
        return nativeBinarySearch(key) >= 0
    }

    actual operator fun get(key: K): V? {
        val index = nativeBinarySearch(key)
        return if (index >= 0) {
            @Suppress("UNCHECKED_CAST")
            valueArray.objectAtIndex(index.toULong()) as V
        } else null
    }

    actual operator fun set(key: K, value: V) {
        val index = nativeBinarySearch(key)
        if (index >= 0) {
            // Key exists, update value
            valueArray.replaceObjectAtIndex(index.toULong(), value as Any)
        } else {
            // Key doesn't exist, insert at correct position
            val insertIndex = -(index + 1)
            keyArray.insertObject(key as Any, insertIndex.toULong())
            valueArray.insertObject(value as Any, insertIndex.toULong())
        }
    }

    actual fun remove(key: K): V? {
        val index = nativeBinarySearch(key)
        return if (index >= 0) {
            @Suppress("UNCHECKED_CAST")
            val removedValue = valueArray.objectAtIndex(index.toULong()) as V
            keyArray.removeObjectAtIndex(index.toULong())
            valueArray.removeObjectAtIndex(index.toULong())
            removedValue
        } else {
            null
        }
    }

    actual fun isEmpty(): Boolean = keyArray.count == 0UL

    actual fun getKeyAt(index: Int): K {
        @Suppress("UNCHECKED_CAST")
        return keyArray.objectAtIndex(index.toULong()) as K
    }

    actual fun getValueAt(index: Int): V {
        @Suppress("UNCHECKED_CAST")
        return valueArray.objectAtIndex(index.toULong()) as V
    }

    actual fun removeAt(index: Int) {
        keyArray.removeObjectAtIndex(index.toULong())
        valueArray.removeObjectAtIndex(index.toULong())
    }

    // Use NSArray's native binary search for optimal performance
    @OptIn(ExperimentalForeignApi::class)
    private fun nativeBinarySearch(key: K): Int {
        if (keyArray.count == 0UL) return -1

        // Use NSArray's built-in binary search using NSComparator
        val comparator = { obj1: Any?, obj2: Any? ->
            @Suppress("UNCHECKED_CAST")
            val k1 = obj1 as K
            @Suppress("UNCHECKED_CAST")
            val k2 = obj2 as K

            when {
                k1 < k2 -> NSOrderedAscending
                k1 > k2 -> NSOrderedDescending
                else -> NSOrderedSame
            }
        }

        val searchOptions = NSBinarySearchingFirstEqual or NSBinarySearchingInsertionIndex
        val index = keyArray.indexOfObject(
            key as Any,
            inSortedRange = NSMakeRange(0UL, keyArray.count),
            options = searchOptions,
            usingComparator = comparator
        )

        // Check if exact match was found
        return if (index < keyArray.count &&
                   (keyArray.objectAtIndex(index) as K) == key) {
            index.toInt()
        } else {
            -(index.toInt() + 1)
        }
    }
}
