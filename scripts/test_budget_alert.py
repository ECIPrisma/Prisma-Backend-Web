#!/usr/bin/env python3
"""
Pruebas de aceptación automatizadas para HU-28 (SCRUM-40):
Alerta de presupuesto de infraestructura.
"""

import unittest
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from audit_budget_alert import evaluar_presupuesto_infraestructura, PRESUPUESTO_TECHO_USD, UMBRAL_ALERTA_PORCENTAJE

class TestBudgetAlert(unittest.TestCase):

    def test_criterio_1_alerta_generada_por_umbral_superado(self):
        """
        Criterio 1:
        Given que el consumo acumulado proyectado del mes supera el 80% del techo presupuestal definido ($110 USD)
        When el sistema de monitoreo evalúa el consumo diario
        Then genera una alerta al administrador por correo o panel indicando el porcentaje consumido
        """
        # 85% proyectado = $93.5 USD (Supera el 80% / $88 USD)
        gasto_proyectado = 93.50
        gasto_acumulado = 60.00

        resultado = evaluar_presupuesto_infraestructura(gasto_acumulado, gasto_proyectado, "admin@prisma.com.co")

        self.assertTrue(resultado["alerta_generada"], "Debe generar alerta al superar el 80% del presupuesto")
        self.assertGreaterEqual(resultado["porcentaje_proyectado"], UMBRAL_ALERTA_PORCENTAJE)
        self.assertIn("ALERTA PRESUPUESTAL PRISMA", resultado["mensaje"])
        self.assertIn("85.0%", resultado["mensaje"])
        self.assertEqual(resultado["destinatario"], "admin@prisma.com.co")

    def test_criterio_2_consumo_dentro_de_rango_normal_sin_alerta(self):
        """
        Criterio 2:
        Given que el consumo proyectado se mantiene por debajo del 80% del techo presupuestal
        When el sistema de monitoreo evalúa el consumo diario
        Then no genera ninguna alerta
        """
        # 50% proyectado = $55.0 USD (Por debajo del 80%)
        gasto_proyectado = 55.00
        gasto_acumulado = 30.00

        resultado = evaluar_presupuesto_infraestructura(gasto_acumulado, gasto_proyectado, "admin@prisma.com.co")

        self.assertFalse(resultado["alerta_generada"], "No debe generar alerta si está por debajo del 80%")
        self.assertLess(resultado["porcentaje_proyectado"], UMBRAL_ALERTA_PORCENTAJE)
        self.assertIn("Consumo dentro de rango normal", resultado["mensaje"])

if __name__ == "__main__":
    unittest.main()
