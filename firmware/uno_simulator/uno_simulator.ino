// MultiScale 테스트용 시뮬레이터 (Arduino Uno + HC-06)
// 배선: HC-06 TX -> D2, HC-06 RX -> D3(분압 권장), VCC 5V, GND
// 동작:
//  - 12개 저울에 500~1000g 랜덤 무게를 채워 0.3초마다 <w1,...,w12> 로 보낸다.
//  - 앱이 <1,3,5> 로 보내 주는 '조합 저울' 번호를 기억한다.
//  - 앱이 <E3> 을 보내면 3번 저울을 0g 으로 비운다.
//  - 조합 저울이 전부 비워지면 0.7초 뒤, 그때까지 비워진 저울(보태기 저울 포함)을 한꺼번에 다시 채운다.
//  - 조합이 없는 상태에서 비운 저울은 0.7초 뒤 개별로 다시 채운다.
#include <SoftwareSerial.h>

SoftwareSerial bt(2, 3); // RX, TX
const uint8_t N = 12;
const unsigned long SEND_MS = 300;
const bool INJECT_ERR = true; // 가끔 ERR 을 섞어 앱의 보정 로직을 시험

int w[N];
uint16_t comboMask = 0, emptiedMask = 0, refillMask = 0;
bool refillPending = false;
unsigned long refillAt = 0, lastTx = 0;
unsigned long idleRefillAt[N]; // 0 이면 예약 없음
char line[48];
uint8_t len = 0;

int rndWeight() { return (int)random(100, 201) * 5; } // 500..1000, 5g 단위

void fillAll() { for (uint8_t i = 0; i < N; i++) w[i] = rndWeight(); }

void handleLine() {
  line[len] = 0;
  char *s = strchr(line, '<');
  if (!s) return;
  s++;
  char *e = strchr(s, '>');
  if (!e) return;
  *e = 0;
  if (!strcmp(s, "TARE")) { bt.println(F("[SYSTEM] 영점 조절 완료")); return; }
  if (!strcmp(s, "VER")) { bt.println(F("[VER] SIM-0.2")); return; }
  if (s[0] == 'E') {
    int n = atoi(s + 1);
    if (n < 1 || n > N) return;
    w[n - 1] = 0;
    if (comboMask == 0) { idleRefillAt[n - 1] = millis() + 700; return; }
    emptiedMask |= (1u << (n - 1));
    if (comboMask && (comboMask & ~emptiedMask) == 0 && !refillPending) {
      refillPending = true;
      refillMask = emptiedMask;
      refillAt = millis() + 700;
    }
    return;
  }
  // 그 외: 조합 저울 번호 목록 (비어 있으면 없음)
  uint16_t m = 0;
  for (char *tok = strtok(s, ","); tok; tok = strtok(NULL, ",")) {
    int n = atoi(tok);
    if (n >= 1 && n <= N) m |= (1u << (n - 1));
  }
  comboMask = m;
}

void setup() {
  bt.begin(9600);
  randomSeed(analogRead(A0) + micros());
  fillAll();
}

void loop() {
  while (bt.available()) {
    char c = bt.read();
    if (c == '\r') continue;
    if (c == '\n') { handleLine(); len = 0; }
    else if (len < sizeof(line) - 1) line[len++] = c;
    else len = 0;
  }
  unsigned long now = millis();
  if (refillPending && now >= refillAt) {
    for (uint8_t i = 0; i < N; i++) if (refillMask & (1u << i)) w[i] = rndWeight();
    emptiedMask = 0; comboMask = 0; refillPending = false;
  }
  for (uint8_t i = 0; i < N; i++) {
    if (idleRefillAt[i] && now >= idleRefillAt[i]) { w[i] = rndWeight(); idleRefillAt[i] = 0; }
  }
  if (now - lastTx >= SEND_MS) {
    lastTx = now;
    int errCh = (INJECT_ERR && random(100) == 0) ? (int)random(N) : -1;
    bt.print('<');
    for (uint8_t i = 0; i < N; i++) {
      if (i) bt.print(',');
      if ((int)i == errCh) bt.print(F("ERR"));
      else bt.print(w[i] > 0 ? w[i] + (int)random(-1, 2) : 0);
    }
    bt.println('>');
  }
}
