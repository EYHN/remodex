package com.remodex.android.data.model

import java.util.concurrent.atomic.AtomicInteger

object CodexMessageOrderCounter {
    private val counter = AtomicInteger(0)

    fun next(): Int = counter.incrementAndGet()

    fun seed(from: Int) {
        counter.set(maxOf(counter.get(), from))
    }

    fun current(): Int = counter.get()
}
