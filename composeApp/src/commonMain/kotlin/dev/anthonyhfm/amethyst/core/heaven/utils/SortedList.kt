package dev.anthonyhfm.amethyst.core.heaven.utils

/**
 * Cross-platform SortedList implementation optimized for performance.
 * Similar to C# SortedList<TKey, TValue> with array-based storage for better cache locality.
 */
expect class SortedList<K : Comparable<K>, V>() {
    val size: Int
    val keys: List<K>
    val values: List<V>

    fun clear()
    fun containsKey(key: K): Boolean
    operator fun get(key: K): V?
    operator fun set(key: K, value: V)
    fun remove(key: K): V?
    fun isEmpty(): Boolean

    // Efficient access by index (like C# SortedList)
    fun getKeyAt(index: Int): K
    fun getValueAt(index: Int): V
    fun removeAt(index: Int)
}
