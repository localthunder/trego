package com.helgolabs.trego.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.AppDatabase
import com.helgolabs.trego.data.local.dataClasses.PreferenceKeys
import com.helgolabs.trego.data.repositories.TransactionRepository
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.SyncManagerProvider
import com.helgolabs.trego.ui.screens.AddExpenseScreen
import com.helgolabs.trego.ui.screens.AddGroupScreen
import com.helgolabs.trego.ui.screens.BankAccountsScreen
import com.helgolabs.trego.ui.screens.DeepLinkTestScreen
import com.helgolabs.trego.ui.screens.ForgotPasswordScreen
import com.helgolabs.trego.ui.screens.GroupBalancesScreen
import com.helgolabs.trego.ui.screens.GroupDetailsScreen
import com.helgolabs.trego.ui.screens.GroupSettingsScreen
import com.helgolabs.trego.ui.screens.GroupTotalsScreen
import com.helgolabs.trego.ui.screens.GroupedInstitutionsScreen
import com.helgolabs.trego.ui.screens.HomeScreen
import com.helgolabs.trego.ui.screens.InstitutionSelectionScreen
import com.helgolabs.trego.ui.screens.InviteMembersScreen
import com.helgolabs.trego.ui.screens.LoginScreen
import com.helgolabs.trego.ui.screens.PaymentScreen
import com.helgolabs.trego.ui.screens.ProfileScreen
import com.helgolabs.trego.ui.screens.RegisterScreen
import com.helgolabs.trego.ui.screens.ResetPasswordScreen
import com.helgolabs.trego.ui.screens.SettingsScreen
import com.helgolabs.trego.ui.screens.SettleUpScreen
import com.helgolabs.trego.ui.screens.TransactionsScreen
import com.helgolabs.trego.ui.screens.UserGroupsScreen
import com.helgolabs.trego.ui.viewmodels.GroupViewModel
import com.helgolabs.trego.ui.viewmodels.PaymentsViewModel
import com.helgolabs.trego.ui.viewmodels.UserViewModel
import com.helgolabs.trego.utils.getUserIdFromPreferences

@Composable
fun NavGraph(
    navController: NavHostController,
    context: Context,
    userId: Int,
    apiService: ApiService,
    modifier: Modifier = Modifier,
    viewModelFactory: ViewModelProvider.Factory,
    themeMode: String = PreferenceKeys.ThemeMode.SYSTEM
) {
    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    val syncManagerProvider = remember {
        SyncManagerProvider(
            context = context,
            apiService = apiService,
            database = database
        )
    }
    val myApplication = context.applicationContext as MyApplication
    val groupViewModel: GroupViewModel = viewModel(factory = myApplication.viewModelFactory)

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {

        composable(
            route = "login?inviteCode={inviteCode}",
            arguments = listOf(navArgument("inviteCode") {
                type = NavType.StringType
                nullable = true
            })
        ) { backStackEntry ->
            val inviteCode = backStackEntry.arguments?.getString("inviteCode")
            LoginScreen(
                navController = navController,
                inviteCode = inviteCode
            )
        }
        composable(route = "register") {
            RegisterScreen(navController)
        }
        composable(route = "forgot_password") {
            ForgotPasswordScreen(navController)
        }
        composable(
            route = "reset_password/{token}",
            arguments = listOf(
                navArgument("token") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val token = backStackEntry.arguments?.getString("token") ?: return@composable
            ResetPasswordScreen(
                navController = navController,
                token = token
            )
        }
        composable(route = "home") {
            HomeScreen(navController, context)
        }
        composable("institutions?returnRoute={returnRoute}",
            arguments = listOf(
                navArgument("returnRoute") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val returnRoute = backStackEntry.arguments?.getString("returnRoute")
            GroupedInstitutionsScreen(
                navController = navController,
                context = context,
                returnRoute = returnRoute
            )
        }

        // Institution selection screen after user taps a bank group
        composable("institution_selection/{bankName}?returnRoute={returnRoute}",
            arguments = listOf(
                navArgument("bankName") {
                    type = NavType.StringType
                },
                navArgument("returnRoute") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val bankName = backStackEntry.arguments?.getString("bankName") ?: ""
            val returnRoute = backStackEntry.arguments?.getString("returnRoute")
            InstitutionSelectionScreen(
                navController = navController,
                context = context,
                bankName = bankName,
                returnRoute = returnRoute
            )
        }
        composable(route = "test") {
            DeepLinkTestScreen(navController)
        }

        // Also ensure your test deep link is handled in the NavGraph
        composable(
            route = "deep-link-test",
        ) {
            DeepLinkTestScreen(navController)
        }
        composable(
            route = "profile/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: return@composable
            ProfileScreen(
                navController = navController,
                userViewModel = viewModel(factory = viewModelFactory),
                bankAccountViewModel = viewModel(factory = viewModelFactory),
                userPreferencesViewModel = viewModel(factory = viewModelFactory),
                userId = userId
            )
        }
        composable(route = "settings") {
            SettingsScreen(
                navController = navController,
                userId = userId
            )
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
        composable("addGroup") {
            AddGroupScreen(navController, LocalContext.current)
        }
        composable("userGroups/{groupId}") { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId")?.toIntOrNull()
            groupId?.let {
                UserGroupsScreen(navController = navController)
            }
        }
        composable(
            route = "groupDetails/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId")?.toIntOrNull() ?: return@composable
            val paymentsViewModel = backStackEntry.sharedViewModel<PaymentsViewModel>(navController, viewModelFactory)
            GroupDetailsScreen(
                navController = navController,
                groupId = groupId,
                groupViewModel = groupViewModel,
                themeMode = themeMode  // Pass the theme mode here
            )
        }
        composable(
            route = "paymentDetails/{groupId}/{paymentId}?transactionId={transactionId}&amount={amount}&description={description}&creditorName={creditorName}&currency={currency}&bookingDateTime={bookingDateTime}&remittanceInfo={remittanceInfo}&institutionId={institutionId}",
            arguments = listOf(
                navArgument("groupId") { type = NavType.IntType },
                navArgument("paymentId") { type = NavType.IntType },
                navArgument("transactionId") { type = NavType.StringType; nullable = true },
                navArgument("amount") { type = NavType.StringType; nullable = true },
                navArgument("description") { type = NavType.StringType; nullable = true },
                navArgument("creditorName") { type = NavType.StringType; nullable = true },
                navArgument("currency") { type = NavType.StringType; nullable = true },
                navArgument("bookingDateTime") { type = NavType.StringType; nullable = true },
                navArgument("remittanceInfo") { type = NavType.StringType; nullable = true },
                navArgument("institutionId") { type = NavType.StringType; nullable = true}
            )
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getInt("groupId") ?: return@composable
            val paymentId = backStackEntry.arguments?.getInt("paymentId") ?: 0
            PaymentScreen(navController, groupId, paymentId, context)
        }
        composable(
            route = "addExpense/{groupId}",
            arguments = listOf(
                navArgument("groupId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getInt("groupId") ?: return@composable
            val transactionRepository = remember {
                syncManagerProvider.provideTransactionRepository()
            }
            AddExpenseScreen(
                navController = navController,
                context = context,
                groupId = groupId,
            )
        }
        composable(
            route = "inviteMembers/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.IntType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getInt("groupId") ?: return@composable
            InviteMembersScreen(navController, groupId)
        }
        composable(
            route = "groupBalances/{groupId}",
            arguments = listOf(
                navArgument("groupId") { type = NavType.IntType }
            )
        ){backStackEntry ->
            val groupId = backStackEntry.arguments?.getInt("groupId") ?: return@composable
            GroupBalancesScreen(navController, context, groupId, groupViewModel, themeMode)
        }
        composable(
            route = "groupSettings/{groupId}",
            arguments = listOf(
                navArgument("groupId") { type = NavType.IntType }
            )
        ){backStackEntry ->
            val groupId = backStackEntry.arguments?.getInt("groupId") ?: return@composable
            GroupSettingsScreen(
                navController = navController,
                groupId = groupId,
                groupViewModel = groupViewModel,
                themeMode = themeMode
            )
        }
        composable(
            route = "groupTotals/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.IntType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getInt("groupId") ?: return@composable
            GroupTotalsScreen(
                navController = navController,
                context = context,
                groupId = groupId,
                groupViewModel = groupViewModel,
                themeMode = themeMode  // Pass the theme mode here
            )
        }
        composable(
            route = "settleUp/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.IntType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getInt("groupId") ?: return@composable
            SettleUpScreen(
                navController = navController,
                groupId = groupId,
                groupViewModel = groupViewModel,
                themeMode = themeMode  // Pass the theme mode here
            )
        }
        composable(
            route = "invite/{inviteCode}",
            arguments = listOf(navArgument("inviteCode") { type = NavType.StringType })
        ) { backStackEntry ->
            val inviteCode = backStackEntry.arguments?.getString("inviteCode")
            val context = LocalContext.current
            val groupViewModel =
                backStackEntry.sharedViewModel<GroupViewModel>(navController, viewModelFactory)
            val userViewModel =
                backStackEntry.sharedViewModel<UserViewModel>(navController, viewModelFactory)

            LaunchedEffect(inviteCode) {
                if (inviteCode != null) {
                    val userId = getUserIdFromPreferences(context)
                    if (userId != null) {
                        // User is logged in, handle group join
                        groupViewModel.joinGroupByInvite(inviteCode)
                            .onSuccess { groupId ->
                                navController.navigate("groupDetails/$groupId") {
                                    popUpTo("invite/$inviteCode") { inclusive = true }
                                }
                            }
                    } else {
                        // User not logged in, redirect to login
                        navController.navigate("login?inviteCode=$inviteCode") {
                            popUpTo("invite/$inviteCode") { inclusive = true }
                        }
                    }
                }
            }
        }
    }
}
