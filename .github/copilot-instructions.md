# Copilot Instructions

## Project Guidelines
- Session flow requirement: launcher creates session token via POST action=create with Bearer JWT; injector passes token to DLL; DLL uses X-Session-Token for action=connect to fetch full user profile and for action=revoke on logout; one active session per user; 24h expiry; banned users revoked on next connect; periodic cleanup of expired/revoked tokens.