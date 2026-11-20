# SCRUM Sprint 4 - PRISMA B2B Marketplace & Modo Pro
**Período del Sprint:** Sprint Final de Consolidación y Analítica Avanzada
**Total de Actividades:** 12 historias / tareas (77 Story Points)
**Instancia Oficial de Jira:** [davidscastro29.atlassian.net](https://davidscastro29.atlassian.net/jira/software/projects/SCRUM/boards/1/backlog)

---

## 1. Índice de Actividades del Sprint 4

| Clave | Tipo | Puntos | Épica | Historia / Tarea | Estado Final | Responsable |
| :---: | :---: | :---: | :--- | :--- | :---: | :--- |
| **SCRUM-25** | Historia | 5.0 | Modo Pro – Inteligencia Territorial | **HU-15 – Ranking de productos por zona** | **Finalizada** | Juan Pablo Nieto Cortes |
| **SCRUM-26** | Historia | 8.0 | Modo Pro – Optimización Comercial | **HU-16 – Detección de brecha de penetración territorial** | **Finalizada** | Juan Pablo Nieto Cortes |
| **SCRUM-27** | Historia | 8.0 | Modo Pro – Optimización Comercial | **HU-17 – Recomendación comercial narrativa con IA** | **Finalizada** | Juan Pablo Nieto Cortes |
| **SCRUM-43** | Historia | 13.0 | Modo Pro – Optimización Comercial | **HU-30 – Elasticidad precio-demanda: prescripción de descuentos** | **Finalizada** | Juan Pablo Nieto Cortes |
| **SCRUM-44** | Historia | 8.0 | Modo Pro – Optimización Comercial | **HU-31 – Minería de canasta: formatos de empaque (FP-Growth)** | **Finalizada** | Juan Pablo Nieto Cortes |
| **SCRUM-65** | Tarea | 8.0 | Modo Pro – Inteligencia Territorial | **N-11 – Pantallas Modo Pro en el portal del proveedor** | **Finalizada** | Juan Pablo Nieto Cortes |
| **SCRUM-66** | Tarea | 5.0 | Modo Pro – Optimización Comercial | **N-12 – Capa narrativa de IA a costo $0 con respaldo en plantilla** | **Finalizada** | Juan Pablo Nieto Cortes |
| **SCRUM-67** | Tarea | 5.0 | Operación Logística y Cierre | **N-13 – Entrega final: documentación, informe de pruebas y demo** | **Finalizada** | Juan Pablo Nieto Cortes |
| **SCRUM-70** | Tarea | 3.0 | Operación Logística y Cierre | **N-16 – Elaboración del Documento de Arquitectura Front-End** | **Finalizada** | Juan Pablo Nieto Cortes |
| **SCRUM-71** | Historia | 5.0 | Arquitectura de Backend | **N-17 – Elaboración del Documento de Arquitectura General** | **Finalizada** | Juan Pablo Nieto Cortes |
| **SCRUM-72** | Tarea | 3.0 | Operación Logística y Cierre | **N-18 – Elaboración del Documento de Requerimientos Funcionales** | **Finalizada** | Juan Pablo Nieto Cortes |
| **SCRUM-73** | Tarea | 2.0 | Operación Logística y Cierre | **N-19 – Elaboración del Manual de Identidad Visual y Guía UI** | **Finalizada** | Juan Pablo Nieto Cortes |

---

## 2. Detalle de Historias de Usuario y Criterios de Aceptación (Gherkin)

### 01. [SCRUM-25] HU-15 – Ranking de productos por zona
- **Narrativa:** Como proveedor, quiero ver el ranking de mis productos por volumen de ventas en cada zona para identificar mis referencias más fuertes y las zonas de mayor oportunidad.
- **Criterio 1 (Ranking generado):** Dado que tengo pedidos entregados en los últimos 30 días y accedo a `/modo-pro/ranking`, el sistema muestra el ranking de productos ordenado por unidades colocadas (200).
- **Criterio 2 (Sin datos suficientes):** Dado que no tengo pedidos entregados registrados en el periodo, el sistema muestra el mensaje *"Aún no hay suficientes datos para generar este reporte"*.
- **Evidencia Técnica:** Endpoint `GET /api/v1/proveedores/modo-pro/ranking` en Spring Boot y vista `RankingZonaPage.jsx` con 12 tests en backend y 6 en Vitest.

### 02. [SCRUM-26] HU-16 – Detección de brecha de penetración territorial
- **Narrativa:** Como proveedor, quiero recibir una alerta cuando un producto superventas en una zona tenga ventas nulas o muy bajas en otra similar, para decidir si lanzo una promoción de introducción en la zona rezagada.
- **Criterio 1 (Alerta generada):** Dado que un producto es superventas en el Clúster 1 y presenta ventas por debajo del percentil 15 en el Clúster 2 comparable, el sistema genera una alerta prescriptiva de "desabastecimiento artificial" visible en `/modo-pro/alertas`.
- **Criterio 2 (Sin brecha):** Dado que las ventas son homogéneas entre clústeres comparables, no se genera ninguna alerta.
- **Evidencia Técnica:** Módulo `brechas.py` con clustering y pantalla `ModoProAlertasPage.jsx`.

### 03. [SCRUM-27] HU-17 – Recomendación comercial narrativa con IA
- **Narrativa:** Como proveedor, quiero recibir una recomendación comercial en lenguaje claro basada en mis datos de ventas para tomar decisiones sin interpretar cifras técnicas.
- **Criterio 1 (Narrativa exitosa):** El motor estadístico calcula elasticidad de demanda y lo envía como contexto a la API de IA (Gemini free tier), publicando una recomendación narrativa en `/modo-pro/recomendaciones`.
- **Criterio 2 (Fallo de IA / Respaldo a costo $0):** Si la API no responde o supera el timeout, el sistema conserva la matriz numérica calculada y genera un diagnóstico por plantilla determinista con el mensaje *"Recomendación narrativa no disponible temporalmente"*.
- **Evidencia Técnica:** Módulo `narrativa.py`, pantalla `ModoProRecomendacionesPage.jsx` y tests de aceptación.

### 04. [SCRUM-43] HU-30 – Elasticidad precio-demanda: prescripción de descuentos
- **Narrativa:** Como proveedor, quiero que el sistema calcule la elasticidad precio-demanda de mis productos por zona para decidir si un descuento por volumen incrementa mis ventas sin sacrificar margen.
- **Criterio 1 (Demanda elástica):** Mediante regresión log-log se calcula coeficiente negativo $< -1$; el sistema prescribe escalas de descuento por volumen (ej. `>= 24` unidades).
- **Criterio 2 (Demanda inelástica):** Coeficiente $>-1$; el sistema bloquea descuentos directos para cuidar el margen y prescribe exhibidores o bundles cruzados.
- **Evidencia Técnica:** Módulo `elasticity.py` y visualización en `EstadisticasEconomicasPage.jsx`.

### 05. [SCRUM-44] HU-31 – Minería de canasta: formatos de empaque (FP-Growth)
- **Narrativa:** Como proveedor, quiero identificar qué combinaciones de productos compran juntas las tiendas para diseñar formatos de empaque agrupados o combos.
- **Criterio 1 (Prescripción de empaque):** Algoritmo FP-Growth descubre reglas con soporte $\ge 20\%$ y confianza $\ge 30\%$, con Lift $> 1$; prescribe multipacks o combos cruzados.
- **Criterio 2 (Sin patrón):** Si no hay soporte suficiente, no emite prescripciones erróneas.
- **Evidencia Técnica:** Módulo `basket_analysis.py` y tarjetas interactivas de afinidad en frontend.

### 06. [SCRUM-65] N-11 – Pantallas Modo Pro en el portal del proveedor
- **Alcance:** Rutas `/modo-pro/ranking`, `/modo-pro/alertas` y `/modo-pro/recomendaciones` en Prisma-Web consumiendo el worker analítico co-alojado con aislamiento multi-tenant por proveedor (`useProveedorId`).

### 07. [SCRUM-66] N-12 – Capa narrativa de IA a costo $0 con respaldo en plantilla
- **Alcance:** Integración con nivel gratuito de Gemini API (x-goog-api-key) con fallback a plantilla determinista, garantizando costo operativo de $0.00 USD y consumo $< 300\text{ MB RAM}$.

### 08. [SCRUM-67] N-13 – Entrega final: documentación, informe de pruebas y demo
- **Alcance:** Consolidación de manuales por rol, informe final IEEE, guion de demo interactivo y retrospectiva de los 4 Sprints.

### 09-12. [SCRUM-70, 71, 72, 73] N-16, N-17, N-18, N-19 – Documentación Técnica Oficial
- **Alcance:** Documento de Arquitectura Front-End, Arquitectura General del Sistema, Especificación de Requerimientos Funcionales y Manual de Identidad Visual formalizados en DOCX, PDF y diapositivas PPTX.
