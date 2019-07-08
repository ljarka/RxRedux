package com.zeyad.rxredux.core

import android.support.v7.util.DiffUtil
import android.util.Log
import com.zeyad.gadapter.ItemInfo
import com.zeyad.rxredux.core.viewmodel.BaseViewModel
import com.zeyad.rxredux.core.viewmodel.SuccessEffectResult
import com.zeyad.rxredux.core.viewmodel.throwIllegalStateException
import com.zeyad.usecases.api.IDataService
import com.zeyad.usecases.db.RealmQueryProvider
import com.zeyad.usecases.requests.GetRequest
import com.zeyad.usecases.requests.PostRequest
import io.reactivex.Flowable
import io.reactivex.functions.BiFunction
import io.realm.Realm
import io.realm.RealmQuery

class UserListVM(private val dataUseCase: IDataService) : BaseViewModel<UserListEvents<*>, UserListResult, UserListState, UserListEffect>() {

    override fun reduceEventsToResults(event: UserListEvents<*>, currentState: Any): Flowable<*> {
        Log.d("UserListVM", "currentStateBundle: $currentState")
        return when (event) {
            is GetPaginatedUsersEvent -> when (currentState) {
                is EmptyState, is GetState -> getUsers(event.getPayLoad())
                else -> throwIllegalStateException(event)
            }
            is DeleteUsersEvent -> when (currentState) {
                is GetState -> deleteCollection(event.getPayLoad())
                else -> throwIllegalStateException(event)
            }
            is SearchUsersEvent -> when (currentState) {
                is GetState -> search(event.getPayLoad())
                else -> throwIllegalStateException(event)
            }
            is UserClickedEvent -> when (currentState) {
                is GetState -> Flowable.just(SuccessEffectResult(NavigateTo(event.getPayLoad()), event))
                else -> throwIllegalStateException(event)
            }
        }
    }

    override fun stateReducer(newResult: UserListResult, currentState: UserListState): UserListState {
        val currentItemInfo = currentState.list.toMutableList()
        return when (currentState) {
            is EmptyState -> when (newResult) {
                is EmptyResult -> EmptyState()
                is UsersResult -> {
                    val pair = Flowable.fromIterable(newResult.list)
                            .map { ItemInfo(it, R.layout.abc_action_menu_item_layout, it.id) }
                            .toList().toFlowable()
                            .calculateDiff(currentItemInfo)
                    GetState(pair.first, pair.first[pair.first.size - 1].id, pair.second)
                }
            }
            is GetState -> when (newResult) {
                is EmptyResult -> EmptyState()
                is UsersResult -> {
                    val pair = Flowable.fromIterable(newResult.list)
                            .map { ItemInfo(it, R.layout.abc_action_menu_item_layout, it.id) }
                            .toList()
                            .map {
                                val list = currentState.list.toMutableList()
                                list.addAll(it)
                                list.toSet().toMutableList()
                            }.toFlowable()
                            .calculateDiff(currentItemInfo)
                    GetState(pair.first, pair.first[pair.first.size - 1].id, pair.second)
                }
            }
        }
    }

    private fun Flowable<MutableList<ItemInfo>>.calculateDiff(initialList: MutableList<ItemInfo>)
            : Pair<MutableList<ItemInfo>, DiffUtil.DiffResult> =
            scan<Pair<MutableList<ItemInfo>, DiffUtil.DiffResult>>(Pair(initialList,
                    DiffUtil.calculateDiff(UserDiffCallBack(mutableListOf(), mutableListOf()))))
            { pair1, next ->
                Pair(next, DiffUtil.calculateDiff(UserDiffCallBack(pair1.first, next)))
            }.skip(1)
                    .blockingFirst()

    private fun getUsers(lastId: Long): Flowable<*> {
//        return if (lastId == 0L)
//            dataUseCase.getListOffLineFirst(GetRequest.Builder(User::class.java, true)
//                    .url(String.format(USERS, lastId))
//                    .build())
//        else
        return dataUseCase.getList<User>(GetRequest.Builder(User::class.java, true)
                .url(String.format(USERS, lastId)).build())
                .map {
                    when (it.isEmpty()) {
                        true -> EmptyResult
                        false -> UsersResult(it)
                    }
                }
    }

    private fun search(query: String): Flowable<UsersResult> {
        return dataUseCase
                .queryDisk(object : RealmQueryProvider<User> {
                    override fun create(realm: Realm): RealmQuery<User> =
                            realm.where(User::class.java).beginsWith(User.LOGIN, query)
                })
                .zipWith(dataUseCase.getObject<User>(GetRequest.Builder(User::class.java, false)
                        .url(String.format(USER, query)).build())
                        .onErrorReturnItem(User())
                        .filter { user -> user.id != 0L }
                        .map { mutableListOf(it) },
                        BiFunction<List<User>, MutableList<User>, List<User>>
                        { singleton, users ->
                            users.addAll(singleton)
                            users.asSequence().toSet().toList()
                        }).map { UsersResult(it) }
    }

    private fun deleteCollection(selectedItemsIds: List<String>): Flowable<List<String>> {
        return dataUseCase.deleteCollectionByIds<Any>(PostRequest.Builder(User::class.java, true)
                .payLoad(selectedItemsIds)
                .idColumnName(User.LOGIN, String::class.java).cache()
                .build())
                .map { selectedItemsIds }
    }

    companion object {
        const val USERS = "users?since=%s"
        const val USER = "users/%s"
    }
}
