package nl.inspiredit.test2

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import nl.inspiredit.prometheus
import nl.inspiredit.prometheusRegistry
import nl.inspiredit.success
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class TimeoutException : Exception()
data class Connection(val nr: Int)
class ConnectionPool(size: Int, meterRegistry: MeterRegistry) {
    private val connections = meterRegistry.gauge("connectionpool", ConcurrentLinkedQueue<Connection>()
        .apply { repeat(size) { this.add(Connection(it)) } }) {
        it.size.toDouble()
    }

    fun getConnection(retries: Int = 5, timeout: Duration = Duration.ofSeconds(1)): Connection {
        val start = Instant.now().plus(timeout)
        var elem = connections.poll()
        while (elem == null) {
            Thread.sleep(10)
            if (start.isAfter(Instant.now())) {
                if (retries > 0) getConnection(retries - 1, timeout)
                else throw TimeoutException()
            }
            elem = connections.poll()
        }
        println("Got connection $elem ${Thread.currentThread().name}")
        return elem
    }

    fun release(connection: Connection) {
        connections.add(connection)
    }

    fun size() = connections.size
}

val poolsize = 35

val database = Executors.newFixedThreadPool(poolsize * 5).asCoroutineDispatcher()

fun main() = runBlocking {
    val meterRegistry = prometheusRegistry
    val perf1 = prometheusRegistry.timer("perfo", "flow", "nr1")
    val perf2 = prometheusRegistry.timer("perfo", "flow", "nr2")
    val pool = ConnectionPool(poolsize, meterRegistry)
    prometheus()
    suspend fun doSomethingInBlocking(nr: Int) {
        withContext(database) {
            try {
                val connection = pool.getConnection(5)
                println("PERFORM $nr-$connection: ${Thread.currentThread().name}")
                val time = Timer.start()
                // This will be the blocking DB call
                Thread.sleep(Random.nextLong(60, 250))
                time.stop(success)
                println("call release $connection")
                pool.release(connection)
                println("call release $connection DONE")
                println("Pool size: ${pool.size()}")
            } catch (e: nl.inspiredit.test2.TimeoutException) {
//                println("$nr: ${e}")
            }
        }
    }

    launch {
        flow<Int> {
            var nr = 0
            while (true) {
                emit(nr++)
            }
        }
            .flatMapMerge(20) { nr ->
                flow {
                    val time = Timer.start()
                    val start = Instant.now()
                    doSomethingInBlocking(nr)
//                println(Duration.between(start, Instant.now()))
                    time.stop(perf1)
                    emit(1)
                }
            }
            .collect { }
    }
var running = AtomicInteger(0)
    launch {
        while(true) {
            println("Running: ${running.get()}, pool size: ${pool.size()}")
            delay(1000)
        }
    }
    launch {
        flow<Int> {
            var nr = 0
            while (true) {
                emit(nr++)
            }
        }
            .flatMapMerge(20) { nr ->
                flow {
                    running.incrementAndGet()
                    val time = Timer.start()
                    val start = Instant.now()
                    doSomethingInBlocking(nr)
//                println(Duration.between(start, Instant.now()))
                    time.stop(perf2)
                    running.decrementAndGet()
                    emit(1)
                }
            }
            .collect { }
    }

    println("STARTED")



}

