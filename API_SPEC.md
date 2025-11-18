# Navi API Specification

## Base URL
```
https://api.navi.app/v1
```

## Authentication Endpoints
- POST /auth/login
- POST /auth/signup
- POST /auth/refresh
- POST /auth/forgot-password
- POST /auth/reset-password
- POST /auth/verify-email
- POST /auth/verify-phone

## User Endpoints
- GET /user/profile
- PUT /user/profile
- GET /user/settings
- PUT /user/settings

## Navigation Endpoints
- POST /routes/calculate
- GET /routes/{id}
- POST /routes/save
- GET /routes/saved
- POST /routes/share
- GET /traffic/live
- GET /incidents

## Places Endpoints
- GET /places/search
- GET /places/{id}
- GET /places/nearby
- GET /places/categories
- GET /places/{id}/reviews
- POST /places/{id}/favorite

## Social Endpoints
- GET /friends
- POST /friends/request
- PUT /friends/{id}/accept
- DELETE /friends/{id}
- POST /location/share
- GET /location/friends
- POST /messages/send
- GET /messages/conversations

## Maps Endpoints
- GET /maps/regions
- POST /maps/download
- GET /maps/downloaded
- DELETE /maps/{id}

## Advanced Features
- GET /weather
- GET /fuel/prices
- GET /ev/charging
- GET /poi/rest-stops

## Response Format
```json
{
  "success": true,
  "data": {},
  "meta": {
    "timestamp": "2024-01-15T10:30:00Z"
  }
}
```
