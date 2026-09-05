package org.nxy.hasstools.utils

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * mTLS 客户端证书信息。
 *
 * @property sslSocketFactory 携带客户端私钥/证书的 SSLSocketFactory
 * @property trustManager 信任管理器（系统默认信任库 + 额外信任的 CA）
 * @property id 用于 HTTP 客户端缓存区分的稳定标识（由证书路径与密码派生）
 */
data class ClientCertInfo(
    val sslSocketFactory: SSLSocketFactory,
    val trustManager: X509TrustManager,
    val id: String
)

/**
 * 组合信任管理器：任一被委托的 TrustManager 校验通过即通过。
 * 用于"系统默认信任库 + 自定义 CA"并存。
 */
private class CompositeTrustManager(private val delegates: List<X509TrustManager>) : X509TrustManager {

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        var lastError: CertificateException? = null
        for (tm in delegates) {
            try {
                tm.checkClientTrusted(chain, authType)
                return
            } catch (e: CertificateException) {
                lastError = e
            }
        }
        throw lastError ?: CertificateException("No trust manager accepted the chain")
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        var lastError: CertificateException? = null
        for (tm in delegates) {
            try {
                tm.checkServerTrusted(chain, authType)
                return
            } catch (e: CertificateException) {
                lastError = e
            }
        }
        // 全部信任管理器都拒绝时，打印服务端证书链概要，便于定位"该上传哪个 CA"
        println(
            "CompositeTrustManager: 服务端证书链被所有信任源拒绝（${delegates.size} 个信任源）。" +
                "证书链: " + chain.joinToString(" -> ") { it.subjectX500Principal.name }
        )
        throw lastError ?: CertificateException("No trust manager accepted the chain")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return delegates.flatMap { it.acceptedIssuers.toList() }.toTypedArray()
    }
}

/**
 * 客户端证书加载工具。
 *
 * 从 PKCS12（.p12/.pfx）文件中读取客户端私钥与证书，构造用于 mTLS 的 [ClientCertInfo]。
 *
 * 关键实现点：
 * 1. 使用内嵌的完整版 BouncyCastle 读取 PKCS12——Android 系统自带的 stripped 版 BC
 *    不支持新版 OpenSSL（3.x）默认的 AES-256-CBC + SHA-256 MAC 加密算法，
 *    直接用 KeyStore.getInstance("PKCS12") 会抛
 *    "error constructing MAC: No installed provider supports this key: PKCS12Key"。
 * 2. 信任链 = 系统默认信任库 + 内置公共根证书（Let's Encrypt X1/X2/YE/YR，
 *    见 [BundledCaCerts]）+ p12 内含的 CA 证书 + 单独指定的服务端 CA 文件（PEM/DER），
 *    任一通过即可，解决自建隧道服务端证书或新 CA 层级
 *    "Trust anchor for certification path not found"。
 */
object ClientCertHelper {

    // 完整版 BouncyCastle Provider 实例（不全局注册，避免与系统 stripped 版命名冲突）
    private val bcProvider = BouncyCastleProvider()

    // 内置公共根证书（Let's Encrypt X1/X2 + 新 YE/YR 层级），惰性解析一次
    private val bundledCas: List<X509Certificate> by lazy {
        try {
            val cf = CertificateFactory.getInstance("X.509")
            BundledCaCerts.PEM_LIST.flatMap { pem ->
                cf.generateCertificates(pem.byteInputStream()).filterIsInstance<X509Certificate>()
            }.also {
                println("ClientCertHelper: 已加载内置公共根证书 ${it.size} 张（Let's Encrypt X1/X2/YE/YR）")
            }
        } catch (e: Throwable) {
            println("ClientCertHelper: 解析内置根证书失败（忽略，仅使用系统信任库）: ${e.message}")
            emptyList()
        }
    }

    /**
     * 构建携带客户端证书的 TLS 材料。
     *
     * @param p12Path PKCS12 文件路径（含私钥与证书）；为空表示不使用客户端证书（仅自定义信任）
     * @param password PKCS12 密码
     * @param serverCaPath 额外信任的服务端 CA 证书文件（PEM/DER，可含多张）；为空表示不额外信任
     */
    fun build(p12Path: String, password: String, serverCaPath: String = ""): ClientCertInfo? {
        return try {
            // 1) 读取 PKCS12（使用完整版 BC，兼容新版 OpenSSL 加密算法）
            var keyManagers: Array<javax.net.ssl.KeyManager>? = null
            val extraCas = mutableListOf<X509Certificate>()

            if (p12Path.isNotEmpty() && password.isNotEmpty()) {
                val keyStore = KeyStore.getInstance("PKCS12", bcProvider)
                FileInputStream(File(p12Path)).use { fis ->
                    keyStore.load(fis, password.toCharArray())
                }

                val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                kmf.init(keyStore, password.toCharArray())
                keyManagers = kmf.keyManagers

                // p12 中可能附带整条 CA 链，全部纳入额外信任
                val aliases = keyStore.aliases()
                while (aliases.hasMoreElements()) {
                    val alias = aliases.nextElement()
                    keyStore.getCertificateChain(alias)?.forEach { cert ->
                        (cert as? X509Certificate)?.let { extraCas.add(it) }
                    }
                    if (keyStore.isCertificateEntry(alias)) {
                        (keyStore.getCertificate(alias) as? X509Certificate)?.let { extraCas.add(it) }
                    }
                }
            }

            // 2) 读取服务端 CA 文件（PEM/DER，可含多张证书）
            if (serverCaPath.isNotEmpty()) {
                val cf = CertificateFactory.getInstance("X.509")
                FileInputStream(File(serverCaPath)).use { fis ->
                    cf.generateCertificates(fis).forEach { cert ->
                        (cert as? X509Certificate)?.let { extraCas.add(it) }
                    }
                }
            }

            // 3) 构造信任管理器：系统默认 + 内置公共根证书 + 额外 CA（按主题去重）
            val allCas = (extraCas + bundledCas).distinctBy { it.subjectX500Principal.name }
            val trustManager = buildTrustManager(allCas)

            // 4) 组装 SSLContext
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(keyManagers, arrayOf(trustManager), SecureRandom())

            ClientCertInfo(
                sslSocketFactory = sslContext.socketFactory,
                trustManager = trustManager,
                id = "$p12Path|$password|$serverCaPath".hashCode().toString()
            )
        } catch (e: Throwable) {
            println("ClientCertHelper: 构建客户端证书失败: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /** 系统默认信任管理器 */
    private fun systemTrustManagers(): List<X509TrustManager> {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>()
    }

    /** 系统默认 + 额外 CA 的组合信任管理器 */
    private fun buildTrustManager(extraCas: List<X509Certificate>): X509TrustManager {
        if (extraCas.isEmpty()) {
            return systemTrustManagers().first()
        }

        val customStore = KeyStore.getInstance(KeyStore.getDefaultType())
        customStore.load(null, null)
        extraCas.forEachIndexed { index, cert ->
            customStore.setCertificateEntry("extra_ca_$index", cert)
        }

        val tmfCustom = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmfCustom.init(customStore)
        val customTms = tmfCustom.trustManagers.filterIsInstance<X509TrustManager>()

        val combined = systemTrustManagers() + customTms
        return CompositeTrustManager(combined)
    }
}
