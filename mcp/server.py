from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from mcp.server.fastmcp import FastMCP

# V4SH Project MCP
# This server is intentionally specification-first:
# it exposes authoritative project knowledge and validation,
# while Antigravity remains responsible for reasoning and code changes.

ROOT = Path(__file__).resolve().parent

SPEC_FILES = {
    "project_spec": "product.json",
    "design_system": "design.json",
    "screen_catalog": "screens.json",
    "navigation_rules": "navigation.json",
    "payment_state_machine": "payment.json",
    "security_rules": "security.json",
    "architecture": "architecture.json",
    "implementation_roadmap": "implementation_roadmap.json",
}

mcp = FastMCP("V4SH Project MCP")


def load_json(filename: str) -> dict[str, Any]:
    path = ROOT / filename
    if not path.exists():
        raise FileNotFoundError(f"V4SH spec file not found: {path}")
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def load_all_specs() -> dict[str, dict[str, Any]]:
    return {
        key: load_json(filename)
        for key, filename in SPEC_FILES.items()
    }


def find_screen(screen_id: str) -> dict[str, Any] | None:
    screens = load_json(SPEC_FILES["screen_catalog"])
    for screen in screens.get("screens", []):
        if screen.get("id") == screen_id:
            return screen
    return None


def get_allowed_payment_states() -> list[str]:
    payment = load_json(SPEC_FILES["payment_state_machine"])
    return payment.get("payment_lifecycle", {}).get("states", [])


@mcp.tool()
def get_project_spec() -> dict[str, Any]:
    """Return the authoritative V4SH product definition and scope."""
    return load_json(SPEC_FILES["project_spec"])


@mcp.tool()
def get_design_system() -> dict[str, Any]:
    """Return the authoritative V4SH visual design system."""
    return load_json(SPEC_FILES["design_system"])


@mcp.tool()
def get_screen_catalog() -> dict[str, Any]:
    """Return the authoritative V4SH screen registry."""
    return load_json(SPEC_FILES["screen_catalog"])


@mcp.tool()
def get_navigation_rules() -> dict[str, Any]:
    """Return the authoritative navigation contract."""
    return load_json(SPEC_FILES["navigation_rules"])


@mcp.tool()
def get_payment_state_machine() -> dict[str, Any]:
    """Return payment lifecycle states and payment safety semantics."""
    return load_json(SPEC_FILES["payment_state_machine"])


@mcp.tool()
def get_security_rules() -> dict[str, Any]:
    """Return credential, privacy, accessibility, and security boundaries."""
    return load_json(SPEC_FILES["security_rules"])


@mcp.tool()
def get_architecture() -> dict[str, Any]:
    """Return the authoritative Android architecture and layer boundaries."""
    return load_json(SPEC_FILES["architecture"])


@mcp.tool()
def get_implementation_roadmap() -> dict[str, Any]:
    """Return the authoritative V4SH implementation phases and gates."""
    return load_json(SPEC_FILES["implementation_roadmap"])


@mcp.tool()
def get_all_specs() -> dict[str, Any]:
    """Return all V4SH source-of-truth specifications."""
    return load_all_specs()


@mcp.tool()
def validate_screen(screen_id: str) -> dict[str, Any]:
    """
    Validate that a screen exists in the authoritative catalog and report
    its source/reference metadata.
    """
    screen = find_screen(screen_id)

    if screen is None:
        return {
            "valid": False,
            "screen_id": screen_id,
            "errors": [f"Unknown V4SH screen: {screen_id}"],
            "source_of_truth": "screens.json",
        }

    return {
        "valid": True,
        "screen_id": screen_id,
        "screen": screen,
        "source_of_truth": "screens.json",
    }


@mcp.tool()
def validate_payment_state(
    state: str,
    initiated: bool | None = None,
    definitive_confirmation: bool = False,
    definitive_failure: bool = False,
) -> dict[str, Any]:
    """
    Validate a proposed payment state against V4SH payment truth rules.

    initiated:
      True  = payment may have been initiated.
      False = payment definitely was not initiated.
      None  = initiation status is not known.

    definitive_confirmation:
      True only when definitive payment confirmation exists.

    definitive_failure:
      True only when definitive payment failure exists.
    """
    state = state.strip().upper()
    allowed = get_allowed_payment_states()
    errors: list[str] = []
    warnings: list[str] = []

    if state not in allowed:
        errors.append(f"Unknown payment state: {state}")

    if state == "SUCCESS" and not definitive_confirmation:
        errors.append("SUCCESS requires definitive confirmation.")

    if state == "FAILED" and not definitive_failure:
        errors.append("FAILED requires definitive failure.")

    if state == "CANCELLED":
        if initiated is not False:
            errors.append(
                "CANCELLED is valid only when payment initiation definitely did not occur."
            )

    if state == "UNKNOWN":
        if initiated is False:
            warnings.append(
                "UNKNOWN is normally unnecessary when payment definitely was not initiated."
            )
        warnings.append("Do not expose immediate Retry Payment for UNKNOWN.")

    if state == "PROCESSING":
        warnings.append(
            "Use 'Stop waiting'; this does not cancel an already initiated payment."
        )

    if state in {"SUCCESS", "FAILED"} and definitive_confirmation and definitive_failure:
        errors.append(
            "A payment cannot simultaneously have definitive confirmation and definitive failure."
        )

    return {
        "valid": not errors,
        "state": state,
        "errors": errors,
        "warnings": warnings,
        "allowed_states": allowed,
        "source_of_truth": "payment.json",
    }


@mcp.tool()
def validate_security_rule(rule: str) -> dict[str, Any]:
    """
    Validate a security-related implementation claim against the frozen rules.
    """
    security = load_json(SPEC_FILES["security_rules"])
    rule_normalized = " ".join(rule.lower().split())

    forbidden_terms = [
        "upi pin",
        "otp",
        "bank password",
        "card pin",
        "cvv",
    ]

    credential_handling_risk = [
        term for term in forbidden_terms if term in rule_normalized
    ]

    risky_phrases = [
        "store",
        "save",
        "log",
        "capture",
        "transmit",
        "automate",
        "enter",
    ]

    violations: list[str] = []
    warnings: list[str] = []

    if credential_handling_risk and any(
        phrase in rule_normalized for phrase in risky_phrases
    ):
        violations.append(
            "The proposed rule appears to handle a sensitive credential. "
            "V4SH must never capture, store, log, or transmit UPI PIN, OTP, "
            "bank passwords, card PIN, or CVV."
        )

    if "accessibility" in rule_normalized and (
        "pin" in rule_normalized or "otp" in rule_normalized
    ):
        violations.append(
            "Accessibility automation must stop at PIN/sensitive screens and "
            "must never enter sensitive credentials."
        )

    if "zero cloud telemetry" in rule_normalized:
        warnings.append(
            "Do not claim Zero Cloud Telemetry unless that behavior is actually implemented and verified."
        )

    return {
        "valid": not violations,
        "errors": violations,
        "warnings": warnings,
        "matched_sensitive_terms": credential_handling_risk,
        "source_of_truth": "security.json",
        "security_spec_loaded": bool(security),
    }


@mcp.tool()
def validate_implementation_change(
    area: str,
    proposed_change: str,
) -> dict[str, Any]:
    """
    Lightweight cross-spec guardrail for proposed implementation changes.
    Use before making changes that could affect product scope, navigation,
    payment, security, or design.
    """
    area_normalized = area.strip().lower()
    change = " ".join(proposed_change.lower().split())

    product = load_json(SPEC_FILES["project_spec"])
    payment = load_json(SPEC_FILES["payment_state_machine"])
    security = load_json(SPEC_FILES["security_rules"])
    design = load_json(SPEC_FILES["design_system"])

    errors: list[str] = []
    warnings: list[str] = []

    forbidden_product_terms = [
        "wallet",
        "v4sh balance",
        "merchant app",
        "merchant onboarding",
        "proprietary qr",
        "imps",
        "neft",
        "rewards",
        "loans",
        "investments",
    ]

    for term in forbidden_product_terms:
        if term in change:
            errors.append(
                f"Potential product-scope conflict: '{term}' is explicitly out of scope."
            )

    sensitive_terms = ["upi pin", "otp", "bank password", "card pin", "cvv"]
    for term in sensitive_terms:
        if term in change and any(
            verb in change for verb in ["store", "capture", "log", "transmit", "save"]
        ):
            errors.append(
                f"Security conflict: V4SH must not capture/store/log/transmit '{term}'."
            )

    if "retry" in change and "unknown" in change:
        errors.append(
            "Payment-safety conflict: UNKNOWN must not expose an immediate retry action."
        )

    if "success" in change and "confirmation" not in change:
        warnings.append(
            "Verify that SUCCESS is based on definitive confirmation."
        )

    if area_normalized in {"design", "ui"}:
        primary = design.get("color_system", {}).get("primary", {}).get("hex")
        if primary and primary.lower() not in change and "color" in change:
            warnings.append(
                f"Design change should be checked against the frozen primary color {primary}."
            )

    return {
        "valid": not errors,
        "area": area,
        "proposed_change": proposed_change,
        "errors": errors,
        "warnings": warnings,
        "source_of_truth": [
            "product.json",
            "payment.json",
            "security.json",
            "design.json",
        ],
        "loaded": {
            "product": bool(product),
            "payment": bool(payment),
            "security": bool(security),
            "design": bool(design),
        },
    }


if __name__ == "__main__":
    # stdio is the simplest local MCP transport for Antigravity.
    mcp.run(transport="stdio")
