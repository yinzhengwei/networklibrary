# networklibrary



# 1、在自定义的application中初始化OkHttpManager:

      例：   OkHttpManager.init(baseUrl,timeout,cashDir,headerMap)
         或  OkHttpManager.init(baseUrl,timeout,cashDir,headerMap,x509TrustManager)


# 2、定义interface接口声明类：

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


# 3、获取接口声明类的实例：

      例： val mIAccountFunctionApi = OkHttpManager.loadApi(IAccountFunctionApi::class.java)



# 4、发送请求：

      例：OkHttpManager.sendRequest(mIAccountFunctionApi.getVerifyPhoneNumber(text),{
          Log.d(it.data)
       },{
          Log.d(it.error.toString())
       })

