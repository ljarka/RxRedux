package com.zeyad.rxredux.screens.list

import android.app.ActivityOptions
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Pair
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import com.jakewharton.rxbinding2.support.v7.widget.RxSearchView
import com.jakewharton.rxbinding2.support.v7.widget.scrollEvents
import com.zeyad.gadapter.*
import com.zeyad.gadapter.GenericAdapter.Companion.SECTION_HEADER
import com.zeyad.rxredux.R
import com.zeyad.rxredux.core.view.IBaseActivity
import com.zeyad.rxredux.screens.User
import com.zeyad.rxredux.screens.detail.IntentBundleState
import com.zeyad.rxredux.screens.detail.UserDetailActivity
import com.zeyad.rxredux.screens.detail.UserDetailFragment
import com.zeyad.rxredux.screens.list.viewHolders.EmptyViewHolder
import com.zeyad.rxredux.screens.list.viewHolders.SectionHeaderViewHolder
import com.zeyad.rxredux.screens.list.viewHolders.UserViewHolder
import com.zeyad.rxredux.utils.hasLollipop
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_user_list.*
import kotlinx.android.synthetic.main.user_list.*
import kotlinx.android.synthetic.main.view_progress.*
import org.koin.android.viewmodel.ext.android.getViewModel
import java.util.concurrent.TimeUnit

/**
 * An activity representing a list of Repos. This activity has different presentations for handset
 * and tablet-size devices. On handsets, the activity presents a list of items, which when touched,
 * lead to a [UserDetailActivity] representing item details. On tablets, the activity presents
 * the list of items and item details side-by-side using two vertical panes.
 */

class UserListActivity2 : AppCompatActivity(), OnStartDragListener, ActionMode.Callback,
        IBaseActivity<UserListIntents, UserListResult, UserListState, UserListEffect, UserListVM> {

    override lateinit var intentStream: Observable<UserListIntents>
    override lateinit var viewModel: UserListVM
    override lateinit var viewState: UserListState
    private lateinit var itemTouchHelper: ItemTouchHelper
    private lateinit var usersAdapter: GenericRecyclerViewAdapter
    private var actionMode: ActionMode? = null
    private var currentFragTag: String = ""
    private var twoPane: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        onCreateImpl(savedInstanceState)
        Log.d("UserListActivity2", "we are here!")
    }

    override fun initialStateProvider(): UserListState = EmptyState()

    override fun isViewStateInitialized(): Boolean = ::viewState.isInitialized

    override fun onSaveInstanceState(outState: Bundle) {
        onSaveInstanceStateImpl(outState, viewState)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        onRestoreInstanceStateImpl(savedInstanceState)
    }

    override fun initialize() {
        viewModel = getViewModel()
        intentStream = Observable.empty()
    }

    override fun setupUI(isNew: Boolean) {
        setContentView(R.layout.activity_user_list)
        setSupportActionBar(toolbar)
        toolbar.title = title
        setupRecyclerView()
        twoPane = findViewById<View>(R.id.user_detail_container) != null
    }

    override fun onResume() {
        super.onResume()
        if (viewState is EmptyState) {
            viewModel.intents.onNext(GetPaginatedUsersIntent(0))
        }
    }

    override fun bindState(successState: UserListState) {
        usersAdapter.setDataList(successState.list, successState.callback)
    }

    override fun bindEffect(effectBundle: UserListEffect) = Unit

    override fun toggleLoadingViews(isLoading: Boolean, intent: UserListIntents?) {
        linear_layout_loader.bringToFront()
        linear_layout_loader.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun bindError(errorMessage: String, intent: UserListIntents?, cause: Throwable) {
//        showErrorSnackBar(errorMessage, user_list, Snackbar.LENGTH_LONG)
    }

    private fun setupRecyclerView() {
        usersAdapter = object : GenericRecyclerViewAdapter() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GenericViewHolder<*> {
                return when (viewType) {
                    SECTION_HEADER -> SectionHeaderViewHolder(layoutInflater
                            .inflate(R.layout.section_header_layout, parent, false))
                    R.layout.empty_view -> EmptyViewHolder(layoutInflater
                            .inflate(R.layout.empty_view, parent, false))
                    R.layout.user_item_layout -> UserViewHolder(layoutInflater
                            .inflate(R.layout.user_item_layout, parent, false))
                    else -> throw IllegalArgumentException("Could not find view of type $viewType")
                }
            }
        }
        usersAdapter.setAreItemsClickable(true)
        usersAdapter.setOnItemClickListener(object : OnItemClickListener {
            override fun onItemClicked(position: Int, itemInfo: ItemInfo<*>, holder: GenericViewHolder<*>) {
                if (actionMode != null) {
                    toggleItemSelection(position)
                } else if (itemInfo.data is User) {
                    val userModel = itemInfo.data as User
                    val userDetailState = IntentBundleState(userModel)
                    var pair: Pair<View, String>? = null
                    var secondPair: Pair<View, String>? = null
                    if (hasLollipop()) {
                        val userViewHolder = holder as UserViewHolder
                        val avatar = userViewHolder.getAvatar()
                        pair = Pair.create(avatar, avatar.transitionName)
                        val textViewTitle = userViewHolder.getTextViewTitle()
                        secondPair = Pair.create(textViewTitle, textViewTitle.transitionName)
                    }
                    if (twoPane) {
                        if (currentFragTag.isNotBlank()) {
                            removeFragment(currentFragTag)
                        }
                        val orderDetailFragment = UserDetailFragment.newInstance(userDetailState)
                        currentFragTag = orderDetailFragment.javaClass.simpleName + userModel.id
                        addFragment(R.id.user_detail_container, orderDetailFragment, currentFragTag,
                                pair!!, secondPair!!)
                    } else {
                        if (hasLollipop()) {
                            val options = ActivityOptions
                                    .makeSceneTransitionAnimation(this@UserListActivity2, pair,
                                            secondPair).toBundle()
                            startActivity(UserDetailActivity
                                    .getCallingIntent(this@UserListActivity2, userDetailState), options)
                        } else {
                            startActivity(UserDetailActivity.getCallingIntent(this@UserListActivity2, userDetailState))
                        }
                    }
                }
            }
        })
        usersAdapter.setOnItemLongClickListener(object : OnItemLongClickListener {
            override fun onItemLongClicked(position: Int, itemInfo: ItemInfo<*>, holder: GenericViewHolder<*>): Boolean {
                if (usersAdapter.isSelectionAllowed) {
                    actionMode = startSupportActionMode(this@UserListActivity2)
                    toggleItemSelection(position)
                }
                return true
            }
        })
        user_list.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        user_list.adapter = usersAdapter
        usersAdapter.setAllowSelection(true)
        //        fastScroller.setRecyclerView(userRecycler)
        intentStream = intentStream.mergeWith(user_list.scrollEvents()
                .map { recyclerViewScrollEvent ->
                    GetPaginatedUsersIntent(
                            if (ScrollEventCalculator.isAtScrollEnd(recyclerViewScrollEvent))
                                viewState.lastId
                            else -1)
                }
                .filter { it.lastId != -1L }
                .throttleLast(200, TimeUnit.MILLISECONDS, Schedulers.computation())
                .debounce(300, TimeUnit.MILLISECONDS, Schedulers.computation())
                .doOnNext { Log.d("NextPageIntent", UserListActivity.FIRED) })
                .mergeWith(usersAdapter.itemSwipeObservable
                        .map { itemInfo -> DeleteUsersIntent(listOf((itemInfo.data as User).login)) }
                        .doOnEach { Log.d("DeleteIntent", UserListActivity.FIRED) })
        itemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback(usersAdapter))
        itemTouchHelper.attachToRecyclerView(user_list)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.list_menu, menu)
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val searchView = menu.findItem(R.id.menu_search).actionView as SearchView
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        searchView.setOnCloseListener {
            viewModel.intents.onNext(GetPaginatedUsersIntent(viewState.lastId))
            false
        }
        intentStream = intentStream.mergeWith(RxSearchView.queryTextChanges(searchView)
                .filter { charSequence -> charSequence.toString().isNotEmpty() }
                .throttleLast(100, TimeUnit.MILLISECONDS, Schedulers.computation())
                .debounce(200, TimeUnit.MILLISECONDS, Schedulers.computation())
                .map { query -> SearchUsersIntent(query.toString()) }
                .distinctUntilChanged()
                .doOnEach { Log.d("SearchIntent", FIRED) })
        return super.onCreateOptionsMenu(menu)
    }

    private fun toggleItemSelection(position: Int) {
        usersAdapter.toggleSelection(position)
        val count = usersAdapter.selectedItemCount
        if (count == 0) {
            actionMode?.finish()
        } else {
            actionMode?.title = count.toString()
            actionMode?.invalidate()
        }
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.selected_list_menu, menu)
        menu.findItem(R.id.delete_item).setOnMenuItemClickListener {
            viewModel.intents.onNext(DeleteUsersIntent(Observable.fromIterable(usersAdapter.selectedItems)
                    .map<String> { itemInfo -> (itemInfo.data as User).login }.toList()
                    .blockingGet()))
            true
        }
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.findItem(R.id.delete_item).setVisible(true).isEnabled = true
        toolbar.visibility = View.GONE
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        return item.itemId == R.id.delete_item
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        try {
            usersAdapter.clearSelection()
        } catch (e: Exception) {
            Log.e("onDestroyActionMode", e.message, e)
        }

        actionMode = null
        toolbar.visibility = View.VISIBLE
    }

    override fun onStartDrag(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder) {
        itemTouchHelper.startDrag(viewHolder)
    }

    fun getImageViewAvatar(): ImageView = imageView_avatar

    /**
     * Adds a [Fragment] to this activity's layout.
     *
     * @param containerViewId The container view to where add the fragment.
     * @param fragment        The fragment to be added.
     */
    @SafeVarargs
    fun addFragment(containerViewId: Int, fragment: Fragment, currentFragTag: String?,
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

    private fun removeFragment(tag: String) {
        supportFragmentManager.findFragmentByTag(tag)?.let {
            supportFragmentManager.beginTransaction().remove(it).commit()
        }
    }

    companion object {
        const val FIRED = "fired!"

        fun getCallingIntent(context: Context): Intent {
            return Intent(context, UserListActivity2::class.java)
        }
    }
}
