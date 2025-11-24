#!/bin/bash
# Maven Repository Health Check Script
# Usage: ./check-maven-access.sh

echo "========================================="
echo "Maven Repository Health Check"
echo "Date: $(date)"
echo "========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check repository accessibility
check_repo() {
    local name=$1
    local url=$2
    echo -n "Checking $name... "
    
    if curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$url" | grep -q "200\|301\|302"; then
        echo -e "${GREEN}✅ OK${NC}"
        return 0
    else
        echo -e "${RED}❌ UNAVAILABLE${NC}"
        return 1
    fi
}

# Check critical repositories
check_repo "Google Maven" "https://dl.google.com/android/repository/repository2-1.xml"
check_repo "Maven Central" "https://repo1.maven.org/maven2/"
check_repo "Gradle Plugin Portal" "https://plugins.gradle.org/m2/"
check_repo "JitPack" "https://jitpack.io/"

# Check mirrors (if configured)
if [ ! -z "$ALIYUN_MIRROR_ENABLED" ]; then
    check_repo "Aliyun Google Mirror" "https://maven.aliyun.com/repository/google/"
    check_repo "Aliyun Public Mirror" "https://maven.aliyun.com/repository/public/"
fi

# Check Nexus (if configured)
if [ ! -z "$NEXUS_URL" ]; then
    check_repo "Nexus (local)" "$NEXUS_URL/service/rest/v1/status"
fi

echo ""
echo "========================================="
echo "Health check completed"
echo "========================================="
