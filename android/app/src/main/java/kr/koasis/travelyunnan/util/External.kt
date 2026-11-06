package kr.koasis.travelyunnan.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** 외부 앱 연동(S2-6). 설치되지 않은 앱 버튼은 화면에서 숨긴다. */
object External {
    const val AMAP = "com.autonavi.minimap"
    const val DIDI = "com.sdu.didi.psnger"
    const val WECHAT = "com.tencent.mm"

    fun isInstalled(ctx: Context, pkg: String): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    fun copy(ctx: Context, text: String, label: String = "복사됨") {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("travel-yunnan", text))
        Toast.makeText(ctx, label, Toast.LENGTH_SHORT).show()
    }

    fun dial(ctx: Context, phone: String) {
        val digits = phone.filter { it.isDigit() || it == '+' }
        start(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")))
    }

    fun openUrl(ctx: Context, url: String) = start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    /** 고덕지도에서 키워드 검색. 주소는 클립보드에도 복사한다. */
    /** 고덕지도 웹 검색 링크(앱이 없을 때). 도시는 주소에서 짐작한다(웹 amapSearchUrl 과 같음) */
    fun amapSearchUrl(keyword: String): String {
        val city = when {
            Regex("丽江|白沙|束河|玉龙|古城区").containsMatchIn(keyword) -> "丽江"
            Regex("香格里拉|迪庆|虎跳峡|独克宗|松赞").containsMatchIn(keyword) -> "迪庆藏族自治州"
            Regex("昆明|官渡|五华|盘龙|西山").containsMatchIn(keyword) -> "昆明"
            else -> ""
        }
        return "https://uri.amap.com/search?keyword=" + Uri.encode(keyword) + (if (city.isNotEmpty()) "&city=" + Uri.encode(city) else "") + "&src=travelyunnan&callnative=1"
    }

    fun openAmapSearch(ctx: Context, keyword: String) {
        copy(ctx, keyword, "주소를 복사했다")
        val uri = Uri.parse("androidamap://poi?sourceApplication=travelyunnan&keywords=" + Uri.encode(keyword) + "&dev=0")
        val intent = Intent(Intent.ACTION_VIEW, uri).setPackage(AMAP)
        if (!start(ctx, intent)) launch(ctx, AMAP)
    }

    /** DiDi 실행. 주소를 먼저 복사해 두면 목적지 칸에 붙여넣을 수 있다. */
    fun openDidi(ctx: Context, address: String) {
        copy(ctx, address, "주소를 복사했다. DiDi 목적지에 붙여넣는다")
        launch(ctx, DIDI)
    }

    fun openWechat(ctx: Context, text: String) {
        copy(ctx, text, "문장을 복사했다. 위챗 대화창에 붙여넣는다")
        launch(ctx, WECHAT)
    }

    private fun launch(ctx: Context, pkg: String): Boolean =
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { start(ctx, it) } ?: false

    private fun start(ctx: Context, intent: Intent): Boolean = runCatching {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)
}
