# Partes de Instalación Camacho

Aplicación Android nativa para la gestión digital de **partes de instalación de acometidas** de agua potable y alcantarillado. Reemplaza el registro en papel del trabajo de campo, permitiendo capturar datos, fotografías y firmas de cada predio, y generar de forma automática los documentos oficiales en PDF/PNG listos para entrega.

Desarrollada para equipos de campo y supervisión de obra, con control de roles, sincronización con backend en la nube y soporte de trabajo sin conexión.

---

## Tabla de contenidos

- [Características](#características)
- [Stack tecnológico](#stack-tecnológico)
- [Arquitectura](#arquitectura)
- [Estructura del proyecto](#estructura-del-proyecto)
- [Modelo de datos](#modelo-de-datos)
- [Generación de documentos PDF/PNG](#generación-de-documentos-pdfpng)
- [Trabajo sin conexión y sincronización](#trabajo-sin-conexión-y-sincronización)
- [Roles de usuario](#roles-de-usuario)
- [Puesta en marcha](#puesta-en-marcha)
- [Configuración de Supabase](#configuración-de-supabase)
- [Compilación y ejecución](#compilación-y-ejecución)

---

## Características

- **Registro de predios**: formulario completo con datos de contrato, dirección, propietario y observaciones.
- **Captura fotográfica guiada**: fotos del predio, la acometida y el medidor/alcantarillado, con recorte (uCrop) y filtro de escaneo basado en OpenCV para mejorar la legibilidad de documentos.
- **Generación de documentos oficiales**: PDF y PNG individuales o por lote, a partir de plantillas oficiales con coordenadas calibradas (AcroForm).
- **Exportación masiva**: selección múltiple de predios, generación de PDF combinado y exportación de imágenes en lote (ZIP).
- **Gestión de proyectos y usuarios**: administración de proyectos (agua potable / alcantarillado), asignación de personal y control de accesos por rol.
- **Modo sin conexión**: caché local con Room y sincronización automática en segundo plano mediante WorkManager cuando se recupera la conectividad.
- **Búsqueda y filtrado** en tiempo real de predios por usuario, contrato o código.

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| UI | Jetpack Compose + Material 3 + Navigation Compose |
| Arquitectura | MVVM + Repository Pattern |
| Backend | [Supabase](https://supabase.com/) (PostgreSQL + REST + RPC + Storage) |
| Red | Retrofit 2 + OkHttp (interceptores de autenticación) |
| Persistencia local | Room (caché offline) + DataStore Preferences |
| Concurrencia | Kotlin Coroutines + StateFlow |
| Tareas en segundo plano | WorkManager (sincronización periódica) |
| Generación de PDF | PDFBox Android |
| Imágenes | Coil, uCrop, OpenCV (filtro de escaneo) |

**Requisitos:** Android `minSdk 26` / `targetSdk 36`, Kotlin + Java 11.

## Arquitectura

El proyecto sigue **MVVM** con una clara separación por capas:

```
ui/        → Pantallas Compose + ViewModels (estado expuesto vía StateFlow)
data/
  model/      → DTOs de red (request/response)
  remote/     → Clientes Retrofit hacia Supabase (REST + RPC + Storage)
  local/      → Room (entidades + DAOs) para caché offline
  repository/ → Orquestan red, caché local y mapeo de modelos
core/
  navigation/ → Grafo de navegación, rutas y sesiones globales
  sync/       → SyncManager, ConnectivityObserver, estado de sincronización
  image/      → Procesamiento de imágenes (filtro de escaneo con OpenCV)
  utils/      → Generación de PDF/PNG, calibración de coordenadas
  theme/      → Tema y paleta de colores de la app
workers/    → Workers de WorkManager (sincronización en segundo plano)
```

**Flujo de datos:** las pantallas observan el estado expuesto por su `ViewModel`, que delega en un `Repository`. Cada repositorio decide si la información se obtiene de Supabase (online) o de la caché local en Room (offline), y mantiene ambas fuentes sincronizadas mediante `SyncManager`.

## Estructura del proyecto

```
acometidas-app/
└── mobile/                # Aplicación Android (módulo principal)
    └── app/src/main/
        ├── java/com/jucha/acometidasapp/
        │   ├── core/      # Navegación, sincronización, utilidades, tema
        │   ├── data/      # DTOs, API remota, Room local, repositorios
        │   ├── domain/    # Modelos y casos de uso de dominio
        │   ├── ui/        # Pantallas y ViewModels por funcionalidad
        │   └── workers/   # Tareas en segundo plano (WorkManager)
        └── assets/        # Plantillas PDF oficiales
```

## Modelo de datos

La aplicación se apoya en una base de datos PostgreSQL gestionada por Supabase:

| Tabla | Descripción |
|---|---|
| `usuarios` | Cuentas de usuario, rol (`admin` / `encargado`) y credenciales (hash con pgcrypto) |
| `proyectos` | Proyectos de obra, con tipo (`agua_potable` / `alcantarillado`) |
| `proyecto_usuario` | Relación N:M entre usuarios y proyectos asignados |
| `predios` | Datos del predio: contrato, código, dirección, propietario, estado, observaciones |
| `fotos` | Fotografías asociadas a cada predio (predio, acometida, medidor/alcantarillado) |

Las imágenes se almacenan en un bucket de **Supabase Storage**, referenciadas por URL pública desde la tabla `fotos`.

## Generación de documentos PDF/PNG

`PdfGeneratorService` rellena las plantillas oficiales (`plantilla_acometida.pdf` y `plantilla_acometida_alcantarillado.pdf`) ubicadas en `assets/`, usando coordenadas calibradas (`PdfCoords`) para posicionar texto y fotografías sobre el formato oficial.

Modos de exportación disponibles:

- **PDF individual** — un parte por predio.
- **PDF por lote** — combina varios partes en un solo documento.
- **PNG individual / por lote** — renderizado en alta resolución (450 DPI), exportado como imagen o como ZIP.
- **Listado tabular en PDF** — resumen de predios filtrable por zona.

## Trabajo sin conexión y sincronización

- Los datos de predios, proyectos y fotos se cachean localmente con **Room** para permitir el trabajo en campo sin conexión a internet.
- `ConnectivityObserver` detecta cambios en la conectividad de red.
- `SyncManager` programa un `Worker` periódico (`SyncPrediosWorker`) que sincroniza los cambios pendientes con Supabase en cuanto hay conexión disponible, además de permitir una sincronización inmediata.

## Roles de usuario

| Rol | Permisos |
|---|---|
| **Administrador** | Acceso total: visualiza todos los proyectos, crea/elimina predios y proyectos, exporta documentos, gestiona usuarios y asignaciones |
| **Encargado** | Acceso restringido a sus proyectos asignados; puede crear y editar predios, sin acceso a exportación ni gestión de usuarios |

## Puesta en marcha

### Requisitos previos

- [Android Studio](https://developer.android.com/studio) (Ladybug o superior recomendado)
- JDK 11
- Una instancia de [Supabase](https://supabase.com/) con el esquema de base de datos correspondiente

### Configuración de Supabase

1. Crea un proyecto en Supabase con las tablas descritas en [Modelo de datos](#modelo-de-datos).
2. Crea un bucket de Storage llamado `acometidas` para almacenar las fotografías.
3. Configura las funciones RPC `login` y `crear_usuario` (hash de contraseñas con `pgcrypto`).
4. En `mobile/local.properties` (no versionado), agrega tus credenciales:

```properties
SUPABASE_URL=https://<tu-proyecto>.supabase.co
SUPABASE_KEY=<tu-api-key>
```

Estas claves se inyectan automáticamente en `BuildConfig` durante la compilación.

### SDK de OpenCV

El módulo `:openCV` (usado para el filtro de escaneo de fotos) requiere las librerías nativas del **OpenCV Android SDK**, que no se incluyen en el repositorio por su tamaño:

1. Descarga el [OpenCV Android SDK](https://opencv.org/releases/) (versión 4.x).
2. Copia la carpeta `native/` del SDK descargado dentro de `mobile/openCV/native/`.

Sin este paso, el módulo `:openCV` no compilará.

## Compilación y ejecución

```bash
cd mobile

# Compilar el proyecto
./gradlew assembleDebug

# Instalar en un dispositivo/emulador conectado
./gradlew installDebug
```

También puedes abrir la carpeta `mobile/` directamente en Android Studio y ejecutar la app desde ahí.

---

## Licencia

Proyecto de uso privado/interno. Todos los derechos reservados.
