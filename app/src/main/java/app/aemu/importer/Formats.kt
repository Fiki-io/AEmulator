package app.aemu.importer

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A file tree of a partition image, delivered object by object (directories before their contents). */
interface TreeSource {
    /** [link] is set for symlinks; [data] opens the contents of a regular file */
    fun walk(visit: (path: String, isDir: Boolean, link: String?, mode: Int, size: Long, data: (() -> InputStream)?) -> Unit)
}

/**
 * super.img of Android 10+ (dynamic partitions): LP metadata at 4096 + 4096 (geometry) -> header, partition table and
 * extents. Returns the byte ranges (in the image) of the named partition, e.g. "system" or "system_a".
 */
object SuperImage {
    private const val GEOMETRY_MAGIC = 0x616c4467
    private const val HEADER_MAGIC = 0x414c5030

    fun probe(src: RandomSource): Boolean {
        if (src.size < 16384) return false
        val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        src.read(4096, b); b.flip()
        return b.int == GEOMETRY_MAGIC
    }

    /** name -> list of (offset in super, length) */
    fun partitions(src: RandomSource): Map<String, List<Pair<Long, Long>>> {
        val g = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
        src.read(4096, g); g.flip()
        if (g.getInt(0) != GEOMETRY_MAGIC) throw IOException("super: no LP geometry")
        val metaMax = g.getInt(40).toLong()
        val hdrOff = 4096L + 2 * 4096
        val h = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
        src.read(hdrOff, h); h.flip()
        if (h.getInt(0) != HEADER_MAGIC) throw IOException("super: no LP header")
        val headerSize = h.getInt(8)
        val tablesSize = h.getInt(44)
        fun desc(at: Int) = Triple(h.getInt(at), h.getInt(at + 4), h.getInt(at + 8))
        val parts = desc(80)
        val extents = desc(92)
        val t = ByteBuffer.allocate(tablesSize).order(ByteOrder.LITTLE_ENDIAN)
        src.read(hdrOff + headerSize, t); t.flip()
        val ext = (0 until extents.second).map { i ->
            val o = extents.first + i * extents.third
            Triple(t.getLong(o), t.getInt(o + 8), t.getLong(o + 12))
        }
        val out = LinkedHashMap<String, List<Pair<Long, Long>>>()
        for (i in 0 until parts.second) {
            val o = parts.first + i * parts.third
            val nb = ByteArray(36); t.position(o); t.get(nb)
            val name = String(nb, 0, nb.indexOf(0).let { if (it < 0) 36 else it }, Charsets.US_ASCII)
            val first = t.getInt(o + 40)
            val num = t.getInt(o + 44)
            out[name] = (first until first + num).mapNotNull { e ->
                ext.getOrNull(e)?.takeIf { it.second == 0 }?.let { (sectors, _, start) -> start * 512 to sectors * 512 }
            }
        }
        if (metaMax <= 0) throw IOException("super: bad geometry")
        return out
    }
}

/** A partition assembled from extents of another source (super.img). */
class ExtentSource(private val src: RandomSource, private val extents: List<Pair<Long, Long>>) : RandomSource {
    override val size: Long = extents.sumOf { it.second }
    override fun read(pos: Long, dst: ByteBuffer) {
        var p = pos
        while (dst.hasRemaining()) {
            if (p >= size) { while (dst.hasRemaining()) dst.put(0); return }
            var base = 0L
            val (off, len) = extents.first { e -> (p < base + e.second).also { if (!it) base += e.second } }
            val inExt = p - base
            val n = minOf(dst.remaining().toLong(), len - inExt).toInt()
            val lim = dst.limit()
            dst.limit(dst.position() + n)
            src.read(off + inExt, dst)
            dst.limit(lim)
            p += n
        }
    }
}

/**
 * Android A/B OTA payload.bin (CrAU). Extracts partitions (system, vendor, etc.) without external tools.
 */
object PayloadReader {
    private const val MAGIC = 0x43724155 // "CrAU"

    fun probe(h: ByteArray): Boolean =
        h.size >= 4 && h[0] == 'C'.code.toByte() && h[1] == 'r'.code.toByte() && h[2] == 'A'.code.toByte() && h[3] == 'U'.code.toByte()

    fun probe(src: RandomSource): Boolean {
        if (src.size < 24) return false
        val b = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        src.read(0, b); b.flip()
        return b.int == MAGIC
    }

    class Extent(val startBlock: Long, val numBlocks: Long)
    class Op(val type: Int, val dataOffset: Long, val dataLength: Long, val dstExtents: List<Extent>)
    class Part(val name: String, val size: Long, val ops: List<Op>)

    private fun manifest(src: RandomSource): Pair<List<Part>, Long>? {
        val b24 = ByteBuffer.allocate(24).order(ByteOrder.BIG_ENDIAN)
        src.read(0, b24); b24.flip()
        if (b24.int != MAGIC) return null
        val version = b24.long
        val manifestSize = b24.long
        val sigSize = if (version >= 2) {
            val b4 = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            src.read(20, b4); b4.flip()
            b4.int.toLong() and 0xffffffffL
        } else 0L

        val manifestOffset = if (version >= 2) 24L else 20L
        if (manifestSize <= 0 || manifestSize > 128 * 1024 * 1024) return null
        val manifestBuf = ByteBuffer.allocate(manifestSize.toInt())
        src.read(manifestOffset, manifestBuf); manifestBuf.flip()
        return parseManifest(manifestBuf.array(), manifestSize.toInt()) to manifestOffset + manifestSize + sigSize
    }

    fun partitionNames(src: RandomSource): List<String> = manifest(src)?.first?.map { it.name } ?: emptyList()

    fun extractPartition(src: RandomSource, partName: String, outFile: File, progress: (Float) -> Unit = {}): Boolean {
        val (partitions, dataOffsetBase) = manifest(src) ?: return false
        val target = partitions.firstOrNull { it.name.equals(partName, true) || it.name.equals("${partName}_a", true) } ?: return false

        RandomAccessFile(outFile, "rw").use { raf ->
            raf.setLength(0)
            val blockSize = 4096L
            val totalOps = target.ops.size.coerceAtLeast(1)
            for ((idx, op) in target.ops.withIndex()) {
                val dataOff = dataOffsetBase + op.dataOffset
                val dataLen = op.dataLength.toInt()
                val chunk = if (dataLen > 0) {
                    val cb = ByteBuffer.allocate(dataLen)
                    src.read(dataOff, cb); cb.flip()
                    cb.array()
                } else ByteArray(0)

                val decompressed: ByteArray? = when (op.type) {
                    0 -> chunk
                    1 -> org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(ByteArrayInputStream(chunk)).use { it.readBytes() }
                    8 -> org.tukaani.xz.XZInputStream(ByteArrayInputStream(chunk)).use { it.readBytes() }
                    6, 7 -> null
                    else -> throw IOException("payload.bin is an incremental update (operation ${op.type}): a full OTA package is needed")
                }

                var chunkPos = 0
                for (ext in op.dstExtents) {
                    if (decompressed == null) continue
                    val bytesToWrite = ext.numBlocks * blockSize
                    val available = minOf(bytesToWrite, (decompressed.size - chunkPos).toLong()).toInt()
                    if (available > 0) {
                        raf.seek(ext.startBlock * blockSize)
                        raf.write(decompressed, chunkPos, available)
                        chunkPos += available
                    }
                }
                if (idx % 20 == 0) progress(idx.toFloat() / totalOps)
            }
            if (target.size > 0 && raf.length() < target.size) {
                raf.setLength(target.size)
            }
        }
        return true
    }

    private fun parseManifest(data: ByteArray, length: Int): List<Part> {
        val parts = ArrayList<Part>()
        var pos = 0
        while (pos < length) {
            val (tag, nextPos) = readVarint(data, pos, length)
            if (nextPos == pos) break
            pos = nextPos
            val fieldNum = (tag ushr 3).toInt()
            val wireType = (tag and 0x7).toInt()
            if (fieldNum == 13 && wireType == 2) {
                val (len, p2) = readVarint(data, pos, length)
                pos = p2
                val partEnd = pos + len.toInt()
                val part = parsePartition(data, pos, minOf(partEnd, length))
                if (part != null) parts.add(part)
                pos = partEnd
            } else {
                pos = skipField(data, pos, length, wireType)
            }
        }
        return parts
    }

    private fun parsePartition(data: ByteArray, start: Int, end: Int): Part? {
        var pos = start
        var name = ""
        var size = 0L
        val ops = ArrayList<Op>()
        while (pos < end) {
            val (tag, nextPos) = readVarint(data, pos, end)
            if (nextPos == pos) break
            pos = nextPos
            val fieldNum = (tag ushr 3).toInt()
            val wireType = (tag and 0x7).toInt()
            when (fieldNum) {
                1 -> if (wireType == 2) {
                    val (len, p2) = readVarint(data, pos, end)
                    pos = p2
                    name = String(data, pos, len.toInt(), Charsets.UTF_8)
                    pos += len.toInt()
                } else pos = skipField(data, pos, end, wireType)
                7 -> if (wireType == 2) {
                    val (len, p2) = readVarint(data, pos, end)
                    pos = p2
                    val infoEnd = pos + len.toInt()
                    while (pos < infoEnd) {
                        val (t2, np2) = readVarint(data, pos, infoEnd)
                        if (np2 == pos) break; pos = np2
                        if ((t2 ushr 3).toInt() == 1 && (t2 and 7).toInt() == 0) {
                            val (s, np3) = readVarint(data, pos, infoEnd)
                            size = s; pos = np3
                        } else pos = skipField(data, pos, infoEnd, (t2 and 7).toInt())
                    }
                    pos = infoEnd
                } else pos = skipField(data, pos, end, wireType)
                8 -> if (wireType == 2) {
                    val (len, p2) = readVarint(data, pos, end)
                    pos = p2
                    val opEnd = pos + len.toInt()
                    val op = parseOp(data, pos, minOf(opEnd, end))
                    if (op != null) ops.add(op)
                    pos = opEnd
                } else pos = skipField(data, pos, end, wireType)
                else -> pos = skipField(data, pos, end, wireType)
            }
        }
        return if (name.isNotEmpty()) Part(name, size, ops) else null
    }

    private fun parseOp(data: ByteArray, start: Int, end: Int): Op? {
        var pos = start
        var type = 0
        var dataOff = 0L
        var dataLen = 0L
        val dstExtents = ArrayList<Extent>()
        while (pos < end) {
            val (tag, nextPos) = readVarint(data, pos, end)
            if (nextPos == pos) break
            pos = nextPos
            val fieldNum = (tag ushr 3).toInt()
            val wireType = (tag and 0x7).toInt()
            when (fieldNum) {
                1 -> if (wireType == 0) { val (v, np) = readVarint(data, pos, end); type = v.toInt(); pos = np } else pos = skipField(data, pos, end, wireType)
                2 -> if (wireType == 0) { val (v, np) = readVarint(data, pos, end); dataOff = v; pos = np } else pos = skipField(data, pos, end, wireType)
                3 -> if (wireType == 0) { val (v, np) = readVarint(data, pos, end); dataLen = v; pos = np } else pos = skipField(data, pos, end, wireType)
                6 -> if (wireType == 2) {
                    val (len, p2) = readVarint(data, pos, end)
                    pos = p2
                    val extEnd = pos + len.toInt()
                    var startBlock = 0L; var numBlocks = 0L
                    while (pos < extEnd) {
                        val (et, enp) = readVarint(data, pos, extEnd)
                        if (enp == pos) break; pos = enp
                        when ((et ushr 3).toInt()) {
                            1 -> { val (v, np) = readVarint(data, pos, extEnd); startBlock = v; pos = np }
                            2 -> { val (v, np) = readVarint(data, pos, extEnd); numBlocks = v; pos = np }
                            else -> pos = skipField(data, pos, extEnd, (et and 7).toInt())
                        }
                    }
                    dstExtents.add(Extent(startBlock, numBlocks))
                    pos = extEnd
                } else pos = skipField(data, pos, end, wireType)
                else -> pos = skipField(data, pos, end, wireType)
            }
        }
        return Op(type, dataOff, dataLen, dstExtents)
    }

    private fun readVarint(data: ByteArray, start: Int, end: Int): Pair<Long, Int> {
        var res = 0L
        var shift = 0
        var pos = start
        while (pos < end && shift < 64) {
            val b = data[pos++].toInt() and 0xff
            res = res or ((b and 0x7f).toLong() shl shift)
            if (b and 0x80 == 0) return res to pos
            shift += 7
        }
        return res to pos
    }

    private fun skipField(data: ByteArray, start: Int, end: Int, wireType: Int): Int {
        var pos = start
        when (wireType) {
            0 -> { while (pos < end && (data[pos++].toInt() and 0x80) != 0) {} }
            1 -> pos += 8
            2 -> { val (len, p2) = readVarint(data, pos, end); pos = p2 + len.toInt() }
            5 -> pos += 4
        }
        return pos.coerceAtMost(end)
    }
}

/** EROFS superblock probe (Android 13+ GSI images). */
object ErofsReader {
    private const val EROFS_SUPER_MAGIC_V1 = 0xE0F5E1E2.toInt()

    fun probe(src: RandomSource): Boolean {
        if (src.size < 2048) return false
        val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        src.read(1024, b); b.flip()
        return b.int == EROFS_SUPER_MAGIC_V1
    }
}
