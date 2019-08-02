# networklibrary
Android网络请求框架的封装，使用方式请阅读以下文档。关于框架的混淆已在 ‘proguard-rules.pro’中完成。

# 配置方式

Step 1. Add the JitPack repository to your build file

Add it in your root build.gradle at the end of repositories:

allprojects {

	repositories {
	
		...
		
		maven { url 'https://jitpack.io' }
		
	}
	
}

Step 2. Add the dependency

dependencies {

	implementation 'com.github.yinzhengwei:networklibrary:2.0.0'
	
}


# 使用方式
1、在自定义的application中初始化OkHttpManager:

      例：   OkHttpManager.init(baseUrl,timeout,cashDir,headerMap)
         或  OkHttpManager.init(baseUrl,timeout,cashDir,headerMap,x509TrustManager)


2、定义interface接口声明类：

      //关于更多api的定义方式请参考源码中文档‘接口类的api定义方式.txt’
      例： public interface IAccountFunctionApi {
   
            /**
             * 检验手机号是否非法
             * @param
             * @return
             */
             
            @FormUrlEncoded
            @POST("v1/isExistedPhone")
            Observable<PhoneExisted> getVerifyPhoneNumber(@Field("phone") String phoneNumber);
        }


3、获取接口声明类的实例：

      例： val mIAccountFunctionApi = OkHttpManager.loadApi(IAccountFunctionApi::class.java)



4、发送请求：

      例：OkHttpManager.sendRequest(mIAccountFunctionApi.getVerifyPhoneNumber(text),{
          Log.d(it.data)
       },{
          Log.d(it.error.toString())
       })

