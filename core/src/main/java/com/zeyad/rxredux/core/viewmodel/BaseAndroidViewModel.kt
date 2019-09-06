package com.zeyad.rxredux.core.viewmodel

import android.app.Application
import android.os.Parcelable
import androidx.lifecycle.AndroidViewModel
import io.reactivex.subjects.BehaviorSubject

abstract class BaseAndroidViewModel<I, R, S : Parcelable, E>(app: Application) :
        AndroidViewModel(app), IBaseViewModel<I, R, S, E> {

    override val currentStateStream: BehaviorSubject<Any> = BehaviorSubject.create()
}
