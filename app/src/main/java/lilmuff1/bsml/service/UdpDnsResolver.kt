package lilmuff1.bsml.service

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

class UdpDnsResolver(
    val dnsServerIp: String,
    val dnsServerPort: Int = 53,
    private val timeoutMs: Int = 5000
) {
    private val cache = ConcurrentHashMap<String, List<InetAddress>>()
    private val socket: DatagramSocket by lazy {
        DatagramSocket().apply {
            soTimeout = timeoutMs
        }
    }

    fun resolve(hostname: String): List<InetAddress> {
        val cached = cache[hostname.lowercase()]
        if (cached != null) return cached
        return resolveUncached(hostname)
    }

    private fun resolveUncached(hostname: String): List<InetAddress> {
        return try {
            val query = buildDnsQuery(hostname)
            val serverAddress = InetSocketAddress(dnsServerIp, dnsServerPort)
            val response = sendUdpRequest(query, serverAddress)
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
        data.writeShort(0x1234)
        data.writeShort(0x0100)
        data.writeShort(1)
        data.writeShort(0)
        data.writeShort(0)
        data.writeShort(0)
        hostname.split('.').forEach { label ->
            data.writeByte(label.length)
            data.writeBytes(label)
        }
        data.writeByte(0)
        data.writeShort(1)
        data.writeShort(1)
        data.flush()
        return out.toByteArray()
    }

    private fun sendUdpRequest(query: ByteArray, serverAddress: InetSocketAddress): ByteArray {
        val responseBuffer = ByteArray(4096)
        val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
        socket.send(DatagramPacket(query, query.size, serverAddress))
        socket.receive(responsePacket)
        return responseBuffer.copyOfRange(0, responsePacket.length)
    }

    private fun parseDnsResponse(response: ByteArray): List<InetAddress> {
        if (response.size < 12) return emptyList()
        val data = java.io.DataInputStream(java.io.ByteArrayInputStream(response))
        data.readShort()
        data.readShort()
        val qdCount = data.readUnsignedShort()
        val anCount = data.readUnsignedShort()
        if (anCount == 0) return emptyList()
        data.readUnsignedShort()
        data.readUnsignedShort()
        repeat(qdCount) {
            skipDomainName(data)
            data.readUnsignedShort()
            data.readUnsignedShort()
        }
        val addresses = mutableListOf<InetAddress>()
        repeat(anCount) {
            skipDomainName(data)
            val type = data.readUnsignedShort()
            data.readUnsignedShort()
            data.readInt()
            val rdLength = data.readUnsignedShort()
            if (type == 1 && rdLength == 4) {
                val ipBytes = ByteArray(4)
                data.readFully(ipBytes)
                addresses.add(InetAddress.getByAddress(ipBytes))
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
                data.readByte()
                break
            }
            data.skipBytes(length)
        }
    }

    fun clearCache() {
        cache.clear()
    }

    fun close() {
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }
}
