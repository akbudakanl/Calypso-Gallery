#!/usr/bin/env bash
# ==============================================================================
# Fossify Gallery Cipher - Linux Quick Launcher
# ==============================================================================

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

# 1. Detect Desktop directory
if command -v xdg-user-dir >/dev/null 2>&1; then
    DESKTOP_DIR="$(xdg-user-dir DESKTOP)"
elif [ -d "$HOME/Desktop" ]; then
    DESKTOP_DIR="$HOME/Desktop"
elif [ -d "$HOME/Masaüstü" ]; then
    DESKTOP_DIR="$HOME/Masaüstü"
else
    DESKTOP_DIR="$HOME/Desktop"
fi

# 2. Check Python dependencies (cryptography, argon2, pillow) across apt / dnf / pacman / zypper
python3 -c "import cryptography, argon2, PIL" 2>/dev/null
if [ $? -ne 0 ]; then
    echo "=========================================================="
    echo "  [WARNING] Required Python libraries are missing!"
    echo "=========================================================="
    PKG_CMD=""
    if command -v apt-get >/dev/null 2>&1; then
        PKG_CMD="sudo apt update && sudo apt install -y python3-cryptography python3-argon2 python3-pil"
    elif command -v dnf >/dev/null 2>&1; then
        PKG_CMD="sudo dnf install -y python3-cryptography python3-argon2-cffi python3-pillow"
    elif command -v pacman >/dev/null 2>&1; then
        PKG_CMD="sudo pacman -S --needed python-cryptography python-argon2-cffi python-pillow"
    elif command -v zypper >/dev/null 2>&1; then
        PKG_CMD="sudo zypper install -y python3-cryptography python3-argon2-cffi python3-Pillow"
    fi

    if [ -n "$PKG_CMD" ]; then
        echo "Detected package manager command:"
        echo "  $PKG_CMD"
        echo ""
        read -rp "Install missing dependencies now? [Y/n]: " DO_INSTALL
        if [[ "$DO_INSTALL" =~ ^([yY]|[yY][eE][sS]|[eE]|[eE][vV][eE][tT]|)$ ]]; then
            eval "$PKG_CMD" || exit 1
        else
            echo "Cannot proceed without required cryptography libraries."
            exit 1
        fi
    else
        echo "Unsupported package manager. Please install: cryptography, argon2-cffi, pillow"
        exit 1
    fi
fi

# 3. Create double-clickable Desktop shortcut icon
DESKTOP_FILE="$DESKTOP_DIR/Fossify-Cipher.desktop"
if [ ! -f "$DESKTOP_FILE" ] && [ -d "$DESKTOP_DIR" ]; then
    cat <<EOF > "$DESKTOP_FILE"
[Desktop Entry]
Version=1.0
Type=Application
Name=Fossify Cipher Vault
Comment=Offline Vault Encryptor for Android Fossify Gallery Cipher
Exec=sh -c 'cd "$DIR" && ./run.sh; echo; read -p "Press Enter to exit..."'
Icon=system-lock-screen
Terminal=true
Categories=Utility;Security;
EOF
    chmod +x "$DESKTOP_FILE" 2>/dev/null || true
    gio set "$DESKTOP_FILE" metadata::trusted true 2>/dev/null || true
fi

# 4. Warn if non-zram disk swap is active (e.g., standard Live ISO that mounted host swap)
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

# 5. Per-process Network & Disk Sandbox Wrapper (does not affect other Python processes)
NEED_ADB=0
for arg in "$@"; do
    case "$arg" in
        --adb)
            NEED_ADB=1
            ;;
    esac
done

if [ "$NEED_ADB" -eq 1 ]; then
    echo "[*] --adb specified: running without network namespace isolation."
    exec python3 "$DIR/cipher_vault.py" "$@"
elif command -v bwrap >/dev/null 2>&1; then
    mkdir -p "$DESKTOP_DIR" 2>/dev/null || true
    BWRAP_ARGS=(
        --unshare-net
        --unshare-ipc
        --unshare-pid
        --die-with-parent
        --proc /proc
        --dev /dev
        --tmpfs /tmp
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

    echo "[Sandbox] Network OFF + Restricted Disk (bwrap)"
    exec bwrap "${BWRAP_ARGS[@]}" python3 "$DIR/cipher_vault.py" "$@"
elif command -v firejail >/dev/null 2>&1; then
    echo "[Sandbox] Network OFF + Restricted Filesystem (firejail)"
    exec firejail --quiet --net=none --private-tmp python3 "$DIR/cipher_vault.py" "$@"
elif command -v unshare >/dev/null 2>&1 && unshare --user --net true 2>/dev/null; then
    echo "[Sandbox] Network OFF (Linux kernel unshare --user --net)"
    exec unshare --user --net python3 "$DIR/cipher_vault.py" "$@"
else
    echo "[!] Sandbox unavailable on this kernel; running Python directly."
    exec python3 "$DIR/cipher_vault.py" "$@"
fi
