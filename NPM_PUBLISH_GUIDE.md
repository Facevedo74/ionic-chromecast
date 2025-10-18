# 📦 Guía de Publicación en NPM - ionic-chromecast

## 🔐 Paso 1: Autenticación en npm

Primero, necesitas autenticarte en npm:

```bash
npm login
```

Te pedirá:
- **Username**: Tu nombre de usuario de npm
- **Password**: Tu contraseña
- **Email**: Tu email (público)
- **One-time password**: Si tienes 2FA habilitado

**Nota**: Si no tienes una cuenta en npm, créala en https://www.npmjs.com/signup

---

## ✅ Paso 2: Verificar antes de publicar

### 2.1 Compilar el proyecto
```bash
npm run build
```

### 2.2 Verificar el contenido del paquete
```bash
npm pack --dry-run
```

Esto mostrará qué archivos se incluirán en el paquete sin crear el archivo .tgz

### 2.3 Verificar la versión
```bash
npm version
```

---

## 📝 Paso 3: Actualizar información del paquete (Opcional)

### 3.1 Revisar package.json
Asegúrate de que estos campos estén correctos:
- ✅ `name`: "ionic-chromecast"
- ✅ `version`: "0.0.1" (primera publicación)
- ✅ `description`: Descripción clara
- ✅ `repository`: URL de GitHub
- ✅ `author`: Tu nombre
- ✅ `license`: "Apache-2.0"
- ✅ `keywords`: Para búsqueda en npm

### 3.2 Actualizar README.md
El README.md será la página principal en npm, asegúrate de que tenga:
- ✅ Descripción clara del plugin
- ✅ Instrucciones de instalación
- ✅ Ejemplos de uso
- ✅ Lista de métodos disponibles
- ✅ Requisitos y configuración

---

## 🚀 Paso 4: Publicar en npm

### Opción A: Publicación pública (recomendado para plugins open source)

```bash
npm publish --access public
```

### Opción B: Verificar antes de publicar (dry-run)

```bash
npm publish --dry-run
```

---

## 🔄 Paso 5: Verificar la publicación

Después de publicar, verifica en:
- https://www.npmjs.com/package/ionic-chromecast
- Instala en un proyecto de prueba:

```bash
npm install ionic-chromecast
```

---

## 📌 Checklist Pre-Publicación

- [ ] Estás autenticado en npm (`npm whoami`)
- [ ] El proyecto compila sin errores (`npm run build`)
- [ ] El README.md está actualizado
- [ ] La versión en package.json es correcta
- [ ] Los archivos innecesarios están en .npmignore
- [ ] Has probado el plugin en un dispositivo Android
- [ ] El repositorio de GitHub existe y es accesible
- [ ] Tienes los derechos para usar el nombre "ionic-chromecast"

---

## 🔄 Futuras Actualizaciones

Para publicar nuevas versiones:

```bash
# Actualizar versión patch (0.0.1 -> 0.0.2)
npm version patch

# Actualizar versión minor (0.0.1 -> 0.1.0)
npm version minor

# Actualizar versión major (0.0.1 -> 1.0.0)
npm version major

# Publicar la nueva versión
npm publish --access public
```

---

## ⚠️ Consideraciones Importantes

1. **Nombre del paquete**: "ionic-chromecast" debe estar disponible en npm. Si ya existe, tendrás que usar un nombre diferente (ej: "@tu-usuario/ionic-chromecast")

2. **Versión semántica**: 
   - `0.0.x` = En desarrollo, puede tener breaking changes
   - `0.x.0` = Beta, API estable pero puede cambiar
   - `1.0.0` = Primera versión estable

3. **Archivos publicados**: Solo se publican los archivos listados en `files` en package.json y los que no están en .npmignore

---

## 🎯 Comandos Rápidos

```bash
# 1. Login en npm
npm login

# 2. Compilar
npm run build

# 3. Verificar contenido
npm pack --dry-run

# 4. Publicar
npm publish --access public

# 5. Verificar
npm view ionic-chromecast
```

---

## 📞 Soporte Post-Publicación

Después de publicar, considera:
- ✅ Crear un tag en GitHub para la versión
- ✅ Actualizar CHANGELOG.md
- ✅ Anunciar en redes sociales / foros de Ionic
- ✅ Responder issues y preguntas en GitHub

---

¡Tu plugin está listo para compartirse con la comunidad! 🎉
