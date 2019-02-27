package com.zeyad.rxredux.screens

import android.os.Parcelable
import android.support.v4.app.Fragment
import android.util.Pair
import android.view.View
import android.widget.Toast
import com.zeyad.rxredux.core.view.BaseActivity
import com.zeyad.rxredux.core.viewmodel.BaseViewModel
import com.zeyad.rxredux.snackbar.SnackBarFactory

abstract class BaseActivity<S : Parcelable, VM : BaseViewModel<S>> : BaseActivity<S, VM>() {

    /**
     * Adds a [Fragment] to this activity's layout.
     *
     * @param containerViewId The container view to where add the fragment.
     * @param fragment The fragment to be added.
     */
    @SafeVarargs
    fun addFragment(containerViewId: Int,
                    fragment: Fragment,
                    currentFragTag: String?,
                    vararg sharedElements: Pair<View, String>) {
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        for (pair in sharedElements) {
            fragmentTransaction.addSharedElement(pair.first, pair.second)
        }
        if (currentFragTag == null || currentFragTag.isEmpty()) {
            fragmentTransaction.addToBackStack(fragment.tag)
        } else {
            fragmentTransaction.addToBackStack(currentFragTag)
        }
        fragmentTransaction.add(containerViewId, fragment, fragment.tag).commit()
    }

    fun removeFragment(tag: String) =
            supportFragmentManager.findFragmentByTag(tag)?.let {
                supportFragmentManager.beginTransaction().remove(it)
                        .commit()
            }

    @JvmOverloads
    fun showToastMessage(message: String, duration: Int = Toast.LENGTH_LONG) =
            Toast.makeText(this, message, duration).show()

    /**
     * Shows a [android.support.design.widget.Snackbar] messageId.
     *
     * @param message An string representing a messageId to be shown.
     */
    fun showSnackBarMessage(view: View, message: String, duration: Int) =
            SnackBarFactory.getSnackBar(SnackBarFactory.TYPE_INFO, view, message, duration).show()

    fun showSnackBarWithAction(typeSnackBar: String,
                               view: View,
                               message: String,
                               actionText: String,
                               onClickListener: View.OnClickListener) =
            SnackBarFactory
                    .getSnackBarWithAction(typeSnackBar, view, message, actionText, onClickListener)
                    .show()

    fun showSnackBarWithAction(typeSnackBar: String,
                               view: View,
                               message: String,
                               actionText: Int,
                               onClickListener: View.OnClickListener) {
        showSnackBarWithAction(typeSnackBar, view, message, getString(actionText), onClickListener)
    }

    /**
     * Shows a [android.support.design.widget.Snackbar] errorResult messageId.
     *
     * @param message  An string representing a messageId to be shown.
     * @param duration Visibility duration.
     */
    fun showErrorSnackBar(message: String, view: View, duration: Int) =
            SnackBarFactory.getSnackBar(SnackBarFactory.TYPE_ERROR, view, message, duration).show()

    fun showErrorSnackBarWithAction(message: String, view: View, actionText: String,
                                    onClickListener: View.OnClickListener) =
            showSnackBarWithAction(SnackBarFactory.TYPE_ERROR, view, message, actionText, onClickListener)
}
