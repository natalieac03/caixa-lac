package br.com.lac.caixa.servico;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Log de auditoria: uma linha por venda, ajuste, correção, cancelamento e cadastro. */
@Component
public class Auditoria {

    private static final Logger LOG = LoggerFactory.getLogger("auditoria");

    public void registrar(String evento, Object... chaveEValor) {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("evento", evento);
        for (int i = 0; i + 1 < chaveEValor.length; i += 2) {
            if (i == 2) campos.put("ip", ipAtual());
            campos.put(String.valueOf(chaveEValor[i]), chaveEValor[i + 1]);
        }
        campos.putIfAbsent("ip", ipAtual());
        String linha = formatar(campos);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    LOG.info(linha);
                }
            });
        } else {
            LOG.info(linha);
        }
    }

    static String formatar(Map<String, Object> campos) {
        StringBuilder sb = new StringBuilder();
        campos.forEach((chave, valor) -> {
            if (sb.length() > 0) sb.append(' ');
            sb.append(chave).append('=').append(valor(valor));
        });
        return sb.toString();
    }

    private static String valor(Object valor) {
        if (valor == null) return "-";
        if (valor instanceof BigDecimal dinheiro)
            return dinheiro.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String texto = valor.toString().replace('\r', ' ').replace('\n', ' ').replace("\"", "'");
        return texto.isEmpty() || texto.contains(" ") || texto.contains("=") ? "\"" + texto + "\"" : texto;
    }

    private static String ipAtual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            HttpServletRequest request = atributos.getRequest();
            return request.getRemoteAddr();
        }
        return "-";
    }
}
