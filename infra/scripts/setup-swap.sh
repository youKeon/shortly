#!/bin/bash

# EC2 Swap 설정 스크립트
# 메모리 부족 상황 대비 2GB swap 파일 생성

echo "🔧 Swap 설정 시작..."

# 기존 swap 확인
EXISTING_SWAP=$(swapon --show)
if [ -n "$EXISTING_SWAP" ]; then
    echo "⚠️  이미 swap이 활성화되어 있습니다:"
    echo "$EXISTING_SWAP"
    read -p "기존 swap을 제거하고 새로 생성하시겠습니까? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "❌ Swap 설정을 취소했습니다."
        exit 0
    fi
    sudo swapoff -a
    sudo rm -f /swapfile
fi

# 2GB swap 파일 생성
echo "📝 2GB swap 파일 생성 중... (시간이 걸릴 수 있습니다)"
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048 status=progress

# 권한 설정
echo "🔐 권한 설정 중..."
sudo chmod 600 /swapfile

# Swap 영역 설정
echo "⚙️  Swap 영역 설정 중..."
sudo mkswap /swapfile

# Swap 활성화
echo "✅ Swap 활성화 중..."
sudo swapon /swapfile

# 부팅 시 자동 마운트 설정
echo "🔄 부팅 시 자동 마운트 설정 중..."
if ! grep -q '/swapfile' /etc/fstab; then
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi

# Swappiness 설정 (10으로 설정 - swap 사용 최소화)
echo "🎚️  Swappiness 설정 중 (현재: $(cat /proc/sys/vm/swappiness))"
sudo sysctl vm.swappiness=10
if ! grep -q 'vm.swappiness' /etc/sysctl.conf; then
    echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf
fi

# 결과 확인
echo ""
echo "✅ Swap 설정 완료!"
echo ""
echo "📊 메모리 상태:"
free -h
echo ""
echo "💾 Swap 상태:"
swapon --show
echo ""
echo "⚙️  Swappiness 값: $(cat /proc/sys/vm/swappiness) (낮을수록 swap 사용 최소화)"
