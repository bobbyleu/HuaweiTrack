package org.nxy.hasstools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.nxy.hasstools.objects.AmapViewModel
import org.nxy.hasstools.objects.FusedProvider
import org.nxy.hasstools.objects.KillPowerAlertViewModel
import org.nxy.hasstools.objects.LocationViewModel
import org.nxy.hasstools.objects.User
import org.nxy.hasstools.objects.UserViewModel
import org.nxy.hasstools.services.LOCATION_WORKING_STATUS_ACTION
import org.nxy.hasstools.services.LocationWorkerStatus
import org.nxy.hasstools.services.refreshKeepAliveConfig
import org.nxy.hasstools.services.startLocationWork
import org.nxy.hasstools.services.stopLocationWork
import org.nxy.hasstools.ui.components.ButtonSettingsItem
import org.nxy.hasstools.ui.components.RadioSettingsItem
import org.nxy.hasstools.ui.components.RadioSettingsOption
import org.nxy.hasstools.ui.components.SettingsBackgroundColor
import org.nxy.hasstools.ui.components.SettingsCard
import org.nxy.hasstools.ui.components.SwitchSettingsItem
import org.nxy.hasstools.ui.location.EditAmapKeyActivity
import org.nxy.hasstools.ui.network.EditNetworkActivity
import org.nxy.hasstools.ui.permission.KillPowerAlertGrantPermissionActivity
import org.nxy.hasstools.ui.permission.LocationGrantPermissionActivity
import org.nxy.hasstools.ui.theme.AppTheme
import org.nxy.hasstools.ui.user.CreateUserActivity
import org.nxy.hasstools.ui.user.EditUserActivity
import org.nxy.hasstools.ui.wifigeofence.ManageWifiGeofenceActivity
import org.nxy.hasstools.utils.Json
import org.nxy.hasstools.utils.UpdateCheckResult
import org.nxy.hasstools.utils.UpdateChecker
import org.nxy.hasstools.utils.amap.AMap
import org.nxy.hasstools.utils.checkGroupPermissions
import org.nxy.hasstools.utils.checkProviderEnabled
import org.nxy.hasstools.utils.killPowerAlertPermissionGroup
import org.nxy.hasstools.utils.locationPermissionGroup
import java.net.URL

internal class MainActivityViewModel : ViewModel() {
    val selectedPage = mutableIntStateOf(0)

    val locationEnabled = mutableStateOf(false)

    val networkTriggerEnabled = mutableStateOf(false)

    val fusedProvider = mutableStateOf(FusedProvider.NONE)

    val users = mutableStateListOf<User>()

    val killPowerAlertEnabled = mutableStateOf(false)

    var workingStatus = mutableStateOf(LocationWorkerStatus.get())
}

class MainActivity : ComponentActivity() {
    // 动态定义receiver
    private val workingStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            println("workingStatusReceiver received: $intent")

            if (intent != null) {
                val viewModel by viewModels<MainActivityViewModel>()

                viewModel.workingStatus.value = intent.getStringExtra("status") ?: LocationWorkerStatus.UNKNOWN

                println("收到广播: ${viewModel.workingStatus.value}")
            }
        }
    }

    private val amapViewModel: AmapViewModel by viewModels()

    private val userViewModel: UserViewModel by viewModels()

    private val killPowerAlertViewModel: KillPowerAlertViewModel by viewModels()

    private val locationViewModel: LocationViewModel by viewModels()

    private val createUserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent = result.data ?: return@registerForActivityResult

                val item = data.getStringExtra("item") ?: return@registerForActivityResult

                val user = Json.decodeFromString<User>(item)

                userViewModel.addUserItem(user)

                val viewModel by viewModels<MainActivityViewModel>()

                viewModel.users.add(user)

                println("result: $result")
            } else {
                println("result: $result")
            }
        }

    private val editUserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent = result.data ?: return@registerForActivityResult

                val item = data.getStringExtra("item") ?: return@registerForActivityResult
                val type = data.getIntExtra("type", 0)

                val updatedUser = Json.decodeFromString<User>(item)
                println("updatedUserItem: $updatedUser")
                println("type: $type")

                val viewModel by viewModels<MainActivityViewModel>()

                when (type) {
                    -1 -> { // 删除
                        // 清理该用户存储的 mTLS 客户端证书文件与服务端 CA 文件
                        if (updatedUser.clientCertPath.isNotEmpty()) {
                            java.io.File(updatedUser.clientCertPath).delete()
                        }
                        if (updatedUser.serverCaPath.isNotEmpty()) {
                            java.io.File(updatedUser.serverCaPath).delete()
                        }
                        viewModel.users.removeIf { it.userId == updatedUser.userId }
                        userViewModel.removeUserItem(updatedUser)
                    }

                    else -> { // 更新
                        viewModel.users.replaceAll {
                            if (it.userId == updatedUser.userId) updatedUser else it
                        }
                        userViewModel.updateUserItem(updatedUser)
                    }
                }

                println("result: $result")
            } else {
                println("result: $result")
            }
        }

    private val amapApiKeyLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent = result.data ?: return@registerForActivityResult

                val runtimeApiKey = data.getStringExtra("runtime_api_key") ?: ""

                amapViewModel.setRuntimeApiKey(runtimeApiKey)

                println("result: $result")
            } else {
                println("result: $result")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 注册receiver
        registerReceiver(
            workingStatusReceiver,
            IntentFilter(LOCATION_WORKING_STATUS_ACTION),
            RECEIVER_NOT_EXPORTED
        )

        val viewModel by viewModels<MainActivityViewModel>()

        locationViewModel.locationConfig.value.apply {
            viewModel.locationEnabled.value = enabled
            viewModel.networkTriggerEnabled.value = networkTriggerEnabled
            viewModel.fusedProvider.value = fusedProvider
        }

        userViewModel.userConfig.value.apply {
            viewModel.users.clear()
            viewModel.users.addAll(items)
        }

        killPowerAlertViewModel.killPowerAlertConfig.value.apply {
            val allPermissionGranted = checkGroupPermissions(this@MainActivity, killPowerAlertPermissionGroup)
            viewModel.killPowerAlertEnabled.value = enabled && allPermissionGranted
        }

        setContent {
            StartLocationUI(
                this,
                viewModel
            )
        }
    }

    override fun onResume() {
        super.onResume()

        // 从各配置子页面（Wi-Fi 地理围栏、网络偏好、高德 Key 等）返回主界面时，
        // 通知运行中的保活服务按最新配置重新注册监听器，让改动立即生效，无需停用重启。
        refreshKeepAliveConfig(this)
    }

    override fun onDestroy() {
        super.onDestroy()

        // 取消注册receiver
        unregisterReceiver(workingStatusReceiver)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun StartLocationUI(
        context: Context,
        viewModel: MainActivityViewModel
    ) {
        var showCrashDialog by remember { mutableStateOf(false) }

        // 布局
        AppTheme {
            // 导航栏
            val selectedPageItemRef = viewModel.selectedPage
            val pageItems = listOf("主页", "用户", "设置")
            val selectedPageIcons = listOf(Icons.Filled.Home, Icons.Filled.AccountCircle, Icons.Filled.Settings)
            val unselectedPageIcons = listOf(Icons.Outlined.Home, Icons.Outlined.AccountCircle, Icons.Outlined.Settings)

            Scaffold(
                topBar = {
                    if (selectedPageItemRef.intValue == 0) {
                        return@Scaffold
                    }

                    TopAppBar(
                        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            titleContentColor = MaterialTheme.colorScheme.primary,
                        ),
                        title = {
                            Text(
                                text = pageItems[selectedPageItemRef.intValue],
                                modifier = Modifier.combinedClickable(
                                    onClick = { },
                                    onLongClick = {
                                        showCrashDialog = true
                                    }
                                )
                            )
                        }
                    )
                },
                content = { padding ->
                    when (selectedPageItemRef.intValue) {
                        0 -> HomePage(context, viewModel, padding)
                        1 -> UserPage(context, viewModel, padding)
                        2 -> SettingsPage(context, viewModel, padding)
                    }
                },
                bottomBar = {
                    NavigationBar {
                        pageItems.forEachIndexed { index, item ->
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        if (selectedPageItemRef.intValue == index) selectedPageIcons[index] else unselectedPageIcons[index],
                                        contentDescription = item
                                    )
                                },
                                label = { Text(item) },
                                selected = selectedPageItemRef.intValue == index,
                                onClick = { selectedPageItemRef.intValue = index }
                            )
                        }
                    }
                }
            )

            // 崩溃确认对话框
            if (showCrashDialog) {
                AlertDialog(
                    onDismissRequest = { showCrashDialog = false },
                    title = { Text("崩溃测试") },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text("确定要这么做吗？")
                        }
                    },
                    confirmButton = {
                        TextButton(
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            onClick = {
                                showCrashDialog = false
                                // 触发崩溃
                                throw RuntimeException("崩溃测试")
                            }
                        ) {
                            Text("确定")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showCrashDialog = false }
                        ) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    }

    @Composable
    private fun HomePage(context: Context, viewModel: MainActivityViewModel, padding: PaddingValues) {
        // 定义变量保存输入框内容
        val workingStatus by viewModel.workingStatus

        val hasUser = viewModel.users.isNotEmpty()
        val hasEnabledUser = viewModel.users.any { it.enabled }

        val canStopWork = workingStatus == LocationWorkerStatus.RUNNING
        val canStartWork = workingStatus == LocationWorkerStatus.STOPPED && hasEnabledUser

        val isWorking = workingStatus != LocationWorkerStatus.STOPPED

        val containerSize = LocalWindowInfo.current.containerSize

        val screenWidth = with(LocalDensity.current) { containerSize.width.toDp() }
        val screenHeight =
            with(LocalDensity.current) { containerSize.height.toDp() } -
                    padding.calculateTopPadding() -
                    padding.calculateBottomPadding()

        val btnSize = minOf(screenWidth * 0.6f, screenHeight * 0.6f, 300.dp) // 80% 的宽高，取较小的值
        val btnPaddingTop = screenHeight * 0.1f

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(top = btnPaddingTop)
                .verticalScroll(rememberScrollState())
                .padding(24.dp, 0.dp)
        ) {
            val icon = if (isWorking) {
                R.drawable.ic_location_on
            } else {
                R.drawable.ic_location_off
            }

            val bgColor = if (isWorking) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }

            val tintColor = if (isWorking) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.secondary
            }

            Button(
                modifier = Modifier
                    .size(btnSize)
                    .background(bgColor, shape = CircleShape),
                // 颜色
                colors = ButtonDefaults.buttonColors(
                    containerColor = bgColor,
                    contentColor = tintColor
                ),
                onClick = {
                    if (canStopWork) {
                        Toast.makeText(
                            context,
                            "正在停止……",
                            Toast.LENGTH_SHORT
                        ).show()

                        // 停止服务
                        stopLocationWork(context)
                        // 更新配置状态
                        locationViewModel.setEnabled(false)
                        viewModel.locationEnabled.value = false
                    } else if (canStartWork) {
                        val allPermissionGranted = checkGroupPermissions(this@MainActivity, locationPermissionGroup)

                        if (!allPermissionGranted) {
                            // 进入LocationGrantPermissionActivity
                            startActivity(Intent(this@MainActivity, LocationGrantPermissionActivity::class.java))
                            return@Button
                        }

                        Toast.makeText(
                            context,
                            "正在启动……",
                            Toast.LENGTH_SHORT
                        ).show()

                        // 启动服务
                        lifecycleScope.launch(IO) {
                            if (startLocationWork(context, userViewModel.userConfig.value)) {
                                // 更新配置状态
                                locationViewModel.setEnabled(true)
                                runOnUiThread {
                                    viewModel.locationEnabled.value = true
                                }
                            } else {
                                runOnUiThread {
                                    Toast.makeText(
                                        context,
                                        "启动失败",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                },
                enabled = canStartWork || canStopWork
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = "定位",
                    modifier = Modifier.size(btnSize * 0.5f)
                )
            }

            Text(
                text = if (isWorking) "已启用" else "已停用",
                style = MaterialTheme.typography.headlineMedium,
                color = tintColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = screenHeight * 0.05f),
            )
        }

        // 置于屏幕底部
        Box(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(align = Alignment.BottomCenter)
        ) {
            AnimatedVisibility(
                visible = !hasEnabledUser,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(padding)
                        .padding(24.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer,
                            shape = MaterialTheme.shapes.large
                        )
                        .shadow(2.dp, shape = MaterialTheme.shapes.large),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp, 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = "错误",
                            modifier = Modifier.size(32.dp),
                        )

                        Text(
                            text = if (hasUser) "请至少启用一个用户。" else "请至少创建一个用户。",
                            style = MaterialTheme.typography.bodyMedium,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        TextButton(
                            onClick = {
                                viewModel.selectedPage.intValue = 1
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                        ) {
                            Text(text = "设置")
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun UserPage(context: Context, viewModel: MainActivityViewModel, padding: PaddingValues) {
        // 用户
        val workingStatus by viewModel.workingStatus

        val canEdit = workingStatus == LocationWorkerStatus.STOPPED

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(18.dp, 0.dp)
        ) {
            // 用户列表
            val userItems = viewModel.users

            LazyColumn(
                modifier = Modifier.padding(0.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(userItems.size) { index ->
                    val userItem = userItems[index]

                    Card {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = canEdit
                                ) {
                                    // 打开EditUserActivity
                                    editUserLauncher.launch(
                                        Intent(context, EditUserActivity::class.java).apply {
                                            putExtra("item", Json.encodeToString(userItem))
                                        }
                                    )
                                }
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainer
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 用户头像
                            Column(
                                modifier = Modifier
                                    .wrapContentWidth()
                                    .padding(18.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.extraLarge
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = "用户头像",
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .height(32.dp)
                                        .width(32.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Column(
                                // 只使用有限的空间，没空间就省略
                                modifier = Modifier.weight(1f)
                            ) {
                                // 用户名称
                                Text(
                                    text = userItem.userName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1
                                )

                                val urlObj = URL(userItem.url)

                                // 服务器地址
                                Text(
                                    text = urlObj.host,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1
                                )
                            }

                            // 启用开关
                            Switch(
                                // 靠右
                                modifier = Modifier.padding(18.dp, 0.dp),
                                checked = userItem.enabled,
                                enabled = canEdit,
                                onCheckedChange = {
                                    // 更新userItems
                                    userItems[index] = userItem.copy(enabled = it)
                                    userViewModel.setEnabled(userItem.userId, it)
                                },
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(align = Alignment.BottomEnd)
        ) {
            AnimatedVisibility(
                visible = canEdit,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        // 打开CreateUserActivity
                        createUserLauncher.launch(Intent(context, CreateUserActivity::class.java))
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(padding)
                        .padding(24.dp)
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "创建用户"
                    )
                }
            }
        }
    }

    @Composable
    private fun SettingsPage(context: Context, viewModel: MainActivityViewModel, padding: PaddingValues) {
        // 设置
        // 原先此处为 canEdit = (workingStatus == STOPPED)，即"定位运行时辅助功能全部置灰不可改"。
        // 现改为运行时可编辑：
        //   · 融合定位：定位服务每次取位置都会重新读取配置，改完立即生效；
        //   · 网络状态触发器 / Wi-Fi 地理围栏：变更后通过 ACTION_REFRESH_CONFIG
        //     通知保活服务重新注册监听器（KeepAliveForegroundService.refreshRegistrations），
        //     无需"先停用再启用"才能让新配置生效。
        // 因此取消运行期锁定。保留该变量以对接各设置项的 enabled 参数。
        val canEdit = true

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(SettingsBackgroundColor)
                .padding(padding),
        ) {
            SettingsCard("辅助功能") {
                val systemFusedSupported = remember { checkProviderEnabled("fused") }
                val amapConfig by amapViewModel.amapConfig.collectAsState()
                val hasAmapKey = AMap.hasStaticApiKey || amapConfig.runtimeApiKey.isNotBlank()

                RadioSettingsItem(
                    title = "融合定位",
                    description = "融合定位依赖第三方云服务，可提升定位的精度和速度。",
                    enabled = canEdit,
                    options = listOf(
                        RadioSettingsOption(
                            value = FusedProvider.NONE,
                            label = {
                                Text(
                                    text = "禁用",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        ),
                        RadioSettingsOption(
                            value = FusedProvider.SYSTEM,
                            label = {
                                Text(
                                    text = "系统",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            enabled = systemFusedSupported,
                            tip = {
                                if (!systemFusedSupported) {
                                    Text(
                                        text = "设备不支持",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        ),
                        RadioSettingsOption(
                            value = FusedProvider.GOOGLE,
                            label = {
                                Text(
                                    text = "谷歌",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            enabled = false,
                            tip = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // 服务条款
                                    Text(
                                        "服务条款", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        textDecoration = TextDecoration.Underline,
                                        modifier = Modifier.clickable {
                                            startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    "https://policies.google.com/terms".toUri()
                                                ),
                                                null
                                            )
                                        }
                                    )

                                    // 隐私政策
                                    Text(
                                        "隐私政策", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        textDecoration = TextDecoration.Underline,
                                        modifier = Modifier.clickable {
                                            startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    "https://policies.google.com/privacy".toUri()
                                                ),
                                                null
                                            )
                                        }
                                    )
                                }

                                Text(
                                    text = "暂不支持",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        ),
                        RadioSettingsOption(
                            value = FusedProvider.AMAP,
                            label = {
                                Text(
                                    text = "高德",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            enabled = hasAmapKey,
                            tip = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // 服务条款
                                    Text(
                                        "服务条款", style = MaterialTheme.typography.bodySmall,
                                        // 下划线
                                        color = MaterialTheme.colorScheme.primary,
                                        textDecoration = TextDecoration.Underline,
                                        modifier = Modifier
                                            .clickable {
                                                startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        "https://lbs.amap.com/home/terms".toUri()
                                                    ),
                                                    null
                                                )
                                            }
                                    )

                                    // 隐私政策
                                    Text(
                                        "隐私政策", style = MaterialTheme.typography.bodySmall,
                                        // 下划线
                                        color = MaterialTheme.colorScheme.primary,
                                        textDecoration = TextDecoration.Underline,
                                        modifier = Modifier
                                            .clickable {
                                                startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        "https://lbs.amap.com/home/privacy".toUri()
                                                    ),
                                                    null
                                                )
                                            }
                                    )

                                    // 配置key
                                    if (!AMap.hasStaticApiKey) {
                                        Text(
                                            "配置", style = MaterialTheme.typography.bodySmall,
                                            // 下划线
                                            color = if (hasAmapKey) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.error
                                            },
                                            textDecoration = TextDecoration.Underline,
                                            modifier = Modifier
                                                .clickable {
                                                    amapApiKeyLauncher.launch(
                                                        Intent(
                                                            this@MainActivity,
                                                            EditAmapKeyActivity::class.java
                                                        ).apply {
                                                            putExtra("runtime_api_key", amapConfig.runtimeApiKey)
                                                        }
                                                    )
                                                }
                                        )
                                    }
                                }

                                if (!hasAmapKey) {
                                    Text(
                                        text = "未授权，请先配置",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        )
                    ),
                    value = viewModel.fusedProvider.value,
                    valueText = when (viewModel.fusedProvider.value) {
                        FusedProvider.NONE -> "禁用"
                        FusedProvider.SYSTEM -> "系统"
                        FusedProvider.AMAP -> "高德"
                        FusedProvider.GOOGLE -> "谷歌"
                    },
                    onSelectChange = { provider ->
                        viewModel.fusedProvider.value = provider
                        locationViewModel.setFusedProvider(provider)
                    }
                )

                // 添加网络状态变化时定位设置
                SwitchSettingsItem(
                    title = "网络状态触发器",
                    description = "当网络状态发生变化时（例如从固定 WiFi 切换至流量）自动上报一次定位。",
                    enabled = canEdit,
                    checked = viewModel.networkTriggerEnabled.value,
                    onCheckedChange = {
                        viewModel.networkTriggerEnabled.value = it
                        locationViewModel.setNetworkTriggerEnabled(it)
                        // 通知运行中的保活服务立即按新配置重新注册网络监听器，
                        // 无需"先停用再启用"；服务未运行时该广播无人接收，启动时会自行按配置注册
                        refreshKeepAliveConfig(context)
                    }
                )

                ButtonSettingsItem(
                    title = "地理围栏",
                    enabled = canEdit,
                    onClick = {
                        startActivity(
                            Intent(
                                this@MainActivity,
                                ManageWifiGeofenceActivity::class.java
                            )
                        )
                    }
                )

                // 网络配置入口
                ButtonSettingsItem(
                    title = "网络偏好",
                    onClick = {
                        startActivity(Intent(this@MainActivity, EditNetworkActivity::class.java))
                    }
                )
            }

            SettingsCard("杂项") {
                SwitchSettingsItem(
                    title = "移除高耗电通知",
                    description = "自动移除 Home Assistant 应用和位置上报的耗电通知。该功能仅在荣耀 MagicOS 设备上生效。",
                    checked = viewModel.killPowerAlertEnabled.value,
                    onCheckedChange = {
                        if (it) {
                            val allPermissionGranted =
                                checkGroupPermissions(this@MainActivity, killPowerAlertPermissionGroup)

                            if (!allPermissionGranted) {
                                // 进入KillPowerAlertGrantPermissionActivity
                                startActivity(
                                    Intent(
                                        this@MainActivity,
                                        KillPowerAlertGrantPermissionActivity::class.java
                                    )
                                )
                                return@SwitchSettingsItem
                            }
                        }

                        viewModel.killPowerAlertEnabled.value = it
                        killPowerAlertViewModel.setEnabled(it)
                    },
                )
            }

            // 关于部分
            var showUpdateDialog by remember { mutableStateOf(false) }
            var isCheckingUpdate by remember { mutableStateOf(false) }
            var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
            var isDownloading by remember { mutableStateOf(false) }
            var downloadProgress by remember { mutableFloatStateOf(0f) }

            SettingsCard("关于") {
                // 检查更新
                ButtonSettingsItem(
                    title = "检查更新",
                    description = "当前版本：v${UpdateChecker.getCurrentVersionName(context)}",
                    enabled = !isCheckingUpdate && !isDownloading,
                    onClick = {
                        isCheckingUpdate = true
                        lifecycleScope.launch(IO) {
                            val result = UpdateChecker.checkForUpdate()
                            updateResult = result
                            isCheckingUpdate = false
                            showUpdateDialog = true
                        }
                    }
                )

                // 开源仓库
                ButtonSettingsItem(
                    title = "开源仓库",
                    onClick = {
                        UpdateChecker.openRepoPage(context)
                    }
                )
            }

            // 更新检查对话框
            if (showUpdateDialog) {
                when (val result = updateResult) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        AlertDialog(
                            onDismissRequest = {
                                if (!isDownloading) {
                                    showUpdateDialog = false
                                }
                            },
                            title = { Text("发现新版本") },
                            text = {
                                Column(
                                    modifier = Modifier.verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "当前版本：${result.currentVersion}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "最新版本：${result.latestVersion}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    if (result.releaseNotes.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "更新说明：",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = result.releaseNotes,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    if (isDownloading) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "下载中：${(downloadProgress * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        androidx.compose.material3.LinearProgressIndicator(
                                            progress = { downloadProgress },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                if (!isDownloading) {
                                    TextButton(
                                        onClick = {
                                            UpdateChecker.openReleasesPage(context)
                                        }
                                    ) {
                                        Text("查看")
                                    }
                                    Row {
                                        if (result.apkAsset != null) {
                                            TextButton(
                                                onClick = {
                                                    isDownloading = true
                                                    downloadProgress = 0f
                                                    lifecycleScope.launch(IO) {
                                                        val downloadResult = UpdateChecker.downloadApk(
                                                            result.apkAsset
                                                        ) { downloaded, total ->
                                                            if (total > 0) {
                                                                downloadProgress = downloaded.toFloat() / total
                                                            }
                                                        }
                                                        isDownloading = false
                                                        when (downloadResult) {
                                                            is UpdateChecker.DownloadResult.Success -> {
                                                                UpdateChecker.installApk(context, downloadResult.file)
                                                                showUpdateDialog = false
                                                            }

                                                            is UpdateChecker.DownloadResult.DigestMismatch -> {
                                                                runOnUiThread {
                                                                    Toast.makeText(
                                                                        context, downloadResult.message,
                                                                        Toast.LENGTH_LONG
                                                                    ).show()
                                                                }
                                                            }

                                                            is UpdateChecker.DownloadResult.Failed -> {
                                                                runOnUiThread {
                                                                    Toast.makeText(
                                                                        context, downloadResult.message,
                                                                        Toast.LENGTH_SHORT
                                                                    ).show()
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            ) {
                                                Text("下载")
                                            }
                                        }
                                    }
                                }
                            },
                            dismissButton = {
                                Row {
                                    if (!isDownloading) {
                                        TextButton(
                                            onClick = { showUpdateDialog = false }
                                        ) {
                                            Text("取消")
                                        }
                                    }
                                }
                            }
                        )
                    }

                    is UpdateCheckResult.UpToDate -> {
                        AlertDialog(
                            onDismissRequest = { showUpdateDialog = false },
                            title = { Text("已是最新版本") },
                            text = {
                                Text("当前版本 ${result.currentVersion} 已是最新版本。")
                            },
                            confirmButton = {
                                TextButton(onClick = { showUpdateDialog = false }) {
                                    Text("确定")
                                }
                            }
                        )
                    }

                    is UpdateCheckResult.Error -> {
                        AlertDialog(
                            onDismissRequest = { showUpdateDialog = false },
                            title = { Text("失败") },
                            text = {
                                Text(result.message)
                            },
                            confirmButton = {
                                TextButton(onClick = { showUpdateDialog = false }) {
                                    Text("确定")
                                }
                            }
                        )
                    }

                    null -> {
                        // 加载中状态
                        AlertDialog(
                            onDismissRequest = { },
                            title = { Text("检查更新") },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text("正在检查更新...")
                                }
                            },
                            confirmButton = { }
                        )
                    }
                }
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun PreviewStartLocationUI() {
        val viewModel = MainActivityViewModel().apply {

        }

        StartLocationUI(
            this,
            viewModel
        )
    }
}
