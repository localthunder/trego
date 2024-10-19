package com.splitter.splittr.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AppViewModelFactory(
    private val dependencies: Map<Class<out ViewModel>, () -> ViewModel>
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return dependencies[modelClass]?.invoke() as? T
            ?: throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}