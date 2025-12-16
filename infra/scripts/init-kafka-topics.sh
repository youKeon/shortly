#!/bin/bash
#
# Kafka Topic 초기화 스크립트
# 
# 이 스크립트는 Shortly 프로젝트에 필요한 Kafka Topic을 생성합니다.
# 고가용성을 위해 명시적으로 Replication Factor와 Partition 설정을 지정합니다.
#
# 실행 방법:
#   ./infra/scripts/init-kafka-topics.sh
#
# 전제 조건:
#   - Kafka 클러스터가 실행 중이어야 함
#   - kafka-topics.sh가 PATH에 있거나 Kafka 컨테이너 내부에서 실행
#

set -e  # 에러 발생 시 즉시 종료

BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092,localhost:9095,localhost:9096}"

echo "=========================================="
echo "Kafka Topic 초기화 시작"
echo "Bootstrap Servers: $BOOTSTRAP_SERVERS"
echo "=========================================="

# 색상 정의
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Topic 생성 함수
create_topic() {
    local topic_name=$1
    local partitions=$2
    local replication_factor=$3
    local min_isr=$4
    local retention_ms=$5
    local description=$6

    echo -e "\n${YELLOW}Creating topic: $topic_name${NC}"
    echo "  Description: $description"
    echo "  Partitions: $partitions"
    echo "  Replication Factor: $replication_factor"
    echo "  Min ISR: $min_isr"
    echo "  Retention: $((retention_ms / 86400000)) days"

    kafka-topics.sh --create \
        --bootstrap-server "$BOOTSTRAP_SERVERS" \
        --topic "$topic_name" \
        --partitions "$partitions" \
        --replication-factor "$replication_factor" \
        --config min.insync.replicas="$min_isr" \
        --config retention.ms="$retention_ms" \
        --if-not-exists

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Topic '$topic_name' created successfully${NC}"
    else
        echo "✗ Failed to create topic '$topic_name'"
        return 1
    fi
}

# 1. url-clicked topic (메인 클릭 이벤트)
create_topic \
    "url-clicked" \
    10 \
    3 \
    2 \
    604800000 \
    "클릭 이벤트를 Click Service로 전달하는 메인 토픽"

# 2. url-clicked-dlq topic (Dead Letter Queue)
create_topic \
    "url-clicked-dlq" \
    3 \
    3 \
    2 \
    2592000000 \
    "처리 실패한 클릭 이벤트를 보관하는 DLQ 토픽"

echo ""
echo "=========================================="
echo "Topic 목록 확인"
echo "=========================================="

kafka-topics.sh --list \
    --bootstrap-server "$BOOTSTRAP_SERVERS"

echo ""
echo "=========================================="
echo "Topic 상세 정보"
echo "=========================================="

for topic in "url-clicked" "url-clicked-dlq"; do
    echo ""
    echo "--- $topic ---"
    kafka-topics.sh --describe \
        --bootstrap-server "$BOOTSTRAP_SERVERS" \
        --topic "$topic"
done

echo ""
echo -e "${GREEN}=========================================="
echo "Topic 초기화 완료!"
echo -e "==========================================${NC}"
