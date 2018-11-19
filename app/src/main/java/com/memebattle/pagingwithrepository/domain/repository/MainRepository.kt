package com.memebattle.pagingwithrepository.domain.repository

import android.content.Context
import com.android.example.paging.pagingwithnetwork.reddit.api.RedditApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import androidx.room.Room
import com.android.example.paging.pagingwithnetwork.reddit.db.RedditDb
import com.android.example.paging.pagingwithnetwork.reddit.db.RedditPostDao


class MainRepository(context: Context) {

    private var retrofit: Retrofit = Retrofit.Builder()
            .baseUrl("https://www.reddit.com/") //Базовая часть адреса
            .addConverterFactory(GsonConverterFactory.create()) //Конвертер, необходимый для преобразования JSON'а в объекты
            .build()

    var db = Room.databaseBuilder(context,
            RedditDb::class.java, "database").build()

    private var api: RedditApi
    private var dao: RedditPostDao

    init {
        api = retrofit.create(RedditApi::class.java) //Создаем объект, при помощи которого будем выполнять запросы
        dao = db.posts()
    }


}