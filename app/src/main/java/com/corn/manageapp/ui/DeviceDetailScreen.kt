package com.corn.manageapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.corn.manageapp.network.HardwareItem
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

@Composable
fun DeviceDetailScreen(
    item: HardwareItem,
    onBack: () -> Unit
) {
    var selectedTab by rememberSaveable(item.serverid ?: item.nbtag ?: item.wltag ?: "") { mutableStateOf(0) }
    val sections = remember(item) {
        listOf(
            DeviceSection(
                title = "基本信息",
                items = listOf(
                    "内部标签" to item.nbtag,
                    "物理标签" to item.wltag,
                    "型号" to item.typename,
                    "类型" to item.stype,
                    "服务器ID" to item.serverid,
                    "机房" to item.house_name,
                    "机柜" to item.cname,
                    "上架时间" to item.time
                )
            ),
            DeviceSection(
                title = "网络信息",
                items = listOf(
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
            DeviceSection(
                title = "电源状态",
                items = listOf(
                    "电源" to item.power,
                    "电源信息" to item.power_msg,
                    "IPMI 支持" to item.ipmi_support,
                    "IPMI IP" to item.ipmi_ip,
                    "IPMI 用户名" to item.ipmi_name,
                    "IPMI 密码" to item.ipmi_pass
                )
            ),
            DeviceSection(
                title = "硬件信息",
                items = listOf(
                    "CPU" to normalizeCpu(item.cpu),
                    "内存" to normalizeNameNumArray(item.ram),
                    "硬盘" to normalizeNameNumArray(item.disk),
                    "PCI" to normalizeNameNumArray(item.pci),
                    "硬盘数量" to item.disk_num,
                    "硬盘容量" to jsonArrayToInline(item.disk_size)
                )
            ),
            DeviceSection(
                title = "系统信息",
                items = listOf(
                    "OS 名称" to item.osname,
                    "OS ID" to item.os_id,
                    "OS 组" to item.os_group_id,
                    "系统账号" to item.osusername,
                    "系统密码" to item.ospassword,
                    "默认用户" to item.default_user,
                    "破解用户" to item.crack_user
                )
            ),
            DeviceSection(
                title = "其它信息",
                items = listOf(
                    "锁定" to item.lock,
                    "平均流量" to item.average_flow,
                    "机柜ID" to item.cid,
                    "机房ID" to item.house,
                    "合计" to item.sum
                )
            )
        )
    }
    val scrollState = rememberScrollState()
    val headerTag = item.nbtag?.takeIf { it.isNotBlank() } ?: "(无内部标签)"
    val physicalTag = item.wltag?.takeIf { it.isNotBlank() }
    val clampedIndex = selectedTab.coerceIn(0, sections.lastIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    headerTag,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                physicalTag?.let {
                    Text(
                        "物理：$it",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ScrollableTabRow(selectedTabIndex = clampedIndex, edgePadding = 0.dp) {
            sections.forEachIndexed { index, section ->
                Tab(
                    selected = clampedIndex == index,
                    onClick = { selectedTab = index },
                    text = { Text(section.title) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            val currentSection = sections.getOrNull(clampedIndex)
            if (currentSection != null) {
                SectionTitle(currentSection.title)
                val entries = currentSection.items.filter { !it.second.isNullOrEmpty() && it.second != "null" }
                if (entries.isEmpty()) {
                    Text(
                        "暂无数据",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    entries.forEach { (label, value) ->
                        Info(label, value)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private data class DeviceSection(
    val title: String,
    val items: List<Pair<String, String?>>
)

/* =========================  UI 小组件  ========================= */

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(12.dp))
    Text(text, style = MaterialTheme.typography.titleMedium)
    Divider()
    Spacer(Modifier.height(6.dp))
}

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
