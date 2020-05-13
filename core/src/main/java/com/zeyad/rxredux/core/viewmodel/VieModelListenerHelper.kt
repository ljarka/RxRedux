package com.zeyad.rxredux.core.viewmodel

class VieModelListenerHelper : ViewModelListener {
    override var effects: (effect: Effect) -> Unit = {}
    override var states: (state: State) -> Unit = {}
    override var progress: (isLoading: Boolean) -> Unit = {}
    override var errors: (error: Error) -> Unit = {}

    fun errors(errors: (error: Error) -> Unit) {
        this.errors = errors
    }

    fun effects(effects: (effect: Effect) -> Unit) {
        this.effects = effects
    }

    fun states(states: (state: State) -> Unit) {
        this.states = states
    }

    fun progress(progress: (isLoading: Boolean) -> Unit) {
        this.progress = progress
    }
}
