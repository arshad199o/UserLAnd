package tech.ula.utils

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import tech.ula.model.entities.Session
import java.lang.Exception

@JsonClass(generateAdapter = true)
internal data class LoginResponse(
        @Json(name = "access_token") val accessToken: String,
        @Json(name = "expires-in") val expiresIn: Int,
        @Json(name = "refresh_token") val refreshToken: String,
        @Json(name = "token_type")val tokenType: String
)

@JsonClass(generateAdapter = true)
internal data class CreateResponse(
        val data: CreateData
)

@JsonClass(generateAdapter = true)
internal data class CreateData(
        val type: String,
        val attributes: CreateAttributes,
        val id: Int
)

@JsonClass(generateAdapter = true)
internal data class CreateAttributes(
        val sshPort: Int,
        val ipAddress: String
)

@JsonClass(generateAdapter = true)
internal data class ListResponse(val data: List<TunnelData>)

@JsonClass(generateAdapter = true)
internal data class TunnelData(val id: Int)

class CloudService {

    companion object {
        var accountEmail = ""
        var accountPassword = ""
    }

    private val baseUrl = "https://api.userland.tech/"
    private val jsonType = MediaType.parse("application/json")
    private var accessToken = ""
    private val client = OkHttpClient()
    private val moshi = Moshi.Builder().build()
    private val publicKey ="hi"

    val SUCCESS = 0
    val LOGIN_FAILURE = 1
    val BOX_FAILURE = 2
    val LIST_FAILURE = 3
    val DELETE_FAILURE = 4

    fun createBox(session: Session): Int {
        var result = login()
        if (result != 0)
            return result
        return box(session)
    }

    private fun login(): Int {
        val request = createLoginRequest()

        val response = try {
            client.newCall(request).execute()
        } catch (err: Exception) {
            return LOGIN_FAILURE
        }
        if (!response.isSuccessful) {
            return LOGIN_FAILURE
        }

        val adapter = moshi.adapter(LoginResponse::class.java)
        val loginResponse = adapter.fromJson(response.body()!!.source())!!
        accessToken = loginResponse.accessToken

        return SUCCESS
    }

    private fun box(session: Session): Int {
        if (accessToken == "") {
            return LOGIN_FAILURE
        }

        val request = createBoxCreateRequest()

        val response = try {
            client.newCall(request).execute()
        } catch (err: Exception) {
            return BOX_FAILURE
        }
        if (!response.isSuccessful) {
            return BOX_FAILURE
        }

        val adapter = moshi.adapter(CreateResponse::class.java)
        val createResponse = try {
            adapter.fromJson(response.body()!!.source())!!
        } catch (err: NullPointerException) {
            return BOX_FAILURE
        }
        session.ip = createResponse.data.attributes.ipAddress
        session.port = createResponse.data.attributes.sshPort.toLong()

        return SUCCESS
    }

    fun delete(): Int {
        if (accessToken == "") {
            return LOGIN_FAILURE
        }

        val request = createListRequest()
        val response = try {
            client.newCall(request).execute()
        } catch (err: Exception) {
            return LIST_FAILURE
        }
        if (!response.isSuccessful) {
            return LIST_FAILURE
        }

        val listAdapter = moshi.adapter(ListResponse::class.java)
        val id = try {
            listAdapter.fromJson(response.body()!!.source())!!.data.first().id
        } catch (err: NullPointerException) {
            return LIST_FAILURE
        }

        val deleteRequest = createDeleteRequest(id)
        val deleteResponse = try {
            client.newCall(deleteRequest).execute()
        } catch (err: Exception) {
            return DELETE_FAILURE
        }
        if (!deleteResponse.isSuccessful) {
            return DELETE_FAILURE
        }

        return SUCCESS
    }

    private fun createLoginRequest(): Request {
        val json = """
            {
                "email": "$accountEmail",
                "password": "$accountPassword"
            }
        """.trimIndent()

        val body = RequestBody.create(jsonType, json)
        return Request.Builder()
                .url("$baseUrl/login")
                .post(body)
                .build()
    }

    private fun createBoxCreateRequest(): Request? {
        val sshKey = publicKey

        val json = """
            {
              "data": {
                "type": "box",
                "attributes": {
                  "port": ["http"],
                  "sshKey": "$sshKey"
                }
              }
            }
        """.trimIndent()

        val body = RequestBody.create(jsonType, json)
        return Request.Builder()
                .url("$baseUrl/boxes")
                .post(body)
                .addHeader("Authorization","Bearer $accessToken")
                .build()
    }

    private fun createListRequest(): Request {
        return Request.Builder()
                .url("$baseUrl/boxes")
                .addHeader("Authorization","Bearer $accessToken")
                .get()
                .build()
    }

    private fun createDeleteRequest(id: Int): Request {
        return Request.Builder()
                .url("$baseUrl/boxes/$id")
                .addHeader("Authorization","Bearer $accessToken")
                .delete()
                .build()
    }

}