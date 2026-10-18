// MultiScale 테스트용 시뮬레이터 (Arduino Mega 2560 + HC-06)  펌웨어 v0.5.1
// 배선: HC-06 TX -> 19(RX1), HC-06 RX -> 18(TX1)(분압 권장), VCC 5V, GND
// Arduino IDE 보드: Arduino Mega or Mega 2560
// 동작:
//  - 12개 저울에 500~1000g 랜덤 무게를 채워 0.3초마다 <w1,...,w12> 로 보낸다.
//  - 앱이 <E3> 또는 <E1,2,3> 을 보내면 해당 저울을 0g 으로 비운다.
//  - 앱이 <F3> 또는 <F1,2,3> 을 보내면 해당 저울에 새 송이(500~1000g 랜덤)를 채운다.
//  - 스스로 채우거나 비우지 않는다. 언제 채울지는 앱이 정한다.

#define FW_VERSION "v0.5.1 MEGA"   // 앱 버전(MultiScale vX.Y.Z)과는 별개로 이 폴더의 펌웨어만 센다

// 블루투스(HC-06): 하드웨어 시리얼1  HC-06 TX -> 19(RX1), HC-06 RX -> 18(TX1)(분압 권장)
#define bt Serial1
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
  Serial.print(F("RX ")); Serial.println(line); // USB 시리얼 모니터(115200)로 받은 명령을 보여준다
  char *s = strchr(line, '<');
  if (!s) return;
  s++;
  char *e = strchr(s, '>');
  if (!e) return;
  *e = 0;
  if (!strcmp(s, "TARE")) { bt.println(F("[SYSTEM] 영점 조절 완료")); return; }
  if (!strcmp(s, "VER")) { bt.print(F("[VER] ")); bt.println(F(FW_VERSION)); return; }
  if (s[0] == 'E' || s[0] == 'F') {
    bool fill = (s[0] == 'F');
    for (char *tok = strtok(s + 1, ","); tok; tok = strtok(NULL, ",")) {
      int n = atoi(tok);
      if (n >= 1 && n <= N) w[n - 1] = fill ? rndWeight() : 0;
    }
    return;
  }
  // 그 외(LED 번호 목록 등)는 무시
}

void setup() {
  Serial.begin(115200);
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
