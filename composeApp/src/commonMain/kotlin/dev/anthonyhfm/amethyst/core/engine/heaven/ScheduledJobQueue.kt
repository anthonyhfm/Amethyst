package dev.anthonyhfm.amethyst.core.engine.heaven

internal class ScheduledJobQueue {
    private val heap = mutableListOf<ScheduledJob>()

    val size: Int
        get() = heap.size

    fun isEmpty(): Boolean = heap.isEmpty()

    fun isNotEmpty(): Boolean = heap.isNotEmpty()

    fun peek(): ScheduledJob? = heap.firstOrNull()

    fun add(job: ScheduledJob) {
        heap.add(job)
        siftUp(heap.lastIndex)
    }

    fun removeFirst(): ScheduledJob {
        check(heap.isNotEmpty())

        val first = heap.first()
        val last = heap.removeAt(heap.lastIndex)
        if (heap.isNotEmpty()) {
            heap[0] = last
            siftDown(0)
        }
        return first
    }

    fun removeAll(filter: (ScheduledJob) -> Boolean): Int {
        val before = heap.size
        heap.removeAll(filter)
        heapify()
        return before - heap.size
    }

    fun clear(): Int {
        val removed = heap.size
        heap.clear()
        return removed
    }

    private fun heapify() {
        for (index in (heap.size / 2 - 1) downTo 0) {
            siftDown(index)
        }
    }

    private fun siftUp(startIndex: Int) {
        var index = startIndex
        while (index > 0) {
            val parentIndex = (index - 1) / 2
            if (!comesBefore(heap[index], heap[parentIndex])) {
                return
            }
            heap.swap(index, parentIndex)
            index = parentIndex
        }
    }

    private fun siftDown(startIndex: Int) {
        var index = startIndex
        while (true) {
            val leftIndex = index * 2 + 1
            if (leftIndex >= heap.size) {
                return
            }

            val rightIndex = leftIndex + 1
            val nextIndex = if (
                rightIndex < heap.size &&
                comesBefore(heap[rightIndex], heap[leftIndex])
            ) {
                rightIndex
            } else {
                leftIndex
            }

            if (!comesBefore(heap[nextIndex], heap[index])) {
                return
            }
            heap.swap(index, nextIndex)
            index = nextIndex
        }
    }

    private fun comesBefore(left: ScheduledJob, right: ScheduledJob): Boolean =
        left.targetTimeNanos < right.targetTimeNanos ||
            (
                left.targetTimeNanos == right.targetTimeNanos &&
                    left.sequence < right.sequence
                )

    private fun MutableList<ScheduledJob>.swap(firstIndex: Int, secondIndex: Int) {
        val first = this[firstIndex]
        this[firstIndex] = this[secondIndex]
        this[secondIndex] = first
    }
}
