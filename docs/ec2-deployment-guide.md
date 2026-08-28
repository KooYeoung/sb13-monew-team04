# EC2 배포 가이드

이 문서는 `sb13-monew-team04` 백엔드를 EC2 인스턴스에 배포하는 절차를 정리한다. 전체 구조는 GitHub Actions가 Docker 이미지를 빌드해 GHCR(GitHub Container Registry)에 올리면, EC2 서버가 그 이미지를 받아 `docker compose`로 실행하는 방식이다. DB는 별도 RDS 없이 같은 EC2 위에서 Docker 컨테이너로 함께 띄운다. 도메인은 아직 없으므로 우선 EC2의 퍼블릭 IP로 접속하고, Nginx를 앞단에 둬서 나중에 도메인과 HTTPS(Let's Encrypt)를 붙일 때 애플리케이션 코드나 컨테이너 구성은 건드리지 않도록 해 두었다.

이 문서와 함께 추가된 파일은 `Dockerfile`, `.dockerignore`, `deploy/compose.prod.yaml`, `deploy/nginx/monew.conf`, `deploy/.env.example`, `.github/workflows/deploy.yml`이다. GHCR 이미지와 배포 대상 저장소는 팀 공용 저장소인 `y2ahhh/sb13-monew-team04` 기준이다(개인 fork인 `HamJiWeon/sb13-monew-team04`가 아니다). GitHub Secrets(`EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`)도 반드시 `y2ahhh/sb13-monew-team04` 저장소의 Settings에 등록되어 있어야 `main` push 시 실제로 배포가 동작한다.

## DB를 EC2에 함께 둔 이유

RDS 대신 같은 EC2에 Postgres 컨테이너를 함께 두도록 구성했다. 지금 저장소의 `compose.yaml`이 이미 이 구조(Docker Postgres)를 기준으로 되어 있어 로컬 개발 환경과 배포 환경의 구성이 크게 다르지 않고, 부트캠프 팀 프로젝트 단계에서 RDS를 새로 만들고 보안 그룹, 서브넷, 파라미터 그룹을 따로 설정하는 비용을 지금 들일 필요가 크지 않다고 판단했기 때문이다. 다만 이 방식에는 명확한 한계가 있다. EC2 인스턴스가 재시작되거나 장애가 나면 DB도 함께 영향을 받고, 자동 백업이나 다중 AZ 복제 같은 운영 기능이 없다. 실제 서비스로 트래픽을 받는 단계까지 간다면 RDS로 옮기는 편이 맞다.

## 인스턴스 사양 및 스왑

실제 배포에 쓰인 EC2는 t3.micro(메모리 1GiB, vCPU 2개)다. Postgres와 Spring Boot를 함께 띄우기에는 메모리가 빠듯해서, Gradle 빌드는 EC2 위에서 직접 돌리지 않고 반드시 로컬(Docker Desktop)이나 GitHub Actions에서 빌드한 이미지를 pull해서 쓴다. 메모리 부족 시 컨테이너가 강제 종료되는 걸 막기 위해 1GiB 스왑 파일을 만들어 뒀다(이미 적용 완료).

```bash
sudo fallocate -l 1G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab
```

배포 후 `docker stats`로 실제 메모리 사용량을 확인하고, 여유가 부족하면 t3.small(메모리 2GiB)로 인스턴스 유형을 변경하는 것을 검토한다. 인스턴스 유형 변경은 정지 후 유형만 바꿔 재시작하면 되고, 같은 EBS 볼륨을 그대로 쓰므로 기존 설정(Docker, 배포 파일 등)은 유지된다.

## 1. EC2 서버 최초 설정 (한 번만)

1. **보안 그룹.** 인바운드 22번(SSH, 내 IP로 제한), 80번(HTTP, 0.0.0.0/0)만 연다. 8080번은 외부에 열지 않는다. `compose.prod.yaml`에서 앱 컨테이너를 `127.0.0.1:8080`에만 바인딩해 뒀기 때문에, 보안 그룹에서 막지 않더라도 EC2 밖에서는 애초에 8080으로 접속할 수 없다.

2. **탄력적 IP.** 인스턴스를 재부팅해도 퍼블릭 IP가 바뀌지 않도록 탄력적 IP를 할당해 연결해 뒀다(적용 완료). 2024년부터 AWS는 일반 퍼블릭 IP와 탄력적 IP 모두 동일하게 시간당 과금하므로, 탄력적 IP를 쓴다고 추가 비용이 드는 것은 아니다.

3. **Docker / Docker Compose 설치.**
   ```bash
   sudo apt-get update
   sudo apt-get install -y ca-certificates curl gnupg
   sudo install -m 0755 -d /etc/apt/keyrings
   curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
   echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
   sudo apt-get update
   sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
   sudo usermod -aG docker $USER
   ```

4. **배포 디렉터리.**
   ```bash
   sudo mkdir -p /opt/monew
   sudo chown $USER:$USER /opt/monew
   ```
   로컬에서 `scp -r deploy/. ubuntu@<IP>:/opt/monew/`로 `deploy/` 안의 파일들을 옮긴다.

5. **`.env.prod` 생성.** `deploy/.env.example`을 `/opt/monew/.env.prod`로 복사한 뒤 실제 값(DB 비밀번호, EC2 퍼블릭 IP, NAVER API 키 등)을 채운다. 이 파일은 Git에 올리지 않는다.
   ```bash
   cp .env.example .env.prod
   ```

6. **최초 이미지 준비.** GitHub Actions 배포 워크플로가 `y2ahhh/sb13-monew-team04`의 `main`에 merge되어 실행되기 전에는 GHCR에 이미지가 없다. 이 최초 이미지를 누가 만들 수 있는지는 GHCR의 패키지 생성 권한 규칙에 따라 달라진다. 저장소 협업자(Write 권한)가 저장소 소유자(개인 계정)의 GHCR 네임스페이스에 새 패키지를 직접 push할 수 있는지는 GitHub 공식 문서에도 명확히 나와 있지 않아, 실제로 시도해서 확인이 필요하다. `docker push`가 `denied: permission_denied`로 실패하면, 우선 개인 fork 네임스페이스(`ghcr.io/hamjiweon/sb13-monew-team04`)로 push해 배포 파이프라인 자체만 검증하거나, `y2ahhh` 저장소 소유자에게 최초 push를 요청하거나, PR이 merge되어 GitHub Actions(`GITHUB_TOKEN`, 저장소에 자동으로 권한 있음)가 최초 이미지를 만들어줄 때까지 기다린다.
   ```bash
   docker login ghcr.io -u <깃허브-아이디>
   docker build --platform linux/amd64 -t ghcr.io/y2ahhh/sb13-monew-team04:latest .
   docker push ghcr.io/y2ahhh/sb13-monew-team04:latest
   ```

7. **최초 컨테이너 기동.**
   ```bash
   cd /opt/monew
   docker login ghcr.io -u <깃허브-아이디>   # GHCR 패키지가 private이라면 필요
   docker compose -f compose.prod.yaml --env-file .env.prod up -d
   docker compose -f compose.prod.yaml logs -f app
   ```

8. **Nginx 설치 및 연결.**
   ```bash
   sudo apt-get install -y nginx
   sudo cp /opt/monew/nginx/monew.conf /etc/nginx/sites-available/monew
   sudo ln -s /etc/nginx/sites-available/monew /etc/nginx/sites-enabled/monew
   sudo rm -f /etc/nginx/sites-enabled/default
   sudo nginx -t
   sudo systemctl reload nginx
   ```
   이후 `http://<EC2 퍼블릭 IP>`로 접속해 Swagger UI(`/swagger-ui.html`)가 뜨는지 확인하면 최초 배포는 끝난다.

## 2. GitHub 저장소 설정

`y2ahhh/sb13-monew-team04` 저장소 Settings → Secrets and variables → Actions에 `EC2_HOST`(탄력적 IP), `EC2_USER`(`ubuntu`), `EC2_SSH_KEY`(EC2 접속용 SSH 프라이빗 키 전체)를 등록한다(등록 완료). `GITHUB_TOKEN`은 자동 발급되므로 별도 등록이 필요 없다.

## 3. 이후 배포 흐름

`develop`에서 `main`으로 PR이 merge되면 `.github/workflows/deploy.yml`이 자동 실행된다. `build-and-push` job이 이미지를 빌드해 GHCR에 올리고(`ghcr.io/y2ahhh/sb13-monew-team04`), `deploy` job이 SSH로 EC2에 접속해 `app` 컨테이너만 재생성한다(`--no-deps`로 `postgres` 컨테이너는 유지). 스키마 변경이 필요한 PR이라면 Flyway 마이그레이션 파일이 같이 있어야 하며, 컨테이너가 새로 뜰 때 기동 시점에 자동으로 마이그레이션이 실행된다.

## 4. 한계와 다음 작업

`build.gradle`에 `spring-boot-starter-actuator`가 없어 `/actuator/health` 같은 표준 헬스체크 엔드포인트가 없다. 배포 자동화를 더 다듬으려면 actuator 추가와 컨테이너 healthcheck, 배포 후 헬스체크 단계 추가를 다음 작업으로 제안한다. HTTPS는 지금 구성에 포함하지 않았다. 도메인을 연결하면 `deploy/nginx/monew.conf`의 `server_name`을 실제 도메인으로 바꾸고 `sudo apt-get install certbot python3-certbot-nginx` 후 `sudo certbot --nginx -d <도메인>`을 실행하면 된다.
