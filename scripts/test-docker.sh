#!/bin/bash
set -e

echo "Starting Docker Compose..."
docker compose up -d --build

echo "Waiting for services to be ready..."
# Give it some time to start and warm up
MAX_RETRIES=30
RETRY_COUNT=0

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
  if curl -s http://localhost:9999/ready | grep -q "OK"; then
    echo "Services are ready!"
    break
  fi
  echo "Still waiting... ($RETRY_COUNT/$MAX_RETRIES)"
  sleep 2
  RETRY_COUNT=$((RETRY_COUNT+1))
done

if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
  echo "Timed out waiting for services."
  docker compose logs
  exit 1
fi

echo "Testing load balancer (Round Robin)..."
# We expect to hit different instances. Since we can't easily see which instance handled the request without headers or logs,
# we'll just check if the endpoint works through Nginx.
RESPONSE=$(curl -s -X POST http://localhost:9999/fraud-score \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test-1",
    "transaction": {
      "amount": 100.0,
      "installments": 1,
      "requested_at": "2024-05-05T12:00:00Z"
    },
    "customer": {
      "avg_amount": 50.0,
      "tx_count_24h": 2,
      "known_merchants": ["m-1"]
    },
    "merchant": {
      "id": "m-2",
      "mcc": "5411",
      "avg_amount": 80.0
    },
    "terminal": {
      "is_online": true,
      "card_present": true,
      "km_from_home": 5.0
    }
  }')

echo "Response: $RESPONSE"
if echo "$RESPONSE" | grep -q "approved"; then
  echo "POST /fraud-score works!"
else
  echo "POST /fraud-score failed!"
  exit 1
fi

echo "Checking resource usage..."
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}"

echo "Total memory usage should be <= 350MB. Please verify manually from the output above."

echo "Cleaning up..."
docker compose down
