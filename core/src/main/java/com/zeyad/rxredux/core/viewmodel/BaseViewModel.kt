package com.zeyad.rxredux.core.viewmodel

import android.os.Parcelable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.zeyad.rxredux.core.ErrorEffect
import com.zeyad.rxredux.core.LoadingEffect
import com.zeyad.rxredux.core.SuccessEffect
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject

private const val ARG_STATE = "arg_state"

@Suppress("UNCHECKED_CAST")
abstract class BaseViewModel<I, R, S : Parcelable, E> constructor(private val savedStateHandle: SavedStateHandle? = null) : ViewModel(), IBaseViewModel<I, R, S, E> {
    var viewModelListener: ViewModelListener? = null

    private val compositeDisposable = CompositeDisposable()

    val states: BehaviorSubject<State> by lazy { createStatesSubject() }

    val effects = PublishSubject.create<Effect>()

    val progress = PublishSubject.create<Boolean>()

    val errors = PublishSubject.create<Error>()

    override val intents: PublishSubject<I> = PublishSubject.create()

    fun bind(initialState: S, intents: () -> Observable<I>) {
        currentPModel = if (states.value == null) {
            states.onNext(initialState as State)
            initialState
        } else {
            states.value as S
        }

        intents().mergeWith(this.intents).toResult().run {
            observeStates(this as Flowable<Result<R, I>>)
            observeEffects(this as Flowable<Result<E, I>>)
            observeErrors(this)
            observeProgress(this)
        }
    }

    fun observe(lifecycleOwner: LifecycleOwner, init: VieModelListenerHelper.() -> Unit) {
        val helper = VieModelListenerHelper()
        helper.init()
        this.viewModelListener = helper
        removeListenerOnDestroy(lifecycleOwner)
    }

    private fun observeProgress(results: Flowable<Result<E, I>>) {
        effectStream(results)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { notifyProgressChanged(it is LoadingEffect) }
                .addTo(compositeDisposable)
    }

    private fun observeErrors(results: Flowable<Result<E, I>>) {
        effectStream(results)
                .observeOn(AndroidSchedulers.mainThread())
                .ofType(ErrorEffect::class.java)
                .map { Error(it.errorMessage, it.error) }
                .subscribe(::notifyError)
                .addTo(compositeDisposable)
    }

    private fun observeEffects(results: Flowable<Result<E, I>>) {
        effectStream(results)
                .observeOn(AndroidSchedulers.mainThread())
                .doAfterNext { middleware(it) }
                .ofType(SuccessEffect::class.java)
                .map { it.bundle }
                .ofType(Effect::class.java)
                .subscribe(::notifyEffect)
                .addTo(compositeDisposable)
    }

    private fun observeStates(results: Flowable<Result<R, I>>) {
        stateStream(results, currentPModel)
                .observeOn(AndroidSchedulers.mainThread())
                .doAfterNext { middleware(it) }
                .filter { it.bundle is State }
                .map { it.bundle as State }
                .subscribe {
                    saveState()
                    notifyNewState(it)
                    notifyProgressChanged(false)
                }
                .addTo(compositeDisposable)
    }

    private fun notifyProgressChanged(isLoading: Boolean) {
        viewModelListener?.progress?.invoke(isLoading)
        progress.onNext(isLoading)
    }

    private fun notifyEffect(effect: Effect) {
        viewModelListener?.effects?.invoke(effect)
        effects.onNext(effect)
    }

    private fun notifyError(error: Error) {
        viewModelListener?.errors?.invoke(error)
        errors.onNext(error)
    }

    private fun notifyNewState(state: State) {
        viewModelListener?.states?.invoke(state)
        states.onNext(state)
    }

    private fun saveState() {
        savedStateHandle?.set(ARG_STATE, states.value)
    }

    override lateinit var currentPModel: S

    override lateinit var disposable: Disposable

    override fun onCleared() {
        super.onCleared()
        onClearImpl()
        compositeDisposable.dispose()
    }

    private fun createStatesSubject(): BehaviorSubject<State> {
        val savedState: State? = savedStateHandle?.get(ARG_STATE)
        return if (savedState != null) {
            BehaviorSubject.createDefault(savedState)
        } else {
            BehaviorSubject.create()
        }
    }

    private fun removeListenerOnDestroy(lifecycleOwner: LifecycleOwner) {
        val lifecycleObserver = object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            fun onDestroy() {
                viewModelListener = null
                lifecycleOwner.lifecycle.removeObserver(this)
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
    }
}
