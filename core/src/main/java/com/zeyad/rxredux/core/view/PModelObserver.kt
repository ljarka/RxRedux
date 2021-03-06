package com.zeyad.rxredux.core.view

import android.os.Parcelable
import androidx.lifecycle.Observer
import com.zeyad.rxredux.core.*

class PModelObserver<I, V : BaseView<I, *, S, E, *>, S : Parcelable, E>(private val view: V) : Observer<PModel<*, I>> {
    override fun onChanged(pModel: PModel<*, I>?) {
        pModel?.apply {
            when (this) {
                is ErrorEffect -> view.bindError(errorMessage, intent, error)
                is SuccessEffect -> view.bindEffect(bundle as E)
                is SuccessState -> {
                    (bundle as? S)?.also {
                        view.setState(it)
                        view.bindState(it)
                    }
                }
            }
            view.toggleLoadingViews(this is LoadingEffect, intent)
        }
    }
}
