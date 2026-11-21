package app.clearcall.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.clearcall.ui.appViewModel
import app.clearcall.ui.home.HomeScreen
import app.clearcall.ui.home.HomeViewModel
import app.clearcall.ui.library.LibraryScreen
import app.clearcall.ui.library.LibraryViewModel
import app.clearcall.ui.player.PlayerScreen
import app.clearcall.ui.player.PlayerViewModel
import app.clearcall.ui.settings.DeviceCheckScreen
import app.clearcall.ui.settings.SettingsScreen
import app.clearcall.ui.settings.SettingsViewModel

private object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val DEVICE = "device"
    const val PLAYER = "player/{id}"

    fun player(id: String) = "player/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "Home", Icons.Rounded.Home),
    Tab(Routes.LIBRARY, "Recordings", Icons.Rounded.GraphicEq),
    Tab(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
)

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val showBar = TABS.any { tab -> destination?.hierarchy?.any { it.route == tab.route } == true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    TABS.forEach { tab ->
                        val selected = destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.navigateToTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable(Routes.HOME) {
                val vm = appViewModel { HomeViewModel(it) }
                HomeScreen(
                    vm = vm,
                    padding = padding,
                    onOpenRecording = { id -> nav.navigate(Routes.player(id)) { launchSingleTop = true } },
                    onOpenLibrary = { nav.navigateToTab(Routes.LIBRARY) },
                    onOpenDeviceCheck = { nav.navigate(Routes.DEVICE) { launchSingleTop = true } },
                )
            }

            composable(Routes.LIBRARY) {
                val vm = appViewModel { LibraryViewModel(it) }
                LibraryScreen(
                    vm = vm,
                    padding = padding,
                    onOpenRecording = { id -> nav.navigate(Routes.player(id)) { launchSingleTop = true } },
                )
            }

            composable(Routes.SETTINGS) {
                val vm = appViewModel { SettingsViewModel(it) }
                SettingsScreen(
                    vm = vm,
                    padding = padding,
                    snackbar = snackbar,
                    onOpenDeviceCheck = { nav.navigate(Routes.DEVICE) { launchSingleTop = true } },
                )
            }

            composable(
                route = Routes.DEVICE,
                enterTransition = { slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280)) },
                popExitTransition = { slideOutHorizontally(tween(240)) { it / 3 } + fadeOut(tween(240)) },
            ) {
                val vm = appViewModel { SettingsViewModel(it) }
                Box(Modifier.padding(padding)) {
                    DeviceCheckScreen(vm = vm, onBack = { nav.popIfPossible() })
                }
            }

            composable(
                route = Routes.PLAYER,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
                enterTransition = { slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280)) },
                popExitTransition = { slideOutHorizontally(tween(240)) { it / 3 } + fadeOut(tween(240)) },
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id").orEmpty()
                val vm = appViewModel(key = "player-$id") { PlayerViewModel(it, id) }
                Box(Modifier.padding(padding)) {
                    PlayerScreen(vm = vm, snackbar = snackbar, onBack = { nav.popIfPossible() })
                }
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Never pops the start destination, so a double back (button + auto-exit after delete) can't blank the app. */
private fun NavHostController.popIfPossible() {
    if (previousBackStackEntry != null) popBackStack()
}
