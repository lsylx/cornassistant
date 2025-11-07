package com.corn.manageapp.network

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken

private val gson = Gson()

/** 测试连通性：返回 “success/失败信息” */
fun parseTestStatus(raw: String): Pair<Boolean, String> {
    val clean = raw.trim().trim('\uFEFF')
    // 如果返回是 HTML/登录页
    if (clean.startsWith("<")) return false to "服务器返回HTML（可能未允许HTTP或鉴权失败）"
    return try {
        val root = JsonParser.parseString(clean)
        when {
            root.isJsonObject -> {
                val obj = root.asJsonObject
                val status = obj.get("status")?.asString ?: ""
                if (status == "success") true to "连接成功"
                else false to (obj.get("msg")?.asString ?: "状态：$status")
            }
            root.isJsonArray -> {
                // 有些实现直接返回列表，也视为可连通
                true to "连接成功（返回数组）"
            }
            else -> false to "非JSON对象/数组"
        }
    } catch (e: Exception) {
        false to "解析失败：${e.message}"
    }
}

/** 查询服务器：尽量把 listing 解析成 List<HardwareItem> */
fun parseListing(raw: String): Pair<List<HardwareItem>, String?> {
    val clean = raw.trim().trim('\uFEFF')
    if (clean.startsWith("<")) return emptyList<HardwareItem>() to "服务器返回HTML（可能未登录/鉴权失败）"

    return try {
        val root = JsonParser.parseString(clean)
        if (root.isJsonObject) {
            val obj = root.asJsonObject
            val status = obj.get("status")?.asString
            if (status != null && status != "success") {
                return emptyList<HardwareItem>() to ("状态：$status，" + (obj.get("msg")?.asString ?: ""))
            }
            val listingEl: JsonElement? = obj.get("listing")
            if (listingEl != null && listingEl.isJsonArray) {
                val type = object : TypeToken<List<HardwareItem>>() {}.type
                val list: List<HardwareItem> = gson.fromJson(listingEl, type)
                return list to null
            }
            // 某些实现直接整个响应就是数组
            if (obj.entrySet().isEmpty()) return emptyList<HardwareItem>() to "无数据"
            return emptyList<HardwareItem>() to "未找到 listing 字段"
        } else if (root.isJsonArray) {
            val type = object : TypeToken<List<HardwareItem>>() {}.type
            val list: List<HardwareItem> = gson.fromJson(root, type)
            return list to null
        } else {
            emptyList<HardwareItem>() to "非JSON对象/数组"
        }
    } catch (e: Exception) {
        emptyList<HardwareItem>() to "解析失败：${e.message}"
    }
}
