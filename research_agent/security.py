import ipaddress
import socket
from urllib.parse import urlparse

from .errors import ValidationError


BLOCKED_HOSTS = {"localhost", "127.0.0.1", "0.0.0.0", "::1"}
BLOCKED_SCHEMES = {"file", "ftp", "ssh"}


def assert_safe_url(raw_url: str) -> None:
    parsed = urlparse(raw_url)
    if parsed.scheme in BLOCKED_SCHEMES:
        raise ValidationError("URL scheme is not allowed")
    if parsed.scheme not in {"http", "https"}:
        raise ValidationError("Only http and https URLs are allowed")
    if not parsed.hostname:
        raise ValidationError("URL host is required")
    host = parsed.hostname.lower()
    if host in BLOCKED_HOSTS:
        raise ValidationError("Local URLs are not allowed")
    try:
        ip = ipaddress.ip_address(host)
    except ValueError:
        for resolved_ip in resolve_host_ips(host):
            assert_public_ip(resolved_ip)
        return
    assert_public_ip(ip)


def resolve_host_ips(host: str):
    try:
        records = socket.getaddrinfo(host, None, proto=socket.IPPROTO_TCP)
    except socket.gaierror as exc:
        raise ValidationError(f"URL host cannot be resolved: {host}") from exc
    addresses = []
    for record in records:
        address = record[4][0]
        try:
            addresses.append(ipaddress.ip_address(address))
        except ValueError:
            raise ValidationError(f"Resolved address is invalid: {address}")
    if not addresses:
        raise ValidationError(f"URL host cannot be resolved: {host}")
    return addresses


def assert_public_ip(ip: ipaddress._BaseAddress) -> None:
    if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_reserved:
        raise ValidationError("Private or reserved IP URLs are not allowed")


def strip_instruction_like_text(content: str) -> str:
    blocked = [
        "ignore previous instructions",
        "忽略之前的指令",
        "忽略以上指令",
        "system prompt",
        "developer message",
    ]
    lines = []
    for line in content.splitlines():
        lowered = line.lower()
        if any(token in lowered for token in blocked):
            continue
        lines.append(line)
    return "\n".join(lines)
