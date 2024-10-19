package com.splitter.splittr.data.network

import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.lang.reflect.ParameterizedType

class CallAdapter<R>(private val delegate: TypeAdapter<R>) : TypeAdapter<Call<R>>() {
    override fun write(out: JsonWriter, value: Call<R>?) {
        throw UnsupportedOperationException("Call cannot be serialized")
    }

    override fun read(`in`: JsonReader): Call<R> {
        return object : Call<R> {
            override fun enqueue(callback: Callback<R>) {
                callback.onResponse(this, Response.success(delegate.read(`in`)))
            }

            override fun execute(): Response<R> {
                return Response.success(delegate.read(`in`))
            }

            override fun clone(): Call<R> = this
            override fun isExecuted(): Boolean = false
            override fun cancel() {}
            override fun isCanceled(): Boolean = false
            override fun request(): Request = Request.Builder().build()
            override fun timeout(): Timeout = Timeout()
        }
    }
}

class CallAdapterFactory : TypeAdapterFactory {
    override fun <T : Any> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (type.rawType != Call::class.java) return null
        val responseType = type.type as? ParameterizedType ?: return null
        val responseClass = TypeToken.get(responseType.actualTypeArguments[0])
        val delegate = gson.getDelegateAdapter(this, responseClass)
        @Suppress("UNCHECKED_CAST")
        return CallAdapter(delegate) as TypeAdapter<T>
    }
}