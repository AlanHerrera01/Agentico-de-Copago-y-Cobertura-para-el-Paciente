# Estimador Agéntico de Copago y Cobertura para el Paciente

Backend profesional para hackathon de seguros médicos. Listo para Render y conexión Next.js (Vercel).

## Tecnologías
- Java 21
- Spring Boot 3
- Maven
- Spring Web, WebFlux, Validation
- Lombok
- Notion API (fuente de datos)
- OpenAI API (interpretación de síntomas)
- CORS listo para Vercel y localhost

## Estructura
```
src/main/java/com/hackathon/copayagent/
  config/
  controller/
  service/
  client/
  dto/
  model/
  exception/
  util/
```

## Variables de entorno
Ver `.env.example` y configura en Render:
```
OPENAI_API_KEY=sk-...
OPENAI_BASE_URL=https://api.openai.com
OPENAI_MODEL=gpt-4o-mini
AI_PROVIDER=openai
NOTION_API_KEY=secret_...
NOTION_DATABASE_ID_POLICIES=...
NOTION_DATABASE_ID_HOSPITALS=...
```

Ejemplo usando GitHub Models (token de GitHub):
```
OPENAI_API_KEY=github_pat_xxx
OPENAI_BASE_URL=https://models.inference.ai.azure.com
OPENAI_MODEL=openai/gpt-4o-mini
AI_PROVIDER=openai
```

## application.yml
```
server:
  port: ${PORT:8080}
```

## Deploy en Render
1. Sube el repo a GitHub.
2. Conecta en Render como servicio web (Java Maven).
3. Agrega variables de entorno.
4. Render detecta el Procfile automáticamente.

## Ejemplo de request desde Next.js
```js
fetch('https://tu-backend.onrender.com/api/chat', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    clientId: '123',
    message: 'Tengo dolor fuerte en el pecho'
  })
})
  .then(res => res.json())
  .then(data => console.log(data));
```

## Ejemplo JSON para Postman
```
POST /api/chat
{
  "clientId": "123",
  "message": "Tengo dolor fuerte en el pecho"
}
```

## Respuesta esperada
```
{
  "specialty": "Cardiología",
  "priority": "Alta",
  "coveragePercentage": 80,
  "estimatedCopay": 25,
  "recommendedHospital": "Hospital Metropolitano",
  "message": "Tu póliza cubre consultas cardiológicas."
}
```

## CI/CD
- Render hace build automático con cada push a main.
- Procfile y variables de entorno listos para producción.

## Notas
- Toda la lógica de negocio está desacoplada y comentada.
- Puedes mapear la respuesta de Notion según tu base.
- Listo para hackathon y demo profesional.
