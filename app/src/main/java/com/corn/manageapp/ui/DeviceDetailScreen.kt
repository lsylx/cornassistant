package com.corn.manageapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.corn.manageapp.network.HardwareItem
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

@Composable
private data class DetailSection(val title: String, val items: List<Pair<String, String?>>)

fun DeviceDetailScreen(
    item: HardwareItem,
    onBack: () -> Unit
) {
    val sections = remember(item) {
        listOf(
            DetailSection(
                "基本信息",
                listOf(
                    "内部标签" to item.nbtag,
                    "物理标签" to item.wltag,
                    "型号" to item.typename,
                    "电源" to item.power,
                    "上架时间" to item.time,
                    "类型" to item.stype,
                    "服务器ID" to item.serverid,
                    "机房" to item.house_name,
                    "机柜" to item.cname
                )
            ),
            DetailSection(
                "网络",
                listOf(
                    "主IP" to item.zhuip,
                    "子网掩码" to item.subnetmask,
                    "网关" to item.gateway,
                    "VLAN" to item.vlan,
                    "VLAN ID" to item.vlanid,
                    "交换机端口" to combineSwitch(item.switch_id, item.switch_num_name, item.switch_num),
                    "上行带宽" to item.in_bw,
                    "下行带宽" to item.out_bw,
                    "MAC" to item.mac,
                    "端口MAC" to jsonArrayToInline(item.port_mac),
                    "IP 列表" to normalizeIpList(item.ip)
                )
            ),
            DetailSection(
                "电源状态",
                listOf(
                    "IPMI 支持" to item.ipmi_support,
                    "IPMI IP" to item.ipmi_ip,
                    "IPMI 用户名" to item.ipmi_name,
                    "IPMI 密码" to item.ipmi_pass,
                    "电源信息" to item.power_msg
                )
            ),
            DetailSection(
                "硬件",
                listOf(
                    "CPU" to normalizeCpu(item.cpu),
                    "内存" to normalizeNameNumArray(item.ram),
                    "硬盘" to normalizeNameNumArray(item.disk),
                    "PCI" to normalizeNameNumArray(item.pci),
                    "硬盘数量" to item.disk_num,
                    "硬盘容量" to jsonArrayToInline(item.disk_size)
                )
            ),
            DetailSection(
                "系统",
                listOf(
                    "OS 名称" to item.osname,
                    "OS ID" to item.os_id,
                    "OS 组" to item.os_group_id,
                    "系统账号" to item.osusername,
                    "系统密码" to item.ospassword,
                    "默认用户" to item.default_user,
                    "破解用户" to item.crack_user
                )
            ),
            DetailSection(
                "其它",
                listOf(
                    "锁定" to item.lock,
                    "平均流量" to item.average_flow,
                    "机柜ID" to item.cid,
                    "机房ID" to item.house,
                    "合计" to item.sum
                )
            )
        )
    }

    var selectedTab by rememberSaveable(item.id) { mutableStateOf(0) }
    if (selectedTab !in sections.indices) {
        selectedTab = 0
    }
    val activeSection = sections.getOrNull(selectedTab) ?: sections.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text(
                text = "内部标签：${item.nbtag?.takeIf { it.isNotBlank() } ?: "-"}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text(
            text = "设备详情",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 0.dp,
            divider = {}
        ) {
            sections.forEachIndexed { index, section ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(section.title, style = MaterialTheme.typography.bodySmall) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text(activeSection.title, style = MaterialTheme.typography.titleMedium)
                Divider()
            }

            val entries = activeSection.items.mapNotNull { (label, value) ->
                val display = value?.takeIf { it.isNotBlank() && it != "null" }
                display?.let { label to it }
            }

            if (entries.isEmpty()) {
                item {
                    Text(
                        "暂无数据",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                items(entries) { (label, value) ->
                    Info(label, value)
                }
            }
        }
    }
}

/* =========================  UI 小组件  ========================= */

/** ✅ 只保留一个 Info（避免 JVM 冲突） */
@Composable
private fun Info(label: String, value: String?) {
    if (!value.isNullOrEmpty() && value != "null") {
        Text("$label：$value", fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(4.dp))
    }
}

/* =========================  文本拼装  ========================= */

private fun combineSwitch(id: String?, name: String?, num: String?): String? {
    if (id.isNullOrEmpty() && name.isNullOrEmpty() && num.isNullOrEmpty()) return null
    val right = name ?: num ?: "-"
    return "${id ?: "-"} / $right"
}

/* =========================  JSON 解析规范化  ========================= */

private fun normalizeIpList(ip: JsonElement?): String? {
    if (ip == null || ip.isJsonNull) return null
    return when {
        ip.isJsonArray -> {
            val arr = ip.asJsonArray
            if (arr.size() == 0) return null
            arr.joinToString("，") { e ->
                if (e.isJsonObject) e.asJsonObject["ipaddress"]?.asString ?: ""
                else e.asSafeString()
            }
        }

        ip.isJsonObject -> ip.asJsonObject["ipaddress"]?.asString

        else -> ip.asSafeString()
    }?.takeIf { it.isNotBlank() }
}

private fun normalizeCpu(cpu: JsonElement?): String? {
    if (cpu == null || cpu.isJsonNull) return null
    return when {
        cpu.isJsonObject -> nameNumOne(cpu.asJsonObject)
        cpu.isJsonArray -> {
            val first = cpu.asJsonArray.firstOrNull() ?: return null
            if (first.isJsonObject) nameNumOne(first.asJsonObject) else first.asSafeString()
        }

        else -> cpu.asSafeString()
    }
}

private fun normalizeNameNumArray(el: JsonElement?): String? {
    if (el == null || el.isJsonNull) return null
    if (!el.isJsonArray) return el.asSafeString()
    val arr = el.asJsonArray
    if (arr.size() == 0) return null
    return arr.joinToString("； ") { e ->
        if (e.isJsonObject) nameNumOne(e.asJsonObject) else e.asSafeString()
    }
}

private fun jsonArrayToInline(el: JsonElement?): String? {
    if (el == null || el.isJsonNull) return null
    return when {
        el.isJsonArray -> {
            val arr = el.asJsonArray
            if (arr.size() == 0) null else arr.joinToString(", ") { it.asSafeString() }
        }

        else -> el.asSafeString()
    }
}

/* =========================  小工具  ========================= */

private fun nameNumOne(obj: JsonObject): String {
    val name = obj["name"]?.asSafeString().orEmpty().ifEmpty { "-" }
    val num = obj["num"]?.asSafeString().orEmpty().ifEmpty { "1" }
    return "$name × $num"
}

private fun JsonElement.asSafeString(): String {
    return try {
        if (isJsonNull) "" else if (isJsonPrimitive) asString else toString()
    } catch (_: Exception) {
        toString()
    }
}

private fun JsonArray.firstOrNull(): JsonElement? = if (size() > 0) get(0) else null
