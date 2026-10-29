#!/usr/bin/env bash
set -e

BASE_URL="http://localhost:8080"
echo "=== OrderFlow End-to-End API Demonstration ==="
echo "Target Base URL: $BASE_URL"

# 1. Health check
echo -e "\n1. Checking Health..."
curl -s "$BASE_URL/actuator/health" | jq . || curl -s "$BASE_URL/actuator/health"
echo ""

# 2. Login as Admin
echo -e "\n2. Logging in as admin..."
ADMIN_TOKEN=$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r .token)
echo "Admin token obtained (prefix): ${ADMIN_TOKEN:0:25}..."

# 3. Browse Catalog
echo -e "\n3. Browsing Product Catalog (Category: Electronics)..."
curl -s "$BASE_URL/api/v1/products?category=Electronics&page=0&size=3" | jq . || true

# 4. Fetch Product 1 (Cached via Redis)
echo -e "\n4. Fetching Product 1 (Redis Cache Read-Through)..."
curl -s "$BASE_URL/api/v1/products/1" | jq . || true

# 5. Register Customer
RANDOM_USER="customer_$(date +%s)"
echo -e "\n5. Registering customer: $RANDOM_USER..."
CUSTOMER_TOKEN=$(curl -s -X POST "$BASE_URL/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$RANDOM_USER\",\"email\":\"$RANDOM_USER@example.com\",\"password\":\"password123\"}" | jq -r .token)
echo "Customer token obtained."

# 6. Checkout (Create Order with Stock Reservation)
echo -e "\n6. Placing Order as customer..."
ORDER_JSON=$(curl -s -X POST "$BASE_URL/api/v1/orders" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "items": [{"productId": 3, "quantity": 2}],
    "shippingAddress": "123 Main St, Tech City"
  }')
echo "$ORDER_JSON" | jq . || echo "$ORDER_JSON"
ORDER_ID=$(echo "$ORDER_JSON" | jq -r .id)

# 7. Check Order by ID
echo -e "\n7. Fetching Order #$ORDER_ID..."
curl -s "$BASE_URL/api/v1/orders/$ORDER_ID" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq . || true

# 8. List Orders
echo -e "\n8. Querying Orders with status PENDING..."
curl -s "$BASE_URL/api/v1/orders?status=PENDING&page=0&size=5" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq . || true

# 9. Register Webhook Subscription
echo -e "\n9. Registering Webhook Subscription (as Admin)..."
curl -s -X POST "$BASE_URL/api/v1/webhooks/subscriptions" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://httpbin.org/post",
    "secret": "whsec_supersecuresecretkey12345",
    "eventTypes": "order.created,order.status_changed"
  }' | jq . || true

# 10. List Outbox Deliveries
echo -e "\n10. Listing Outbox Deliveries..."
curl -s "$BASE_URL/api/v1/webhooks/deliveries" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq . || true

echo -e "\n=== Demonstration Completed Successfully ==="
