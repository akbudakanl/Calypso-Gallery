package org.fossify.gallery

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.StandardOpenOption
import java.util.Random
import kotlin.math.roundToInt

/**
 * ADR §11.1 - segment boyutu olcumu (JVM).
 *
 * Normal test kosumunda KAPALIDIR; calistirmak icin:
 *   CIPHER_BENCHMARK=1 ./gradlew :app:testFossDebugUnitTest --tests "*V4SegmentBenchmark"
 *
 * Bu olcum goreli karsilastirma saglar (ayni makinede tum secenekler). Gercek cihaz
 * sayilari icin EncryptedMediaDataSource uzerindeki opt-in probe kullanilmalidir.
 */
class V4SegmentBenchmark {

    private val key = ByteArray(32) { (it * 7 + 3).toByte() }
    private val fileId = ByteArray(16) { (it * 11 + 1).toByte() }
    private val plaintextSize = 128L * 1024 * 1024
    private val ioBuffer = 256 * 1024
    private val readBufferSize = 8 * 1024

    @Test
    fun measureSegmentSizes() {
        if (System.getenv("CIPHER_BENCHMARK") != "1") {
            println("V4SegmentBenchmark: skipped (set CIPHER_BENCHMARK=1 to run)")
            return
        }

        val dir = File(System.getProperty("java.io.tmpdir"), "v4_segment_bench")
        dir.mkdirs()

        println("\n=== ADR 11.1 - segment benchmark (JVM, ${plaintextSize / (1024 * 1024)} MiB) ===")
        println("%-10s %-12s %-14s %-14s %-12s".format("segment", "overhead", "seq MiB/s", "seek avg ms", "seek p95 ms"))

        listOf(16 * 1024, 64 * 1024, 256 * 1024, 1024 * 1024).forEach { segment ->
            val file = File(dir, "seg_$segment.bin")
            val aead = aead(segment)

            writeEncrypted(aead, file)
            require(file.length() == aead.expectedCiphertextSize(plaintextSize)) {
                "ciphertext length mismatch for segment $segment"
            }

            val seqThroughputs = mutableListOf<Double>()
            repeat(3) { seqThroughputs += sequentialThroughput(aead, file) }
            val seekStats = seekLatency(aead, file)

            val overhead = 16.0 / segment * 100
            val medianSeq = seqThroughputs.sorted()[1]
            println(
                "%-10s %-12s %-14s %-14s %-12s".format(
                    "${segment / 1024} KiB",
                    "%.4f%%".format(overhead),
                    "%.1f".format(medianSeq),
                    "%.3f".format(seekStats.avg),
                    "%.3f".format(seekStats.p95)
                )
            )

            file.delete()
        }
        println("=== end benchmark ===\n")
    }

    private fun aead(segment: Int): AesGcmHkdfStreaming =
        AesGcmHkdfStreaming(key, "HmacSha256", 32, segment, 0)

    private fun writeEncrypted(aead: AesGcmHkdfStreaming, file: File) {
        val buf = ByteArray(ioBuffer) { (it * 31 + 7).toByte() }
        var remaining = plaintextSize
        FileOutputStream(file).use { fos ->
            aead.newEncryptingStream(fos, fileId).use { out ->
                while (remaining > 0) {
                    val chunk = minOf(buf.size.toLong(), remaining).toInt()
                    out.write(buf, 0, chunk)
                    remaining -= chunk
                }
            }
        }
    }

    private fun sequentialThroughput(aead: AesGcmHkdfStreaming, file: File): Double {
        val channel = openChannel(aead, file)
        val buf = ByteBuffer.allocate(ioBuffer)
        val start = System.nanoTime()
        var total = 0L
        try {
            while (channel.read(buf) != -1) {
                total += buf.position().toLong()
                buf.clear()
            }
        } finally {
            channel.close()
        }
        val seconds = (System.nanoTime() - start) / 1e9
        require(total == plaintextSize) { "unexpected plaintext size $total" }
        return (total / (1024.0 * 1024.0)) / seconds
    }

    private fun seekLatency(aead: AesGcmHkdfStreaming, file: File): Stats {
        val channel = openChannel(aead, file)
        val random = Random(42)
        val offsets = LongArray(50) { random.nextLong(plaintextSize) }
        val buf = ByteBuffer.allocate(readBufferSize)
        val samples = mutableListOf<Double>()
        try {
            offsets.forEach { offset ->
                channel.position(offset)
                buf.clear()
                val t0 = System.nanoTime()
                channel.read(buf)
                samples += (System.nanoTime() - t0) / 1e6
            }
        } finally {
            channel.close()
        }
        val sorted = samples.sorted()
        return Stats(
            avg = samples.average(),
            p95 = sorted[(sorted.size * 0.95).roundToInt().coerceIn(0, sorted.lastIndex)]
        )
    }

    private fun openChannel(aead: AesGcmHkdfStreaming, file: File): SeekableByteChannel {
        val raw: SeekableByteChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ)
        return try {
            aead.newSeekableDecryptingChannel(raw, fileId)
        } catch (e: Exception) {
            raw.close()
            throw e
        }
    }

    private data class Stats(val avg: Double, val p95: Double)
}
