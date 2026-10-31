package br.com.lac.caixa.modelo;

/** Situação de uma cobrança Pix do Mercado Pago, do ponto de vista do caixa. */
public enum StatusCobranca {
    PENDENTE,
    PAGA,
    EXPIRADA,
    CANCELADA,
    ESTORNADA
}
