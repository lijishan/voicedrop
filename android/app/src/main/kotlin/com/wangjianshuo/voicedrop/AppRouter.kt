package com.wangjianshuo.voicedrop

import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

val LocalAuthStore = staticCompositionLocalOf<AuthStore> { error("AuthStore not provided") }
val LocalAPI = staticCompositionLocalOf<API> { error("API not provided") }
val LocalHttpClient = staticCompositionLocalOf<HttpClient> { error("HttpClient not provided") }
val LocalLibraryStore = staticCompositionLocalOf<LibraryStore> { error("LibraryStore not provided") }

sealed class Screen(val route: String) {
    data object Library : Screen("library")
    data object RecordingDetail : Screen("recording/{stem}") {
        fun createRoute(stem: String) = "recording/$stem"
    }
    data object Settings : Screen("settings")
    data object CommunityPost : Screen("community/{shareId}") {
        fun createRoute(shareId: String) = "community/$shareId"
    }
}

@Composable
fun AppRoot(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Library.route) {
        composable(Screen.Library.route) {
            LibraryView(navController = navController)
        }
        composable(
            route = Screen.RecordingDetail.route,
            arguments = listOf(navArgument("stem") { type = NavType.StringType })
        ) { backStackEntry ->
            val stem = backStackEntry.arguments?.getString("stem") ?: return@composable
            RecordingDetailView(stem = stem, navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsView(navController = navController)
        }
        composable(
            route = Screen.CommunityPost.route,
            arguments = listOf(navArgument("shareId") { type = NavType.StringType })
        ) { backStackEntry ->
            val shareId = backStackEntry.arguments?.getString("shareId") ?: return@composable
            CommunityPostView(shareId = shareId, navController = navController)
        }
    }
}
