#!/bin/bash

##############################################
# Shortly - Distributed Load Test Runner
# Server: 다른 MacBook (Spring Boot + Docker)
# Client: 현재 MacBook (k6만 실행)
##############################################

# STEP 1: 서버 MacBook의 IP 주소 입력
echo "서버 MacBook의 IP 주소를 입력하세요 (예: 192.168.0.100):"
read SERVER_IP

# IP 형식 검증
if [[ ! $SERVER_IP =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "❌ 잘못된 IP 주소 형식입니다."
  exit 1
fi

echo "✓ 서버 IP: $SERVER_IP"

# STEP 2: 서버 연결 확인
echo ""
echo "서버 연결 확인 중..."

check_service() {
  local service_name=$1
  local port=$2
  local url="http://${SERVER_IP}:${port}/actuator/health"

  response=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 "$url")

  if [ "$response" = "200" ]; then
    echo "✓ $service_name (${port}): OK"
    return 0
  else
    echo "❌ $service_name (${port}): FAILED (HTTP $response)"
    return 1
  fi
}

all_ok=true
check_service "URL Service" 8081 || all_ok=false
check_service "Redirect Service" 8082 || all_ok=false
check_service "Click Service" 8083 || all_ok=false

if [ "$all_ok" = false ]; then
  echo ""
  echo "❌ 서버 연결 실패. 다음을 확인하세요:"
  echo "  1. 서버 MacBook에서 모든 서비스가 실행 중인가?"
  echo "  2. 서버 MacBook 방화벽이 비활성화되어 있는가?"
  echo "  3. 두 MacBook이 같은 Wi-Fi에 연결되어 있는가?"
  exit 1
fi

# STEP 3: 테스트 시나리오 선택
echo ""
echo "부하 테스트 시나리오를 선택하세요:"
echo "  1) 5K TPS 테스트 (6분, VU 6000 - 기본)"
echo "  2) 5K TPS 테스트 (6분, VU 8000 - 권장)"
echo "  3) 5K TPS 테스트 (6분, VU 10000 - 최대)"
echo "  4) Smoke Test (1분, 빠른 확인)"
read -p "선택 (1-4): " choice

case $choice in
  1)
    TEST_FILE="tps-5k.js"
    export K6_VU_MULTIPLIER="1.0"
    echo "✓ 5K TPS 테스트 (VU 6000) 선택"
    ;;
  2)
    TEST_FILE="tps-5k.js"
    export K6_VU_MULTIPLIER="1.33"
    echo "✓ 5K TPS 테스트 (VU 8000) 선택 - 권장"
    ;;
  3)
    TEST_FILE="tps-5k.js"
    export K6_VU_MULTIPLIER="1.67"
    echo "✓ 5K TPS 테스트 (VU 10000) 선택 - 최대"
    ;;
  4)
    TEST_FILE="smoke-test.js"
    export K6_VU_MULTIPLIER="1.0"
    echo "✓ Smoke Test 선택"
    ;;
  *)
    echo "❌ 잘못된 선택입니다."
    exit 1
    ;;
esac

# STEP 4: k6 실행
echo ""
echo "========================================="
echo "부하 테스트 시작"
echo "========================================="
echo "서버: $SERVER_IP"
echo "테스트: $TEST_FILE"
echo "VU 배율: ${K6_VU_MULTIPLIER}x"
echo "========================================="
echo ""

export URL_SERVICE="http://${SERVER_IP}:8081"
export REDIRECT_SERVICE="http://${SERVER_IP}:8082"
export CLICK_SERVICE="http://${SERVER_IP}:8083"

k6 run "$TEST_FILE"

# STEP 5: 결과 확인
echo ""
echo "========================================="
echo "테스트 완료!"
echo "========================================="
echo "결과 파일:"
echo "  - summary-5k.html (브라우저로 열기)"
echo "  - tps-5k-result.json (JSON 결과)"
echo ""
echo "서버 메트릭 확인:"
echo "  - Prometheus: http://${SERVER_IP}:9090"
echo "  - Grafana: http://${SERVER_IP}:3000"
echo "========================================="
