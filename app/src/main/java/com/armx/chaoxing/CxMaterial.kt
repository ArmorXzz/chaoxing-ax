package com.armx.chaoxing

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.MaterialBlendMode
import io.github.suqi8.coui.kmp.blur.MaterialBlurDefaults
import io.github.suqi8.coui.kmp.blur.MaterialBlurStyle
import io.github.suqi8.coui.kmp.blur.ViewBlurStyle
import io.github.suqi8.coui.kmp.blur.couiMaterialBlur
import io.github.suqi8.coui.kmp.blur.couiViewBlur
import io.github.suqi8.coui.kmp.effect.MaterialBackdropProvider
import io.github.suqi8.coui.kmp.effect.MaterialBackdropSpec
import io.github.suqi8.coui.kmp.effect.MaterialBlurProvider
import io.github.suqi8.coui.kmp.effect.MaterialBlurSpec
import io.github.suqi8.coui.kmp.shader.isRuntimeShaderSupported

/**
 * 材质能力探测：RuntimeShader 需要 Android 13（API 33）以上。
 * 系统不支持时不要塞 provider，COUI 组件会自动回退成不透明底色。
 */
fun blurSupported(): Boolean = isRuntimeShaderSupported()

/** Android 17 起材质模糊半径收窄，Android 16 及以下保持 COUI 原值。 */
private fun blurFactor(): Float =
    if (android.os.Build.VERSION.SDK_INT >= 37) 0.7f else 1f

/**
 * 视图模糊（底栏、导航胶囊等「窗口材质」）：
 * COUI 只负责算出 MaterialBackdropSpec，真正的模糊由 coui-blur 的 couiViewBlur 执行。
 */
class CxViewBlurProvider(private val backdrop: Backdrop) : MaterialBackdropProvider {
    override fun materialBackdrop(spec: MaterialBackdropSpec, shape: Shape): Modifier =
        Modifier.couiViewBlur(
            backdrop,
            ViewBlurStyle(spec.blurRadiusPx * blurFactor(), spec.layer1, spec.mode1, spec.layer2, spec.mode2),
            shape,
        )
}

/** 弹层/面板材质模糊（对话框、下拉、BottomSheet）。 */
class CxMaterialBlurProvider(private val backdrop: Backdrop) : MaterialBlurProvider {
    override fun materialBlur(spec: MaterialBlurSpec, shape: Shape): Modifier =
        Modifier.couiMaterialBlur(
            backdrop,
            MaterialBlurStyle(
                spec.blurRadiusPx * blurFactor(),
                spec.mixColor,
                spec.blendColor,
                MaterialBlendMode.valueOf(spec.blendMode.name),
            ),
            shape,
        )
}

/** COUI 17「毛玻璃对话框」：面板背景透明 + 材质模糊 + 材质描边。 */
fun dialogBlur(backdrop: Backdrop, isDark: Boolean): Modifier =
    Modifier.couiMaterialBlur(backdrop, MaterialBlurDefaults.dialog(isDark))
