# GitHub Secrets 설정 가이드

GitHub 리포지토리 → Settings → Secrets and variables → Actions에서 다음 시크릿들을 추가하세요:

## 필수 Secrets

### 1. GPG_PRIVATE_KEY
```bash
# 현재 private key를 base64로 인코딩
base64 -w 0 ~/.gnupg/private.key
```
위 명령어 결과값을 복사해서 설정

### 2. SIGNING_KEY_ID
```
6BFDEAE8
```

### 3. SIGNING_PASSWORD
```
""
```
(빈 문자열)

### 4. SONATYPE_USERNAME
Maven Central Portal 로그인에 사용하는 GitHub 사용자명

### 5. SONATYPE_PASSWORD  
Maven Central Portal에서 생성한 토큰 (선택사항)

## 사용법

1. 새 버전 릴리스: `git tag v1.0.1 && git push origin v1.0.1`
2. GitHub Actions가 자동으로 빌드, 패키징, 릴리스 생성
3. GitHub Releases에서 ZIP 다운로드
4. Maven Central Portal에 수동 업로드

## 향후 개선
- Sonatype API를 사용한 완전 자동 업로드
- 자동 버전 범프
- 자동 릴리스 노트 생성