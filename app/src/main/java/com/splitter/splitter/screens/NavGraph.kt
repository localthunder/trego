package com.splitter.splitter.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun NavGraph(navController: NavHostController, context: Context, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = "login",
        modifier = modifier
    ) {
        composable("login") { LoginScreen(navController, context) }
        composable("register") { RegisterScreen(navController, context) }
        composable("home") { HomeScreen(navController) }
        composable("institutions") { InstitutionsScreen(navController, context) }
    }
}