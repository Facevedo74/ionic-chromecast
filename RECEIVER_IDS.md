# Google Cast Receiver Application IDs

## ❌ Problema con CC1AD845 (Default Media Receiver)

El ID `CC1AD845` es el **Default Media Receiver** de Google, pero tiene limitaciones:
- Solo reproduce formatos básicos
- No siempre funciona de forma confiable para pruebas
- Puede mostrar el logo de Cast pero fallar al cargar el video
- Tiene menos características de depuración

## ✅ Solución: C0868879 (Styled Media Receiver)

El ID correcto para **pruebas y desarrollo** es: **`C0868879`**

### Ventajas del Styled Media Receiver:
- Más robusto y confiable
- Mejor manejo de diferentes formatos de video
- Soporte completo para metadatos (título, subtítulo, imágenes)
- Interfaz visual mejorada en el TV
- Recomendado por Google para desarrollo y pruebas

## Receiver IDs de Google

| Receiver ID | Nombre | Uso Recomendado |
|-------------|--------|-----------------|
| `C0868879` | **Styled Media Receiver** | ✅ **Desarrollo y pruebas** (RECOMENDADO) |
| `CC1AD845` | Default Media Receiver | ⚠️ Solo para casos básicos |
| `[Custom]` | Custom Receiver | 🏢 Producción con app personalizada |

## Código Actualizado

```javascript
const result = await IonicChromecast.initialize({
    receiverApplicationId: 'C0868879' // Styled Media Receiver (recommended for testing)
});
```

## Qué Esperar Ahora

Con el Styled Media Receiver (`C0868879`):

1. **El diálogo de dispositivos se muestra** correctamente
2. **Seleccionas tu Chromecast**
3. **El logo de Cast aparece** en el TV
4. **La barra de carga completa** su progreso
5. **El video se reproduce** con la interfaz visual del Styled Media Receiver
6. **Se muestran los metadatos**: título, subtítulo, imagen del video

## Referencias

- [Google Cast Developer Console](https://cast.google.com/publish)
- [Cast SDK Documentation](https://developers.google.com/cast/docs/developers)
- Styled Media Receiver: `C0868879` (público, sin registro necesario)

## Nota para Producción

Para producción, deberás:
1. Crear tu propio Custom Receiver en el [Google Cast Developer Console](https://cast.google.com/publish)
2. Obtener tu propio Application ID único
3. Personalizar la interfaz y funcionalidad según tus necesidades
