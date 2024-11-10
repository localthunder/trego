package com.splitter.splittr.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.splitter.splittr.data.local.AppDatabase
import com.splitter.splittr.data.local.repositories.TransactionRepository
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.ui.screens.AddExpenseScreen
import com.splitter.splittr.ui.screens.AddGroupScreen
import com.splitter.splittr.ui.screens.BankAccountsScreen
import com.splitter.splittr.ui.screens.CurrencySelectionScreen
import com.splitter.splittr.ui.screens.GroupBalancesScreen
import com.splitter.splittr.ui.screens.GroupDetailsScreen
import com.splitter.splittr.ui.screens.HomeScreen
import com.splitter.splittr.ui.screens.InstitutionsScreen
import com.splitter.splittr.ui.screens.InviteMembersScreen
import com.splitter.splittr.ui.screens.LoginScreen
import com.splitter.splittr.ui.screens.PaymentScreen
import com.splitter.splittr.ui.screens.RegisterScreen
import com.splitter.splittr.ui.screens.TransactionsScreen
import com.splitter.splittr.ui.screens.UserGroupsScreen
import com.splitter.splittr.ui.viewmodels.GroupViewModel
import com.splitter.splittr.ui.viewmodels.PaymentsViewModel
import com.splitter.splittr.utils.AppCoroutineDispatchers

@Composable
fun NavGraph(navController: NavHostController, context: Context, userId: Int, apiService: ApiService, modifier: Modifier = Modifier, viewModelFactory: ViewModelProvider.Factory
) {
    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }
    val bankAccountDao = database.bankAccountDao()
    val groupDao = database.groupDao()
    val groupMemberDao = database.groupMemberDao()
    val institutionDao = database.institutionDao()
    val paymentDao = database.paymentDao()
    val paymentSplitDao = database.paymentSplitDao()
    val transactionDao = database.transactionDao()
    val userDao = database.userDao()

    NavHost(
        navController = navController,
        startDestination = "login",
        modifier = modifier
    ) {
        composable(route = "login") {
            LoginScreen(navController)
        }
        composable(route = "register") {
            RegisterScreen(navController)
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
            val groupViewModel = backStackEntry.sharedViewModel<GroupViewModel>(navController, viewModelFactory)
            val paymentsViewModel = backStackEntry.sharedViewModel<PaymentsViewModel>(navController, viewModelFactory)
            GroupDetailsScreen(
                navController = navController,
                groupId = groupId
            )
        }
        composable(
            route = "paymentDetails/{groupId}/{paymentId}?transactionId={transactionId}&amount={amount}&description={description}&creditorName={creditorName}&currency={currency}&bookingDateTime={bookingDateTime}&remittanceInfo={remittanceInfo}",
            arguments = listOf(
                navArgument("groupId") { type = NavType.IntType },
                navArgument("paymentId") { type = NavType.IntType },
                navArgument("transactionId") { type = NavType.StringType; nullable = true },
                navArgument("amount") { type = NavType.StringType; nullable = true },
                navArgument("description") { type = NavType.StringType; nullable = true },
                navArgument("creditorName") { type = NavType.StringType; nullable = true },
                navArgument("currency") { type = NavType.StringType; nullable = true },
                navArgument("bookingDateTime") { type = NavType.StringType; nullable = true },
                navArgument("remittanceInfo") { type = NavType.StringType; nullable = true }
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
                TransactionRepository(
                    transactionDao = transactionDao,
                    bankAccountDao = bankAccountDao,
                    apiService = apiService,
                    dispatchers = AppCoroutineDispatchers(),
                    context = context
                )
            }
            AddExpenseScreen(
                navController = navController,
                context = context,
                groupId = groupId,
                transactionRepository = transactionRepository
            )
        }
        composable("currencySelection") {
            CurrencySelectionScreen(navController) { selectedCurrency ->
                navController.previousBackStackEntry?.savedStateHandle?.set("currency", selectedCurrency)
            }
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
            GroupBalancesScreen(navController, context, groupId)
        }
    }
}
