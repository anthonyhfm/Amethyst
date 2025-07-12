package dev.anthonyhfm.amethyst.core.heaven.utils

import kotlinx.atomicfu.*

class ConcurrentQueue<T> {
    private class Node<T>(val value: T?) {
        val next: AtomicRef<Node<T>?> = atomic(null)
    }

    private val head: AtomicRef<Node<T>> = atomic(Node(null))
    private val tail: AtomicRef<Node<T>> = atomic(head.value)

    fun enqueue(item: T) {
        val newNode = Node(item)
        while (true) {
            val last = tail.value
            val next = last.next.value

            if (last == tail.value) {
                if (next == null) {
                    if (last.next.compareAndSet(null, newNode)) {
                        tail.compareAndSet(last, newNode)
                        break
                    }
                } else {
                    tail.compareAndSet(last, next)
                }
            }
        }
    }

    fun tryDequeue(): T? {
        while (true) {
            val first = head.value
            val last = tail.value
            val next = first.next.value

            if (first == head.value) {
                if (first == last) {
                    if (next == null) {
                        return null
                    }
                    tail.compareAndSet(last, next)
                } else {
                    val data = next?.value
                    if (head.compareAndSet(first, next!!)) {
                        return data
                    }
                }
            }
        }
    }

    fun isEmpty(): Boolean {
        val first = head.value
        val last = tail.value
        return first == last && first.next.value == null
    }
}