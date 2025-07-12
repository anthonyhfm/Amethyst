package dev.anthonyhfm.amethyst.core.heaven.utils

import kotlinx.atomicfu.*
import kotlin.collections.MutableList

class SortedDictionary<K : Comparable<K>, V> {
    private class Node<K : Comparable<K>, V>(
        var key: K,  // Changed to var
        var value: V,
        var left: Node<K, V>? = null,
        var right: Node<K, V>? = null,
        var height: Int = 1
    )

    private val root: AtomicRef<Node<K, V>?> = atomic(null)

    private fun height(node: Node<K, V>?): Int = node?.height ?: 0

    private fun balance(node: Node<K, V>?): Int =
        if (node == null) 0 else height(node.left) - height(node.right)

    private fun updateHeight(node: Node<K, V>) {
        node.height = 1 + maxOf(height(node.left), height(node.right))
    }

    private fun rotateRight(y: Node<K, V>): Node<K, V> {
        val x = y.left!!
        val t2 = x.right
        x.right = y
        y.left = t2
        updateHeight(y)
        updateHeight(x)
        return x
    }

    private fun rotateLeft(x: Node<K, V>): Node<K, V> {
        val y = x.right!!
        val t2 = y.left
        y.left = x
        x.right = t2
        updateHeight(x)
        updateHeight(y)
        return y
    }

    fun put(key: K, value: V) {
        root.value = insert(root.value, key, value)
    }

    private fun insert(node: Node<K, V>?, key: K, value: V): Node<K, V> {
        if (node == null) return Node(key, value)

        when {
            key < node.key -> node.left = insert(node.left, key, value)
            key > node.key -> node.right = insert(node.right, key, value)
            else -> {
                node.value = value
                return node
            }
        }

        updateHeight(node)

        val balanceFactor = balance(node)

        // Left Left Case
        if (balanceFactor > 1 && key < node.left!!.key)
            return rotateRight(node)

        // Right Right Case
        if (balanceFactor < -1 && key > node.right!!.key)
            return rotateLeft(node)

        // Left Right Case
        if (balanceFactor > 1 && key > node.left!!.key) {
            node.left = rotateLeft(node.left!!)
            return rotateRight(node)
        }

        // Right Left Case
        if (balanceFactor < -1 && key < node.right!!.key) {
            node.right = rotateRight(node.right!!)
            return rotateLeft(node)
        }

        return node
    }

    fun get(key: K): V? = search(root.value, key)?.value

    private fun search(node: Node<K, V>?, key: K): Node<K, V>? {
        if (node == null || node.key == key) return node
        return if (key < node.key) search(node.left, key) else search(node.right, key)
    }

    fun remove(key: K): V? {
        val oldValue = get(key)
        root.value = delete(root.value, key)
        return oldValue
    }

    private fun delete(node: Node<K, V>?, key: K): Node<K, V>? {
        if (node == null) return null

        when {
            key < node.key -> node.left = delete(node.left, key)
            key > node.key -> node.right = delete(node.right, key)
            else -> {
                if (node.left == null) return node.right
                if (node.right == null) return node.left

                val minRight = findMin(node.right!!)
                node.key = minRight.key
                node.value = minRight.value
                node.right = delete(node.right, minRight.key)
            }
        }

        updateHeight(node)

        val balanceFactor = balance(node)

        if (balanceFactor > 1 && balance(node.left) >= 0)
            return rotateRight(node)

        if (balanceFactor > 1 && balance(node.left) < 0) {
            node.left = rotateLeft(node.left!!)
            return rotateRight(node)
        }

        if (balanceFactor < -1 && balance(node.right) <= 0)
            return rotateLeft(node)

        if (balanceFactor < -1 && balance(node.right) > 0) {
            node.right = rotateRight(node.right!!)
            return rotateLeft(node)
        }

        return node
    }

    private fun findMin(node: Node<K, V>): Node<K, V> =
        if (node.left == null) node else findMin(node.left!!)

    fun getKeysUpTo(maxKey: K): List<K> {
        val result = mutableListOf<K>()
        collectKeysUpTo(root.value, maxKey, result)
        return result
    }

    private fun collectKeysUpTo(node: Node<K, V>?, maxKey: K, result: MutableList<K>) {
        if (node == null) return

        collectKeysUpTo(node.left, maxKey, result)

        if (node.key <= maxKey) {
            result.add(node.key)
            collectKeysUpTo(node.right, maxKey, result)
        }
    }

    fun isEmpty(): Boolean = root.value == null

    fun containsKey(key: K): Boolean = get(key) != null
}
