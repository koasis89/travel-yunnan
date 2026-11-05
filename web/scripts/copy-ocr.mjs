// 영수증 OCR(Tesseract.js) 실행 파일·중국어 데이터를 public/ocr 로 복사한다. 외부 CDN 없이 자체 서버에서 받게 해
// 중국 내 접속·오프라인에서도 쓸 수 있게 한다. public/ocr 은 git 에 넣지 않는다.
import { cpSync, mkdirSync, readdirSync } from 'node:fs'
const out = 'public/ocr'
mkdirSync(`${out}/core`, { recursive: true }); mkdirSync(`${out}/lang`, { recursive: true })
cpSync('node_modules/tesseract.js/dist/worker.min.js', `${out}/worker.min.js`)
const core = readdirSync('node_modules/tesseract.js-core').filter((f) => f.endsWith('-lstm.wasm.js'))
for (const f of core) cpSync(`node_modules/tesseract.js-core/${f}`, `${out}/core/${f}`)
cpSync('node_modules/@tesseract.js-data/chi_sim/4.0.0_best_int/chi_sim.traineddata.gz', `${out}/lang/chi_sim.traineddata.gz`)
console.log(`OCR 파일 복사: core ${core.length}개 + worker + chi_sim`)
