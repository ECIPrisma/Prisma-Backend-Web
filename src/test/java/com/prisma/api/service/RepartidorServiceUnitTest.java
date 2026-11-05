package com.prisma.api.service;

import com.prisma.api.dto.ConfirmarEntregaPinRequest;
import com.prisma.api.entity.Pedido;
import com.prisma.api.entity.Ruta;
import com.prisma.api.enums.EstadoPedido;
import com.prisma.api.enums.MedioPago;
import com.prisma.api.exception.BadRequestException;
import com.prisma.api.exception.ResourceNotFoundException;
import com.prisma.api.repository.PedidoRepository;
import com.prisma.api.repository.RutaRepository;
import com.prisma.api.service.impl.RepartidorServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepartidorServiceUnitTest {

    @Mock
    private RutaRepository rutaRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @InjectMocks
    private RepartidorServiceImpl repartidorService;

    @Test
    @DisplayName("Confirmar entrega con PIN correcto transiciona a ENTREGADO y suma recaudo")
    void testConfirmarEntregaPinCorrecto() {
        Ruta ruta = new Ruta();
        ruta.setId(10L);
        ruta.setTotalRecaudoEfectivo(new BigDecimal("50000.00"));

        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setPinEntregaHash("123456");
        pedido.setEstado(EstadoPedido.EN_RUTA);
        pedido.setTotalAPagar(new BigDecimal("25000.00"));
        pedido.setRuta(ruta);

        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        ConfirmarEntregaPinRequest req = new ConfirmarEntregaPinRequest();
        req.setPin("123456");
        req.setMedioPago(MedioPago.EFECTIVO);

        Pedido resultado = repartidorService.confirmarEntrega(1L, req);

        assertThat(resultado.getEstado()).isEqualTo(EstadoPedido.ENTREGADO);
        assertThat(resultado.getPinValidadoEn()).isNotNull();
        assertThat(ruta.getTotalRecaudoEfectivo()).isEqualByComparingTo("75000.00");
        verify(pedidoRepository).save(pedido);
        verify(rutaRepository).save(ruta);
    }

    @Test
    @DisplayName("Confirmar entrega con PIN incorrecto lanza BadRequestException")
    void testConfirmarEntregaPinIncorrecto() {
        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setPinEntregaHash("123456");

        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));

        ConfirmarEntregaPinRequest req = new ConfirmarEntregaPinRequest();
        req.setPin("999999");
        req.setMedioPago(MedioPago.EFECTIVO);

        assertThatThrownBy(() -> repartidorService.confirmarEntrega(1L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PIN ingresado es incorrecto");

        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Iniciar ruta cambia estado de ruta a EN_EJECUCION y pedidos a EN_RUTA")
    void testIniciarRuta() {
        Ruta ruta = new Ruta();
        ruta.setId(10L);
        ruta.setEstado("PROGRAMADA");

        Pedido p1 = new Pedido();
        p1.setEstado(EstadoPedido.CREADO);

        when(rutaRepository.findById(10L)).thenReturn(Optional.of(ruta));
        when(rutaRepository.save(any(Ruta.class))).thenAnswer(i -> i.getArgument(0));
        when(pedidoRepository.findByRutaIdOrderByOrdenParadaAsc(10L)).thenReturn(List.of(p1));

        Ruta resultado = repartidorService.iniciarRuta(10L);

        assertThat(resultado.getEstado()).isEqualTo("EN_EJECUCION");
        assertThat(resultado.getHoraInicioReal()).isNotNull();
        assertThat(p1.getEstado()).isEqualTo(EstadoPedido.EN_RUTA);
        verify(pedidoRepository).save(p1);
    }

    @Test
    @DisplayName("Iniciar ruta inexistente lanza ResourceNotFoundException")
    void testIniciarRutaInexistente() {
        when(rutaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> repartidorService.iniciarRuta(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Ruta no encontrada");
    }

    @Test
    @DisplayName("Finalizar ruta marca el estado en FINALIZADA")
    void testFinalizarRuta() {
        Ruta ruta = new Ruta();
        ruta.setId(10L);
        ruta.setCodigoRuta("RUTA-TEST-01");
        ruta.setEstado("EN_EJECUCION");

        when(rutaRepository.findById(10L)).thenReturn(Optional.of(ruta));

        var res = repartidorService.finalizarRuta(10L);

        assertThat(ruta.getEstado()).isEqualTo("FINALIZADA");
        assertThat(ruta.getHoraFinReal()).isNotNull();
        assertThat(res.get("estado")).isEqualTo("FINALIZADA");
        verify(rutaRepository).save(ruta);
    }

    @Test
    @DisplayName("HU-11 Edge Case (AAA): 3 intentos fallidos de OTP bloquean temporalmente el pedido")
    void testBloqueoTemporalTrasTresIntentosFallidosPin() {
        // Arrange
        Pedido pedido = new Pedido();
        pedido.setId(88L);
        pedido.setPinEntregaHash("456789");

        when(pedidoRepository.findById(88L)).thenReturn(Optional.of(pedido));

        ConfirmarEntregaPinRequest reqErroneo = new ConfirmarEntregaPinRequest();
        reqErroneo.setPin("000000");
        reqErroneo.setMedioPago(MedioPago.EFECTIVO);

        // Act & Assert: 3 intentos fallidos
        for (int i = 1; i <= 3; i++) {
            assertThatThrownBy(() -> repartidorService.confirmarEntrega(88L, reqErroneo))
                    .isInstanceOf(BadRequestException.class);
        }

        // Act & Assert: 4to intento con el PIN correcto es rechazado por bloqueo activo
        ConfirmarEntregaPinRequest reqCorrecto = new ConfirmarEntregaPinRequest();
        reqCorrecto.setPin("456789");
        reqCorrecto.setMedioPago(MedioPago.EFECTIVO);

        assertThatThrownBy(() -> repartidorService.confirmarEntrega(88L, reqCorrecto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("bloqueada temporalmente");
    }
}
