package lilmuff1.bsml.config

const val NULLS_PROXY_DNS = "dns.nullsproxy.com"
const val NULLS_PROXY_DNS_IP = "45.77.211.77"
const val NULLS_PROXY_BACKUP_IPS = "45.77.211.77, 161.189.28.62, 52.83.179.16, 52.83.90.66"
const val DEFAULT_DOH_URL = "https://dns.nullsproxy.com/dns-query"
const val CLOUDFLARE_DOH_URL = "https://cloudflare-dns.com/dns-query"
const val GOOGLE_DOH_URL = "https://dns.google/dns-query"
const val QUAD9_DOH_URL = "https://dns.quad9.net/dns-query"

data class DnsServerPreset(
    val name: String,
    val dohUrl: String,
    val bootstrapIp: String
)

val DNS_SERVER_PRESETS = listOf(
    DnsServerPreset("Null's Proxy", DEFAULT_DOH_URL, NULLS_PROXY_DNS_IP),
    DnsServerPreset("Cloudflare", CLOUDFLARE_DOH_URL, "1.1.1.1"),
    DnsServerPreset("Google", GOOGLE_DOH_URL, "8.8.8.8"),
    DnsServerPreset("Quad9", QUAD9_DOH_URL, "9.9.9.9")
)

fun findPresetByUrl(url: String): DnsServerPreset? {
    val normalized = url.trim().lowercase()
    return DNS_SERVER_PRESETS.firstOrNull { it.dohUrl.lowercase() == normalized }
}
