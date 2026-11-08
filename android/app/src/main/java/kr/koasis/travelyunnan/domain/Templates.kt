package kr.koasis.travelyunnan.domain

/** 위챗 메시지 템플릿(S6-2). 변수 값을 치환해 중국어 본문과 한국어 설명을 만든다. */
/** kind: "date"·"time"·"datetime" 이면 숫자로만 받는다(DateText). approx: 시각 뒤에 左右 */
data class TemplateField(val key: String, val label: String, val default: String, val hint: String = "", val kind: String? = null, val approx: Boolean = false)

data class TemplateLine(val zh: String, val ko: String)

data class Contact(val name: String, val wechat: String, val email: String = "", val friendNote: String = "", val friendNoteKo: String = "", val booker: String = "", val bookingRef: String = "", val note: String = "")

data class Template(val id: String, val title: String, val fields: List<TemplateField>, val render: (Map<String, String>) -> String, val renderKo: (Map<String, String>) -> String, val contact: Contact? = null, val lines: List<TemplateLine> = emptyList())

object Templates {
    /** 12/18 옥룡설산 투어 가이드·기사(바우처 기준). 연락은 위챗, 메일은 참고. 바우처의 +86 긴 번호는 깨진 값 */
    /**
     * 옥룡설산 투어(클룩) 대표 예약자·예약 번호. 개인 정보라 저장소(공개)에 넣지 않는다.
     * 앱 assets 의 data/private.json(빌드 때 저장소 밖 파일에서 넣음)을 TripData 가 읽어 setGuideBooking 으로 채운다(웹과 같다).
     * 없으면 빈칸이고, 템플릿 화면에서 직접 적을 수 있다.
     */
    @Volatile var guideBooker = ""; private set
    @Volatile var guideRef = ""; private set
    fun setGuideBooking(booker: String?, ref: String?) { guideBooker = booker.orEmpty().trim(); guideRef = ref.orEmpty().trim() }
    private const val FRIEND_NOTE = "12月18日玉龙雪山一日游 3位客人"
    private const val FRIEND_NOTE_KO = "12월 18일 옥룡설산 일일투어 3명"
    val GUIDE: Contact get() = Contact("옥룡설산 투어 가이드·기사", "18088479095", "2487812697@qq.com",
        if (guideRef.isNotBlank()) "$FRIEND_NOTE 订单号$guideRef" else FRIEND_NOTE,
        if (guideRef.isNotBlank()) "$FRIEND_NOTE_KO 예약번호 $guideRef" else FRIEND_NOTE_KO, guideBooker, guideRef)
    /** 예약자·예약 번호 줄(값이 있는 것만) */
    private fun bookingLines(booker: String, ref: String) = (if (booker.isNotBlank()) "预订人：$booker\n" else "") + (if (ref.isNotBlank()) "订单号（Booking reference）：$ref\n" else "")

    private fun raw(m: Map<String, String>, k: String, fields: List<TemplateField>) =
        m[k]?.takeIf { it.isNotBlank() } ?: fields.first { it.key == k }.default

    /** 중국어 본문용 값(날짜·시각은 숫자 → 중국어) */
    private fun v(m: Map<String, String>, k: String, fields: List<TemplateField>): String {
        val f = fields.first { it.key == k }; val r = raw(m, k, fields)
        return f.kind?.let { DateText.zh(r, it, f.approx) } ?: r
    }

    /** 한국어 설명용 값(날짜·시각은 숫자 → 한국어) */
    private fun vk(m: Map<String, String>, k: String, fields: List<TemplateField>): String {
        val f = fields.first { it.key == k }; val r = raw(m, k, fields)
        return f.kind?.let { DateText.ko(r, it) } ?: r
    }

    /** 배달 앱(메이투안 등) 요청사항 칸에 붙이는 문장. 하나씩 따로 복사한다(웹 DELIVERY_NOTES) */
    val DELIVERY_NOTES = listOf(
        TemplateLine("我们没有国内手机号，请别打电话，直接让机器人送上来就行。", "국내 전화번호가 없으니 전화하지 말고, 바로 로봇으로 올려 보내 주세요."),
        TemplateLine("我们没有国内手机号，请别打电话，直接放在前台就行。", "국내 전화번호가 없으니 전화하지 말고, 프런트에 맡겨 주세요."),
    )
    val deliveryNote = Template("tpl-delivery-note", "배달 요청사항(전화 대신 로봇·프런트)", emptyList(),
        render = { DELIVERY_NOTES.joinToString("\n") { it.zh } }, renderKo = { DELIVERY_NOTES.joinToString("\n") { it.ko } }, lines = DELIVERY_NOTES)

    val tigerLeaping: Template = run {
        val f = listOf(
            TemplateField("from", "출발지", "白沙古镇 Rishine Villa（日沐山）", "숙소 중국어명"),
            TemplateField("date", "출발 날짜·시각", "12/20 11:30", kind = "datetime"),
            TemplateField("to", "목적지", "虎跳峡本地湾 行者民宿·Come Inn（中途客栈旁边）"),
            TemplateField("persons", "인원", "3"),
        )
        Template("tpl-tiger", "호도협 Come Inn 직행 전용차 예약 요청", f,
            render = { m ->
                "你好，我想预订${v(m, "date", f)}的包车，${v(m, "persons", f)}个人，带行李箱。\n" +
                    "• 出发：${v(m, "from", f)}\n" +
                    "• 到达：${v(m, "to", f)}\n" +
                    "请问一辆车的总费用是多少钱？大概要开多久？请写在微信上，谢谢！"
            },
            renderKo = { m -> "${vk(m, "date", f)} ${vk(m, "from", f)}에서 ${vk(m, "to", f)}까지 전용차 예약 요청(${vk(m, "persons", f)}명, 캐리어 포함). 차 1대 총비용과 걸리는 시간 문의, 위챗으로 답장 요청." })
    }

    val shangrila: Template = run {
        val f = listOf(
            TemplateField("date", "출발 날짜·시각", "12/21 10:30", kind = "datetime"),
            TemplateField("from", "출발지", "靠山十九度"),
            TemplateField("to", "도착지", "香格里拉 建塘镇东旺路77-83号 格拉丹措Oura Villa民宿（不在古城里面）"),
            TemplateField("persons", "인원", "3"),
        )
        Template("tpl-shangrila", "샹그릴라 빵차 예약 요청", f,
            render = { m -> "你好，我想预订${v(m, "date", f)}，从${v(m, "from", f)}到${v(m, "to", f)}的包车，${v(m, "persons", f)}个人，行李一起。请问一辆车的费用是多少钱？请写在微信上，谢谢！" },
            renderKo = { m -> "${vk(m, "date", f)} ${vk(m, "from", f)}에서 ${vk(m, "to", f)}까지 ${vk(m, "persons", f)}명 빵차(짐 포함) 예약 요청, 차 1대 비용 문의." })
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
            TemplateField("date", "도착 날짜·시각", "12/17 12:27", "열차 도착 시각", kind = "datetime"),
            TemplateField("where", "도착 장소", "丽江站"),
            TemplateField("persons", "인원·짐", "3个人，3个行李箱"),
        )
        Template("tpl-pickup", "숙소 픽업·짐 운반 요청", f,
            render = { m -> "你好，我们${v(m, "date", f)}到${v(m, "where", f)}，${v(m, "persons", f)}。请问可以安排接送吗？古城里车进不去的话，请告诉我们在哪里下车，麻烦帮忙运一下行李。谢谢！" },
            renderKo = { m -> "${vk(m, "date", f)} ${vk(m, "where", f)} 도착(${vk(m, "persons", f)}). 픽업 가능 여부와 고성 밖 하차 지점, 짐 운반을 요청한다." })
    }

    val guide: Template get() = run {
        val f = listOf(
            TemplateField("date", "투어 날짜", "12/18", kind = "date"),
            TemplateField("tour", "투어 이름", "玉龙雪山+蓝月谷一日游（含大索道、印象丽江）"),
            TemplateField("persons", "인원", "3位成人"),
            TemplateField("booker", "대표 예약자(영문)", guideBooker, "예: HONG GILDONG"),
            TemplateField("ref", "예약 번호(Booking reference)", guideRef, "클룩 바우처의 Booking reference"),
            TemplateField("hotel", "숙소", "丽江古城 沐蘭遇の汤宿（黄山上段18号）"),
        )
        Template("tpl-guide", "옥룡설산 가이드 첫 연락(12/15까지)", f, contact = GUIDE,
            render = { m ->
                "您好！我们预订了${v(m, "date", f)}的${v(m, "tour", f)}，${v(m, "persons", f)}。\n" + bookingLines(v(m, "booker", f), v(m, "ref", f)) + "我们住在${v(m, "hotel", f)}。\n" +
                    "请问：\n1. 那天几点、在四方街哪里集合？\n2. 回来时可以送到离酒店最近的停车点（大水车附近）吗？\n请在微信上联系我们，谢谢！"
            },
            renderKo = { m -> "${vk(m, "date", f)} 옥룡설산 투어 예약자(${vk(m, "persons", f)}" + (if (vk(m, "booker", f).isNotBlank()) ", 대표 예약자 ${vk(m, "booker", f)}" else "") + (if (vk(m, "ref", f).isNotBlank()) ", 예약 번호 ${vk(m, "ref", f)}" else "") + "), 숙소는 ${vk(m, "hotel", f)}. ① 몇 시에 사방가 어디서 만나는지 ② 돌아올 때 호텔에서 가장 가까운 차량 지점(대수차 근처)에 내려 줄 수 있는지 묻고, 위챗으로 연락을 요청한다." })
    }

    private fun simple(id: String, title: String, f: List<TemplateField>, zh: (Map<String, String>, (String) -> String) -> String, ko: (Map<String, String>, (String) -> String) -> String) =
        Template(id, title, f, render = { m -> zh(m) { k -> vk(m, k, f) } }, renderKo = { m -> ko(m) { k -> vk(m, k, f) } }, contact = GUIDE)

    val guideConfirm: Template get() = simple("tpl-guide-confirm", "가이드 전날 확인(12/17)",
        listOf(TemplateField("date", "투어 날짜", "12/18", kind = "date"), TemplateField("where", "집합 장소", "四方街")),
        { _, x -> "您好！我们是${x("date")}玉龙雪山+蓝月谷一日游的客人，3位成人" + (if (guideRef.isNotBlank()) "（预订人$guideBooker，订单号$guideRef）" else "") + "。请问几点在${x("where")}哪个位置集合？我们会提前10分钟到。谢谢！" },
        { _, x -> "${x("date")} 옥룡설산 투어 손님 3명이다" + (if (guideRef.isNotBlank()) "(예약자 $guideBooker, 예약 번호 $guideRef)" else "") + ". 몇 시에 ${x("where")} 어디서 모이는지 묻고, 10분 일찍 가겠다고 알린다." })
    val guideArrive: Template get() = simple("tpl-guide-arrive", "픽업 장소 도착(12/18)",
        listOf(TemplateField("where", "지금 있는 곳", "四方街", "예: 四方街科贡坊旁边"), TemplateField("wear", "옷차림", "黑色羽绒服", "예: 红色外套")),
        { _, x -> "您好，我们3个人已经到${x("where")}了，穿着${x("wear")}。" + (if (guideRef.isNotBlank()) "订单号$guideRef（$guideBooker）。" else "") + "请问您在哪里？" },
        { _, x -> "우리 3명이 ${x("where")}에 도착했고 ${x("wear")}을(를) 입고 있다. 어디 계신지 묻는다." })
    val guideLate: Template get() = simple("tpl-guide-late", "픽업 늦을 때(12/18)", listOf(TemplateField("min", "늦는 시간(분)", "10")),
        { _, x -> "不好意思，我们会晚到${x("min")}分钟左右，请稍等一下，谢谢！" },
        { _, x -> "죄송하다, ${x("min")}분쯤 늦으니 조금만 기다려 달라." })
    val guideDropoff: Template get() = simple("tpl-guide-dropoff", "복귀 하차 지점 요청(12/18)", listOf(TemplateField("to", "내릴 곳", "古城大水车附近的停车场（离我们的酒店最近）")),
        { _, x -> "师傅您好，回程的时候可以把我们送到${x("to")}吗？古城里面车进不去。谢谢！" },
        { _, x -> "돌아갈 때 ${x("to")}에 내려 달라고 부탁한다(고성 안은 차가 못 들어간다)." })

    /** D5 Come Inn(호도협 본지만): 중도객잔에서 가는 길·위치 문의(웹 tpl-comeinn-route 와 같다) */
    val COMEINN = Contact("Come Inn(行者民宿·本地湾店) 숙소", "18987966608", friendNote = "12月20日入住 3位客人 想提前入住", friendNoteKo = "12월 20일 체크인 3명, 얼리체크인 희망",
        note = "위챗 ID 는 숙소 전화번호로 검색한다(시골 객잔은 대개 같다). 안 나오면 예약한 앱(아고다 등)의 숙소 메시지로 같은 문장을 보낸다.")
    /** D5 Come Inn: 얼리체크인 + 차마객잔 → 숙소 빵차 요청(웹 tpl-comeinn-route 와 같다) */
    val comeinnRoute: Template = run {
        val f = listOf(
            TemplateField("date", "체크인 날짜", "12/20", kind = "date"),
            TemplateField("arrive", "도착 시각(차로, 대략)", "14:00", kind = "time", approx = true),
            TemplateField("van", "차마객잔 빵차 시각", "16:30", kind = "time"),
        )
        Template("tpl-comeinn-route", "Come Inn 얼리체크인·빵차 요청(D5)", f, contact = COMEINN,
            render = { m -> "您好！我们是${v(m, "date", f)}入住行者民宿·Come Inn（本地湾店）的客人，3位。我们从白沙坐包车过来，大概${v(m, "arrive", f)}到客栈。\n请问：\n1. 可以提前入住吗？放好行李休息一下，然后从客栈徒步去茶马客栈。\n2. ${v(m, "van", f)}可以帮我们叫一辆面包车，从茶马客栈接我们回客栈吗？大概多少钱？\n3. 可以发一个微信位置给我们吗？（司机好找）\n谢谢！" },
            renderKo = { m -> "${vk(m, "date", f)} 체크인하는 3명이다. 백사에서 전용차로 ${vk(m, "arrive", f)}쯤 도착한다. ① 얼리체크인이 되는지(짐 두고 쉬었다가 숙소에서 차마객잔까지 걷는다) ② ${vk(m, "van", f)}에 차마객잔에서 숙소로 돌아올 빵차를 불러 줄 수 있는지, 요금은 얼마인지 ③ 기사가 찾기 쉽게 위챗 위치를 보내 달라고 부탁한다." })
    }
    val all: List<Template> get() = listOf(tigerLeaping, shangrila, delivery, deliveryNote, pickup, guide, guideConfirm, guideArrive, guideLate, guideDropoff, comeinnRoute)
}
