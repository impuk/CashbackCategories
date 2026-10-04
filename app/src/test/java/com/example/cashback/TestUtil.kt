package com.example.cashback

/** Ждёт, пока условие станет истинным: Room отдаёт данные из своих потоков. */
fun awaitUntil(timeoutMs: Long = 5_000, message: String = "условие не выполнилось", condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
        if (System.currentTimeMillis() > deadline) throw AssertionError(message)
        Thread.sleep(10)
    }
}
