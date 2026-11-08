package com.hamad.tvhome.aerial

import android.content.Context
import com.hamad.tvhome.R
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * The device's system trust store (Android 10) does not contain the Apple Root CA,
 * so every plain HTTPS request to sylvan.apple.com fails with an untrusted-chain
 * error. Aerial media comes from Apple's own CDN, so this client trusts exactly
 * the bundled Apple Root CA (and nothing else) for those requests.
 */
object AerialTls {

    fun client(context: Context, base: OkHttpClient): OkHttpClient {
        val certificates = CertificateFactory.getInstance("X.509")
        val root = context.resources.openRawResource(R.raw.apple_root_ca).use { input ->
            certificates.generateCertificate(input) as X509Certificate
        }
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("apple-root", root)
        }
        val factory = TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore) }
        val trustManager = factory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), null)
        }
        return base.newBuilder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .build()
    }
}
