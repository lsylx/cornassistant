package com.corn.manageapp.network

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * DCIM API（hardwareListing）
 * - testConnection：测试连通性（最小代价）
 * - queryServers  ：标准查询（统一搜索 search=key, key=...，亦支持全部高级参数）
 * - queryServersRaw：拿原始完整 JSON（调试专用）
 *
 * 注意：
 * - baseUrl 必须以 "/" 结尾（create() 已兜底）
 * - HTTP 场景请在 Manifest 设置 android:usesCleartextTraffic="true"
 * - HTTPS 自签需配置 network_security_config
 */
interface DcimApi {

    /** ✅ 连通性测试：只需用户名/密码/少量数据 */
    @FormUrlEncoded
    @POST("index.php?m=api&a=hardwareListing")
    suspend fun testConnection(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("listpages") listpages: Int = 1
    ): DcimResponse

    /** ✅ 标准查询：完整 hardwareListing（默认统一搜索：search=key） */
    @FormUrlEncoded
    @POST("index.php?m=api&a=hardwareListing")
    suspend fun queryServers(
        @Field("username") username: String,
        @Field("password") password: String,

        // —— 搜索维度（默认统一搜索）——
        @Field("search") search: String? = "key",
        @Field("key") key: String? = null,

        // —— 分页/排序/类型 ——
        @Field("listpages") listpages: Int? = 20,
        @Field("orderby") orderby: String? = null,
        @Field("sorting") sorting: String? = null, // asc / desc
        @Field("offset") offset: Int? = null,
        @Field("type") type: String? = null,       // 1租用 2自用 5托管

        // —— 高级/特殊搜索 ——（全部可空）
        @Field("cpu") cpu: List<String>? = null,
        @Field("cpu_num") cpu_num: Int? = null,
        @Field("ram") ram: List<String>? = null,
        @Field("ram_num") ram_num: Int? = null,
        @Field("disk") disk: List<String>? = null,
        @Field("disk_num") disk_num: Int? = null,
        @Field("pci") pci: List<String>? = null,
        @Field("pci_num") pci_num: Int? = null,
        @Field("ip") ip: String? = null,
        @Field("ip_num") ip_num: Int? = null,
        @Field("ram_capacity") ram_capacity: Int? = null,
        @Field("filter") filter: Int? = null       // 0:一致 1:包含
    ): DcimFullResponse

    /** ✅ 原始 JSON（调试查看完整返回） */
    @FormUrlEncoded
    @POST("index.php?m=api&a=hardwareListing")
    suspend fun queryServersRaw(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("search") search: String? = "key",
        @Field("key") key: String? = null,
        @Field("listpages") listpages: Int? = 20
    ): ResponseBody

    companion object {
        fun create(baseUrl: String): DcimApi {
            val gson = GsonBuilder()
                .setLenient() // ✅ 放宽解析（容错不规范/异构 JSON）
                .create()

            return Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/") // ✅ 兜底
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(DcimApi::class.java)
        }
    }
}

/* ======================== 数据模型（关键字段可空，提升容错） ======================== */

data class DcimResponse(
    val status: String? = null,
    val msg: String? = null
)

data class DcimFullResponse(
    val status: String? = null,
    val listing: List<HardwareItem>? = null,

    // 🛡️ 不同环境下 intranetServer / switch 可能是 object 或 array
    //    用 JsonElement 接收，后续在 UI 判型展示
    val intranetServer: JsonElement? = null,

    @SerializedName("switch")
    val switchLinks: JsonElement? = null,

    // meta
    val sum: Int? = null,
    val listpages: Int? = null,
    val search: String? = null,
    val key: String? = null,
    val orderby: String? = null,
    val sorting: String? = null,
    val offset: Int? = null,
    val type: String? = null
)

data class HardwareItem(
    val id: String? = null,
    val wltag: String? = null,
    val nbtag: String? = null,

    // 这些在不同机房可能是对象或数组：用 JsonElement 接
    val cpu: JsonElement? = null,    // {name,num} 或 [{name,num},...]
    val ram: JsonElement? = null,    // [{name,num},...]
    val disk: JsonElement? = null,   // [{name,num},...]
    val pci: JsonElement? = null,    // [{name,num},...]

    val time: String? = null,
    val serverid: String? = null,
    val stype: String? = null,        // 1租用 2自用 5托管 8机柜客户

    val power: String? = null,        // on/off/error/nonsupport
    val power_msg: String? = null,
    val ipmi_support: String? = null, // "true"/"false"
    val ipmi_name: String? = null,
    val ipmi_pass: String? = null,
    val ipmi_ip: String? = null,

    val zhuip: String? = null,
    val subnetmask: String? = null,
    val gateway: String? = null,

    // 有的环境是 ["1.2.3.4"]，有的环境是 [{"ipaddress":"1.2.3.4"}]
    val ip: JsonElement? = null,

    val vlan: String? = null,
    val vlanid: String? = null,

    val switch_id: String? = null,
    val switch_num: String? = null,
    val switch_num_name: String? = null,
    val in_bw: String? = null,
    val out_bw: String? = null,

    val intranet_id: String? = null,
    val lock: String? = null,
    val mac: String? = null,
    val port_mac: JsonElement? = null, // 可能是 []

    val mac_diff: String? = null,
    val typename: String? = null,
    val pid: String? = null,
    val pdu: String? = null,
    val pdu_num: String? = null,
    val showtype: String? = null,

    val cid: String? = null,
    val cname: String? = null,
    val house: String? = null,
    val house_name: String? = null,

    val osname: String? = null,
    val os_id: String? = null,
    val os_group_id: String? = null,
    val osusername: String? = null,
    val ospassword: String? = null,

    val sum: String? = null,

    // 你样本里有 disk_num / disk_size 等字段（可选）
    val disk_num: String? = null,
    val disk_size: JsonElement? = null,

    val average_flow: String? = null,
    val crack_user: String? = null,
    val default_user: String? = null
)