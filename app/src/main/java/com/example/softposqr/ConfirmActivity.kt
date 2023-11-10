package com.example.softposqr

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.softposqr.databinding.ActivityConfirmBinding
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

open class ConfirmActivity : AppCompatActivity() {
    var binding: ActivityConfirmBinding? = null
    private var qrString: String? = null

    private var contentType = "application/json"

    private var authorization =
        "Basic MzJjYTIwZjZiMDkwZDJmOWUzNjk5OWM1MGJkNzBlNGU3MTExYjI2ODphNDA4ZTFkMmM3ZTdlZTQxZmQ4OWM2ZDYzYjE4NWZmNDM3N2E2MWUw"
    private var xRequestID = ""
    private var xRequestDate = ""
    private var apiPath = "/acquirer/v1/webhook/payment-hub/napas247"
    private var baseURL = "https://squidgate.digipay.dev"
    private var memo = ""
    private var refID = ""
    private var amount = ""
    private var toAccount = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfirmBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        qrString = intent.getStringExtra("qrString")
        authorization = intent.getStringExtra("authorization")!!
        apiPath = intent.getStringExtra("apiPath")!!
        baseURL = intent.getStringExtra("baseURL")!!

        binding?.btnBack?.setOnClickListener { finish() }

        binding?.btnPay?.setOnClickListener {
            requestAPI()
        }

        try  { parseInfo(qrString!!) }
        catch (_: java.lang.Exception) {
            Toast.makeText(this@ConfirmActivity, "QR invalid", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun requestAPI() {
//        {
//            "rqUID": "7df73a0b-590c-4b49-858d-995fe0252abb",
//            "Channel": "MB",
//            "TransferType": "O2K",
//            "TransactionDate": "2023-10-26T06:52:27.818Z",
//            "TransactionReference": "20231027081336038694",
//            "FromAcctNo": "668888-970406",
//            "ReceivingAcctNo": "210810307000061",
//            "TransactionAmount": "60000",
//            "CCYCD": "VND",
//            "Memo": "KVNQR20231027081336038694",
//            "TransferStatus": 0
//        }
//        val date = Date()

        val json = JSONObject()
        json.put("rqUID", UUID.randomUUID())
        json.put("Channel", "MB")
        json.put("TransferType", "O2K")
        json.put("TransactionDate", getUTCDateISO8601()/*SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(date)*/)
        json.put("TransactionReference", refID)
        json.put("FromAcctNo", "668888-970406")
        json.put("ReceivingAcctNo", toAccount)
        json.put("TransactionAmount", amount)
        json.put("CCYCD", "VND")
        json.put("Memo", memo)
        json.put("TransferStatus", 0)

        xRequestID = json.getString("rqUID")
        xRequestDate = json.getString("TransactionDate")


        buildRetrofit()!!
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .concatMap { buildAPI(it) }
            .concatMap { request(it, json.toString()) }
            .doOnNext {
                binding?.txtInfo?.text = it.toString()
            }
            .doOnError {
                binding?.txtInfo?.text = it.toString()
            }
            .onErrorReturn { "" }
            .subscribe()
    }

    @SuppressLint("SimpleDateFormat")
    fun getUTCDateISO8601(localDate: Date = Date()): String {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        df.timeZone = TimeZone.getTimeZone("gmt")
        print(localDate)
        return df.format(localDate)
    }

    @SuppressLint("SetTextI18n")
    @Throws(Exception::class)
    private fun parseInfo(qrString: String) {
        val jsonA = analyseQRString(qrString)
        val jsonB = analyseQRString(jsonA.getString("62"))
        memo = jsonB.getString("08") //KVNQR20231029075512128394
        refID = memo.substring("KVNQR".length) //20231029075512128394
        amount = jsonA.getString("54")
        val jsonC =
            analyseQRString(jsonA.getString("38")) // 0010A0000007270126000666888801121050000040440208QRIBFTTA
        val jsonD = analyseQRString(jsonC.getString("01")) // 000666888801121050000040440208QRIBFTTA
        toAccount = jsonD.getString("01") // 105000004044

        binding?.txtInfo?.text = """
            |ReferenceID: $refID
            |
            |Memo: $memo
            |
            |Amount: $amount
            |
            |To: $toAccount
            """.trimMargin("|")
    }

    private fun analyseQRString(qrString: String): JSONObject {
//        val qrString = "00020101021238560010A0000007270126000666888801121050000040440208QRIBFTTA530370454065555555802VN62290825KVNQR202310290755121283946304D6F6"
//        tag: 00 len 2 val=01
//        tag: 01 len 2 val=12
//        tag: 38 len 56 val=0010A0000007270126000666888801121050000040440208QRIBFTTA
//        tag: 53 len 3 val=704
//        tag: 54 len 6 val=555555
//        tag: 58 len 2 val=VN
//        tag: 62 len 29 val=0825KVNQR20231029075512128394
//        tag: 63 len 4 val=D6F6
        val json = JSONObject()
        var index = 0
        while (index < qrString.length) {
            val tag = qrString.substring(index, index + 2)
            index += 2
            val length = qrString.substring(index, index + 2).toInt()
            index += 2
            val value = qrString.substring(index, index + length)
            index += length
            Log.d("TAG", "tag: $tag len $length val=$value")
            json.put(tag, value)
        }
        return json
    }

    protected open fun buildRetrofit(): Observable<Retrofit>? {
        val okHttpClient = createOkHttpClient()

        val retrofit: Retrofit = Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl(baseURL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return Observable.just(retrofit)
    }

    @SuppressLint("CustomX509TrustManager")
    protected open fun createOkHttpClient(): OkHttpClient {
        val okHttpClient: OkHttpClient = if (baseURL.lowercase().startsWith("https://")) {
            OkHttpClient.Builder()
                .sslSocketFactory(
                    createUnsafeSslSocketFactory(),
                    object : X509TrustManager {
                        @SuppressLint("TrustAllX509TrustManager")
                        override fun checkClientTrusted(
                            chain: Array<X509Certificate>,
                            authType: String
                        ) {
                        }

                        @SuppressLint("TrustAllX509TrustManager")
                        override fun checkServerTrusted(
                            chain: Array<X509Certificate>,
                            authType: String
                        ) {
                        }

                        override fun getAcceptedIssuers(): Array<X509Certificate> {
                            return arrayOf()
                        }
                    })
                .connectTimeout(10 * 1000L, TimeUnit.MILLISECONDS)
                .readTimeout(10 * 1000L, TimeUnit.MILLISECONDS)
                .hostnameVerifier { _: String?, _: SSLSession? -> true }
                .build()
        } else {
            OkHttpClient.Builder()
                .connectTimeout(10 * 1000L, TimeUnit.MILLISECONDS)
                .readTimeout(10 * 1000L, TimeUnit.MILLISECONDS)
                .build()
        }
        return okHttpClient
    }

    @Throws(NoSuchAlgorithmException::class, KeyManagementException::class)
    private fun createUnsafeSslSocketFactory(): SSLSocketFactory {
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        return sslContext.socketFactory
    }

    @SuppressLint("TrustAllX509TrustManager", "CustomX509TrustManager")
    private val trustAllCerts: Array<TrustManager> = arrayOf(
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return arrayOf()
            }
        })


    protected open fun buildAPI(retrofit: Retrofit): Observable<ApiInterface?>? {
        val apiInterface: ApiInterface = retrofit.create(
            ApiInterface::class.java
        )
        return Observable.just(apiInterface)
    }


    protected open fun request(
        apiInterface: ApiInterface,
        data: String
    ): Observable<String>? {
        Log.d(TAG, "REQUEST ${baseURL}/${apiPath}")
        Log.d(TAG, "REQUEST $data")
        return Observable.create { emitter: ObservableEmitter<String> ->
            apiInterface.req(apiPath, authorization, contentType, xRequestID, xRequestDate, data)
                ?.enqueue(object :
                    Callback<String> {
                    override fun onResponse(
                        call: Call<String>,
                        response: Response<String>
                    ) {
                        if (response.isSuccessful && response.body() != null) {
                            try {
                                val json = response.body()!!
                                Log.d(TAG, json)
//                                validateRespCode(json)
                                emitter.onNext(json)
                                emitter.onComplete()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                emitter.onError(e)
                            }
                        } else if (!response.isSuccessful && response.errorBody() != null) {
                            try {
                                val json = response.errorBody()!!.string()
                                Log.w(TAG, json)
//                                validateRespCode(json)
                                emitter.onNext(json)
                                emitter.onComplete()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                emitter.onError(e)
                            }
                        } else {
                            Log.e(TAG, "emitter.onError")
                            emitter.onError(Exception("emitter.onError"))
                        }
                    }

                    override fun onFailure(
                        call: Call<String>,
                        t: Throwable
                    ) {
                        t.printStackTrace()
                        if (t is SocketTimeoutException) {
                            Log.d(TAG, "read data timeout.")
                            emitter.onError(Exception("ExceptionType.COMM_EXCEPTION, CommErrorType.READ_DATA_TIMEOUT"))
                        } else if (t is IOException) {
                            Log.d(TAG, "read data, failed.")
                            emitter.onError(Exception("ExceptionType.COMM_EXCEPTION, CommErrorType.READ_DATA_FAILED"))
                        } else {
                            emitter.onError(t)
                        }
                    }
                })
        }
    }

    interface ApiInterface {
        @POST
        @Headers("User-Agent: Gini Scanning Apps")
        fun req(
            @Url path: String?,
            @Header("Authorization") authorization: String?,
            @Header("Content-Type") contentType: String?,
            @Header("X-Request-Id") xRequestID: String?,
            @Header("X-Request-Date") xRequestDate: String?,
            @Body body: String?
        ): Call<String>?
    }

    companion object {
        const val TAG = "ConfirmActivity"
    }
}