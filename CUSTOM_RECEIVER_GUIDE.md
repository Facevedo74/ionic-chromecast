# Solución Definitiva: Custom Cast Receiver Application

## Problema

Los receiver IDs públicos de Google tienen limitaciones:
- `CC1AD845` (Default Media Receiver): Solo muestra logo de Cast, no reproduce videos personalizados
- `C0868879` (Styled Media Receiver): Reproduce un video demo por defecto, no el que enviamos
- Otros IDs públicos: Limitados o descontinuados

## Solución: Registrar Tu Propio Receiver (GRATIS)

### Paso 1: Registrarte en Google Cast SDK Developer Console

1. Ve a: https://cast.google.com/publish/
2. Inicia sesión con tu cuenta de Google
3. Acepta los términos de servicio ($5 USD - pago único de por vida)

### Paso 2: Crear una Custom Receiver Application

1. Click en "Add New Application"
2. Selecciona **"Custom Receiver"**
3. Configuración:
   - **Name**: "My Test Cast App" (o el nombre que quieras)
   - **Custom Receiver URL**: `https://www.gstatic.com/cast/sdk/libs/caf_receiver/v3/cast_receiver.html`
   - **Category**: Media
   - **Enable Guest Mode**: Yes (opcional)

4. Click "Save"
5. **Copia tu Application ID** (formato: `XXXXXXXX`)

### Paso 3: Agregar Dispositivos de Prueba (IMPORTANTE)

1. En el dashboard, encuentra tu Chromecast device ID:
   - En tu TV con Chromecast, ve a Settings
   - Busca "Cast device ID" o similar
   - Es un código de 12 dígitos hexadecimal

2. En la consola de desarrollador:
   - Click en "Add New Device"
   - Pega el Device ID de tu Chromecast
   - Guarda

3. **Reinicia tu Chromecast** (desconecta y conecta el poder)

### Paso 4: Esperar Propagación (5-15 minutos)

Google necesita tiempo para propagar los cambios a tu dispositivo.

### Paso 5: Usar Tu Application ID

Cambia en tu código:
```javascript
const result = await IonicChromecast.initialize({
    receiverApplicationId: 'TU_APP_ID_AQUI' // El ID que copiaste
});
```

---

## Alternativa Temporal: Receiver ID de Prueba

Mientras registras tu receiver, puedes probar con estos IDs conocidos:

### Opción A: Demo Receiver
```javascript
receiverApplicationId: '4F8B3483' // Demo receiver - más permisivo
```

### Opción B: Media Receiver de Prueba
```javascript
receiverApplicationId: '91F61A6E' // Alternative test receiver
```

### Opción C: Usar el Custom Receiver Genérico
```javascript
receiverApplicationId: 'A12D4273' // Generic media receiver
```

---

## URLs de Receiver Personalizados

Si quieres máximo control, puedes hospedar tu propio HTML receiver:

### Receiver HTML Básico

```html
<!DOCTYPE html>
<html>
<head>
  <script src="//www.gstatic.com/cast/sdk/libs/caf_receiver/v3/cast_receiver_framework.js"></script>
</head>
<body>
  <script>
    const context = cast.framework.CastReceiverContext.getInstance();
    const playerManager = context.getPlayerManager();
    
    // Configuración para soportar múltiples formatos
    playerManager.setMessageInterceptor(
      cast.framework.messages.MessageType.LOAD,
      request => {
        console.log('Loading media:', request.media);
        return request;
      }
    );
    
    context.start();
  </script>
</body>
</html>
```

Sube este archivo a un hosting HTTPS (GitHub Pages, Netlify, etc.) y usa la URL en tu receiver application.

---

## Configuración Recomendada para Producción

```javascript
const RECEIVER_IDS = {
  development: 'CC1AD845', // Default para desarrollo rápido
  staging: 'TU_STAGING_ID', // Tu receiver de pruebas
  production: 'TU_PROD_ID'  // Tu receiver de producción
};

const receiverId = RECEIVER_IDS[environment];
```

---

## Solución Inmediata (Sin Registro)

Si NO quieres registrarte ahora, la única opción confiable es:

1. **Usar un video demo público conocido** que funcione con el Default Receiver
2. **O** encontrar alguien que te preste un receiver ID registrado para pruebas

### Videos que SÍ funcionan con CC1AD845:

```javascript
// Estos videos públicos de Google sí funcionan:
const WORKING_VIDEOS = {
  bunny: 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
  elephant: 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4',
  tears: 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4'
};
```

**Nota**: Algunos receivers requieren HTTP en lugar de HTTPS para ciertos videos.

---

## Receiver IDs Conocidos (Para Referencias)

| App ID | Nombre | Estado | Notas |
|--------|--------|--------|-------|
| CC1AD845 | Default Media Receiver | ✅ Activo | Solo videos específicos |
| C0868879 | Styled Media Receiver | ✅ Activo | UI mejorada, limitado |
| 4F8B3483 | Demo Receiver | ⚠️ Limitado | Puede funcionar |
| 91F61A6E | Test Receiver | ⚠️ Desconocido | Probar |
| A12D4273 | Generic Receiver | ⚠️ Desconocido | Probar |

---

## Decisión Recomendada

**Para una solución permanente y confiable:**
👉 Registra tu propio Custom Receiver ($5 USD, una sola vez, de por vida)

**Para prueba rápida ahora:**
👉 Prueba con receiver ID `4F8B3483` y la URL actual
👉 O cambia la URL del video a HTTP (sin S)
