package kr.koasis.travelyunnan.domain

/** 위챗 메시지 템플릿(S6-2). 변수 값을 치환해 중국어 본문과 한국어 설명을 만든다. */
data class TemplateField(val key: String, val label: String, val default: String, val hint: String = "")

data class Template(val id: String, val title: String, val fields: List<TemplateField>, val render: (Map<String, String>) -> String, val renderKo: (Map<String, String>) -> String)

object Templates {
    private fun v(m: Map<String, String>, k: String, fields: List<TemplateField>) =
        m[k]?.takeIf { it.isNotBlank() } ?: fields.first { it.key == k }.default

    val tigerLeaping: Template = run {
        val f = listOf(
            TemplateField("from", "출발지", "白沙古镇 Rishine Villa（日沐山）", "숙소 중국어명"),
            TemplateField("date", "날짜", "12月20日（周日）早上9点", "예: 明天早上9点"),
            TemplateField("wait", "상호도협 대기", "1到1.5小时"),
            TemplateField("to", "최종 목적지", "茶马客栈"),
            TemplateField("luggage", "짐 보낼 곳", "中途客栈附近的“靠山十九度 (COME INN) 客栈”"),
            TemplateField("persons", "인원", "3"),
        )
        Template("tpl-tiger", "호도협 전용차 예약 요청", f,
            render = { m ->
                "你好，我想预订去虎跳峡徒步的包车，${v(m, "persons", f)}个人。行程如下：\n" +
                    "• 出发：${v(m, "from", f)}，${v(m, "date", f)}\n" +
                    "• 经停：上虎跳峡景区，停留${v(m, "wait", f)}\n" +
                    "• 终点：${v(m, "to", f)}\n" +
                    "另外，我们的行李箱到达${v(m, "to", f)}后，需要托运到${v(m, "luggage", f)}。\n" +
                    "请问这样包一辆车的总费用是多少钱？请写在微信上，谢谢！"
            },
            renderKo = { m ->
                "호도협 트레킹 전용차 예약 요청(${v(m, "persons", f)}명). 출발 ${v(m, "from", f)} ${v(m, "date", f)}, 상호도협 ${v(m, "wait", f)} 대기 후 ${v(m, "to", f)}까지. 짐은 ${v(m, "luggage", f)}로 탁송. 차 1대 총비용 문의, 위챗으로 답장 요청."
            })
    }

    val shangrila: Template = run {
        val f = listOf(
            TemplateField("date", "출발 시각", "明天上午10点半", "예: 12月21日上午10点半"),
            TemplateField("from", "출발지", "靠山十九度"),
            TemplateField("to", "도착지", "香格里拉 独克宗古城 龟山巷3号（Oura Villa）"),
            TemplateField("persons", "인원", "3"),
        )
        Template("tpl-shangrila", "샹그릴라 빵차 예약 요청", f,
            render = { m -> "你好，我想预订${v(m, "date", f)}，从${v(m, "from", f)}到${v(m, "to", f)}的包车，${v(m, "persons", f)}个人，行李一起。请问一辆车的费用是多少钱？请写在微信上，谢谢！" },
            renderKo = { m -> "${v(m, "date", f)} ${v(m, "from", f)}에서 ${v(m, "to", f)}까지 ${v(m, "persons", f)}명 빵차(짐 포함) 예약 요청, 차 1대 비용 문의." })
    }

    val delivery: Template = run {
        val f = listOf(
            TemplateField("hotel", "호텔 중국어명", "格菲酒店(昆明火车站北广场店)"),
            TemplateField("room", "방 번호", "", "체크인 후 입력"),
        )
        Template("tpl-delivery", "배달 주소 요청(프런트)", f,
            render = { m -> "麻烦帮我们写一下送到我们住的酒店房间的收货地址，酒店是${v(m, "hotel", f)}" + (m["room"]?.takeIf { it.isNotBlank() }?.let { "，房间号${it}" } ?: "") + "。我们没有国内手机号，请别打电话，直接放在前台就行。" },
            renderKo = { m -> "메이투안 배달용 수령 주소(호텔명·방 번호)를 적어 달라는 요청. 중국 번호가 없으니 전화 말고 프런트에 맡겨 달라고 덧붙인다." })
    }

    val pickup: Template = run {
        val f = listOf(
            TemplateField("date", "도착 시각", "12月17日 12:27", "열차 도착 시각"),
            TemplateField("where", "도착 장소", "丽江站"),
            TemplateField("persons", "인원·짐", "3个人，3个行李箱"),
        )
        Template("tpl-pickup", "숙소 픽업·짐 운반 요청", f,
            render = { m -> "你好，我们${v(m, "date", f)}到${v(m, "where", f)}，${v(m, "persons", f)}。请问可以安排接送吗？古城里车进不去的话，请告诉我们在哪里下车，麻烦帮忙运一下行李。谢谢！" },
            renderKo = { m -> "${v(m, "date", f)} ${v(m, "where", f)} 도착(${v(m, "persons", f)}). 픽업 가능 여부와 고성 밖 하차 지점, 짐 운반을 요청한다." })
    }

    val all = listOf(tigerLeaping, shangrila, delivery, pickup)
}
