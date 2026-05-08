# Render Deployment Guide

## Configuración de Variables de Entorno

Para desplegar en Render, necesitas configurar las siguientes variables de entorno en el dashboard de Render:

### Variables Obligatorias

1. **NOTION_TOKEN**
   - Tu token de API de Notion
   - Formato: `ntn_xxxxxxxxxxxxxxxxxxxx`

2. **NOTION_DB_PACIENTES**
   - ID de la base de datos de pacientes en Notion
   - Formato: `a84f25ec4a0882aab7e801932c90abb3`

3. **NOTION_DB_PLANES**
   - ID de la base de datos de planes en Notion
   - Formato: `6bdf25ec4a0883618159812e13c3ae54`

4. **NOTION_DB_HOSPITALES**
   - ID de la base de datos de hospitales en Notion
   - Formato: `462f25ec4a088273821501ba923457b6`

5. **NOTION_DB_ESPECIALIDADES**
   - ID de la base de datos de especialidades en Notion
   - Formato: `7bef25ec4a088399ba5d8165b9e1182b`

6. **GITHUB_MODELS_API_KEY**
   - Tu token de GitHub Models
   - Formato: `github_pat_xxxxxxxxxxxxxxxxxxxx`

### Variables Opcionales (ya configuradas)

- **AI_PROVIDER**: `github-models`
- **OPENAI_MODEL**: `gpt-4o-mini`
- **PORT**: `8081`
- **JAVA_VERSION**: `17`

## Pasos para Despliegue

1. **Conectar tu repositorio a Render**
   - Ve a Render Dashboard
   - Click "New +" → "Web Service"
   - Conecta tu repositorio de GitHub

2. **Configurar el Backend**
   - **Name**: `copayagent-api`
   - **Environment**: `Java`
   - **Build Command**: `./mvnw clean package -DskipTests`
   - **Start Command**: `java -jar -Dserver.port=$PORT target/copayagent-0.0.1-SNAPSHOT.jar`
   - **Health Check Path**: `/actuator/health`

3. **Configurar Variables de Entorno**
   - Agrega todas las variables listadas arriba
   - Asegúrate de marcarlas como "secret" cuando contengan tokens

4. **Desplegar el Frontend (opcional)**
   - Crea otro servicio para el frontend React
   - **Environment**: `Static`
   - **Build Command**: `cd Frontend && npm run build`
   - **Publish Directory**: `Frontend/dist`

## Solución de Problemas

### Error: "Did not observe any item or terminal signal within 5000ms"
Este error fue solucionado aumentando los timeouts:
- GitHub Models timeout: 15 segundos
- HTTP connection timeout: 10 segundos
- Response timeout: 30 segundos

### Error: "No open ports detected"
Render necesita que la aplicación escuche en el puerto `$PORT`. La configuración ya está ajustada para usar esta variable.

### Logs Útiles
- Health check: `GET /actuator/health`
- Debug logs: Habilitados en startup
- Error handling: Mejorado con fallbacks

## Endpoints Disponibles

- **API Health**: `https://tu-app.onrender.com/actuator/health`
- **Chat API**: `https://tu-app.onrender.com/api/chat`
- **Debug Info**: `https://tu-app.onrender.com/debug`

## Monitoreo

Render proporciona:
- Logs en tiempo real
- Métricas de rendimiento
- Health checks automáticos
- Alertas por email

La aplicación está configurada para ser robusta con fallbacks si GitHub Models no está disponible temporalmente.
