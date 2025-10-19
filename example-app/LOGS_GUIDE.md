# 📱 Guía para Ver Logs de ionic-chromecast

## 🚀 Opción 1: Script de Logs (Más Fácil)

```bash
cd /Users/fabian/Documents/GitHub/CapacitorChromeCast/ionic-chromecast/example-app
./view-logs.sh
```

## 📋 Opción 2: Comandos Manuales con ADB

### Ver logs en tiempo real (filtrados):
```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
adb logcat | grep -E "IonicChromecast|CastContext|CastOptions"
```

### Ver TODOS los logs de errores:
```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
adb logcat *:E
```

### Ver logs de tu app específicamente:
```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
adb logcat | grep "com.example.plugin"
```

### Limpiar logs y empezar de nuevo:
```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
adb logcat -c
adb logcat
```

## 🔍 Opción 3: Android Studio (Recomendado para debugging)

1. Abre Android Studio:
   ```bash
   cd /Users/fabian/Documents/GitHub/CapacitorChromeCast/ionic-chromecast/example-app
   npx cap open android
   ```

2. En Android Studio:
   - Ve a la pestaña **Logcat** (parte inferior)
   - En el filtro, escribe: `IonicChromecast` o `Cast`
   - También puedes filtrar por nivel: Solo `Error`, `Warning`, etc.

3. Ejecuta la app desde Android Studio con el botón ▶️ (Run)

## 🐛 Comandos de Debugging

### Ver dispositivos conectados:
```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
adb devices
```

### Reinstalar la app:
```bash
cd /Users/fabian/Documents/GitHub/CapacitorChromeCast/ionic-chromecast/example-app
npx cap sync android
npx cap run android
```

## 📝 Buscar Errores Específicos

### Ver errores del Cast SDK:
```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
adb logcat | grep -i "cast.*error"
```

### Ver stack traces completos:
```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
adb logcat *:E
```

## 💡 Tips

- **Ctrl + C** para detener los logs en terminal
- Si ves muchos logs irrelevantes, usa más filtros específicos
- Los logs de Capacitor plugins aparecen con el tag del plugin
- Los errores de Cast SDK suelen tener tags como `CastContext`, `SessionManager`, etc.

## 🆘 Si el error persiste

1. Limpia el build:
   ```bash
   cd android
   ./gradlew clean
   cd ..
   npx cap sync android
   ```

2. Verifica que el dispositivo y Chromecast están en la misma WiFi

3. Chequea que Google Play Services está actualizado en el dispositivo
