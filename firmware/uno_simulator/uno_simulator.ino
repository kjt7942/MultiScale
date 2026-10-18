// MultiScale 테스트용 시뮬레이터 (Arduino Uno + HC-06)  v0.3
// 배선: HC-06 TX -> D2, HC-06 RX -> D3(분압 권장), VCC 5V, GND
// 동작:
//  - 12개 저울에 500~1000g 랜덤 무게를 채워 0.3초마다 <w1,...,w12> 로 보낸다.
//  - 앱이 <E3> 을 보내면 3번 저울을 0g 으로 비운다.
//  - 앱이 <F3> 을 보내면 3번 저울에 새 송이(500~1000g 랜덤)를 채운다.
//  - 자동으로 채우는 동작은 없다. 앱에서 0g 저울을 눌러 채운다.
#include <SoftwareSerial.h>

SoftwareSerial bt(2, 3); // RX, TX
const uint8_t N = 12;
const unsigned long SEND_MS = 300;
const bool INJECT_ERR = true; // 가끔 ERR 을 섞어 앱의 보정 로직을 시험

int w[N];
unsigned long lastTx = 0;
char line[48];
uint8_t len = 0;

int rndWeight() { return (int)random(100, 201) * 5; } // 500..1000, 5g 단위

void handleLine() {
  line[len] = 0;
  char *s = strchr(line, '<');
  if (!s) return;
  s++;
  char *e = strchr(s, '>');
  if (!e) return;
  *e = 0;
  if (!strcmp(s, "TARE")) { bt.println(F("[SYSTEM] 영점 조절 완료")); return; }
  if (!strcmp(s, "VER")) { bt.println(F("[VER] SIM-0.3")); return; }
  if (s[0] == 'E' || s[0] == 'F') {
    int n = atoi(s + 1);
    if (n < 1 || n > N) return;
    w[n - 1] = (s[0] == 'E') ? 0 : rndWeight();
  }
  // 그 외(LED 번호 목록 등)는 무시
}

void setup() {
  bt.begin(9600);
  randomSeed(analogRead(A0) + micros());
  for (uint8_t i = 0; i < N; i++) w[i] = rndWeight();
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
