package com.remodex.android.di

import android.content.Context
import com.remodex.android.data.store.SecureStore
import com.remodex.android.service.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideSecureStore(@ApplicationContext context: Context): SecureStore =
        SecureStore(context)

    @Provides
    @Singleton
    fun provideCryptoHelpers(): CryptoHelpers = CryptoHelpers()

    @Provides
    @Singleton
    fun provideConnectionManager(
        okHttpClient: OkHttpClient,
        secureTransport: SecureTransport,
        json: Json
    ): ConnectionManager = ConnectionManager(okHttpClient, secureTransport, json)

    @Provides
    @Singleton
    fun provideSecureTransport(
        cryptoHelpers: CryptoHelpers,
        secureStore: SecureStore,
        json: Json
    ): SecureTransport = SecureTransport(cryptoHelpers, secureStore, json)

    @Provides
    @Singleton
    fun provideMessageTransport(
        connectionManager: ConnectionManager,
        secureTransport: SecureTransport,
        json: Json
    ): MessageTransport = MessageTransport(connectionManager, secureTransport, json)

    @Provides
    @Singleton
    fun provideHistoryDecoder(json: Json): HistoryDecoder = HistoryDecoder(json)

    @Provides
    @Singleton
    fun provideCodexService(
        secureStore: SecureStore,
        connectionManager: ConnectionManager,
        secureTransport: SecureTransport,
        messageTransport: MessageTransport,
        historyDecoder: HistoryDecoder,
        json: Json
    ): CodexService = CodexService(
        secureStore, connectionManager, secureTransport,
        messageTransport, historyDecoder, json
    )
}
