package lilmuff1.bsml.service

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class DnsOverHttpsResolver(
    val dohUrl: String,
    private val bootstrapIp: String? = null
) {
    private val cache = ConcurrentHashMap<String, List<Inet4Address>>()

    fun resolve(hostname: String): List<Inet4Address> {
        val cached = cache[hostname.lowercase()]
        if (cached != null) return cached
        return resolveUncached(hostname)
    }

    private fun resolveUncached(hostname: String): List<Inet4Address> {
        return try {
            val query = buildDnsQuery(hostname)
            val response = sendDoHRequest(query)
            val addresses = parseDnsResponse(response)
            if (addresses.isNotEmpty()) {
                cache[hostname.lowercase()] = addresses
            }
            addresses
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildDnsQuery(hostname: String): ByteArray {
        val out = ByteArrayOutputStream()
        val data = DataOutputStream(out)
        // Header
        data.writeShort(0x1234) // Transaction ID
        data.writeShort(0x0100) // Flags: standard query, RD=1
        data.writeShort(1)      // QDCOUNT
        data.writeShort(0)      // ANCOUNT
        data.writeShort(0)      // NSCOUNT
        data.writeShort(0)      // ARCOUNT
        // Question
        hostname.split('.').forEach { label ->
            data.writeByte(label.length)
            data.writeBytes(label)
        }
        data.writeByte(0)       // End of QNAME
        data.writeShort(1)      // QTYPE: A
        data.writeShort(1)      // QCLASS: IN
        data.flush()
        return out.toByteArray()
    }

    private fun sendDoHRequest(query: ByteArray): ByteArray {
        val url = URL(dohUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/dns-message")
        connection.setRequestProperty("Accept", "application/dns-message")
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.doOutput = true
        connection.outputStream.use { it.write(query) }
        return connection.inputStream.use { it.readBytes() }
    }

    private fun parseDnsResponse(response: ByteArray): List<Inet4Address> {
        if (response.size < 12) return emptyList()
        val data = java.io.DataInputStream(java.io.ByteArrayInputStream(response))
        data.readShort()  // Transaction ID
        data.readShort()  // Flags
        val qdCount = data.readUnsignedShort()
        val anCount = data.readUnsignedShort()
        if (anCount == 0) return emptyList()
        data.readUnsignedShort()  // NSCOUNT
        data.readUnsignedShort()  // ARCOUNT
        // Skip question section
        repeat(qdCount) {
            skipDomainName(data)
            data.readUnsignedShort()  // QTYPE
            data.readUnsignedShort()  // QCLASS
        }
        // Parse answer section
        val addresses = mutableListOf<Inet4Address>()
        repeat(anCount) {
            skipDomainName(data)  // NAME (possibly compressed)
            val type = data.readUnsignedShort()
            data.readUnsignedShort()  // CLASS
            data.readInt()        // TTL
            val rdLength = data.readUnsignedShort()
            if (type == 1 && rdLength == 4) { // A record
                val ipBytes = ByteArray(4)
                data.readFully(ipBytes)
                val addr = Inet4Address.getByAddress(ipBytes)
                if (addr is Inet4Address) addresses.add(addr)
            } else {
                data.skipBytes(rdLength)
            }
        }
        return addresses
    }

    private fun skipDomainName(data: java.io.DataInputStream) {
        while (true) {
            val length = data.readUnsignedByte()
            if (length == 0) break
            if (length and 0xC0 == 0xC0) {
                data.readByte() // second byte of compressed pointer
                break
            }
            data.skipBytes(length)
        }
    }

    fun clearCache() {
        cache.clear()
    }
}
