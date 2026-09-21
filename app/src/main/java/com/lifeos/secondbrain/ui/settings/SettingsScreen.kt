package com.lifeos.secondbrain.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.secondbrain.settings.AppearanceMode
import com.lifeos.secondbrain.network.TunnelState
import com.lifeos.secondbrain.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val keyConfigured by vm.aiKeyConfigured.collectAsStateWithLifecycle()
    val proxyPasswordConfigured by vm.proxyPasswordConfigured.collectAsStateWithLifecycle()
    var baseUrl by remember(settings.aiBaseUrl) { mutableStateOf(settings.aiBaseUrl) }
    var model by remember(settings.aiModel) { mutableStateOf(settings.aiModel) }
    var apiKey by remember { mutableStateOf("") }
    var aiEnabled by remember(settings.aiEnabled) { mutableStateOf(settings.aiEnabled) }
    var ignored by remember(settings.ignoredFolders) { mutableStateOf(settings.ignoredFolders) }
    var proxyEnabled by remember(settings.proxyEnabled) { mutableStateOf(settings.proxyEnabled) }
    var proxyType by remember(settings.proxyType) { mutableStateOf(settings.proxyType) }
    var proxyHost by remember(settings.proxyHost) { mutableStateOf(settings.proxyHost) }
    var proxyPort by remember(settings.proxyPort) { mutableStateOf(settings.proxyPort.toString()) }
    var proxyUser by remember(settings.proxyUsername) { mutableStateOf(settings.proxyUsername) }
    var proxyPass by remember { mutableStateOf("") }
    var tunnelEnabled by remember(settings.tunnelEnabled) { mutableStateOf(settings.tunnelEnabled) }
    var nodeHost by remember(settings.nodeHost) { mutableStateOf(settings.nodeHost) }
    var nodePort by remember(settings.nodePort) { mutableStateOf(settings.nodePort.toString()) }
    var nodeUuid by remember(settings.nodeUuid) { mutableStateOf(settings.nodeUuid) }
    var nodePublicKey by remember(settings.nodePublicKey) { mutableStateOf(settings.nodePublicKey) }
    var nodeShortId by remember(settings.nodeShortId) { mutableStateOf(settings.nodeShortId) }
    var nodeSni by remember(settings.nodeSni) { mutableStateOf(settings.nodeSni) }
    val tunnelState by vm.tunnelState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("设置") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回") } }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionTitle("数据")
                Text("Google 账号：${settings.googleAccount ?: "未登录"}", style = MaterialTheme.typography.bodyLarge)
                Text("Google Drive：${settings.vaultName ?: "未连接"}", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { vm.refresh() }, enabled = settings.vaultFolderId != null) { Text("立即同步") }
                    OutlinedButton(onClick = { vm.clearLocalCache() }) { Text("清除本地缓存") }
                }
                OutlinedButton(onClick = { vm.disconnectDrive() }, enabled = settings.vaultFolderId != null) { Text("断开 Drive") }
                OutlinedTextField(
                    ignored,
                    { ignored = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("忽略文件夹（逗号分隔）") },
                    singleLine = true
                )
                Text(
                    "这些文件夹里的笔记不会被索引（模板、系统文件夹）。隐藏文件夹（. 开头）始终忽略。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { vm.saveIgnoredFolders(ignored) }) { Text("保存忽略列表并重建") }
            }
            item {
                SectionTitle("VPS 直连（内置内核）")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("App 内直连 VPS，不用系统 VPN")
                    Switch(checked = tunnelEnabled, onCheckedChange = { tunnelEnabled = it })
                }
                OutlinedTextField(nodeHost, { nodeHost = it }, Modifier.fillMaxWidth(), label = { Text("主机（VPS IP）") }, singleLine = true)
                OutlinedTextField(
                    nodePort,
                    { nodePort = it.filter { ch -> ch.isDigit() }.take(5) },
                    Modifier.fillMaxWidth(),
                    label = { Text("端口") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(nodeUuid, { nodeUuid = it }, Modifier.fillMaxWidth(), label = { Text("UUID") }, singleLine = true)
                OutlinedTextField(nodePublicKey, { nodePublicKey = it }, Modifier.fillMaxWidth(), label = { Text("Reality PublicKey") }, singleLine = true)
                OutlinedTextField(nodeShortId, { nodeShortId = it }, Modifier.fillMaxWidth(), label = { Text("Reality ShortId") }, singleLine = true)
                OutlinedTextField(nodeSni, { nodeSni = it }, Modifier.fillMaxWidth(), label = { Text("SNI（伪装域名）") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        vm.saveTunnelSettings(tunnelEnabled, nodeHost, nodePort, nodeUuid, nodePublicKey, nodeShortId, nodeSni)
                    }) { Text("保存并应用") }
                    OutlinedButton(onClick = { vm.testProxy() }) { Text("测试连接") }
                }
                Text(
                    when (val s = tunnelState) {
                        is TunnelState.Off -> "状态：未启动"
                        is TunnelState.Starting -> "状态：正在启动……"
                        is TunnelState.Running -> "状态：运行中（本地端口 ${s.port}）"
                        is TunnelState.Failed -> "状态：启动失败 — ${s.reason}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "内置内核只代理本 App 的 Drive/AI 请求；Google 登录由系统服务完成，不受影响。节点信息只保存在本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                SectionTitle("代理")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("仅本 App 使用代理")
                    Switch(checked = proxyEnabled, onCheckedChange = { proxyEnabled = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("SOCKS5", "HTTP").forEach { type ->
                        FilterChip(
                            selected = proxyType == type,
                            onClick = { proxyType = type },
                            label = { Text(type) }
                        )
                    }
                }
                OutlinedTextField(proxyHost, { proxyHost = it }, Modifier.fillMaxWidth(), label = { Text("主机") }, singleLine = true)
                OutlinedTextField(
                    proxyPort,
                    { proxyPort = it.filter { ch -> ch.isDigit() }.take(5) },
                    Modifier.fillMaxWidth(),
                    label = { Text("端口") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(proxyUser, { proxyUser = it }, Modifier.fillMaxWidth(), label = { Text("用户名（可选）") }, singleLine = true)
                OutlinedTextField(
                    proxyPass,
                    { proxyPass = it },
                    Modifier.fillMaxWidth(),
                    label = { Text(if (proxyPasswordConfigured) "密码（已保存；留空不修改）" else "密码（可选）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        vm.saveProxySettings(proxyEnabled, proxyType, proxyHost, proxyPort, proxyUser, proxyPass.takeIf { it.isNotBlank() })
                        proxyPass = ""
                    }) { Text("保存代理") }
                    OutlinedButton(onClick = { vm.testProxy() }) { Text("测试代理") }
                    if (proxyPasswordConfigured) OutlinedButton(onClick = { vm.clearProxyPassword() }) { Text("删除密码") }
                }
                Text(
                    "代理只作用于本 App 的网络请求（Drive / AI），不影响系统其他 App，也不需要开系统 VPN。Google 登录由系统服务完成，不受此设置影响。先保存，再测试。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                SectionTitle("AI")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("保存后自动整理")
                    Switch(checked = aiEnabled, onCheckedChange = { aiEnabled = it })
                }
                OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text("API Base URL") }, singleLine = true)
                OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true)
                OutlinedTextField(
                    apiKey,
                    { apiKey = it },
                    Modifier.fillMaxWidth(),
                    label = { Text(if (keyConfigured) "API Key（已安全保存；留空不修改）" else "API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { vm.saveAiSettings(aiEnabled, baseUrl, model, apiKey.takeIf { it.isNotBlank() }); apiKey = "" }) { Text("保存 AI 设置") }
                    OutlinedButton(onClick = { vm.testAiConnection(baseUrl, model, apiKey.takeIf { it.isNotBlank() }) }) { Text("测试连接") }
                    if (keyConfigured) OutlinedButton(onClick = { vm.clearAiKey() }) { Text("删除 Key") }
                }
            }
            item {
                SectionTitle("外观")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppearanceMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.appearance == mode,
                            onClick = { vm.setAppearance(mode) },
                            label = { Text(when (mode) { AppearanceMode.SYSTEM -> "跟随系统"; AppearanceMode.LIGHT -> "浅色"; AppearanceMode.DARK -> "深色" }) }
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("减少动态效果")
                    Switch(checked = settings.reduceMotion, onCheckedChange = { vm.setReduceMotion(it) })
                }
            }
            item {
                SectionTitle("隐私")
                Text("AI Key 使用 Android Keystore 加密；Vault 内容不会发送到分析服务。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                SectionTitle("关于")
                Text("人生第二大脑 · 0.1.0")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
}
