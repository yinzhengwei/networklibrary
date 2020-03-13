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
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.nio.charset.Charset
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.*
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
    var headerMap = mutableMapOf<String, String>()

    private var isDebug = true
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
     * @param isDebug 当前环境
     * @param baseUrl 请求服务的host，如：http://www.baidu.com/
     * @param timeout 请求服务器的超时时间
     * @param cashDir 缓存数据的地址
     * @param headerMap 请求服务的头参数
     * @param x509TrustManager ssl加密证书
     */
    fun init(
        isDebug: Boolean,
        baseUrl: String,
        cashDir: File,
        headerMap: HashMap<String, String>,
        x509TrustManager: X509TrustManager? = null
    ) {
        OkHttpManager.isDebug = isDebug
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
                builder!!.cache(Cache(File(cashDir, "OkHttpCache"), (1024 * 1024 * 10).toLong()))
            }
            builder!!.retryOnConnectionFailure(true)
                .addInterceptor(mHeaderInterceptor)
                .addInterceptor(sReplaceUrlInterceptor)
                .connectTimeout(timeoutTimed, TimeUnit.SECONDS)
                .readTimeout(timeoutTimed, TimeUnit.SECONDS)
                .writeTimeout(timeoutTimed, TimeUnit.SECONDS)

            if (isDebug) {
                //测试地址 打印log
                builder!!.addInterceptor(sLoggingInterceptor)
            }
            //添加证书
            if (x509TrustManager != null)
                builder!!.sslSocketFactory(sSLSocketFactory(), x509TrustManager!!)
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
    fun <T> sendRequest(
        observer: Observable<T>,
        successful: (T) -> Unit,
        fail: (Throwable) -> Unit
    ) {
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

        observer.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe(value)
//        observer.compose { observable ->
//            observable.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
//        }.subscribe(value)
    }

    /**
     * 请求包装、缓存处理
     * 添加请求Header或cache
     */
    private val mHeaderInterceptor = Interceptor { chain ->
        //请求定制(添加请求头)
        val requestBuilder = chain.request().newBuilder().apply {
            if (headerMap.isNotEmpty()) {
                //请求定制(添加请求头)
                headerMap.forEach {
                    addHeader(it.key, it.value)
                }
            }
        }
        chain.proceed(requestBuilder.build())
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
        chain.proceed(requestBuilder.build())
    }

     /**
     * 打印返回的json数据拦截器
     */
    private val sLoggingInterceptor = Interceptor { chain ->
        //打印返回的json数据
        val request = chain.request()
        val requestPath = request?.url()?.toString() ?: ""
        val requestMethod = request.method()
        val params = StringBuffer()
        bodyToString(request, params)

        var result = ""
        var code = ""
        var response: Response? = null
        var tookMs = 0L
        try {
            var charset = Charset.forName("UTF-8")

            //转码
            val requestBody = request.body()
            val buffer = Buffer()
            requestBody?.writeTo(buffer)
            buffer.readString(charset)
            val contentType = requestBody?.contentType()
            if (contentType != null) {
                charset = contentType.charset(charset)
            }

            //请求时长
            val startNs = System.nanoTime()
            try {
                response = chain.proceed(request)
            } catch (e: Exception) {
                throw e
            }
            tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)

            //返回码
            if (response.code() != 200)
                code = "\n异常错误码:${response.code()}\n"

            //打印返回的json数据拦截器
            result = response.body()?.source()!!.apply { request(Long.MAX_VALUE) }.buffer()!!.clone().readString(charset)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Log.i("OkHttp", "\n\n请求地址:$requestPath\n请求方式:$requestMethod\n\n$params$code\n")
        Log.i("OkHttp", "请求时长:\n$tookMs ms   \n\n返回的出参数据:\n$result")
        response
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
                            sb.append(body.name(index) + "=" + body.value(index) + ";\n")
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
