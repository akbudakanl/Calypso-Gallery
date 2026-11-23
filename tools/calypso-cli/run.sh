#!/usr/bin/env bash
# ==============================================================================
# Calypso Gallery - Linux & macOS Quick Launcher
# ==============================================================================

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

OS_NAME="$(uname -s)"

# 0. Select Python interpreter (prefer local .venv if present)
PYTHON_BIN=""
if [ -x "$DIR/.venv/bin/python3" ]; then
    PYTHON_BIN="$DIR/.venv/bin/python3"
elif command -v python3 >/dev/null 2>&1; then
    PYTHON_BIN="$(command -v python3)"
elif [ "$OS_NAME" = "Darwin" ]; then
    for cand in /opt/homebrew/bin/python3 /usr/local/bin/python3; do
        if [ -x "$cand" ]; then
            PYTHON_BIN="$cand"
            break
        fi
    done
fi

if [ -z "$PYTHON_BIN" ]; then
    echo "=========================================================="
    echo "  [ERROR] Python 3 is not installed!"
    echo "=========================================================="
    if [ "$OS_NAME" = "Darwin" ]; then
        echo "Install Python 3 via Homebrew ('brew install python') or https://www.python.org/downloads/"
    else
        echo "Please install python3 using your Linux package manager."
    fi
    exit 1
fi

# Tink supports Python 3.10-3.13. Reject 3.14+ early with a clear message.
"$PYTHON_BIN" -c "import sys; sys.exit(0 if (3, 10) <= sys.version_info[:2] <= (3, 13) else 1)" 2>/dev/null
if [ $? -ne 0 ]; then
    echo "=========================================================="
    echo "  [ERROR] Unsupported Python version: $("$PYTHON_BIN" --version 2>&1)"
    echo "  Tink requires Python 3.10-3.13. Install a supported"
    echo "  interpreter (e.g. python3.12) and re-run this launcher."
    echo "=========================================================="
    exit 1
fi

# 1. Detect Desktop directory
if [ "$OS_NAME" = "Darwin" ]; then
    DESKTOP_DIR="$HOME/Desktop"
elif command -v xdg-user-dir >/dev/null 2>&1; then
    DESKTOP_DIR="$(xdg-user-dir DESKTOP)"
elif [ -d "$HOME/Desktop" ]; then
    DESKTOP_DIR="$HOME/Desktop"
elif [ -d "$HOME/Masaüstü" ]; then
    DESKTOP_DIR="$HOME/Masaüstü"
else
    DESKTOP_DIR="$HOME/Desktop"
fi

# 2. Check Python dependencies (cryptography, argon2, pillow, tink)
"$PYTHON_BIN" -c "import cryptography, argon2, PIL, tink" 2>/dev/null
if [ $? -ne 0 ]; then
    echo "=========================================================="
    echo "  [WARNING] Required Python libraries are missing!"
    echo "=========================================================="
    PKG_CMD=""
    if [ "$OS_NAME" = "Linux" ]; then
        if command -v apt-get >/dev/null 2>&1; then
            PKG_CMD="sudo apt update && sudo apt install -y python3-cryptography python3-argon2 python3-pil"
        elif command -v dnf >/dev/null 2>&1; then
            PKG_CMD="sudo dnf install -y python3-cryptography python3-argon2-cffi python3-pillow"
        elif command -v pacman >/dev/null 2>&1; then
            PKG_CMD="sudo pacman -S --needed python-cryptography python-argon2-cffi python-pillow"
        elif command -v zypper >/dev/null 2>&1; then
            PKG_CMD="sudo zypper install -y python3-cryptography python3-argon2-cffi python3-Pillow"
        fi
    fi

    if [ -n "$PKG_CMD" ]; then
        echo "Detected package manager command:"
        echo "  $PKG_CMD"
        echo ""
        read -rp "Install missing dependencies now? [Y/n]: " DO_INSTALL
        if [[ "$DO_INSTALL" =~ ^([yY]|[yY][eE][sS]|)$ ]]; then
            eval "$PKG_CMD" || exit 1
        else
            echo "Cannot proceed without required cryptography libraries."
            exit 1
        fi
    else
        echo "Will create a local virtual environment (.venv) and install dependencies from requirements.txt:"
        echo "  \"$PYTHON_BIN\" -m venv \"$DIR/.venv\" && \"$DIR/.venv/bin/pip\" install -r \"$DIR/requirements.txt\""
        echo ""
        read -rp "Create .venv and install dependencies now? [Y/n]: " DO_VENV
        if [[ "$DO_VENV" =~ ^([yY]|[yY][eE][sS]|)$ ]]; then
            "$PYTHON_BIN" -m venv "$DIR/.venv" || exit 1
            "$DIR/.venv/bin/pip" install -r "$DIR/requirements.txt" || exit 1
            PYTHON_BIN="$DIR/.venv/bin/python3"
        else
            echo "Cannot proceed without required cryptography libraries."
            exit 1
        fi
    fi
fi

# 2b. Prefer bubblewrap for maximum isolation; try to install it if missing.
# Tails runs as the unprivileged "amnesia" user, so the install may fail there —
# that is expected and simply leaves the firejail/unshare fallbacks in place.
if [ "$OS_NAME" = "Linux" ] && ! command -v bwrap >/dev/null 2>&1; then
    BWRAP_PKG_CMD=""
    if command -v apt-get >/dev/null 2>&1; then
        BWRAP_PKG_CMD="sudo apt-get install -y bubblewrap"
    elif command -v dnf >/dev/null 2>&1; then
        BWRAP_PKG_CMD="sudo dnf install -y bubblewrap"
    elif command -v pacman >/dev/null 2>&1; then
        BWRAP_PKG_CMD="sudo pacman -S --needed bubblewrap"
    elif command -v zypper >/dev/null 2>&1; then
        BWRAP_PKG_CMD="sudo zypper install -y bubblewrap"
    fi

    if [ -n "$BWRAP_PKG_CMD" ]; then
        echo "=========================================================="
        echo "  [i] bubblewrap (bwrap) enables the strongest sandbox."
        echo "=========================================================="
        echo "Install with: $BWRAP_PKG_CMD"
        read -rp "Try installing bubblewrap now? [Y/n]: " DO_BWRAP
        if [[ "$DO_BWRAP" =~ ^([yY]|[yY][eE][sS]|)$ ]]; then
            if ! eval "$BWRAP_PKG_CMD" >/dev/null 2>&1; then
                echo "[!] Could not install bubblewrap; will use an available fallback sandbox."
            fi
        fi
    fi
fi

# 3. macOS fast path (no .desktop shortcut, /proc/swaps, or Linux bwrap namespaces)
if [ "$OS_NAME" = "Darwin" ]; then
    echo "[Sandbox] Python audit hook active (OS-level bwrap sandbox is Linux-only)."
    exec "$PYTHON_BIN" "$DIR/calypso_vault.py" "$@"
fi

# 4. Create double-clickable Desktop shortcut icon (Linux)
DESKTOP_FILE="$DESKTOP_DIR/Calypso-Gallery-Vault.desktop"
if [ ! -f "$DESKTOP_FILE" ] && [ -d "$DESKTOP_DIR" ]; then
    cat <<EOF > "$DESKTOP_FILE"
[Desktop Entry]
Version=1.0
Type=Application
Name=Calypso Gallery Vault
Comment=Offline Encrypted Vault Encryptor for Android Calypso Gallery
Exec=sh -c 'cd "$DIR" && ./run.sh; echo; read -p "Press Enter to exit..."'
Icon=system-lock-screen
Terminal=true
Categories=Utility;Security;
EOF
    chmod +x "$DESKTOP_FILE" 2>/dev/null || true
    gio set "$DESKTOP_FILE" metadata::trusted true 2>/dev/null || true
fi

# 5. Warn if non-zram disk swap is active (e.g., standard Live ISO that mounted host swap)
if [ -r /proc/swaps ]; then
    DISK_SWAPS=$(awk 'NR>1 && $1 !~ /^\/dev\/zram/ {print $1}' /proc/swaps)
    if [ -n "$DISK_SWAPS" ]; then
        echo "=========================================================="
        echo "  [WARNING] Active disk swap detected: $DISK_SWAPS"
        echo "  RAM data could be paged to disk. Run 'sudo swapoff -a'"
        echo "  or boot with 'noswap' for strict RAM-only operation."
        echo "=========================================================="
    fi
fi

# 6. Per-process Network & Disk Sandbox Wrapper (does not affect other Python processes)
if command -v bwrap >/dev/null 2>&1; then
    mkdir -p "$DESKTOP_DIR" 2>/dev/null || true
    BWRAP_ARGS=(
        --unshare-net
        --unshare-ipc
        --unshare-pid
        --die-with-parent
        --clearenv
        --setenv PATH /usr/bin:/bin:/usr/local/bin
        --setenv HOME "$HOME"
        --setenv LANG "${LANG:-C}"
        --setenv TMPDIR /tmp
        --proc /proc
        --dev /dev
        --tmpfs /tmp
        --seccomp 21
        --ro-bind "$DIR" "$DIR"
        --bind "$DESKTOP_DIR" "$DESKTOP_DIR"
    )
    for sysdir in /usr /lib /lib64 /bin /sbin /etc /opt; do
        [ -e "$sysdir" ] && BWRAP_ARGS+=(--ro-bind "$sysdir" "$sysdir")
    done

    # Allow direct writing to mounted external vaults (/media, /mnt, /run/media, Persistent).
    for extdir in /media /mnt /run/media "$HOME/Persistent" "/home/amnesia/Persistent"; do
        [ -e "$extdir" ] && BWRAP_ARGS+=(--bind "$extdir" "$extdir")
    done

    # Bind any custom -i / -o / -v / -k paths passed on CLI so custom paths still work
    _bind_cli_path() {
        local kind="$1"
        local val="$2"
        local abs_p
        case "$kind" in
            vault)
                abs_p="$(readlink -f "$val" 2>/dev/null || echo "$val")"
                if [ -f "$abs_p" ]; then
                    BWRAP_ARGS+=(--ro-bind "$abs_p" "$abs_p")
                elif [ -d "$abs_p" ]; then
                    BWRAP_ARGS+=(--bind "$abs_p" "$abs_p")
                fi
                ;;
            input)
                abs_p="$(readlink -f "$val" 2>/dev/null || echo "$val")"
                [ -e "$abs_p" ] && BWRAP_ARGS+=(--bind "$abs_p" "$abs_p")
                ;;
            output)
                mkdir -p "$val" 2>/dev/null || true
                abs_p="$(readlink -f "$val" 2>/dev/null || echo "$val")"
                [ -e "$abs_p" ] && BWRAP_ARGS+=(--bind "$abs_p" "$abs_p")
                ;;
        esac
    }

    prev=""
    for arg in "$@"; do
        case "$prev" in
            -v|-k|--vault)
                _bind_cli_path vault "$arg"
                prev=""
                continue
                ;;
            -i|--input)
                _bind_cli_path input "$arg"
                prev=""
                continue
                ;;
            -o|--output)
                _bind_cli_path output "$arg"
                prev=""
                continue
                ;;
        esac
        case "$arg" in
            --vault=*)
                _bind_cli_path vault "${arg#*=}"
                ;;
            --input=*)
                _bind_cli_path input "${arg#*=}"
                ;;
            --output=*)
                _bind_cli_path output "${arg#*=}"
                ;;
            -v|-k|--vault|-i|--input|-o|--output)
                prev="$arg"
                ;;
        esac
    done

# ---- Automatic phone bridge sync (Linux only) ---------------------------------
BRIDGE_TEMP=""
PHONE_CLI=""
if [ "$OS_NAME" = "Linux" ] && command -v gio >/dev/null 2>&1; then
    MTP_URI="$(gio mount -li 2>/dev/null | awk -F'activation_root=' '/activation_root=mtp:/ {print $2; exit}')"
    if [ -n "$MTP_URI" ]; then
        gio mount "$MTP_URI" >/dev/null 2>&1 || true
        for suffix in "Internal storage/Calypso Gallery/CLI" "Dahili depolama/Calypso Gallery/CLI"; do
            if gio list "$MTP_URI/$suffix" >/dev/null 2>&1; then
                PHONE_CLI="$MTP_URI/$suffix"
                break
            fi
        done
        if [ -n "$PHONE_CLI" ]; then
            BRIDGE_TEMP="$(mktemp -d "${TMPDIR:-/tmp}/calypso-cli-bridge.XXXXXX")"
            mkdir -p "$BRIDGE_TEMP/bridge"
            if gio copy -r "$PHONE_CLI" "$BRIDGE_TEMP/bridge/"; then
                export CALYPSO_BRIDGE_ROOT="$BRIDGE_TEMP/bridge/CLI"
                echo "[Phone] Bridge copied to temporary workspace."
            else
                rm -rf "$BRIDGE_TEMP"
                BRIDGE_TEMP=""
                echo "[Phone] Could not copy the bridge; run the manual steps in the user manual."
            fi
        else
            echo "[Phone] No MTP phone bridge found; continuing with local bridges."
        fi
    else
        echo "[Phone] No MTP device found; continuing with local bridges."
    fi
fi

CLI_RC=0
if command -v bwrap >/dev/null 2>&1; then
    echo "[Sandbox] Network OFF + Restricted Disk (bwrap)"
    bwrap "${BWRAP_ARGS[@]}" "$PYTHON_BIN" "$DIR/calypso_vault.py" "$@" || CLI_RC=$?
elif command -v firejail >/dev/null 2>&1; then
    echo "[Sandbox] Network OFF + Restricted Filesystem (firejail)"
    firejail --quiet --net=none --private-tmp "$PYTHON_BIN" "$DIR/calypso_vault.py" "$@" || CLI_RC=$?
elif command -v unshare >/dev/null 2>&1 && unshare --user --net true 2>/dev/null; then
    echo "[Sandbox] Network OFF (Linux kernel unshare --user --net)"
    unshare --user --net "$PYTHON_BIN" "$DIR/calypso_vault.py" "$@" || CLI_RC=$?
else
    echo "=========================================================="
    echo "  No filesystem sandbox available on this system."
    echo "=========================================================="
    echo "Running with the Python audit hook only - no OS-level filesystem or"
    echo "network sandbox. No sandbox could be started on this system."
    printf "Continue anyway? [y/N]: "
    read -r answer
    case "$answer" in
        y|Y) "$PYTHON_BIN" "$DIR/calypso_vault.py" "$@" || CLI_RC=$? ;;
        *) echo "Aborted."; CLI_RC=1 ;;
    esac
fi

# ---- Copy the processed bridge back to the phone -------------------------------
if [ -n "$BRIDGE_TEMP" ] && [ -n "$PHONE_CLI" ] && [ "$CLI_RC" -eq 0 ] && [ -n "$CALYPSO_BRIDGE_ROOT" ]; then
    for bridge_dir in "$CALYPSO_BRIDGE_ROOT"/*; do
        [ -d "$bridge_dir" ] || continue
        if ! gio copy -r "$bridge_dir" "$PHONE_CLI/"; then
            echo "[Phone] Could not copy a bridge back; copy it manually."
            CLI_RC=1
        fi
    done
    rm -rf "$BRIDGE_TEMP"
elif [ -n "$BRIDGE_TEMP" ]; then
    echo "[Phone] Temporary workspace kept at $BRIDGE_TEMP (copy failed or CLI error)."
fi

exit "$CLI_RC"
