package com.armx.chaoxing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armx.chaoxing.R
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.utils.PressFeedbackType
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.ButtonMaterial
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.layout.ListDialogItem
import io.github.suqi8.coui.kmp.overlay.OverlaySingleChoiceDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.VisualTransformation

/** 登录页：账号密码 + 记住密码 + 已保存账号回填（COUI 组件）。 */
@Composable
fun LoginScreen(controller: CxController, modifier: Modifier = Modifier) {
    val scheme = COUITheme.colorScheme
    val accounts = remember(controller.logged) { controller.savedAccounts() }
    var username by remember {
        mutableStateOf(controller.prefillUsername ?: accounts.firstOrNull()?.username ?: "")
    }
    var password by remember {
        mutableStateOf(
            controller.prefillPassword
                ?: controller.decryptPassword(accounts.firstOrNull()?.password)
        )
    }
    // 设置页「切换账号」后，把新账号填进来
    androidx.compose.runtime.LaunchedEffect(controller.prefillSeq) {
        controller.prefillUsername?.let { username = it }
        controller.prefillPassword?.let { password = it }
    }
    var remember by remember { mutableStateOf(true) }
    var showAccounts by remember { mutableStateOf(false) }
    var showPwd by remember { mutableStateOf(false) }
    val loginSource = remember { MutableInteractionSource() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 22.dp,
                    end = 22.dp,
                    top = pad.calculateTopPadding() + 48.dp,
                    bottom = 40.dp,
                ),
        ) {
            val icon = controller.appIcon
            if (icon != null) {
                Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            } else {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(scheme.primary),
                )
            }
            Text(
                "超星AX",
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.labelPrimary,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                Loc.of("超星 · 学习通 · 尔雅 · 任务点助手"),
                fontSize = 13.sp,
                color = scheme.labelSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(32.dp))

            if (accounts.isNotEmpty()) {
                SavedAccountRow(
                    text = accounts.firstOrNull { it.username == username }?.title() ?: Loc.of("选择已保存的账号"),
                    onClick = { showAccounts = true },
                )
                Spacer(Modifier.height(10.dp))
            }

            TextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                label = Loc.of("手机号 / 用户名"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth()) {
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = Loc.of("密码"),
                    singleLine = true,
                    visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                IconButton(
                    onClick = { showPwd = !showPwd },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(
                        if (showPwd) CxIcons.EyeOff else CxIcons.Eye,
                        contentDescription = if (showPwd) Loc.of("隐藏密码") else Loc.of("显示密码"),
                        tint = scheme.labelSecondaryVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(Loc.of("记住密码"), fontSize = 15.sp, color = scheme.labelPrimary)
                    Text(
                        Loc.of("本地加密保存，下次自动回填"),
                        fontSize = 12.sp,
                        color = scheme.labelSecondaryVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Switch(checked = remember, onCheckedChange = { remember = it })
            }

            Spacer(Modifier.height(26.dp))

            // 与退出登录弹窗里的「退出」保持同一套：COUI Button + Primary 材料，
            // 按压回弹 / 高光 / 材料全部由 Button 自己出，不再叠自定义缩放。
            Button(
                onClick = { controller.login(username, password, remember) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !controller.busy,
                colors = ButtonDefaults.buttonColorsPrimary(),
                material = ButtonMaterial.Primary,
                cornerRadius = 24.dp,
                insideMargin = PaddingValues(vertical = 13.dp),
                minHeight = 46.dp,
            ) {
                Text(
                    if (controller.busy) Loc.of("正在登录…") else Loc.of("登 录"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }

            Text(
                Loc.of("本地加密存储 · 仅保存登录凭据"),
                fontSize = 11.sp,
                color = scheme.labelSecondaryVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        OverlaySingleChoiceDialog(
            show = showAccounts,
            title = Loc.of("已保存的账号"),
            items = accounts.map { ListDialogItem(text = it.title(), summary = it.username) },
            selectedIndex = accounts.indexOfFirst { it.username == username },
            onSelectedIndexChange = { index ->
                val a = accounts.getOrNull(index)
                if (a != null) {
                    username = a.username
                    password = controller.decryptPassword(a.password)
                }
                showAccounts = false
            },
            onDismissRequest = { showAccounts = false },
        )
    }
}

@Composable
private fun SavedAccountRow(text: String, onClick: () -> Unit) {
    val scheme = COUITheme.colorScheme
    CxCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = 12.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val switchSource = remember { MutableInteractionSource() }
            Text("👤", fontSize = 15.sp)
            Text(text, fontSize = 14.sp, color = scheme.labelPrimary, modifier = Modifier.weight(1f))
            Button(
                onClick = onClick,
                modifier = Modifier.touchFeel(switchSource),
                minHeight = 28.dp,
                minWidth = 0.dp,
                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                colors = ButtonDefaults.buttonColors(
                    color = scheme.cardPressed,
                    contentColor = scheme.labelPrimary,
                ),
                interactionSource = switchSource,
            ) {
                Text(Loc.of("切换"), fontSize = 12.sp, color = scheme.labelPrimary)
            }
        }
    }
}
