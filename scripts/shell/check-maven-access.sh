#!/bin/bash
# Maven Repository Health Check Script
# Usage: ./check-maven-access.sh
# Exit codes: 0 = all OK, 1 = Google Maven failed, 2 = other critical repo failed

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

# Status tracking
GOOGLE_MAVEN_FAILED=0
OTHER_CRITICAL_FAILED=0

# Function to check repository accessibility
check_repo() {
    local name=$1
    local url=$2
    local critical=$3  # "google" | "critical" | "optional"
    echo -n "Checking $name... "
    
    local http_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$url")
    
    if echo "$http_code" | grep -q "200\|301\|302"; then
        echo -e "${GREEN}✅ OK (HTTP $http_code)${NC}"
        return 0
    else
        echo -e "${RED}❌ UNAVAILABLE (HTTP $http_code)${NC}"
        
        # Track failure type
        if [ "$critical" == "google" ]; then
            GOOGLE_MAVEN_FAILED=1
        elif [ "$critical" == "critical" ]; then
            OTHER_CRITICAL_FAILED=1
        fi
        return 1
    fi
}

# Check critical repositories
check_repo "Google Maven" "https://dl.google.com/android/repository/repository2-1.xml" "google"
check_repo "Maven Central" "https://repo1.maven.org/maven2/" "critical"
check_repo "Gradle Plugin Portal" "https://plugins.gradle.org/m2/" "critical"
check_repo "JitPack" "https://jitpack.io/" "optional"

# Check Google Maven Mirror (if configured or by default)
if [ ! -z "$GOOGLE_MIRROR_ENABLED" ] || [ "$GOOGLE_MAVEN_FAILED" -eq 1 ]; then
    echo ""
    echo "--- Checking mirrors ---"
    check_repo "Google Maven Mirror (googleapis.com)" "https://maven.googleapis.com/maven2/" "optional"
fi

# Check Aliyun mirrors (if configured)
if [ ! -z "$ALIYUN_MIRROR_ENABLED" ]; then
    check_repo "Aliyun Google Mirror" "https://maven.aliyun.com/repository/google/" "optional"
    check_repo "Aliyun Public Mirror" "https://maven.aliyun.com/repository/public/" "optional"
fi

# Check Nexus (if configured)
if [ ! -z "$NEXUS_URL" ]; then
    check_repo "Nexus (local)" "$NEXUS_URL/service/rest/v1/status" "optional"
fi

echo ""
echo "========================================="
echo "Health check completed"
echo "========================================="

# Determine exit code based on failures
if [ "$GOOGLE_MAVEN_FAILED" -eq 1 ] && [ "$OTHER_CRITICAL_FAILED" -eq 1 ]; then
    echo -e "${RED}⚠️ Both Google Maven and other critical repos failed${NC}"
    echo "Exit code: 2 (multiple critical failures)"
    exit 2
elif [ "$GOOGLE_MAVEN_FAILED" -eq 1 ]; then
    echo -e "${YELLOW}⚠️ Google Maven unavailable (use GitHub-hosted runner)${NC}"
    echo "Exit code: 1 (Google Maven only)"
    exit 1
elif [ "$OTHER_CRITICAL_FAILED" -eq 1 ]; then
    echo -e "${RED}⚠️ Critical repository failure detected${NC}"
    echo "Exit code: 2 (other critical repo)"
    exit 2
else
    echo -e "${GREEN}✅ All critical repositories accessible${NC}"
    echo "Exit code: 0 (success)"
    exit 0
fi
