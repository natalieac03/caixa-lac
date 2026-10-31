/* Pix do Mercado Pago na tela de venda: o painel de cada parte (gerar, consultar, cancelar, Pix manual). */
const paineisPix = new Set();
window.addEventListener('pagehide', function () { paineisPix.forEach(p => p.parar()); });

function botaoPequeno(texto, aoClicar) {
    const b = document.createElement('button');
    b.type = 'button'; b.className = 'botao botao-secundario botao-pequeno'; b.textContent = texto;
    b.addEventListener('click', aoClicar);
    return b;
}

async function pedirJson(url, opcoes) {
    const resposta = await fetch(url, Object.assign({headers: {'Accept': 'application/json'}}, opcoes || {}));
    const o = (resposta.headers.get('content-type') || '').includes('application/json') ? await resposta.json() : null;
    return {status: resposta.status, ok: resposta.ok, o: o};
}

function criarPainelPix(parte, valorAtual) {
    const raiz = document.createElement('div'); raiz.className = 'painel-pix';
    const origem = document.createElement('input'); origem.type = 'hidden'; origem.className = 'origem';
    const botoes = document.createElement('div'); botoes.className = 'acoes-linha';
    const gerar = botaoPequeno('Gerar Pix', function () { gerarPix(); });
    const manual = botaoPequeno('Pix manual', function () { usarManual(); });
    botoes.append(gerar, manual);
    const area = document.createElement('div'); area.className = 'qr-area oculto';
    const img = document.createElement('img'); img.className = 'qr-pix'; img.alt = 'QR code do Pix';
    const valorTexto = document.createElement('p'); valorTexto.className = 'qr-valor';
    const copiar = botaoPequeno('Copiar código', function () {
        if (!codigo) return;
        navigator.clipboard?.writeText(codigo).then(() => { copiar.textContent = 'Código copiado'; });
    });
    const manualNoQr = botaoPequeno('Usar Pix manual', function () { usarManual(); });
    area.append(img, valorTexto, copiar, manualNoQr);
    const situacao = document.createElement('p'); situacao.className = 'ajuda situacao-pix'; situacao.setAttribute('aria-live', 'polite');
    raiz.append(origem, botoes, area, situacao);

    let estado = 'escolher', cobranca = null, codigo = null, intervalo = null, controle = null;
    let aviso = '';

    function desenhar(mensagem) {
        area.classList.toggle('oculto', !(estado === 'aguardando' || estado === 'paga'));
        botoes.classList.toggle('oculto', estado === 'aguardando' || estado === 'paga' || estado === 'gerando');
        manualNoQr.classList.toggle('oculto', estado !== 'aguardando');
        const v = valorAtual();
        gerar.disabled = !(v > 0) || unidades === 0;
        if (mensagem !== undefined) aviso = mensagem;
        if (aviso) situacao.textContent = aviso;
        else if (estado === 'aguardando') situacao.textContent = 'Aguardando pagamento...';
        else if (estado === 'paga') situacao.textContent = 'Pix recebido (Mercado Pago).';
        else if (estado === 'manual') situacao.textContent = 'Pix manual: confira o recebimento no app.';
        else if (estado === 'gerando') situacao.textContent = 'Gerando Pix...';
        else situacao.textContent = '';
    }

    function parar() {
        if (intervalo) clearInterval(intervalo);
        intervalo = null;
        if (controle) controle.abort();
        controle = null;
    }

    async function gerarPix() {
        const v = valorAtual();
        if (!(v > 0)) return;
        aviso = '';
        estado = 'gerando'; desenhar();
        try {
            const r = await pedirJson('/venda/pix', {method: 'POST', body: new URLSearchParams({
                token: tokenVenda, parte: String(parte), valor: (v / 100).toFixed(2)})});
            if (r.status === 401) { estado = 'escolher'; sessaoExpirada(); return; }
            if (!r.ok || !r.o) throw new Error(r.o && r.o.erro ? r.o.erro : 'Não foi possível gerar o Pix.');
            cobranca = centavosDoJson(r.o.valor);
            codigo = r.o.qrCode;
            if (r.o.qrCodeBase64) img.src = 'data:image/png;base64,' + r.o.qrCodeBase64;
            valorTexto.textContent = reaisCentavos(cobranca);
            copiar.textContent = 'Copiar código';
            origem.value = 'MERCADO_PAGO';
            aplicar(r.o.status);
        } catch (e) {
            estado = 'escolher'; desenhar(e.message + ' Use Pix manual se precisar.');
        }
        atualizarPagamento();
    }

    function aplicar(status) {
        if (status === 'PAGA') { estado = 'paga'; aviso = ''; parar(); desenhar(); }
        else if (status === 'PENDENTE') {
            estado = 'aguardando'; aviso = ''; desenhar();
            if (!intervalo) intervalo = setInterval(consultar, 3000);
        } else {
            estado = 'escolher'; parar(); origem.value = '';
            desenhar(status === 'EXPIRADA' ? 'O Pix expirou: gere outro.'
                : status === 'ESTORNADA' ? 'O Pix foi estornado no Mercado Pago: gere outro ou use Pix manual.'
                : 'O Pix foi cancelado: gere outro.');
        }
    }

    async function consultar() {
        if (controle) return;
        controle = new AbortController();
        try {
            const r = await pedirJson('/venda/pix/' + encodeURIComponent(tokenVenda) + '/' + parte, {signal: controle.signal});
            if (r.status === 401) { parar(); sessaoExpirada(); return; }
            if (!r.ok || !r.o) return;
            if (r.o.status === 'PENDENTE' && r.o.valorPago !== null && centavosDoJson(r.o.valorPago) !== cobranca) {
                desenhar('Pago com valor diferente (' + reaisCentavos(centavosDoJson(r.o.valorPago)) + '): resolva à mão.');
                return;
            }
            aplicar(r.o.status);
            atualizarPagamento();
        } catch (e) {
            if (e.name !== 'AbortError') desenhar('Sem resposta do servidor; tentando de novo...');
        } finally {
            controle = null;
        }
    }

    async function cancelarCobranca() {
        parar();
        try {
            const r = await pedirJson('/venda/pix/cancelar', {method: 'POST',
                body: new URLSearchParams({token: tokenVenda, parte: String(parte)})});
            return r.ok && r.o ? r.o.status : null;
        } catch (e) {
            return null;
        }
    }

    async function usarManual() {
        if (estado === 'aguardando') {
            desenhar('Cancelando o Pix no Mercado Pago...');
            const status = await cancelarCobranca();
            if (status === 'PAGA') { aplicar('PAGA'); atualizarPagamento(); return; }
            if (status === 'PENDENTE') {
                aplicar('PENDENTE');
                desenhar('O Mercado Pago ainda está processando este Pix; espere alguns segundos.');
                return;
            }
        }
        aviso = '';
        estado = 'manual'; origem.value = 'MANUAL'; desenhar();
        atualizarPagamento();
    }

    function reiniciar() {
        if (estado === 'aguardando') cancelarCobranca();
        if (estado !== 'paga') { estado = 'escolher'; origem.value = ''; desenhar(); }
    }

    function ok() {
        const v = valorAtual();
        if (estado === 'aguardando' && v !== cobranca) {
            estado = 'escolher'; origem.value = '';
            desenhar('O valor mudou: o Pix anterior foi cancelado. Gere outro.');
            cancelarCobranca().then(function (status) {
                if (status === 'PAGA') { aplicar('PAGA'); atualizarPagamento(); }
                else if (status === 'PENDENTE') desenhar('O Pix anterior ainda está em processamento no Mercado Pago; espere alguns segundos antes de gerar outro.');
            });
        }
        if (estado === 'paga' && v !== cobranca) {
            desenhar('Este Pix já foi pago (' + reaisCentavos(cobranca) + '): ajuste o valor das partes.');
            return false;
        }
        if (estado === 'escolher' || estado === 'manual') desenhar();
        return estado === 'manual' || estado === 'paga';
    }

    function destruir() { reiniciar(); parar(); paineisPix.delete(painel); }

    const painel = {elemento: raiz, ok: ok, reiniciar: reiniciar, parar: parar, destruir: destruir};
    paineisPix.add(painel);
    desenhar();
    return painel;
}
