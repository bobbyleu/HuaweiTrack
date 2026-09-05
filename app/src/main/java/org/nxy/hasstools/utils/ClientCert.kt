package org.nxy.hasstools.utils

import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * mTLS 客户端证书信息。
 *
 * @property sslSocketFactory 携带客户端私钥/证书的 SSLSocketFactory
 * @property trustManager 信任管理器（使用系统默认信任库，以信任服务端公开证书）
 * @property id 用于 HTTP 客户端缓存区分的稳定标识（由证书路径与密码派生）
 */
data class ClientCertInfo(
    val sslSocketFactory: SSLSocketFactory,
    val trustManager: X509TrustManager,
    val id: String
)

/**
 * 客户端证书加载工具。
 *
 * 从 PKCS12（.p12/.pfx）文件中读取客户端私钥与证书，构造用于 mTLS 的 [ClientCertInfo]。
 * 服务端证书信任沿用系统默认信任库（Cloudflare 等公网证书本就被系统信任）。
 */
object ClientCertHelper {
    fun build(path: String, password: String): ClientCertInfo? {
        return try {
            val keyStore = KeyStore.getInstance("PKCS12")
            FileInputStream(File(path)).use { fis ->
                keyStore.load(fis, password.toCharArray())
            }

            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(keyStore, password.toCharArray())

            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            // 传入 null 使用系统默认信任库
            tmf.init(null)

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(kmf.keyManagers, tmf.trustManagers, SecureRandom())

            ClientCertInfo(
                sslSocketFactory = sslContext.socketFactory,
                trustManager = tmf.trustManagers[0] as X509TrustManager,
                id = "$path|$password".hashCode().toString()
            )
        } catch (e: Throwable) {
            println("ClientCertHelper: 构建客户端证书失败: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}
