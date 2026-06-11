#!/bin/bash

# --- Shared firewall and utility functions for Gimlee local scripts ---

# Run a privileged command, using sudo only when not already root.
as_root() {
    if [ "$(id -u)" -eq 0 ]; then
        "$@"
    elif command -v sudo &> /dev/null; then
        sudo "$@"
    else
        echo "❌ Error: need root privileges to change the firewall, but 'sudo' is not available."
        return 1
    fi
}

# Detect the active firewall backend: ufw, firewalld, iptables, or none.
detect_firewall() {
    if command -v ufw &> /dev/null && as_root ufw status 2>/dev/null | grep -qi "Status: active"; then
        echo "ufw"
    elif command -v firewall-cmd &> /dev/null && as_root firewall-cmd --state 2>/dev/null | grep -qi "running"; then
        echo "firewalld"
    elif command -v iptables &> /dev/null; then
        echo "iptables"
    else
        echo "none"
    fi
}

# Open a TCP port in the detected firewall.
expose_port() {
    local port="$1"
    local label="$2"
    local backend
    backend="$(detect_firewall)"
    echo "🌐 Opening firewall for TCP port $port (backend: $backend, label: $label)..."
    case "$backend" in
        ufw)
            as_root ufw allow "$port/tcp" comment "$label"
            ;;
        firewalld)
            as_root firewall-cmd --permanent --add-port="$port/tcp" >/dev/null
            as_root firewall-cmd --reload >/dev/null
            ;;
        iptables)
            # Insert only if an identical ACCEPT rule does not already exist.
            if ! as_root iptables -C INPUT -p tcp --dport "$port" -j ACCEPT 2>/dev/null; then
                as_root iptables -I INPUT -p tcp --dport "$port" -j ACCEPT
            fi
            echo "   ⚠️  iptables rules are not persisted across reboots."
            ;;
        none)
            echo "   ℹ️  No active firewall detected (ufw/firewalld/iptables). The port is"
            echo "      most likely already reachable on your LAN. Nothing to do."
            return
            ;;
    esac
    echo "✅ TCP port $port is now open."
}

# Remove a TCP port rule from the detected firewall.
unexpose_port() {
    local port="$1"
    local backend
    backend="$(detect_firewall)"
    echo "🌐 Closing firewall for TCP port $port (backend: $backend)..."
    case "$backend" in
        ufw)
            as_root ufw delete allow "$port/tcp" || true
            ;;
        firewalld)
            as_root firewall-cmd --permanent --remove-port="$port/tcp" >/dev/null || true
            as_root firewall-cmd --reload >/dev/null || true
            ;;
        iptables)
            as_root iptables -D INPUT -p tcp --dport "$port" -j ACCEPT 2>/dev/null || true
            ;;
        none)
            echo "   ℹ️  No active firewall detected. Nothing to do."
            return
            ;;
    esac
    echo "✅ Firewall rule for TCP port $port removed."
}

# Treat common truthy values as "yes".
is_truthy() {
    case "${1,,}" in
        1|true|yes|y|on) return 0 ;;
        *) return 1 ;;
    esac
}
