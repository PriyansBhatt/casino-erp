# Casino ERP Frontend API Guide

Base URL:
http://localhost:8080

## Standard API Response

All APIs return this format:

{
"success": true,
"message": "Action completed successfully",
"data": {},
"timestamp": "2026-06-27T14:30:00"
}

## Validation Error Response

If required fields are missing or invalid:

{
"success": false,
"message": "Username is required, Password is required",
"data": null,
"timestamp": "2026-06-27T14:30:00"
}

## Public APIs

### Health Check
GET /api/health

### Login
POST /api/auth/login

Body:
{
"username": "admin",
"password": "admin123"
}

Response includes:
- username
- status
- role
- token

## Protected API Rule

All protected requests must include:

Authorization: Bearer YOUR_TOKEN

## Useful Protected APIs

GET /api/alerts/dashboard

GET /api/audit-logs

GET /api/business-date

POST /api/business-date/open/{businessDate}

POST /api/business-date/close/{businessDate}

PUT /api/system-lock/lock

PUT /api/system-lock/unlock

GET /api/business-date-summary/{businessDate}

# Casino Dashboard

## Get Dashboard Summary

GET /api/casino-dashboard/{businessDate}

Example:

GET /api/casino-dashboard/2026-06-27

Response

{
"success": true,
"message": "Casino dashboard loaded successfully",
"data": {
"businessDate": "2026-06-27",
"totalBuyIn": 500000,
"totalCashOut": 420000,
"casinoNet": 80000,
"activeCustomers": 42,
"highValueAlerts": 3,
"suspiciousAlerts": 1
}
}

## Frontend Token Usage

After login, save token:

localStorage.setItem("token", response.token)

For protected API calls:

headers: {
Authorization: `Bearer ${localStorage.getItem("token")}`
}