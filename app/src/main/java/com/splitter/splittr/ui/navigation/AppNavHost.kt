package com.splitter.splittr.ui.navigation
//
//import androidx.compose.runtime.Composable
//import androidx.lifecycle.ViewModelProvider
//import androidx.navigation.NavHostController
//import androidx.navigation.compose.NavHost
//import androidx.navigation.compose.composable
//import com.splitter.splitter.ui.screens.GroupDetailsScreen
//import com.splitter.splitter.ui.viewmodels.GroupViewModel
//import com.splitter.splitter.ui.viewmodels.PaymentsViewModel
//
//@Composable
//fun AppNavHost(
//    navController: NavHostController,
//    startDestination: String,
//    viewModelFactory: ViewModelProvider.Factory
//) {
//    NavHost(navController = navController, startDestination = startDestination) {
//        composable("groupDetails/{groupId}") { backStackEntry ->
//            val groupId = backStackEntry.arguments?.getString("groupId")?.toIntOrNull() ?: return@composable
//            val groupViewModel = backStackEntry.sharedViewModel<GroupViewModel>(navController, viewModelFactory)
//            val paymentsViewModel = backStackEntry.sharedViewModel<PaymentsViewModel>(navController, viewModelFactory)
//            GroupDetailsScreen(
//                navController = navController,
//                groupId = groupId
//            )
//        }
//        // Other composables...
//    }
//}