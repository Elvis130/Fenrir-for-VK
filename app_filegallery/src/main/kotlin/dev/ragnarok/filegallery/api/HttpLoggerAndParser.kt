package dev.ragnarok.filegallery.api

import android.annotation.SuppressLint
import dev.ragnarok.filegallery.Constants
import dev.ragnarok.filegallery.model.ParserType
import dev.ragnarok.filegallery.settings.Settings
import dev.ragnarok.filegallery.util.Utils
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Converter
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object HttpLoggerAndParser {
    fun selectConverterFactory(
        json: Converter.Factory,
        msgpack: Converter.Factory
    ): Converter.Factory {
        return if (Settings.get().main().currentParser == ParserType.MSGPACK) {
            msgpack
        } else {
            json
        }
    }

    fun Request.Builder.serverHeader(onlyJson: Boolean): Request.Builder {
        if (!onlyJson && Utils.currentParser == ParserType.MSGPACK) {
            addHeader("X-Response-Format", "msgpack")
        }
        return this
    }

    val DEFAULT_LOGGING_INTERCEPTOR: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().setLevel(if (Constants.IS_DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE)
    }

    fun adjust(builder: OkHttpClient.Builder) {
        if (Constants.IS_DEBUG) {
            builder.addInterceptor(DEFAULT_LOGGING_INTERCEPTOR)
        }
    }

    fun configureToIgnoreCertificates(builder: OkHttpClient.Builder) {
        if (Settings.get().main().isValidate_tls) {
            return
        }
        try {
            val trustAllCerts: Array<TrustManager> = arrayOf(
                @SuppressLint("CustomX509TrustManager")
                object : X509TrustManager {
                    @SuppressLint("TrustAllX509TrustManager")
                    @Throws(CertificateException::class)
                    override fun checkClientTrusted(
                        chain: Array<X509Certificate>,
                        authType: String?
                    ) {
                    }

                    @SuppressLint("TrustAllX509TrustManager")
                    @Throws(CertificateException::class)
                    override fun checkServerTrusted(
                        chain: Array<X509Certificate>,
                        authType: String?
                    ) {
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate> {
                        return arrayOf()
                    }
                }
            )
            val sslContext: SSLContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory: SSLSocketFactory = sslContext.socketFactory
            builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}