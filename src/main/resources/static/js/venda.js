/* Tela de venda: quantidades, busca, prévia do preço e pagamento; o painel do Pix fica em venda-pix.js. */
function sem(t) {
    return (t || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

function filtrar() {
    const q = sem(document.getElementById('busca').value);
    let visiveis = 0;
    document.querySelectorAll('#tabelaProdutos tbody tr').forEach(function (tr) {
        const inp = tr.querySelector('.qtd');
        if (!inp) return;
        const ok = sem(inp.getAttribute('data-nome')).indexOf(q) !== -1
                || sem(inp.getAttribute('data-catnome')).indexOf(q) !== -1;
        tr.style.display = ok ? '' : 'none';
        if (ok) visiveis++;
    });
    document.getElementById('semResultado').classList.toggle('oculto', visiveis > 0);
}

function passo(botao, delta) {
    const inp = botao.parentNode.querySelector('.qtd');
    const max = parseInt(inp.getAttribute('max'));
    let v = (parseInt(inp.value) || 0) + delta;
    if (v < 0) v = 0;
    if (!isNaN(max) && v > max) v = max;
    inp.value = v;
    recalcular();
}

let unidades = 0, totalPrevia = null, dividido = false;
const pixAutomatico = document.getElementById('formVenda').dataset.pixAutomatico === 'true';
const tokenVenda = document.getElementById('formVenda').dataset.token;

function centavos(texto) {
    let t = (texto || '').trim();
    if (!t) return null;
    if (!/^(?:[0-9]+(?:[.,][0-9]{1,2})?|[0-9]{1,3}(?:\.[0-9]{3})+,[0-9]{1,2})$/.test(t)) return null;
    if (t.indexOf(',') !== -1) t = t.replace(/\./g, '').replace(',', '.');
    return Math.round(parseFloat(t) * 100);
}
function centavosDoJson(v) {
    return Math.round(Number(v) * 100);
}
function reaisCentavos(c) {
    const inteiro = Math.floor(Math.abs(c) / 100).toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    return 'R$ ' + inteiro + ',' + String(Math.abs(c) % 100).padStart(2, '0');
}

function alvo() {
    const ajuste = document.getElementById('valorTotal').value.trim();
    if (ajuste) return centavos(ajuste);
    return totalPrevia;
}

function formaUnica() {
    const r = document.querySelector('input[name=pag_forma_0]:checked');
    return r ? r.value : '';
}

function mostrar(el, sim) {
    el.classList.toggle('oculto', !sim);
    el.querySelectorAll('input').forEach(i => { i.disabled = !sim; });
}

const painelUnico = pixAutomatico ? criarPainelPix(0, () => alvo()) : null;
if (painelUnico) {
    painelUnico.elemento.querySelector('input.origem').name = 'pag_origem_0';
    document.getElementById('pixUnico').append(painelUnico.elemento);
}
let proximaParte = 1;

function alternarUnico(focar) {
    const forma = formaUnica();
    mostrar(document.getElementById('campoOutro'), !dividido && forma === 'OUTRO');
    mostrar(document.getElementById('campoRecebido'), !dividido && forma === 'DINHEIRO');
    mostrar(document.getElementById('pixUnico'), !dividido && forma === 'PIX' && pixAutomatico);
    if (painelUnico && forma !== 'PIX') painelUnico.reiniciar();
    if (focar && forma === 'OUTRO') document.getElementById('pagDescricaoUnico').focus();
    atualizarPagamento();
}

function abrirDivisao() {
    dividido = true;
    if (painelUnico) painelUnico.reiniciar();
    document.querySelectorAll('#pagamentoUnico input').forEach(i => { i.disabled = true; });
    document.getElementById('pagamentoUnico').classList.add('oculto');
    document.getElementById('pagamentoDividido').classList.remove('oculto');
    const primeira = formaUnica() || 'PIX';
    adicionarLinha(primeira);
    adicionarLinha(primeira === 'DINHEIRO' ? 'PIX' : 'DINHEIRO');
}

function fecharDivisao() {
    dividido = false;
    document.querySelectorAll('#linhasPagamento .linha-pagamento').forEach(l => { if (l.painelPix) l.painelPix.destruir(); });
    document.getElementById('linhasPagamento').replaceChildren();
    document.getElementById('pagamentoDividido').classList.add('oculto');
    document.getElementById('pagamentoUnico').classList.remove('oculto');
    document.querySelectorAll('#pagamentoUnico input[type=radio]').forEach(i => { i.disabled = false; });
    alternarUnico(false);
}

function campo(rotulo, classe) {
    const div = document.createElement('div'); div.className = 'campo ' + classe;
    const label = document.createElement('label'); label.textContent = rotulo;
    const input = document.createElement('input'); input.type = 'text';
    input.addEventListener('input', atualizarPagamento);
    label.append(input); div.append(label);
    return {div: div, input: input};
}

function adicionarLinha(forma) {
    const lista = document.getElementById('linhasPagamento');
    const linha = document.createElement('div'); linha.className = 'linha-pagamento';

    const seletor = document.getElementById('modeloForma').cloneNode(true);
    seletor.removeAttribute('id'); seletor.hidden = false; seletor.disabled = false;
    seletor.removeAttribute('aria-hidden'); seletor.className = 'forma';
    seletor.setAttribute('aria-label', 'Forma de pagamento');
    if (typeof forma === 'string') seletor.value = forma;

    const valor = campo('Valor', 'campo-valor'); valor.input.className = 'valor mono'; valor.input.inputMode = 'decimal';
    const descricao = campo('Qual?', 'campo-descricao'); descricao.input.className = 'descricao'; descricao.input.maxLength = 255;
    const recebido = campo('Recebido (opcional)', 'campo-recebido'); recebido.input.className = 'recebido mono'; recebido.input.inputMode = 'decimal';
    const troco = document.createElement('p'); troco.className = 'ajuda troco'; recebido.div.append(troco);
    const parte = proximaParte++;
    const numeroParte = document.createElement('input'); numeroParte.type = 'hidden'; numeroParte.className = 'parte';
    numeroParte.value = String(parte);
    const pix = document.createElement('div'); pix.className = 'campo campo-pix';
    if (pixAutomatico) {
        linha.painelPix = criarPainelPix(parte, () => centavos(valor.input.value));
        pix.append(linha.painelPix.elemento);
    }

    const remover = document.createElement('button');
    remover.type = 'button'; remover.className = 'botao botao-secundario botao-pequeno'; remover.textContent = 'Remover';
    remover.setAttribute('aria-label', 'Remover esta forma');
    remover.addEventListener('click', function () {
        if (linha.painelPix) linha.painelPix.destruir();
        linha.remove();
        if (!lista.children.length) { fecharDivisao(); return; }
        renumerar(); atualizarPagamento();
    });
    seletor.addEventListener('change', function () { ajustarLinha(linha); atualizarPagamento(); });

    linha.append(seletor, valor.div, descricao.div, recebido.div, pix, numeroParte, remover);
    lista.append(linha);
    ajustarLinha(linha);
    renumerar();
    atualizarPagamento();
    valor.input.focus();
}

function ajustarLinha(linha) {
    const forma = linha.querySelector('select.forma').value;
    mostrar(linha.querySelector('.campo-descricao'), forma === 'OUTRO');
    mostrar(linha.querySelector('.campo-recebido'), forma === 'DINHEIRO');
    mostrar(linha.querySelector('.campo-pix'), forma === 'PIX' && pixAutomatico);
    if (linha.painelPix && forma !== 'PIX') linha.painelPix.reiniciar();
}

function renumerar() {
    document.querySelectorAll('#linhasPagamento .linha-pagamento').forEach(function (linha, n) {
        linha.querySelector('select.forma').name = 'pag_forma_' + n;
        linha.querySelector('input.valor').name = 'pag_valor_' + n;
        linha.querySelector('input.descricao').name = 'pag_descricao_' + n;
        linha.querySelector('input.recebido').name = 'pag_recebido_' + n;
        linha.querySelector('input.parte').name = 'pag_parte_' + n;
        const origem = linha.querySelector('input.origem');
        if (origem) origem.name = 'pag_origem_' + n;
    });
}

function mostrarTroco(saida, textoRecebido, valor) {
    saida.textContent = '';
    if (!textoRecebido.trim()) return true;
    const recebido = centavos(textoRecebido);
    if (recebido === null || valor === null) { saida.textContent = 'Valor recebido inválido.'; return false; }
    if (recebido < valor) { saida.textContent = 'Recebido menor que o valor em dinheiro.'; return false; }
    saida.textContent = 'Troco: ' + reaisCentavos(recebido - valor);
    return true;
}

function pagamentoOk() {
    const saldo = document.getElementById('saldoPagamento');
    saldo.textContent = ''; saldo.className = 'saldo-pagamento';
    const total = alvo();
    if (total === null) {
        if (document.getElementById('valorTotal').value.trim()) saldo.textContent = 'Valor ajustado inválido.';
        return false;
    }
    if (!dividido) {
        const forma = formaUnica();
        if (forma === 'OUTRO' && total > 0 && !document.getElementById('pagDescricaoUnico').value.trim()) {
            saldo.textContent = 'Diga qual é a outra forma de pagamento.'; return false;
        }
        if (forma === 'DINHEIRO') {
            return mostrarTroco(document.getElementById('trocoUnico'), document.getElementById('pagRecebidoUnico').value, total);
        }
        if (forma === 'PIX' && painelUnico && total > 0 && !painelUnico.ok()) {
            saldo.textContent = 'Gere o Pix e aguarde o pagamento, ou escolha Pix manual.'; return false;
        }
        return true;
    }
    if (total === 0) { saldo.textContent = 'Cortesia (valor zero) não tem pagamento: volte para uma forma só.'; return false; }
    let soma = 0, ok = true;
    document.querySelectorAll('#linhasPagamento .linha-pagamento').forEach(function (linha) {
        const forma = linha.querySelector('select.forma').value;
        const valor = centavos(linha.querySelector('input.valor').value);
        if (valor === null || valor <= 0) ok = false; else soma += valor;
        if (forma === 'OUTRO' && !linha.querySelector('input.descricao').value.trim()) ok = false;
        if (forma === 'PIX' && linha.painelPix && !linha.painelPix.ok()) ok = false;
        const troco = linha.querySelector('.troco');
        if (forma === 'DINHEIRO') { if (!mostrarTroco(troco, linha.querySelector('input.recebido').value, valor)) ok = false; }
        else troco.textContent = '';
    });
    const diferenca = total - soma;
    if (diferenca > 0) { saldo.textContent = 'Falta ' + reaisCentavos(diferenca); saldo.classList.add('saldo-falta'); }
    else if (diferenca < 0) { saldo.textContent = 'Sobra ' + reaisCentavos(-diferenca); saldo.classList.add('saldo-falta'); }
    else { saldo.textContent = 'Pagamento fechado: ' + reaisCentavos(total); saldo.classList.add('saldo-ok'); }
    return ok && diferenca === 0;
}

function atualizarPagamento() {
    const ok = pagamentoOk();
    const botao = document.getElementById('botaoConfirmar');
    botao.disabled = enviando || unidades === 0 || !ok;
    const total = alvo();
    botao.textContent = unidades > 0 && total !== null ? 'Confirmar ' + reaisCentavos(total) : 'Confirmar venda';
}

let enviando = false;
document.getElementById('formVenda').addEventListener('submit', function(e) {
    if (enviando) { e.preventDefault(); return; }
    enviando = true;
    document.getElementById('botaoConfirmar').disabled = true;
});
let timer, versao = 0;
function linha(texto, valor) {
    const div = document.createElement('div'); div.className = 'item-carrinho';
    const nome = document.createElement('span'); nome.textContent = texto;
    const preco = document.createElement('span'); preco.textContent = valor;
    div.append(nome, preco); document.getElementById('carrinho').append(div);
}
const SEM_PREVIA = 'Não foi possível calcular a prévia. Confira a conexão e tente novamente.';

function sessaoExpirada() {
    totalPrevia = null; unidades = 0;
    const aviso = document.createElement('p');
    aviso.className = 'aviso aviso-erro';
    aviso.id = 'sessaoExpirada';
    aviso.setAttribute('role', 'alert');
    aviso.append('Sessão expirou. Entre de novo. ');
    const entrar = document.createElement('a');
    entrar.href = '/login';
    entrar.textContent = 'Entrar';
    aviso.append(entrar);
    document.getElementById('carrinho').replaceChildren(aviso);
    atualizarPagamento();
}

let previaEmAndamento = null;
function recalcular() {
    clearTimeout(timer);
    const atual = ++versao;
    document.getElementById('botaoConfirmar').disabled = true;
    timer = setTimeout(async function () {
        if (previaEmAndamento) previaEmAndamento.abort();
        const controle = new AbortController();
        previaEmAndamento = controle;
        try {
            const resposta = await fetch('/venda/previa', {method:'POST', signal: controle.signal,
                    body:new URLSearchParams(new FormData(document.getElementById('formVenda')))});
            const json = (resposta.headers.get('content-type') || '').includes('application/json');
            const o = json ? await resposta.json() : null;
            if (atual !== versao) return;
            if (resposta.status === 401) { sessaoExpirada(); return; }
            if (!resposta.ok || !o || o.erro) throw new Error(o && o.erro ? o.erro : SEM_PREVIA);
            document.getElementById('carrinho').replaceChildren();
            unidades = 0;
            o.itens.forEach(i => {
                unidades += i.quantidade;
                const unitario = centavosDoJson(i.precoUnitario);
                linha(i.nome + ' · ' + i.categoria + ' · ' + i.quantidade + ' × ' + reaisCentavos(unitario), reaisCentavos(i.quantidade * unitario));
            });
            o.descontos.forEach(d => linha(d.descricao, '− ' + reaisCentavos(centavosDoJson(d.valor))));
            if (o.aproximado) linha('Carrinho grande: promoções escolhidas por aproximação.', '');
            totalPrevia = centavosDoJson(o.total);
            document.getElementById('totalCarrinho').textContent = reaisCentavos(totalPrevia).slice(3);
            document.getElementById('contagemItens').textContent = unidades + (unidades === 1 ? ' item' : ' itens');
            atualizarPagamento();
        } catch (e) {
            if (e.name === 'AbortError' || atual !== versao) return;
            totalPrevia = null; unidades = 0;
            document.getElementById('carrinho').replaceChildren();
            linha(e instanceof TypeError ? SEM_PREVIA : e.message, '');
            atualizarPagamento();
        }
    }, 200);
}

document.addEventListener('keydown', function (e) {
    const busca = document.getElementById('busca');
    if (e.key === '/' && busca && document.activeElement !== busca
            && !/input|textarea|select/i.test(document.activeElement.tagName)) {
        e.preventDefault();
        busca.focus();
    }
});

alternarUnico(false);
recalcular();
