import { setGuideBooking } from './templates'
import { setDriveDefault } from '../store/drive'
import type { Blog } from './docs'
// data/json 구조(앱 Models.kt 와 동일)
export interface Day { id: string; date: string; dateLabel: string; route: string; summary: string; lodgingLabel: string; lodgingId: string | null; region: string | null; altitude: string | null; tempDay: string | null; tempNight: string | null; outfit: string | null; mapIds: string[] }
export interface Schedule { id: string; dayId: string; seq: number; timeLabel: string; timeStart: string | null; timeEnd: string | null; from: string; via: string; to: string; transport: string; duration: string; tip: string; amapUrl: string | null; message: string | null }
export interface Lodging { id: string; dates: string[]; nights: number | null; region: string; nameKo: string; nameZh: string; addrZh: string; addrEn: string; phones: string[]; room: string; bookingNo: string; bookedBy: string; priceKrw: number | null; status: string; note: string; confirmed: boolean; amapUrl?: string | null }
export interface MealOption { id: string; name: string; nameKo?: string; nameZh: string; addrZh: string; amapUrl: string | null; menu: string }
export interface Meal { id: string; dayId: string; seq: number; area: string; slot: string; options: MealOption[]; budgetCny: number | null; note: string; extra: string | null }
export interface Food { id: string; region: string; no: number | null; nameZh: string; nameKo: string; description: string; imageKind: string; image: string | null; source: string }
export interface Phrase { id: string; zh: string; pinyin: string; categories: string[]; ko: string; koRead: string; copyOnly: boolean }
export interface Place { id: string; nameKo: string; nameZh: string; addrZh: string; amapUrl?: string | null; helperIds?: string[] }
export interface PlaceLink { match: string; type: 'lodging' | 'place' | 'meal'; key: string }
export interface TemplateLink { id: string; dayId: string; timeStart: string; templateId: string; label: string; values: Record<string, string> }
export interface TransportItem { id: string; group: string; title: string; unit: string; unitPrice: number | null; currency: string; qty: number; note: string; totalKrw: number }
export interface BudgetItem { category: string; amountKrw: number; isTotal: boolean }
export interface Rates { USD: number; CNY: number; SGD: number; persons: number }
export interface Weather { region: string; dates: string; altitude: string; tempDay: string; tempNight: string; feature: string; outfit: string }
export interface PackingItem { id: string; group: string; name: string; qty: string; note: string }
export interface Note { id: string; title: string; body: string; dayIds: string[]; phrasesInline: string[] }
export interface TripNotification { id: string; at: string; title: string; body: string; dayId: string; kind: string }
export interface LodgingTodo { id: string; lodgingId: string; text: string; phraseId: string | null }
export interface SightCredit { title: string; author: string; license: string; url: string }
export interface Sight { id: string; region: string; nameKo: string; nameZh: string; dayIds: string[]; placeId: string | null; keywords: string[]; summary: string; highlights: string[]; tips: string[]; hours: string; fee: string; duration: string; image: string | null; credit: SightCredit | null; amapUrl?: string | null }
export interface MenuItem { zh: string; ko: string; desc: string; image?: string }
export interface Rating { source: string; name: string; score: number | null; reviews: number | null; price: string; addr: string; popular: { zh: string; ko: string }[]; label?: string; note?: string }
export interface Restaurant { id: string; region: string; nameKo: string; nameZh: string; optionIds: string[]; dayIds: string[]; slots: string[]; kind: string; summary: string; menu: MenuItem[]; tips: string[]; perPerson: string; hours: string; image: string | null; credit: SightCredit | null; imageNote: string; keywords: string[]; amapUrl?: string | null; placeId?: string | null; ratings?: Rating[]; routes?: { label: string; url: string; note?: string }[] }
/** 밀크티 종류: data/milktea.json */
export interface MilkTeaDrink { id: string; zh: string; pinyin: string; koRead: string; desc: string; opt?: string; tag?: string }
export interface MilkTeaBrand { id: string; nameZh: string; pinyin: string; nameKo: string; nameEn: string; color: string; note?: string; drinks: MilkTeaDrink[] }
export interface MilkTea { items: MilkTeaBrand[]; sugar: [string, string][]; ice: [string, string][] }
/** 여행 전(사전) 일정: data/pretrip.json */
export interface CheckItem { id: string; text: string }
export interface PreTrip { id: string; date: string; dateLabel: string; time: string; title: string; detail: string; tips: string[]; templateLinkId?: string; notificationId?: string; checklist?: CheckItem[]; link?: string; copy?: { label: string; value: string; note?: string }[] }
export interface ArrivalField { group: string; label: string; value: string; note?: string; copy?: boolean; input?: string }
export interface NextDayList { dayId: string; nextId: string; title: string; items: CheckItem[] }
export interface Checklists {
  arrivalCard: { title: string; summary: string; steps: { title: string; desc: string }[]; notes: string[]; fields: ArrivalField[] }
  departure: { title: string; items: CheckItem[] }
  nextDay: NextDayList[]
}
export interface Hospital { id: string; nameKo: string; nameZh: string; addrZh: string; placeId: string; amapUrl: string; region: string; note: string; phrases: { zh: string; ko: string }[] }
export interface EmergencyContact { id: string; group: string; name: string; phone: string; note: string }

type Items<T> = { items: T[] };
export type RawBundle = Record<string, unknown>;

/** 복호화된 데이터 묶음 → 조회 함수가 붙은 객체 */
export class TripData {
  days: Day[]; schedule: Schedule[]; lodgings: Lodging[]; lodgingNotes: string[]; meals: Meal[]; mealNotes: string[]
  foods: Food[]; phrases: Phrase[]; places: Place[]; placeLinks: PlaceLink[]; templateLinks: TemplateLink[]
  transport: TransportItem[]; transportNotes: string[]; budget: BudgetItem[]; rates: Rates; weather: Weather[]
  packing: PackingItem[]; notes: Note[]; notifications: TripNotification[]; todos: LodgingTodo[]
  emergency: { note: string; contacts: EmergencyContact[]; altitude: string[]; hospitals: Hospital[] }
  restaurantTips: string[]
  ratingsNote: string
  sights: Sight[]
  pretrip: PreTrip[]
  /** 체크리스트(입국신고 QR·출국 당일·일정마다 다음날 준비) — data/checklists.json */
  checklists: Checklists
  milktea: MilkTea
  /** 중국어만 있는 화면 글자의 한국어 번역(data/ko_gloss.json) */
  koGloss: Record<string, string>
  blogs: Blog[]
  foodPhotoNames: Record<string, { nameKo: string; nameZh: string }>
  foodPhotoRestaurant: string
  /** 사진 id → 음식 도감 id(워드 사진) */
  foodPhotoFood: Record<string, string>
  /** 워드에서 넣을 사진 id(이 밖의 그림은 광고·인물 등이라 건너뛴다) */
  docxPhotoIds: Set<string>
  foodAlsoFor: Record<string, string>
  restaurants: Restaurant[]
  version: string

  constructor(b: RawBundle) {
    const it = <T,>(n: string) => ((b[n] as Items<T> | undefined)?.items ?? [])
    this.days = it<Day>('days.json'); this.schedule = it<Schedule>('schedule.json')
    this.lodgings = it<Lodging>('lodgings.json'); this.lodgingNotes = (b['lodgings.json'] as { notes?: string[] })?.notes ?? []
    this.meals = it<Meal>('meals.json'); this.mealNotes = (b['meals.json'] as { notes?: string[] })?.notes ?? []
    this.foods = it<Food>('foods.json'); this.phrases = it<Phrase>('phrases.json'); this.places = it<Place>('places.json')
    this.placeLinks = it<PlaceLink>('place_links.json'); this.templateLinks = it<TemplateLink>('template_links.json')
    this.transport = it<TransportItem>('transport.json'); this.transportNotes = (b['transport.json'] as { notes?: string[] })?.notes ?? []
    this.budget = it<BudgetItem>('budget.json'); this.rates = (b['rates.json'] as Rates) ?? { USD: 1450, CNY: 200, SGD: 1120, persons: 3 }
    this.weather = it<Weather>('weather.json'); this.packing = it<PackingItem>('packing.json'); this.notes = it<Note>('notes.json')
    this.notifications = it<TripNotification>('notifications.json'); this.todos = it<LodgingTodo>('lodging_todos.json')
    const em = b['emergency.json'] as { note?: string; contacts?: EmergencyContact[]; altitude?: string[]; hospitals?: Hospital[] } | undefined
    this.emergency = { note: em?.note ?? '', contacts: em?.contacts ?? [], altitude: em?.altitude ?? [], hospitals: em?.hospitals ?? [] }
    this.restaurantTips = (b['restaurants.json'] as { tips?: string[] })?.tips ?? []
    this.ratingsNote = (b['restaurants.json'] as { ratingsNote?: string })?.ratingsNote ?? ''
    this.sights = it<Sight>('sights.json')
    this.blogs = (b['sights.json'] as { blogs?: Blog[] })?.blogs ?? []
    const fp = b['food_photos.json'] as { restaurant?: string; items?: { file: string; nameKo: string; nameZh: string }[] } | undefined
    this.foodPhotoNames = Object.fromEntries((fp?.items ?? []).map((x) => [x.file, { nameKo: x.nameKo, nameZh: x.nameZh }]))
    const fx = b['food_photos.json'] as { docx?: { id: string; foodId: string }[]; alsoFor?: Record<string, string> } | undefined
    this.foodPhotoFood = Object.fromEntries((fx?.docx ?? []).map((x) => [x.id, x.foodId]))
    this.docxPhotoIds = new Set(Object.keys(this.foodPhotoFood))
    this.foodAlsoFor = fx?.alsoFor ?? {}
    for (const [id, fid] of Object.entries(this.foodPhotoFood)) {
      const f = this.foods.find((x) => x.id === fid); if (f) this.foodPhotoNames[id] = { nameKo: f.nameKo, nameZh: f.nameZh }
    }
    this.foodPhotoRestaurant = fp?.restaurant ?? ''
    this.restaurants = it<Restaurant>('restaurants.json')
    this.pretrip = it<PreTrip>('pretrip.json')
    const cl = b['checklists.json'] as Partial<Checklists> | undefined
    this.checklists = {
      arrivalCard: cl?.arrivalCard ?? { title: '', summary: '', steps: [], notes: [], fields: [] },
      departure: cl?.departure ?? { title: '', items: [] }, nextDay: cl?.nextDay ?? [],
    }
    this.koGloss = (b['ko_gloss.json'] as { items?: Record<string, string> })?.items ?? {}
    const mt = b['milktea.json'] as Partial<MilkTea> | undefined
    this.milktea = { items: mt?.items ?? [], sugar: mt?.sugar ?? [], ice: mt?.ice ?? [] }
    // 개인 정보(저장소 밖에서 빌드 때 암호화해 넣은 값)
    const pv = b['private.json'] as { guideBooker?: string; guideRef?: string; driveUrl?: string; driveToken?: string } | undefined
    setGuideBooking(pv?.guideBooker, pv?.guideRef)
    setDriveDefault(pv?.driveUrl, pv?.driveToken)
    this.version = (b['version.json'] as { data?: string })?.data ?? ''
  }
  day = (id?: string | null) => this.days.find((d) => d.id === id)
  lodging = (id?: string | null) => this.lodgings.find((l) => l.id === id)
  place = (id?: string | null) => this.places.find((p) => p.id === id)
  phrase = (id?: string | null) => this.phrases.find((p) => p.id === id)
  scheduleOf = (dayId: string) => this.schedule.filter((r) => r.dayId === dayId).sort((a, b) => a.seq - b.seq)
  mealsOf = (dayId: string) => this.meals.filter((m) => m.dayId === dayId)
  mealOption(id?: string | null): [Meal, MealOption] | undefined {
    for (const m of this.meals) { const o = m.options.find((x) => x.id === id); if (o) return [m, o] }
  }
  findMealOption(keyZh: string): [Meal, MealOption] | undefined {
    for (const m of this.meals) { const o = m.options.find((x) => x.name.includes(keyZh) || x.nameZh.includes(keyZh)); if (o) return [m, o] }
  }
  todosOf = (lodgingId: string) => this.todos.filter((t) => t.lodgingId === lodgingId)
  templatesFor = (r: Schedule) => this.templateLinks.filter((t) => t.dayId === r.dayId && t.timeStart === r.timeStart)
  templateLink = (id?: string | null) => this.templateLinks.find((t) => t.id === id)
  sight = (id?: string | null) => this.sights.find((s) => s.id === id)
  restaurant = (id?: string | null) => this.restaurants.find((r) => r.id === id)
  restaurantOfOption = (optionId: string) => this.restaurants.find((r) => r.optionIds.includes(optionId))
  /** 일정 행에 나오는 식당(그 일차 식당 중 이름 키워드가 행 글에 있는 것) */
  restaurantsFor = (r: Schedule) => this.restaurants.filter((x) => x.dayIds.includes(r.dayId) && x.keywords.some((k) => `${r.from} ${r.via} ${r.to} ${r.tip}`.includes(k)))
  /** 그 음식의 기기 사진 id(워드 사진). alsoFor 로 같은 음식 사진을 함께 쓴다 */
  photoIdsForFood = (foodId: string, have: string[]) => have.filter((id) => { const f = this.foodPhotoFood[id]; return !!f && (f === foodId || f === this.foodAlsoFor[foodId]) })
  /** 중국어 → 한국어 번역(없으면 빈칸) */
  ko = (zh?: string | null) => (zh ? this.koGloss[zh.trim()] ?? '' : '')
  /** 그날 일정 맨 아래에 보일 '다음날 준비' 체크리스트 */
  nextDayOf = (dayId: string) => this.checklists.nextDay.find((x) => x.dayId === dayId)
  blogsFor = (sightId: string) => this.blogs.filter((x) => x.sightIds.includes(sightId))
  sightsOf = (dayId: string) => this.sights.filter((s) => s.dayIds.includes(dayId))
  /** 일정 행에 관련된 관광지: 그 일차 관광지 중 키워드가 행 글에 들어 있는 것 */
  sightsFor = (r: Schedule) => this.sightsOf(r.dayId).filter((s) => s.keywords.some((k) => `${r.from} ${r.via} ${r.to} ${r.tip}`.includes(k)))
}
