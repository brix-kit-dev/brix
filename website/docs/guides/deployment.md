---
id: deployment
title: Deployment Guide
sidebar_label: Deployment
sidebar_position: 5
---

# Deployment Guide

This guide covers deploying Brix applications to production environments.

## Build for Production

### Backend Build

```bash
# Build all modules
mvn clean package -Pprod -DskipTests

# Build produces:
# enterprise-host/target/enterprise-host-3.0.0.jar
```

### Frontend Build

```bash
# Install dependencies
pnpm install

# Build all packages
pnpm build

# Build produces optimized bundles in dist/ folders
```

### Combined Build

```bash
# Full build script
./scripts/build-production.sh

# Or manually:
mvn clean package -Pprod
pnpm build
```

## Docker Deployment

### Dockerfile

```dockerfile
# Dockerfile
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="Brix Team <team@brix.dev>"
LABEL description="Brix Enterprise Application"

# Create non-root user
RUN addgroup -S brix && adduser -S brix -G brix
USER brix

WORKDIR /app

# Copy application JAR
COPY --chown=brix:brix enterprise-host/target/enterprise-host-*.jar app.jar

# Copy frontend assets (served by Spring Boot)
COPY --chown=brix:brix packages/@brix/enterprise-host/web/dist static/

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Expose port
EXPOSE 8080

# JVM options
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+UseStringDeduplication"

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### Docker Compose

```yaml
# docker-compose.yml
version: "3.9"

services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DATABASE_URL=jdbc:postgresql://db:5432/brix
      - DATABASE_USERNAME=${DB_USERNAME}
      - DATABASE_PASSWORD=${DB_PASSWORD}
      - REDIS_HOST=redis
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_started
      kafka:
        condition: service_started
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--spider", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  db:
    image: postgres:15-alpine
    environment:
      - POSTGRES_DB=brix
      - POSTGRES_USER=${DB_USERNAME}
      - POSTGRES_PASSWORD=${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME}"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      - KAFKA_BROKER_ID=1
      - KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181
      - KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092
      - KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1
    depends_on:
      - zookeeper

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      - ZOOKEEPER_CLIENT_PORT=2181

volumes:
  postgres_data:
  redis_data:
```

### Build and Run

```bash
# Build image
docker build -t brix-app:latest .

# Run with compose
docker-compose up -d

# View logs
docker-compose logs -f app

# Scale
docker-compose up -d --scale app=3
```

## Kubernetes Deployment

### Deployment Manifest

```yaml
# k8s/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: brix-app
  labels:
    app: brix
spec:
  replicas: 3
  selector:
    matchLabels:
      app: brix
  template:
    metadata:
      labels:
        app: brix
    spec:
      containers:
        - name: brix
          image: brix-app:latest
          ports:
            - containerPort: 8080
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
            - name: DATABASE_URL
              valueFrom:
                secretKeyRef:
                  name: brix-secrets
                  key: database-url
            - name: DATABASE_USERNAME
              valueFrom:
                secretKeyRef:
                  name: brix-secrets
                  key: database-username
            - name: DATABASE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: brix-secrets
                  key: database-password
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1Gi"
              cpu: "1000m"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 5
```

### Service

```yaml
# k8s/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: brix-service
spec:
  selector:
    app: brix
  ports:
    - protocol: TCP
      port: 80
      targetPort: 8080
  type: ClusterIP
```

### Ingress

```yaml
# k8s/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: brix-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  tls:
    - hosts:
        - app.brix.dev
      secretName: brix-tls
  rules:
    - host: app.brix.dev
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: brix-service
                port:
                  number: 80
```

### Deploy

```bash
# Create namespace
kubectl create namespace brix

# Apply secrets
kubectl apply -f k8s/secrets.yaml -n brix

# Deploy
kubectl apply -f k8s/ -n brix

# Check status
kubectl get pods -n brix
kubectl logs -f deployment/brix-app -n brix
```

## Environment Configuration

### Application Properties

```yaml
# application-prod.yml
spring:
  profiles:
    active: prod
  
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000

brix:
  adapters:
    cache:
      type: redis
      redis:
        host: ${REDIS_HOST:localhost}
        port: ${REDIS_PORT:6379}
        password: ${REDIS_PASSWORD:}
        database: 0
        timeout: 2000
    
    event-bus:
      type: kafka
      kafka:
        bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
        consumer:
          group-id: ${KAFKA_GROUP_ID:brix-app}
          auto-offset-reset: earliest
        producer:
          acks: all
          retries: 3
    
    file-storage:
      type: s3
      s3:
        region: ${AWS_REGION:us-east-1}
        bucket: ${S3_BUCKET}
        access-key: ${AWS_ACCESS_KEY_ID}
        secret-key: ${AWS_SECRET_ACCESS_KEY}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when_authorized
      probes:
        enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
```

### Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `DATABASE_URL` | JDBC connection URL | Yes |
| `DATABASE_USERNAME` | Database user | Yes |
| `DATABASE_PASSWORD` | Database password | Yes |
| `REDIS_HOST` | Redis hostname | Yes |
| `REDIS_PASSWORD` | Redis password | No |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers | Yes |
| `AWS_ACCESS_KEY_ID` | AWS access key | If using S3 |
| `AWS_SECRET_ACCESS_KEY` | AWS secret key | If using S3 |

## CI/CD Pipeline

### GitHub Actions

```yaml
# .github/workflows/deploy.yml
name: Deploy

on:
  push:
    branches: [main]
    tags: ['v*']

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  build:
    runs-on: ubuntu-latest
    outputs:
      version: ${{ steps.version.outputs.version }}
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'maven'
      
      - name: Set up pnpm
        uses: pnpm/action-setup@v2
        with:
          version: 8
      
      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'pnpm'
      
      - name: Get version
        id: version
        run: |
          if [[ "${{ github.ref }}" == refs/tags/v* ]]; then
            echo "version=${GITHUB_REF#refs/tags/v}" >> $GITHUB_OUTPUT
          else
            echo "version=latest" >> $GITHUB_OUTPUT
          fi
      
      - name: Build backend
        run: mvn clean package -Pprod -DskipTests
      
      - name: Build frontend
        run: |
          pnpm install
          pnpm build
      
      - name: Log in to registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      
      - name: Build and push image
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: |
            ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ steps.version.outputs.version }}
            ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:latest
  
  deploy:
    needs: build
    runs-on: ubuntu-latest
    environment: production
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up kubectl
        uses: azure/setup-kubectl@v3
      
      - name: Configure kubeconfig
        run: |
          echo "${{ secrets.KUBE_CONFIG }}" | base64 -d > kubeconfig
          export KUBECONFIG=kubeconfig
      
      - name: Deploy to Kubernetes
        run: |
          kubectl set image deployment/brix-app \
            brix=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ needs.build.outputs.version }} \
            -n brix
          kubectl rollout status deployment/brix-app -n brix
```

## Monitoring

### Prometheus Metrics

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'brix-app'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['brix-app:8080']
```

### Grafana Dashboard

Import dashboard ID `17175` for Spring Boot metrics or create custom:

```json
{
  "dashboard": {
    "title": "Brix Application",
    "panels": [
      {
        "title": "Request Rate",
        "type": "graph",
        "targets": [{
          "expr": "rate(http_server_requests_seconds_count[5m])"
        }]
      },
      {
        "title": "Error Rate",
        "type": "graph",
        "targets": [{
          "expr": "rate(http_server_requests_seconds_count{status=~\"5..\"}[5m])"
        }]
      },
      {
        "title": "Response Time P99",
        "type": "graph",
        "targets": [{
          "expr": "histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))"
        }]
      }
    ]
  }
}
```

### Logging

Configure structured logging:

```yaml
# application-prod.yml
logging:
  pattern:
    console: '{"timestamp":"%d","level":"%p","service":"brix","trace":"%X{traceId}","span":"%X{spanId}","message":"%m"}%n'
  level:
    root: WARN
    io.brix: INFO
    com.example: INFO
```

## Security Checklist

- [ ] **HTTPS only** - Configure TLS termination
- [ ] **Secrets management** - Use Kubernetes Secrets or Vault
- [ ] **Network policies** - Restrict pod-to-pod communication
- [ ] **Image scanning** - Scan for vulnerabilities
- [ ] **Non-root user** - Run containers as non-root
- [ ] **Resource limits** - Set CPU/memory limits
- [ ] **Pod security** - Enable pod security standards
- [ ] **Audit logging** - Enable Kubernetes audit logs

## Scaling

### Horizontal Pod Autoscaler

```yaml
# k8s/hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: brix-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: brix-app
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
```

## Troubleshooting

### Check Application Health

```bash
# Kubernetes
kubectl exec -it deployment/brix-app -n brix -- wget -qO- http://localhost:8080/actuator/health

# Docker
docker exec brix-app wget -qO- http://localhost:8080/actuator/health
```

### View Logs

```bash
# Kubernetes
kubectl logs -f deployment/brix-app -n brix --tail=100

# Docker
docker logs -f brix-app --tail=100
```

### Debug Container

```bash
# Kubernetes
kubectl exec -it deployment/brix-app -n brix -- /bin/sh

# Docker
docker exec -it brix-app /bin/sh
```

## Next Steps

- [Architecture Guard](./architecture-guard) - Enforce architecture
- [Testing Guide](./testing) - Ensure quality
- [FAQ](../faq) - Common questions
