// 실제 OCR(Tesseract.js) → 해석기
import { createWorker } from 'tesseract.js'
import { parseReceipt } from '../src/domain/receipt.ts'
const dir = process.argv[2]
const days = [{ id: 'D2', date: '2026-12-17' }, { id: 'D4', date: '2026-12-19' }]
const langPath = '/home/user/travel-yunnan/web/node_modules/@tesseract.js-data'
const worker = await createWorker('chi_sim', 1, { langPath: '/tmp/claude-0/-home-user-travel-yunnan/6b762b61-5d14-5dc3-a699-a781cf0d07f2/scratchpad/receipts/lang', cachePath: dir, gzip: true, cacheMethod: 'none',
  // eng 은 다른 폴더라 별도 지정
})
const expect = { wechat: 128, paper: 226, didi: 32.5, alipay: 45 }
for (const n of Object.keys(expect)) {
  const t0 = Date.now()
  const { data } = await worker.recognize(`${dir}/${n}.jpg`)
  const g = parseReceipt(data.text, days)
  console.log(n.padEnd(7), (g.amount === expect[n] ? 'OK ' : 'NG ') + JSON.stringify({ amount: g.amount, merchant: g.merchant, date: g.date, time: g.time, dayId: g.dayId, cat: g.category }), `${Date.now() - t0}ms`)
  if (g.amount !== expect[n]) console.log('   OCR:', data.text.replace(/\n/g, ' | ').slice(0, 200))
}
await worker.terminate()
