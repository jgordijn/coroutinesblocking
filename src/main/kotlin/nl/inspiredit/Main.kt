package nl.inspiredit

import com.sun.net.httpserver.HttpServer
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import java.io.IOException
import java.lang.Exception
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random.Default.nextInt
import kotlin.random.Random.Default.nextLong

//val reg = SimpleMeterRegistry()
val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
val timer = prometheusRegistry.timer("perf")
val success = prometheusRegistry.timer("success")

fun prometheus() {

    try {
        val server = HttpServer.create(InetSocketAddress(8080), 0)
        server.createContext("/prometheus") { httpExchange ->
            val response = prometheusRegistry.scrape()
            httpExchange.sendResponseHeaders(200, response.encodeToByteArray().size.toLong())
            httpExchange.getResponseBody().use { os ->
                os.write(response.encodeToByteArray())
            }
        }

        Thread(server::start).start()
    } catch (e: IOException) {
        throw RuntimeException(e)
    }
}

fun main() = runBlocking {
    initialize()
    prometheus()

    flow<Int> {
        var nr = 0
        while (true) {
            emit(nr++)
        }
    }
        .flatMapMerge(1000) { nr ->
            flow {
                val time = Timer.start()
                doSomethingInBlocking(nr)
                time.stop(timer)
                emit(1)
            }
        }
        .collect { }
}

val poolSize = 35

var available = Channel<Int>(poolSize)


suspend fun acquireConnectionWithoutRetry(): Int = withContext(Dispatchers.IO) {
    val c = available.receive()
    println("GOT CONNECTION (-) $c")
    c
}

suspend fun acquireConnection(retries: Int = 5): Int = withContext(Dispatchers.IO) {
    try {
        withTimeout(3500) {
            val c = available.receive()
            println("GOT CONNECTION ($retries) $c")
            c
        }
    } catch (toe: TimeoutCancellationException) {
        if (retries > 0) acquireConnection(retries - 1) else throw toe
    }
}

suspend fun release(connection: Int) {
    println("Realease $connection")
    available.send(connection)
}

suspend fun initialize() {
    repeat(poolSize) {
        available.send(it)
    }
}

val c = Executors.newFixedThreadPool(12).asCoroutineDispatcher()
suspend fun doSomethingInBlocking(nr: Int) {
    withContext(c) {
        try {
            val connection = acquireConnection(0)
            val time = Timer.start()
            Thread.sleep(nextLong(60, 250))
            println("$nr-$connection: ${Thread.currentThread().name}")
            time.stop(success)
            release(connection)
        } catch (e: Exception) {
            println("$nr: ${e.javaClass}")
        }
    }
}


