package br.com.lac.caixa.repositorio;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Pix estornado no Mercado Pago que ainda paga uma venda ativa: a order, o valor e a venda. */
public record PixEstornadoDeVenda(String orderId, BigDecimal valor, Long vendaId, LocalDateTime dataHora) {}
