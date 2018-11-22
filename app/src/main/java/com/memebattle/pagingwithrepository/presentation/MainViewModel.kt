package com.memebattle.pagingwithrepository.presentation

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import com.memebattle.pagingwithrepository.domain.repository.core.RedditPostRepository

class MainViewModel(private val repository: RedditPostRepository) : ViewModel() {

    private val subredditName = MutableLiveData<String>()
    private val repoResult = Transformations.map(subredditName) {
        repository.postsOfSubreddit(it, 30)
    }
    val posts = Transformations.switchMap(repoResult) { it.pagedList }!!
    val networkState = Transformations.switchMap(repoResult) { it.networkState }!!
    val refreshState = Transformations.switchMap(repoResult) { it.refreshState }!!

    fun refresh() {
        repoResult.value?.refresh?.invoke()
    }

    fun showSubReddit(subreddit: String): Boolean {
        if (subredditName.value == subreddit) {
            return false
        }
        subredditName.value = subreddit
        return true
    }

    fun retry() {
        val listing = repoResult?.value
        listing?.retry?.invoke()
    }

    fun currentSubreddit(): String? = subredditName.value
}