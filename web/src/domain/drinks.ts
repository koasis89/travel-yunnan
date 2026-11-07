// 주문 문장 만들기: 음료(물·맥주·운남 술·운남 인기 음료·커피). 앱 MealScreens.kt 의 DRINKS 와 같다
export interface Drink { zh: string; ko: string; measure: string; group: string; note?: string }

export const DRINKS: Drink[] = [
  { zh: '矿泉水', ko: '생수', measure: '瓶', group: '물·차' },
  { zh: '热水', ko: '뜨거운 물', measure: '杯', group: '물·차' },
  { zh: '普洱茶', ko: '보이차', measure: '壶', group: '물·차', note: '윈난 대표 차(주전자)' },
  { zh: '滇红茶', ko: '전홍(윈난 홍차)', measure: '壶', group: '물·차' },
  { zh: '冰啤酒', ko: '시원한 맥주', measure: '瓶', group: '맥주' },
  { zh: '风花雪月啤酒', ko: '풍화설월 맥주', measure: '瓶', group: '맥주', note: '윈난(다리) 맥주, 병이 예쁘다' },
  { zh: '大理啤酒', ko: '다리 맥주', measure: '瓶', group: '맥주', note: '윈난 지역 맥주' },
  { zh: '鹤庆乾酒', ko: '학경건주(윈난 고량주)', measure: '小瓶', group: '술', note: '다리 학경의 수수 백주, 도수 높음' },
  { zh: '杨林肥酒', ko: '양림비주(쿤밍 녹색 술)', measure: '小瓶', group: '술', note: '약재 향 단맛 백주' },
  { zh: '丽江窨酒', ko: '리장 음주(나시족 쌀술)', measure: '小瓶', group: '술', note: '달고 순한 나시족 술' },
  { zh: '本地苞谷酒', ko: '옥수수 술(현지 백주)', measure: '小杯', group: '술' },
  { zh: '酸角汁', ko: '산각즙(타마린드 주스)', measure: '杯', group: '윈난 음료', note: '새콤달콤, 윈난 식당 단골' },
  { zh: '木瓜水', ko: '모과수(젤리 음료)', measure: '碗', group: '윈난 음료', note: '시원한 젤리 디저트 음료' },
  { zh: '牦牛酸奶', ko: '야크 요거트', measure: '杯', group: '윈난 음료', note: '샹그릴라·리장' },
  { zh: '酥油茶', ko: '수유차(버터차)', measure: '壶', group: '윈난 음료', note: '티베트식 짭짤한 버터차, 샹그릴라' },
  { zh: '玫瑰花茶', ko: '장미꽃차', measure: '壶', group: '윈난 음료' },
  { zh: '可乐', ko: '콜라', measure: '瓶', group: '음료수' },
  { zh: '雪碧', ko: '사이다(스프라이트)', measure: '瓶', group: '음료수' },
  { zh: '橙汁', ko: '오렌지 주스', measure: '杯', group: '음료수' },
  { zh: '云南小粒咖啡', ko: '윈난 원두 커피', measure: '杯', group: '커피', note: '윈난 아라비카' },
  { zh: '热美式', ko: '따뜻한 아메리카노', measure: '杯', group: '커피' },
  { zh: '冰美式', ko: '아이스 아메리카노', measure: '杯', group: '커피' },
  { zh: '热拿铁', ko: '따뜻한 라테', measure: '杯', group: '커피' },
]

export const DRINK_REQUESTS: [string, string][] = [['饮料要冰的', '음료 차갑게'], ['饮料要常温的', '음료 상온'], ['请帮忙开瓶', '병 따 주세요'], ['请给我们三个杯子', '컵 3개 주세요']]

const NUM = ['', '一', '两', '三', '四', '五', '六']
export const drinkPhrase = (d: Drink, n: number) => `${NUM[n] ?? n}${d.measure}${d.zh}`

/** 여러 음식(각 수량) + 음료 + 요청 → 주문 문장. 음식이 없으면 pointUnit('一份' 등)으로 '이거' 주문, null 이면 음료만.
 *  "我要一份汽锅鸡、两份三七汽锅鸡，再要一瓶矿泉水，不要辣。" */
export function composeMultiOrder(foods: [string, number][], pointUnit: string | null, drinks: [Drink, number][], requests: string[]): string {
  const parts: string[] = []
  const fs = foods.filter(([, n]) => n > 0)
  if (fs.length) parts.push(`我要${fs.map(([z, n]) => `${NUM[n] ?? n}份${z}`).join('、')}`)
  else if (pointUnit) parts.push(`这个要${pointUnit}`)
  const ds = drinks.filter(([, n]) => n > 0).map(([d, n]) => drinkPhrase(d, n))
  if (ds.length) parts.push(`${parts.length ? '再' : '我'}要${ds.join('、')}`)
  if (!parts.length) parts.push('你好，我要点菜')
  return [...parts, ...requests].join('，') + '。'
}

/** 주소 쿼리 ?foods=汽锅鸡*1|三七汽锅鸡*2 ↔ 목록 */
export const encodeFoods = (l: [string, number][]) => l.filter(([, n]) => n > 0).map(([z, n]) => `${z}*${n}`).join('|')
export const decodeFoods = (s: string | null): [string, number][] =>
  (s ?? '').split('|').map((x) => x.trim()).filter(Boolean).map((x) => { const [z, n] = x.split('*'); return [z, Math.max(1, Math.min(9, Number(n) || 1))] as [string, number] })

/** 음식(없으면 '이거', null 이면 음료만) + 음료 + 요청 → 주문 문장.
 *  "我要一份过桥米线，再要两瓶冰啤酒、一瓶矿泉水，不要辣。" */
export function composeFullOrder(food: string | null | undefined, unit: string, drinks: [Drink, number][], requests: string[]): string {
  const parts: string[] = []
  if (food !== null) parts.push(food ? `我要${unit}${food}` : `这个要${unit}`)
  const ds = drinks.filter(([, n]) => n > 0).map(([d, n]) => drinkPhrase(d, n))
  if (ds.length) parts.push(`${parts.length ? '再' : '我'}要${ds.join('、')}`)
  if (!parts.length) parts.push('你好，我要点菜')
  return [...parts, ...requests].join('，') + '。'
}

/** 주문 화면 탭: 음식 · 음료(물·차·윈난 음료·음료수·커피) · 맥주 · 술 */
export type OrderTab = 'food' | 'drink' | 'beer' | 'liquor'
export const ORDER_TABS: [OrderTab, string][] = [['food', '음식'], ['drink', '음료'], ['beer', '맥주'], ['liquor', '술']]
export const tabOf = (d: Drink): OrderTab => (d.group === '맥주' ? 'beer' : d.group === '술' ? 'liquor' : 'drink')
export const ORDER_REQUESTS: [string, string][] = [['不要辣', '안 맵게'], ['微辣', '조금만 맵게'], ['不要香菜', '고수 빼고'], ['少放盐', '소금 적게'], ['打包', '포장'], ...DRINK_REQUESTS]

const num = (n: number) => NUM[n] ?? String(n)
/**
 * 고른 음식(각 수량) + '이거'(메뉴를 가리키며, 수량) + 음료·맥주·술(각 수량) + 요청 → 주문 문장.
 * "我要两份过桥米线，这个也要一份，再要三瓶冰啤酒，不要辣。" 아무것도 없으면 "你好，我要点菜。"
 */
export function composeTabOrder(foods: [string, number][], point: number, drinks: [Drink, number][], requests: string[]): string {
  const parts: string[] = []
  const fs = foods.filter(([, n]) => n > 0).map(([z, n]) => `${num(n)}份${z}`)
  if (fs.length) parts.push(`我要${fs.join('、')}`)
  if (point > 0) parts.push(fs.length ? `这个也要${num(point)}份` : `这个要${num(point)}份`)
  const ds = drinks.filter(([, n]) => n > 0).map(([d, n]) => drinkPhrase(d, n))
  if (ds.length) parts.push(`${parts.length ? '再' : '我'}要${ds.join('、')}`)
  if (!parts.length) parts.push('你好，我要点菜')
  return [...parts, ...requests].join('，') + '。'
}
