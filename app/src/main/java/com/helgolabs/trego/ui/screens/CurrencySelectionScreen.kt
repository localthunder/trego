//// CurrencySelectionScreen.kt
//
package com.helgolabs.trego.ui.screens
//
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.unit.dp
//import androidx.navigation.NavController
//import com.helgolabs.trego.ui.components.GlobalTopAppBar
//import com.helgolabs.trego.ui.theme.GlobalTheme
//import com.helgolabs.trego.utils.CurrencyUtils
//
//@Composable
//fun CurrencySelectionScreen(navController: NavController, onCurrencySelected: (String) -> Unit) {
//    var searchQuery by remember { mutableStateOf("") }
//    val filteredCurrencies = CurrencyUtils.currencyNames.filterKeys {
//        it.contains(searchQuery, ignoreCase = true) ||
//                CurrencyUtils.currencySymbols[it]?.contains(searchQuery, ignoreCase = true) == true ||
//                CurrencyUtils.currencyNames[it]?.contains(searchQuery, ignoreCase = true) == true
//    }
//
//    GlobalTheme {
//        Scaffold(
//            topBar = {
//                GlobalTopAppBar(title = { Text("Select Currency") })
//            },
//            content = { padding ->
//                Column(
//                    modifier = Modifier
//                        .fillMaxSize()
//                        .padding(padding)
//                        .padding(16.dp)
//                ) {
//                    OutlinedTextField(
//                        value = searchQuery,
//                        onValueChange = { searchQuery = it },
//                        label = { Text("Search Currency") },
//                        modifier = Modifier.fillMaxWidth()
//                    )
//
//                    LazyColumn(
//                        modifier = Modifier
//                            .fillMaxSize()
//                            .padding(top = 16.dp),
//                        verticalArrangement = Arrangement.Top
//                    ) {
//                        items(filteredCurrencies.keys.toList().size) { index ->
//                            val currencyCode = filteredCurrencies.keys.toList()[index]
//                            val currencySymbol = CurrencyUtils.currencySymbols[currencyCode]
//                            val currencyName = filteredCurrencies[currencyCode]
//                            Row(
//                                modifier = Modifier
//                                    .fillMaxWidth()
//                                    .clickable {
//                                        onCurrencySelected(currencyCode)
//                                        navController.popBackStack()
//                                    }
//                                    .padding(16.dp)
//                            ) {
//                                Text(
//                                    text = "$currencyName ($currencySymbol) [$currencyCode]",
//                                    style = MaterialTheme.typography.bodyLarge
//                                )
//                            }
//                        }
//                    }
//                }
//            }
//        )
//    }
//}
