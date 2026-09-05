package org.nxy.hasstools.utils

import android.net.wifi.ScanResult
import android.os.Build

/**
 * 安全读取 [ScanResult] 的 SSID。
 *
 * `ScanResult.getWifiSsid()` 是 **Android 13（API 33）** 才新增的方法。
 * 在 API 31/32 的设备上（例如基于 Android 12 兼容层运行的鸿蒙机型）直接调用会抛出：
 *
 * ```
 * java.lang.NoSuchMethodError: No virtual method getWifiSsid()Landroid/net/wifi/WifiSsid;
 * in class Landroid/net/wifi/ScanResult
 * ```
 *
 * 该异常属于 Error 而非 Exception，**try/catch(Exception) 捕获不到**，会直接导致进程崩溃：
 * - 发生在保活服务内 → 服务崩溃后被 START_STICKY 反复拉起，系统提示"位置上报屡次停止运行"；
 * - 发生在扫描界面 → 点击扫描即闪退。
 *
 * 因此必须用 [Build.VERSION.SDK_INT] 做分支，保证高版本方法在低版本设备上**根本不会被解析调用**。
 * 返回值已去除 SSID 两端的引号。
 */
@Suppress("DEPRECATION")
fun ScanResult.safeSsid(): String {
    val raw: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13+：使用官方推荐的 WifiSsid
        wifiSsid?.toString()
    } else {
        // Android 12 及以下：使用已废弃但唯一可用的 SSID 字段
        SSID
    }
    return raw?.removeSurrounding("\"") ?: ""
}
