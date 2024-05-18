package com.splitter.splitter.network

import com.splitter.splitter.model.Institution
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class User(val username: String = "", val email: String, val password: String)

data class AuthResponse(val token: String?, val success: Boolean = true, val message: String?, val refreshToken: String?)

interface ApiService {
    @GET("/api/gocardless/institutions")
    fun getInstitutions(@Query("country") country: String): Call<List<Institution>>

    @POST("/api/auth/register")
    fun registerUser(@Body user: User): Call<AuthResponse>

    @POST("/api/auth/login")
    fun loginUser(@Body user: User): Call<AuthResponse>

    @POST("/auth/refresh-token")
    fun refreshToken(@Body body: Map<String, String>): Call<AuthResponse>
}
