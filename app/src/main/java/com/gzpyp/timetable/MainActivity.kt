/*
 * 广职大课表 —— 广州职业技术大学教务系统课表客户端
 * Copyright (C) 2026 Campsisian
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPLv3）的条款
 * 再分发和/或修改它。本程序按“原样”提供，不带任何担保。
 * 完整条款见项目根目录的 LICENSE 文件。
 */

package com.gzpyp.timetable

import android.app.Activity
import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.gzpyp.timetable.notify.Reminders
import com.gzpyp.timetable.ui.AppTheme
import com.gzpyp.timetable.ui.LocalAppDark
import com.gzpyp.timetable.ui.SettingsScreen
import com.gzpyp.timetable.ui.TimetableScreen
import com.gzpyp.timetable.ui.TodayScreen
import com.gzpyp.timetable.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk 35 在 Android 15 上强制边到边，这里主动打开并自己处理 inset
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val s = vm.settings
            AppTheme(themeMode = s.themeMode, dynamicColor = s.dynamicColor) {
                SystemBarsEffect()
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 回到前台时对齐一次状态：跨天后周次要更新，部件也要跟着刷新；
        // 顺带把提醒重新排一遍（万一某个闹钟被系统省电策略清掉了）
        WidgetUpdater.refreshAll(this)
        val tt = vm.timetable
        val st = vm.settings
        lifecycleScope.launch(Dispatchers.IO) { Reminders.reschedule(this@MainActivity, tt, st) }
    }
}

/** 状态栏/导航栏图标跟随主题反色，深色主题下用浅色图标 */
@Composable
private fun SystemBarsEffect() {
    val view = LocalView.current
    if (view.isInEditMode) return
    // LocalView 的 context 可能是 ContextThemeWrapper，穿透找 Activity
    val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>()
        .firstOrNull() ?: return
    val window = activity.window
    val dark = LocalAppDark.current
    SideEffect {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }
}

@Composable
fun AppRoot(vm: AppViewModel) {
    // 只在启动时取一次，之后由用户点标签切换；改设置不会把当前页面抢走
    var tab by remember { mutableIntStateOf(vm.settings.defaultTab.coerceIn(0, 1)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 顶栏（TopAppBar）和底部导航栏各自处理系统栏 inset，
        // 这里清零，否则 Scaffold 和 TopAppBar 会各加一次状态栏高度
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp
            ) {
                TABS.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> TimetableScreen(vm)
                1 -> TodayScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}

private data class TabItem(val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem("课表", Icons.Default.DateRange),
    TabItem("今日", Icons.Default.Home),
    TabItem("设置", Icons.Default.Settings)
)
