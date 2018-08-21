package com.zeyad.rxredux.core.viewmodel

import android.arch.lifecycle.ViewModel
import android.os.Parcelable
import com.jakewharton.rx.ReplayingShare
import com.zeyad.rxredux.core.*
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableTransformer
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.annotations.NonNull
import io.reactivex.functions.BiFunction
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers

/**
 * @author Zeyad Gasser.
 */
abstract class BaseViewModel<S : Parcelable> : ViewModel() {
    abstract fun stateReducer(): StateReducer<S>

    abstract fun mapEventsToActions(): Function<BaseEvent<*>, Flowable<*>>

    fun processEvents(events: Observable<BaseEvent<*>>, initialState: S?): Flowable<UIModel<S>> =
            events.toFlowable(BackpressureStrategy.BUFFER)
                    .compose<UIModel<S>>(uiModelsTransformer(initialState))
                    .compose(ReplayingShare.instance())

    private fun uiModelsTransformer(initialState: S?): FlowableTransformer<BaseEvent<*>, UIModel<S>> =
            FlowableTransformer { events ->
                events.observeOn(Schedulers.computation())
                        .flatMap { event ->
                            Flowable.just(event)
                                    .flatMap(mapEventsToActions())
                                    .compose(mapActionsToResults(event.javaClass.simpleName))
                        }
                        .distinctUntilChanged { t1: Result<*>, t2: Result<*> -> t1 == t2 }
                        .scan<UIModel<S>>(resolveInitialState(initialState), reducer())
                        .distinctUntilChanged { t1: UIModel<*>, t2: UIModel<*> -> t1 == t2 }
                        .observeOn(AndroidSchedulers.mainThread())
            }

    private fun resolveInitialState(initialState: S?): UIModel<S> =
            if (initialState == null)
                EmptyState()
            else SuccessState(initialState)

    @NonNull
    private fun mapActionsToResults(eventName: String): FlowableTransformer<Any, Result<*>> =
            FlowableTransformer { it ->
                it.map<Result<*>> { SuccessResult(it, eventName) }
                        .onErrorReturn { ErrorResult(it, eventName) }
                        .observeOn(AndroidSchedulers.mainThread())
                        .startWith(LoadingResult(eventName))
                        .observeOn(Schedulers.computation())
            }

    @NonNull
    private fun reducer(): BiFunction<UIModel<S>, Result<*>, UIModel<S>> =
            BiFunction { currentUIModel, result ->
                when (result) {
                    is LoadingResult -> when (currentUIModel) {
                        is LoadingState -> LoadingState(currentUIModel.bundle, result.event)
                        is SuccessState -> LoadingState(currentUIModel.bundle, result.event)
                        else -> throw IllegalStateException("Can not reduce from $currentUIModel to LoadingState")
                    }
                    is ErrorResult -> ErrorState(result.error, result.event)
                    is SuccessResult<*> -> when (currentUIModel) {
                        is SuccessState -> SuccessState(stateReducer().reduce(result.bundle!!,
                                result.event, currentUIModel.bundle), result.event)
                        is LoadingState -> SuccessState(stateReducer().reduce(result.bundle!!, result.event,
                                currentUIModel.bundle), result.event)
                        else -> throw IllegalStateException("Can not reduce from $currentUIModel to SuccessState")
                    }
                }
            }
}