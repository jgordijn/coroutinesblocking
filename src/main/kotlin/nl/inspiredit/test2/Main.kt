package nl.inspiredit.test2

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import nl.inspiredit.prometheus
import nl.inspiredit.prometheusRegistry
import nl.inspiredit.success
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
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
            println("No connection")
            Thread.sleep(10)
            if (start.isAfter(Instant.now())) {
                if (retries > 0) getConnection(retries - 1, timeout)
                else throw TimeoutException()
            }
            elem = connections.poll()
        }
        return elem
    }

    fun release(connection: Connection) {
        connections.add(connection)
    }
}

val poolsize = 35

val database = Executors.newFixedThreadPool(poolsize).asCoroutineDispatcher()

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
                val time = Timer.start()
                Thread.sleep(Random.nextLong(60, 250))
//                println("$nr-$connection: ${Thread.currentThread().name}")
                time.stop(success)
                pool.release(connection)
            } catch (e: Exception) {
                println("$nr: ${e.javaClass}")
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
                    time.stop(perf2)
                    emit(1)
                }
            }
            .collect { }
    }

    println("STARTED")



}

