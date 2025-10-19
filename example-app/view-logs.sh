#!/bin/bash

# Script para ver logs del plugin ionic-chromecast

export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"

echo "📱 Dispositivos conectados:"
adb devices
echo ""

echo "🧹 Limpiando logs anteriores..."
adb logcat -c

echo ""
echo "📋 Mostrando logs en tiempo real (Ctrl+C para salir):"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Mostrar solo logs relevantes
adb logcat | grep -E "IonicChromecast|CastContext|CastOptions|CastSession|WebView|Capacitor|chromInfo"
