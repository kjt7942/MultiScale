# 멀티스케일 (Android)

HC-06/HC-05 블루투스 + 아두이노로 들어오는 12채널 무게를 받아 최적 조합을 보여주는 갤럭시 전용 앱입니다.
화면과 알고리즘은 기존 `grape-sorter`(라즈베리파이판)를 그대로 옮겼습니다.

## 설치 (갤럭시 S25)
1. 폰 브라우저에서 이 저장소의 **Releases → 최신 `MultiScale-x.y.z.apk`** 다운로드
2. 설치 시 "이 출처 허용" 켜기 → 설치
3. 폰 설정에서 HC-06 을 먼저 블루투스 페어링(비번 1234 또는 0000) → 앱 [관리] → 블루투스 연결

## 자동 업데이트
앱이 켜질 때와 6시간마다 최신 릴리스를 확인하고, 새 버전이 있으면 상단에 배너가 뜹니다. 버튼 한 번이면 내려받아 설치합니다.
(안드로이드 규칙상 완전 무확인 설치는 보장되지 않습니다.)

## 배포 방법
```
git tag v0.1.1 && git push origin v0.1.1
```
태그를 푸시하면 GitHub Actions 가 테스트 → 서명 → APK 빌드 → Release 업로드까지 합니다.
버전 코드는 `X*10000 + Y*100 + Z` 입니다.

### 최초 1회 설정 (Settings → Secrets and variables → Actions)
`scripts/make_keystore.sh` 실행 후 `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` 4개를 등록하세요.
**release.jks 는 커밋 금지, 별도 백업 필수** (분실 시 업데이트 불가).

## 프로토콜
수신: `<w1, …, w12>\n` (ERR 허용), `[TARE] …`, `[VER] x`, `[SYSTEM] 영점 조절 완료`
송신: `<TARE>\n`, `<VER>\n`, `<1,3,5>\n`(LED)
