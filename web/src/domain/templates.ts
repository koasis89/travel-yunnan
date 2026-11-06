export interface TemplateField { key: string; label: string; def: string; hint?: string }
export interface Contact { name: string; wechat: string; email?: string; friendNote: string }
export interface Template { id: string; title: string; fields: TemplateField[]; render: (v: Record<string, string>) => string; renderKo: (v: Record<string, string>) => string; contact?: Contact }

/** 12/18 옥룡설산 투어 가이드·기사(바우처 기준). 연락은 위챗, 메일은 참고. 바우처의 +86 긴 번호는 깨진 값이라 쓰지 않는다 */
export const GUIDE: Contact = { name: '옥룡설산 투어 가이드·기사', wechat: '18088479095', email: '2487812697@qq.com', friendNote: '12月18日玉龙雪山一日游 3位客人' }

const val = (v: Record<string, string>, f: TemplateField[], k: string) => (v[k]?.trim() ? v[k] : f.find((x) => x.key === k)!.def)

const tigerF: TemplateField[] = [
  { key: 'from', label: '출발지', def: '白沙古镇 Rishine Villa（日沐山）', hint: '숙소 중국어명' },
  { key: 'date', label: '날짜', def: '12月20日（周日）早上9点', hint: '예: 明天早上9点' },
  { key: 'wait', label: '상호도협 대기', def: '1到1.5小时' },
  { key: 'to', label: '최종 목적지', def: '茶马客栈' },
  { key: 'luggage', label: '짐 보낼 곳', def: '中途客栈附近的“靠山十九度 (COME INN) 客栈”' },
  { key: 'persons', label: '인원', def: '3' },
]
const shangF: TemplateField[] = [
  { key: 'date', label: '출발 시각', def: '明天上午10点半', hint: '예: 12月21日上午10点半' },
  { key: 'from', label: '출발지', def: '靠山十九度' },
  { key: 'to', label: '도착지', def: '香格里拉 独克宗古城 龟山巷3号（Oura Villa）' },
  { key: 'persons', label: '인원', def: '3' },
]
const delivF: TemplateField[] = [
  { key: 'hotel', label: '호텔 중국어명', def: '格菲酒店(昆明火车站北广场店)' },
  { key: 'room', label: '방 번호', def: '', hint: '체크인 후 입력' },
]
const pickF: TemplateField[] = [
  { key: 'date', label: '도착 시각', def: '12月17日 12:27', hint: '열차 도착 시각' },
  { key: 'where', label: '도착 장소', def: '丽江站' },
  { key: 'persons', label: '인원·짐', def: '3个人，3个行李箱' },
]
const guideF: TemplateField[] = [
  { key: 'date', label: '투어 날짜', def: '12月18日' },
  { key: 'tour', label: '투어 이름', def: '玉龙雪山+蓝月谷一日游（含大索道、印象丽江）' },
  { key: 'persons', label: '인원', def: '3位成人' },
  { key: 'hotel', label: '숙소', def: '丽江古城 沐蘭遇の汤宿（黄山上段18号）' },
]

export const TEMPLATES: Template[] = [
  { id: 'tpl-tiger', title: '호도협 전용차 예약 요청', fields: tigerF,
    render: (v) => `你好，我想预订去虎跳峡徒步的包车，${val(v, tigerF, 'persons')}个人。行程如下：\n• 出发：${val(v, tigerF, 'from')}，${val(v, tigerF, 'date')}\n• 经停：上虎跳峡景区，停留${val(v, tigerF, 'wait')}\n• 终点：${val(v, tigerF, 'to')}\n另外，我们的行李箱到达${val(v, tigerF, 'to')}后，需要托运到${val(v, tigerF, 'luggage')}。\n请问这样包一辆车的总费用是多少钱？请写在微信上，谢谢！`,
    renderKo: (v) => `호도협 트레킹 전용차 예약 요청(${val(v, tigerF, 'persons')}명). 출발 ${val(v, tigerF, 'from')} ${val(v, tigerF, 'date')}, 상호도협 ${val(v, tigerF, 'wait')} 대기 후 ${val(v, tigerF, 'to')}까지. 짐은 ${val(v, tigerF, 'luggage')}로 탁송. 차 1대 총비용 문의, 위챗으로 답장 요청.` },
  { id: 'tpl-shangrila', title: '샹그릴라 빵차 예약 요청', fields: shangF,
    render: (v) => `你好，我想预订${val(v, shangF, 'date')}，从${val(v, shangF, 'from')}到${val(v, shangF, 'to')}的包车，${val(v, shangF, 'persons')}个人，行李一起。请问一辆车的费用是多少钱？请写在微信上，谢谢！`,
    renderKo: (v) => `${val(v, shangF, 'date')} ${val(v, shangF, 'from')}에서 ${val(v, shangF, 'to')}까지 ${val(v, shangF, 'persons')}명 빵차(짐 포함) 예약 요청, 차 1대 비용 문의.` },
  { id: 'tpl-delivery', title: '배달 주소 요청(프런트)', fields: delivF,
    render: (v) => `麻烦帮我们写一下送到我们住的酒店房间的收货地址，酒店是${val(v, delivF, 'hotel')}` + (v.room?.trim() ? `，房间号${v.room.trim()}` : '') + '。我们没有国内手机号，请别打电话，直接放在前台就行。',
    renderKo: () => '메이투안 배달용 수령 주소(호텔명·방 번호)를 적어 달라는 요청. 중국 번호가 없으니 전화 말고 프런트에 맡겨 달라고 덧붙인다.' },
  { id: 'tpl-pickup', title: '숙소 픽업·짐 운반 요청', fields: pickF,
    render: (v) => `你好，我们${val(v, pickF, 'date')}到${val(v, pickF, 'where')}，${val(v, pickF, 'persons')}。请问可以安排接送吗？古城里车进不去的话，请告诉我们在哪里下车，麻烦帮忙运一下行李。谢谢！`,
    renderKo: (v) => `${val(v, pickF, 'date')} ${val(v, pickF, 'where')} 도착(${val(v, pickF, 'persons')}). 픽업 가능 여부와 고성 밖 하차 지점, 짐 운반을 요청한다.` },
]
const confirmF: TemplateField[] = [{ key: 'date', label: '날짜', def: '明天（12月18日）' }, { key: 'where', label: '집합 장소', def: '四方街' }]
const arriveF: TemplateField[] = [{ key: 'where', label: '지금 있는 곳', def: '四方街', hint: '예: 四方街科贡坊旁边' }, { key: 'wear', label: '옷차림', def: '黑色羽绒服', hint: '예: 红色外套' }]
const lateF: TemplateField[] = [{ key: 'min', label: '늦는 시간(분)', def: '10' }]
const dropF: TemplateField[] = [{ key: 'to', label: '내릴 곳', def: '古城大水车附近的停车场（离我们的酒店最近）' }]

TEMPLATES.push({ id: 'tpl-guide', title: '옥룡설산 가이드 첫 연락(12/15까지)', fields: guideF, contact: GUIDE,
  render: (v) => `您好！我们预订了${val(v, guideF, 'date')}的${val(v, guideF, 'tour')}，${val(v, guideF, 'persons')}。我们住在${val(v, guideF, 'hotel')}。\n请问：\n1. 那天几点、在四方街哪里集合？\n2. 回来时可以送到离酒店最近的停车点（大水车附近）吗？\n请在微信上联系我们，谢谢！`,
  renderKo: (v) => `${val(v, guideF, 'date')} 옥룡설산 투어 예약자(${val(v, guideF, 'persons')}), 숙소는 ${val(v, guideF, 'hotel')}. ① 몇 시에 사방가 어디서 만나는지 ② 돌아올 때 호텔에서 가장 가까운 차량 지점(대수차 근처)에 내려 줄 수 있는지 묻고, 위챗으로 연락을 요청한다.` })
TEMPLATES.push(
  { id: 'tpl-guide-confirm', title: '가이드 전날 확인(12/17)', fields: confirmF, contact: GUIDE,
    render: (v) => `您好！我们是${val(v, confirmF, 'date')}玉龙雪山+蓝月谷一日游的客人，3位成人。请问几点在${val(v, confirmF, 'where')}哪个位置集合？我们会提前10分钟到。谢谢！`,
    renderKo: (v) => `${val(v, confirmF, 'date')} 옥룡설산 투어 손님 3명이다. 몇 시에 ${val(v, confirmF, 'where')} 어디서 모이는지 묻고, 10분 일찍 가겠다고 알린다.` },
  { id: 'tpl-guide-arrive', title: '픽업 장소 도착(12/18)', fields: arriveF, contact: GUIDE,
    render: (v) => `您好，我们3个人已经到${val(v, arriveF, 'where')}了，穿着${val(v, arriveF, 'wear')}。请问您在哪里？`,
    renderKo: (v) => `우리 3명이 ${val(v, arriveF, 'where')}에 도착했고 ${val(v, arriveF, 'wear')}을(를) 입고 있다. 어디 계신지 묻는다.` },
  { id: 'tpl-guide-late', title: '픽업 늦을 때(12/18)', fields: lateF, contact: GUIDE,
    render: (v) => `不好意思，我们会晚到${val(v, lateF, 'min')}分钟左右，请稍等一下，谢谢！`,
    renderKo: (v) => `죄송하다, ${val(v, lateF, 'min')}분쯤 늦으니 조금만 기다려 달라.` },
  { id: 'tpl-guide-dropoff', title: '복귀 하차 지점 요청(12/18)', fields: dropF, contact: GUIDE,
    render: (v) => `师傅您好，回程的时候可以把我们送到${val(v, dropF, 'to')}吗？古城里面车进不去。谢谢！`,
    renderKo: (v) => `돌아갈 때 ${val(v, dropF, 'to')}에 내려 달라고 부탁한다(고성 안은 차가 못 들어간다).` },
)
export const templateById = (id?: string | null) => TEMPLATES.find((t) => t.id === id)

export function composeOrder(foodZh: string | null, unit: string, requests: string[]): string {
  const main = foodZh ? `我要${unit}${foodZh}` : `这个要${unit}`
  return [main, ...requests].join('，') + '。'
}
