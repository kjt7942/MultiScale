#!/usr/bin/env bash
# 릴리스 서명 키 생성. 이 키를 잃어버리면 기존 설치본을 업데이트할 수 없으니 반드시 백업하세요.
set -euo pipefail
OUT="${1:-release.jks}"
ALIAS="${KEY_ALIAS:-grape}"
PW="${KEYSTORE_PASSWORD:-$(openssl rand -hex 12)}"
keytool -genkeypair -v -keystore "$OUT" -alias "$ALIAS" -keyalg RSA -keysize 2048 \
  -validity 36500 -storepass "$PW" -keypass "$PW" -dname "CN=MultiScale, O=kjt7942, C=KR"
base64 -w0 "$OUT" > "$OUT.b64"
echo "KEYSTORE_PASSWORD=$PW"
echo "KEY_PASSWORD=$PW"
echo "KEY_ALIAS=$ALIAS"
echo "KEYSTORE_BASE64 -> $OUT.b64 파일 내용 전체"
