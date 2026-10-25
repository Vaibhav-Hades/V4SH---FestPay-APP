# V4SH Project MCP

This directory contains the authoritative V4SH specification layer and a local
MCP server intended to be connected to Antigravity.

## Files

- screens.json
- navigation.json
- payment.json
- security.json
- design.json
- architecture.json
- product.json
- implementation_roadmap.json
- server.py
- requirements.txt

## Design principle

The MCP server exposes V4SH specifications and validation. Antigravity remains
responsible for reasoning, editing the Android project, building, testing, and
iterating.

Do not give the MCP server broad authority to rewrite the repository.

## Local setup

From `D:\FestPay\mcp`:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
```

Then test that the server starts:

```powershell
python server.py
```

The process is expected to wait for MCP stdio messages.

## Antigravity

After the server is locally validated, configure Antigravity to launch
`server.py` with the MCP server configuration. Do not connect it until the
local startup test succeeds.
