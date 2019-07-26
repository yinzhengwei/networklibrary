package com.android.yzw.networklibrary

import android.text.TextUtils
import android.util.Log
import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.*
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Create by yinzhengwei on 2019-07-18
 * @Function
 */
object OkHttpManager {

    private var builder: OkHttpClient.Builder? = null
    private var x509TrustManager: X509TrustManager? = null
    private var retrofit: Retrofit? = null
    private var headerMap = mutableMapOf<String, String>()

    private var baseUrl = ""
    private var mSecondBaseUrl = ""
    private var cashDir: File? = null
    private var timeoutTimed: Long = 30_000

    fun setTimeout(time: Long) {
        timeoutTimed = time
        builder = null
        retrofit = null
    }

    /**
     * 初始化
     * @param baseUrl 请求服务的host，如：http://www.baidu.com/
     * @param timeout 请求服务器的超时时间
     * @param cashDir 缓存数据的地址
     * @param headerMap 请求服务的头参数
     * @param x509TrustManager ssl加密证书
     */
    fun init(baseUrl: String, timeout: Long, cashDir: File, headerMap: HashMap<String, String>, x509TrustManager: X509TrustManager? = null) {
        setTimeout(timeout)
        OkHttpManager.baseUrl = baseUrl
        OkHttpManager.cashDir = cashDir
        OkHttpManager.headerMap = headerMap
        OkHttpManager.x509TrustManager = x509TrustManager
    }

    /**
     * 根据定义的interface接口声明类，构造出对象实例
     * 例如：val mIAccountFunctionApi = loadApi(IAccountFunctionApi::class.java)
     *
     * @param clazz 接口对象
     * @param secondBaseUrl 特殊接口的不同请求地址
     */
    fun <T> loadApi(clazz: Class<T>, secondBaseUrl: String? = null): T {
        if (secondBaseUrl != null)
            mSecondBaseUrl = secondBaseUrl
        if (builder == null) {
            builder = OkHttpClient.Builder()
            // 指定缓存路径,缓存大小100Mb
            if (cashDir != null && cashDir!!.exists()) {
                builder!!.cache(Cache(File(cashDir, "HttpCache"), (1024 * 1024 * 100).toLong()))
            }
            builder!!.retryOnConnectionFailure(true)
                    .addInterceptor(mCommonParamsProcessInterceptor)
                    .addInterceptor(sReplaceUrlInterceptor)
                    .addInterceptor(sLoggingInterceptor)
                    .connectTimeout(timeoutTimed, TimeUnit.SECONDS)
                    .readTimeout(timeoutTimed, TimeUnit.SECONDS)
                    .writeTimeout(timeoutTimed, TimeUnit.SECONDS)

            if (BuildConfig.DEBUG) {
                //测试地址 打印log
                builder!!.addInterceptor(sLoggingInterceptor)
            } else {
                //添加证书
                if (x509TrustManager != null)
                    builder!!.sslSocketFactory(sSLSocketFactory(), x509TrustManager!!)
            }
        }
        if (retrofit == null)
            retrofit = Retrofit.Builder()
                    .addConverterFactory(GsonConverterFactory.create(Gson()))
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                    .client(builder!!.build())
                    .baseUrl(baseUrl)
                    .build()

        return retrofit!!.create(clazz)
    }

    /**
     * 发出请求的方法
     *
     * @param observer 定义声明类里的观察者方法,例如：
     *      @GET("v1/userinfo")
     *      Observable<UserInfo> netUserInfo();
     *
     * @param successful 请求成功的回调
     * @param fail       请求失败的回调
     */
    fun <T> sendRequest(observer: Observable<T>, successful: (T) -> Unit, fail: (Throwable) -> Unit) {
        if (TextUtils.isEmpty(baseUrl)) {
            fail(Throwable("域服务器域名地址为空"))
            return
        }
        val value = object : Observer<T> {
            override fun onComplete() {
            }

            override fun onSubscribe(d: Disposable) {
            }

            override fun onNext(t: T) {
                successful(t)
            }

            override fun onError(e: Throwable) {
                fail(e)
            }
        }
        observer.compose { observable ->
            observable.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
        }.subscribe(value)
    }

    /**
     * 请求包装、缓存处理
     * 添加请求Header或cache
     */
    private val mCommonParamsProcessInterceptor = Interceptor { chain ->
        val requestBuilder = chain.request().newBuilder()
        if (headerMap.size >= 0) {
            //请求定制(添加请求头)
            headerMap.forEach {
                requestBuilder.apply {
                    addHeader(it.key, it.value)
                }
            }
        }
        chain.proceed(requestBuilder.build())
    }

    /**
     * 打印返回的json数据拦截器
     */
    private val sLoggingInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        val bodyString = response.body()?.string() ?: ""

        // 解决响应时崩溃无log问题
        val sbRequest = StringBuffer()
        bodyToString(request, sbRequest)

        Log.d("OkHttp", "\n请求地址:${request?.url()?.toString()}\n请求方式:${request.method()}\n\n$sbRequest\n返回参数:\n$bodyString\n")

        response.newBuilder().body(ResponseBody.create(response.body()!!.contentType(), bodyString)).build()
    }

    /**
     * 地址替换
     */
    private val sReplaceUrlInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder().apply {
            if (!TextUtils.isEmpty(mSecondBaseUrl)) {
                this.url(HttpUrl.parse(mSecondBaseUrl)!!)
            }
        }
        val request = requestBuilder.build()
        chain.proceed(request)
    }

    private fun bodyToString(request: Request, sb: StringBuffer): String {
        try {
            sb.append("请求头的参数：\n")
            request.headers().toMultimap().forEach {
                sb.append(it.key)
                sb.append("=")
                sb.append(it.value)
                sb.append("\n")
            }
            sb.append("\n接口的入参数据：\n")
            if (request.method() == "POST") {
                if (request.body() is FormBody) {
                    val body = request.body() as FormBody
                    if (body.size() == 0) {
                        sb.append("无入参数据\n")
                    } else
                        for (index in 0 until body.size()) {
                            sb.append(body.encodedName(index) + "=" + body.encodedValue(index) + ";\n")
                        }
                } else
                    sb.append("无入参数据\n")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return sb.toString()
    }

    @Throws(NoSuchAlgorithmException::class, KeyManagementException::class)
    private fun sSLSocketFactory(): SSLSocketFactory {
        val context = SSLContext.getInstance("TLS")
        val trustManagers = arrayOf<TrustManager>(x509TrustManager!!)
        context.init(null, trustManagers, SecureRandom())
        return context.socketFactory
    }

}