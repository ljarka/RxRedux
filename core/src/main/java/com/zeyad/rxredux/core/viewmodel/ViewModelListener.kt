package com.zeyad.rxredux.core.viewmodel

interface ViewModelListener {
    var effects: (effect: Effect) -> Unit
    var states: (state: State) -> Unit
    var errors: (error: Error) -> Unit
    var progress: (isLoading: Boolean) -> Unit
}
