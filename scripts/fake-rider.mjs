#!/usr/bin/env node
// 폰 앱 대신 탑승자 역할을 하는 테스트 클라이언트(Node 22 이상, 내장 WebSocket 사용).
// 차 화면의 6자리 코드로 연결 → "데리러 와줘" → 수락되면 학원 출입구에서 차까지 2초 간격으로 걸어간다.
//
//   node scripts/fake-rider.mjs <연결코드> [서버 주소=http://localhost:8080] [이름=민지]
//
// 좌표는 데모 시나리오(docs/TASKS.md D1): 학원 37.49830, 127.06280 → 차 37.49950, 127.06030

const [code, server = 'http://localhost:8080', name = '민지'] = process.argv.slice(2)
if (!code) {
  console.error('사용법: node scripts/fake-rider.mjs <연결코드> [서버 주소] [이름]')
  process.exit(1)
}

const ACADEMY = { lat: 37.4983, lng: 127.0628 }
const CAR = { lat: 37.4995, lng: 127.0603 }
const STEPS = 15
const STEP_MS = 2000

const log = (...args) => console.log(new Date().toISOString().slice(11, 19), ...args)
const envelope = (type, payload = {}) => JSON.stringify({ type, ts: Date.now(), payload })

const res = await fetch(`${server}/api/pairings`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ code, riderName: name }),
})
const body = await res.json()
if (!res.ok) {
  log('연결 실패', res.status, body)
  process.exit(1)
}
log('연결 완료', body.riderId)

const ws = new WebSocket(`${server.replace(/^http/, 'ws')}/ws?token=${body.riderToken}`)
let walking = null

ws.onmessage = (event) => {
  const message = JSON.parse(event.data)
  log('받음', message.type, JSON.stringify(message.payload))
  switch (message.type) {
    case 'session.state':
      log('"데리러 와줘" 보냄')
      ws.send(envelope('pickup.request', { riderName: name }))
      break
    case 'pickup.accepted':
      walking = walk()
      break
    case 'session.ended':
    case 'error':
      clearInterval(walking)
      ws.close()
      break
  }
}
ws.onclose = (event) => {
  log('연결 종료', event.code)
  process.exit(0)
}

function walk() {
  let step = 0
  return setInterval(() => {
    const t = Math.min(step / STEPS, 1)
    const lat = ACADEMY.lat + (CAR.lat - ACADEMY.lat) * t
    const lng = ACADEMY.lng + (CAR.lng - ACADEMY.lng) * t
    ws.send(envelope('location.update', { lat, lng, accuracy: 5.0 }))
    log(`걸어가는 중 ${step}/${STEPS}`, lat.toFixed(5), lng.toFixed(5))
    step += 1
  }, STEP_MS)
}
