package com.corn.manageapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val scrollState = rememberScrollState()
    val tabTitles = remember { listOf("基本信息", "网络", "电源状态", "硬件", "系统", "其它") }

    LaunchedEffect(selectedTab) {
        scrollState.scrollTo(0)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Spacer(Modifier.width(8.dp))
            Text(
                text = item.nbtag?.takeIf { it.isNotBlank() } ?: "内部标签未知",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 12.dp
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Divider()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    SectionTitle("基本信息")
                    Info("物理标签", item.wltag)
                    Info("型号", item.typename)
                    Info("电源", item.power)
                    Info("上架时间", item.time)
                    Info("类型", item.stype)
                    Info("服务器ID", item.serverid)
                    Info("机房", item.house_name)
                    Info("机柜", item.cname)
                }

                1 -> {
                    SectionTitle("网络信息")
                    Info("主IP", item.zhuip)
                    Info("子网掩码", item.subnetmask)
                    Info("网关", item.gateway)
                    Info("VLAN", item.vlan)
                    Info("VLAN ID", item.vlanid)
                    Info("交换机端口", combineSwitch(item.switch_id, item.switch_num_name, item.switch_num))
                    Info("上行带宽", item.in_bw)
                    Info("下行带宽", item.out_bw)
                    Info("MAC", item.mac)
                    Info("端口MAC", jsonArrayToInline(item.port_mac))
                    Info("IP 列表", normalizeIpList(item.ip))
                }

                2 -> {
                    SectionTitle("电源 / 状态")
                    Info("IPMI 支持", item.ipmi_support)
                    Info("IPMI IP", item.ipmi_ip)
                    Info("IPMI 用户名", item.ipmi_name)
                    Info("IPMI 密码", item.ipmi_pass)
                    Info("电源信息", item.power_msg)
                }

                3 -> {
                    SectionTitle("硬件")
                    Info("CPU", normalizeCpu(item.cpu))
                    Info("内存", normalizeNameNumArray(item.ram))
                    Info("硬盘", normalizeNameNumArray(item.disk))
                    Info("PCI", normalizeNameNumArray(item.pci))
                    Info("硬盘数量", item.disk_num)
                    Info("硬盘容量", jsonArrayToInline(item.disk_size))
                }

                4 -> {
                    SectionTitle("系统")
                    Info("OS 名称", item.osname)
                    Info("OS ID", item.os_id)
                    Info("OS 组", item.os_group_id)
                    Info("系统账号", item.osusername)
                    Info("系统密码", item.ospassword)
                    Info("默认用户", item.default_user)
                    Info("破解用户", item.crack_user)
                }

                else -> {
                    SectionTitle("其它")
                    Info("锁定", item.lock)
                    Info("平均流量", item.average_flow)
                    Info("机柜ID", item.cid)
                    Info("机房ID", item.house)
                    Info("合计", item.sum)
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

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
