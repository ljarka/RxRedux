package com.zeyad.rxredux.screens.user.list

import android.support.v7.util.DiffUtil
import com.zeyad.gadapter.ItemInfo
import com.zeyad.rxredux.R
import com.zeyad.rxredux.core.BaseEvent
import com.zeyad.rxredux.core.viewmodel.BaseViewModel
import com.zeyad.rxredux.screens.user.User
import com.zeyad.rxredux.screens.user.UserDiffCallBack
import com.zeyad.rxredux.screens.user.list.events.DeleteUsersEvent
import com.zeyad.rxredux.screens.user.list.events.GetPaginatedUsersEvent
import com.zeyad.rxredux.screens.user.list.events.SearchUsersEvent
import com.zeyad.rxredux.utils.Constants.URLS.USER
import com.zeyad.rxredux.utils.Constants.URLS.USERS
import com.zeyad.usecases.api.IDataService
import com.zeyad.usecases.db.RealmQueryProvider
import com.zeyad.usecases.requests.GetRequest
import com.zeyad.usecases.requests.PostRequest
import io.reactivex.Flowable
import io.reactivex.functions.BiFunction
import io.reactivex.functions.Function
import io.realm.Realm
import io.realm.RealmQuery

class UserListVM(private val dataUseCase: IDataService) : BaseViewModel<UserListState>() {

    override fun mapEventsToActions(): Function<BaseEvent<*>, Flowable<*>> {
        return Function { event ->
            when (event) {
                is GetPaginatedUsersEvent -> getUsers(event.getPayLoad())
                is DeleteUsersEvent -> deleteCollection(event.getPayLoad())
                is SearchUsersEvent -> search(event.getPayLoad())
                else -> Flowable.empty<Any>()
            }
        }
    }

    override fun stateReducer(): (newResult: Any, event: BaseEvent<*>, currentStateBundle: UserListState) -> UserListState {
        return { newResult, _, currentStateBundle ->
            val currentItemInfo = currentStateBundle.list.toMutableList()
            when (currentStateBundle) {
                is EmptyState -> when (newResult) {
                    is List<*> -> {
                        val pair = Flowable.fromIterable(newResult as List<User>)
                                .map { ItemInfo(it, R.layout.user_item_layout).setId(it.id) }
                                .toList()
                                .toFlowable()
                                .scan<Pair<MutableList<ItemInfo>, DiffUtil.DiffResult>>(Pair(currentItemInfo,
                                        DiffUtil.calculateDiff(UserDiffCallBack(mutableListOf(), mutableListOf()))))
                                { pair1, next -> Pair(next, DiffUtil.calculateDiff(UserDiffCallBack(pair1.first, next))) }
                                .skip(1)
                                .blockingFirst()
                        GetState(pair.first, currentStateBundle.lastId, pair.second)
                    }
                    else -> throw IllegalStateException("Can not reduce EmptyState with this result: $newResult!")
                }
                is GetState -> when (newResult) {
                    is List<*> -> {
                        currentItemInfo.addAll(Flowable.fromIterable(newResult as List<User>)
                                .map { ItemInfo(it, R.layout.user_item_layout).setId(it.id) }
                                .toList().blockingGet())
                        val pair = Flowable.just(currentItemInfo.toSet().toMutableList())
                                .scan<Pair<MutableList<ItemInfo>, DiffUtil.DiffResult>>(Pair(currentItemInfo,
                                        DiffUtil.calculateDiff(UserDiffCallBack(mutableListOf(),
                                                mutableListOf()))))
                                { pair1, next ->
                                    Pair(next, DiffUtil.calculateDiff(UserDiffCallBack(pair1.first, next)))
                                }
                                .skip(1)
                                .blockingFirst()
                        GetState(pair.first, currentStateBundle.lastId + 1, pair.second)
                    }
                    else -> throw IllegalStateException("Can not reduce GetState with this result: $newResult!")
                }
            }
        }
    }

    private fun getUsers(lastId: Long): Flowable<List<User>> {
        return if (lastId == 0L)
            dataUseCase.getListOffLineFirst(GetRequest.Builder(User::class.java, true)
                    .url(String.format(USERS, lastId))
                    .build())
        else
            dataUseCase.getList(GetRequest.Builder(User::class.java, true)
                    .url(String.format(USERS, lastId)).build())
    }

    private fun search(query: String): Flowable<List<User>> {
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
                            users.toSet().toList()
                        })
    }

    private fun deleteCollection(selectedItemsIds: List<String>): Flowable<List<String>> {
        return dataUseCase.deleteCollectionByIds<Any>(PostRequest.Builder(User::class.java, true)
                .payLoad(selectedItemsIds)
                .idColumnName(User.LOGIN, String::class.java).cache()
                .build())
                .map { o -> selectedItemsIds }
    }
}
