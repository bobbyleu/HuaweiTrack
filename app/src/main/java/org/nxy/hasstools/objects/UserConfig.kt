package org.nxy.hasstools.objects

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class UserConfig(
    val items: List<User> = emptyList(),
) {
    fun hasEnabledUser(): Boolean {
        return items.any { it.enabled }
    }
}

@Serializable
data class User(
    val enabled: Boolean = true,
    val userId: String = "${System.nanoTime()}-${UUID.randomUUID()}",
    val userType: String,
    val userName: String = "",
    val url: String = "http://homeassistant.local:8123",
    val token: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val webhookId: String = "",
    // mTLS 客户端证书（用于通过要求客户端证书的隧道，如 Cloudflare Access mTLS 访问 Home Assistant）
    val clientCertEnabled: Boolean = false,
    val clientCertPath: String = "",
    val clientCertPassword: String = "",
    // 额外信任的服务端 CA 证书（PEM/DER；自建隧道服务端证书不被系统信任时使用）
    val serverCaPath: String = "",
) {
    companion object {
        const val REGISTER_USER_TYPE = "register"

        const val BIND_USER_TYPE = "bind"
    }
}
