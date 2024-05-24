package com.splitter.splitter.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.splitter.splitter.network.ApiService

@Composable
fun NavGraph(navController: NavHostController, context: Context, userId: Int, apiService: ApiService, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = "login",
        modifier = modifier
    ) {
        composable(route = "login") {
            LoginScreen(navController, context)
        }
        composable(route = "register") {
            RegisterScreen(navController, context)
        }
        composable(route = "home") {
            HomeScreen(navController, context)
        }
        composable(route = "institutions") {
            InstitutionsScreen(navController, context)
        }
        composable(
            route = "bankaccounts/{requisitionId}",
            arguments = listOf(navArgument("requisitionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val requisitionId = backStackEntry.arguments?.getString("requisitionId") ?: return@composable
            BankAccountsScreen(navController, context, requisitionId, userId)
        }
        composable(
            route = "transactions/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: return@composable
            TransactionsScreen(navController, context, userId)
        }
        composable("userGroups/{groupId}") { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId")?.toIntOrNull()
            groupId?.let {
                UserGroupsScreen(context = context, navController = navController)
            }
        }
        composable("groupDetails/{groupId}") { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId")?.toIntOrNull()
            groupId?.let {
                GroupDetailsScreen(navController, it, apiService)
            }
        }
        composable(
            route = "paymentDetails/{groupId}/{paymentId}",
            arguments = listOf(
                navArgument("groupId") { type = NavType.IntType },
                navArgument("paymentId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getInt("groupId") ?: return@composable
            val paymentId = backStackEntry.arguments?.getInt("paymentId") ?: return@composable
            PaymentScreen(navController, groupId, paymentId, apiService, context)
        }
    }
}
