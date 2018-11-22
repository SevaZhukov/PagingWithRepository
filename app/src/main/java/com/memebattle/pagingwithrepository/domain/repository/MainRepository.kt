package com.memebattle.pagingwithrepository.domain.repository

import android.content.Context
import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.android.example.paging.pagingwithnetwork.reddit.api.RedditApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import androidx.room.Room
import com.android.example.paging.pagingwithnetwork.reddit.db.RedditDb
import com.android.example.paging.pagingwithnetwork.reddit.db.RedditPostDao
import com.memebattle.pagingwithrepository.domain.model.RedditPost
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.Executors
import androidx.paging.LivePagedListBuilder
import com.memebattle.pagingwithrepository.domain.repository.core.Listing
import com.memebattle.pagingwithrepository.domain.repository.boundary.SubredditBoundaryCallback
import com.memebattle.pagingwithrepository.domain.repository.network.NetworkState
import com.memebattle.pagingwithrepository.domain.repository.core.RedditPostRepository

class MainRepository(context: Context) : RedditPostRepository {

    private var retrofit: Retrofit = Retrofit.Builder()
            .baseUrl("https://www.reddit.com/") //Базовая часть адреса
            .addConverterFactory(GsonConverterFactory.create()) //Конвертер, необходимый для преобразования JSON'а в объекты
            .build()

    var db = Room.databaseBuilder(context,
            RedditDb::class.java, "database").build()

    private var redditApi: RedditApi
    private var dao: RedditPostDao

    val ioExecutor = Executors.newSingleThreadExecutor()

    init {
        redditApi = retrofit.create(RedditApi::class.java) //Создаем объект, при помощи которого будем выполнять запросы
        dao = db.posts()
    }

    /**
     * Inserts the response into the database while also assigning position indices to items.
     */
    private fun insertResultIntoDb(subredditName: String, body: RedditApi.ListingResponse?) {
        body!!.data.children.let { posts ->
            db.runInTransaction {
                val start = db.posts().getNextIndexInSubreddit(subredditName)
                val items = posts.mapIndexed { index, child ->
                    child.data.indexInResponse = start + index
                    child.data
                }
                db.posts().insert(items)
            }
        }
    }

    /**
     * When refresh is called, we simply run a fresh network request and when it arrives, clear
     * the database table and insert all new items in a transaction.
     * <p>
     * Since the PagedList already uses a database bound data source, it will automatically be
     * updated after the database transaction is finished.
     */
    @MainThread
    private fun refresh(subredditName: String): LiveData<NetworkState> {
        val networkState = MutableLiveData<NetworkState>()
        networkState.value = NetworkState.LOADING
        redditApi.getTop(subredditName, 10).enqueue(
                object : Callback<RedditApi.ListingResponse> {
                    override fun onFailure(call: Call<RedditApi.ListingResponse>, t: Throwable) {
                        // retrofit calls this on main thread so safe to call set value
                        networkState.value = NetworkState.error(t.message)
                    }

                    override fun onResponse(
                            call: Call<RedditApi.ListingResponse>,
                            response: Response<RedditApi.ListingResponse>) {
                        ioExecutor.execute {
                            db.runInTransaction {
                                db.posts().deleteBySubreddit(subredditName)
                                insertResultIntoDb(subredditName, response.body())
                            }
                            // since we are in bg thread now, post the result.
                            networkState.postValue(NetworkState.LOADED)
                        }
                    }
                }
        )
        return networkState
    }

    /**
     * Returns a Listing for the given subreddit.
     */
    override fun postsOfSubreddit(subReddit: String, pageSize: Int): Listing<RedditPost> {
        // create a boundary callback which will observe when the user reaches to the edges of
        // the list and update the database with extra data.
        val boundaryCallback = SubredditBoundaryCallback(
                webservice = redditApi,
                subredditName = subReddit,
                handleResponse = this::insertResultIntoDb,
                ioExecutor = ioExecutor,
                networkPageSize = 10)
        // we are using a mutable live data to trigger refresh requests which eventually calls
        // refresh method and gets a new live data. Each refresh request by the user becomes a newly
        // dispatched data in refreshTrigger
        val refreshTrigger = MutableLiveData<Unit>()
        val refreshState = Transformations.switchMap(refreshTrigger) {
            refresh(subReddit)
        }
        // We use toLiveData Kotlin extension function here, you could also use LivePagedListBuilder
        val livePagedList = LivePagedListBuilder(db.posts().postsBySubreddit(subReddit), pageSize)
                .setBoundaryCallback(boundaryCallback)
                .build()

        return Listing(
                pagedList = livePagedList,
                networkState = boundaryCallback.networkState,
                retry = {
                    boundaryCallback.helper.retryAllFailed()
                },
                refresh = {
                    refreshTrigger.value = null
                },
                refreshState = refreshState
        )
    }
}