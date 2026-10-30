/*
 * Tela de Finanças: desenha os gráficos com os dados de /financas/dados e cuida
 * da busca, da ordenação e do drill-down das tabelas. Nenhuma conta de dinheiro
 * aqui: valores e textos já vêm prontos do servidor.
 */
(function () {
    'use strict';

    var raiz = document.getElementById('financas');
    if (!raiz) return;

    function cor(nome, reserva) {
        var v = getComputedStyle(document.documentElement).getPropertyValue(nome).trim();
        return v || reserva;
    }

    /* ---------- Tabelas: busca, ordenação e drill-down (funcionam sem gráficos) ---------- */

    document.querySelectorAll('input[data-busca]').forEach(function (campo) {
        var tabela = document.getElementById(campo.getAttribute('data-busca'));
        campo.addEventListener('input', function () {
            var termo = campo.value.trim().toLowerCase();
            tabela.querySelectorAll('tbody tr').forEach(function (tr) {
                tr.classList.toggle('oculto', termo !== '' && (tr.getAttribute('data-nome') || '').indexOf(termo) < 0);
            });
        });
    });

    document.querySelectorAll('table.ordenavel').forEach(function (tabela) {
        tabela.querySelectorAll('button[data-ordenar]').forEach(function (botao) {
            botao.addEventListener('click', function () {
                var coluna = Number(botao.getAttribute('data-ordenar'));
                var th = botao.closest('th');
                var crescente = th.getAttribute('aria-sort') !== 'ascending';
                tabela.querySelectorAll('th').forEach(function (x) { x.removeAttribute('aria-sort'); });
                th.setAttribute('aria-sort', crescente ? 'ascending' : 'descending');
                var corpo = tabela.tBodies[0];
                var linhas = Array.prototype.slice.call(corpo.rows);
                linhas.sort(function (a, b) {
                    var x = a.cells[coluna].getAttribute('data-valor') || '';
                    var y = b.cells[coluna].getAttribute('data-valor') || '';
                    var nx = Number(x), ny = Number(y);
                    var r = (x !== '' && y !== '' && !isNaN(nx) && !isNaN(ny)) ? nx - ny : x.localeCompare(y, 'pt-BR');
                    return crescente ? r : -r;
                });
                linhas.forEach(function (tr) { corpo.appendChild(tr); });
            });
        });
    });

    var visao = document.getElementById('categorias-visao');
    function abrirCategoria(id) {
        var drill = document.getElementById('drill-' + id);
        if (!drill || !visao) return;
        visao.classList.add('oculto');
        drill.classList.remove('oculto');
        drill.focus();
    }
    document.querySelectorAll('button[data-categoria]').forEach(function (b) {
        b.addEventListener('click', function () { abrirCategoria(b.getAttribute('data-categoria')); });
    });
    document.querySelectorAll('button[data-voltar]').forEach(function (b) {
        b.addEventListener('click', function () {
            b.closest('.drill').classList.add('oculto');
            visao.classList.remove('oculto');
            var titulo = document.getElementById('titulo-categorias');
            if (titulo) titulo.scrollIntoView();
        });
    });

    /* ---------- Gráficos ---------- */

    if (typeof Chart === 'undefined' || !document.getElementById('grafico-serie')) return;
    var script = document.getElementById('script-financas');
    var url = script.getAttribute('data-dados') + '?' + raiz.getAttribute('data-consulta')
        + '&metrica=' + encodeURIComponent(raiz.getAttribute('data-metrica'));

    var tinta = cor('--tinta-suave', '#625878');
    var linha = cor('--linha', '#e4dcf0');
    var roxo = cor('--roxo', '#8a00f4');
    var laranja = cor('--laranja', '#f48d00');
    Chart.defaults.animation = false;
    Chart.defaults.color = tinta;
    Chart.defaults.borderColor = linha;
    Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
    Chart.defaults.maintainAspectRatio = false;

    var nomeMetrica = { QUANTIDADE: 'Quantidade vendida', RECEITA: 'Receita líquida', LUCRO: 'Lucro bruto' };

    function texto(textos) {
        return function (ctx) { return ctx.dataset.label + ': ' + textos[ctx.dataIndex]; };
    }

    /* Listras para não depender só de cor. */
    function listras(base) {
        var c = document.createElement('canvas');
        c.width = c.height = 8;
        var g = c.getContext('2d');
        g.fillStyle = base;
        g.fillRect(0, 0, 8, 8);
        g.strokeStyle = 'rgba(255,255,255,0.6)';
        g.lineWidth = 2;
        g.beginPath();
        g.moveTo(0, 8);
        g.lineTo(8, 0);
        g.stroke();
        return g.createPattern(c, 'repeat');
    }

    fetch(url, { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
        .then(function (r) { if (!r.ok) throw new Error(r.status); return r.json(); })
        .then(function (d) {
            if (d.vazio) return;
            new Chart(document.getElementById('grafico-serie'), {
                type: 'line',
                data: {
                    labels: d.receita.rotulos,
                    datasets: [
                        { label: 'Receita líquida', data: d.receita.valores, borderColor: roxo, backgroundColor: roxo, pointStyle: 'circle', tension: 0.2 },
                        { label: 'Lucro bruto', data: d.lucroBruto.valores, borderColor: laranja, backgroundColor: laranja, borderDash: [6, 4], pointStyle: 'rectRot', tension: 0.2 }
                    ]
                },
                options: {
                    plugins: {
                        legend: { labels: { usePointStyle: true } },
                        tooltip: { callbacks: { label: function (ctx) {
                            var t = ctx.datasetIndex === 0 ? d.receita.textos : d.lucroBruto.textos;
                            return ctx.dataset.label + ': ' + t[ctx.dataIndex];
                        } } }
                    }
                }
            });
            new Chart(document.getElementById('grafico-produtos'), {
                type: 'bar',
                data: { labels: d.produtos.rotulos, datasets: [{ label: nomeMetrica[d.metrica] || 'Valor', data: d.produtos.valores, backgroundColor: roxo }] },
                options: { indexAxis: 'y', plugins: { tooltip: { callbacks: { label: texto(d.produtos.textos) } } } }
            });
            new Chart(document.getElementById('grafico-categorias'), {
                type: 'bar',
                data: { labels: d.categorias.rotulos, datasets: [{ label: 'Receita líquida', data: d.categorias.valores, backgroundColor: roxo }] },
                options: {
                    indexAxis: 'y',
                    plugins: { tooltip: { callbacks: { label: texto(d.categorias.textos) } } },
                    onClick: function (evento, elementos) {
                        if (!elementos.length) return;
                        var id = d.categoriaIds[elementos[0].index];
                        abrirCategoria(id === null ? 'sem' : String(id));
                    }
                }
            });
            new Chart(document.getElementById('grafico-formas'), {
                type: 'bar',
                data: {
                    labels: d.formasValor.rotulos,
                    datasets: [
                        { label: 'Valor recebido', data: d.formasValor.valores, backgroundColor: roxo, yAxisID: 'y' },
                        { label: 'Vendas', data: d.formasVendas.valores, backgroundColor: listras(laranja), borderColor: laranja, borderWidth: 1, yAxisID: 'y2' }
                    ]
                },
                options: {
                    scales: { y: { position: 'left', beginAtZero: true }, y2: { position: 'right', beginAtZero: true, grid: { drawOnChartArea: false }, ticks: { precision: 0 } } },
                    plugins: { tooltip: { callbacks: { label: function (ctx) {
                        var t = ctx.datasetIndex === 0 ? d.formasValor.textos : d.formasVendas.textos;
                        return ctx.dataset.label + ': ' + t[ctx.dataIndex];
                    } } } }
                }
            });
            raiz.setAttribute('data-graficos', 'prontos');
        })
        .catch(function () {
            document.querySelectorAll('figure.grafico').forEach(function (f) {
                f.insertAdjacentHTML('beforeend', '<p class="ajuda">Não deu para carregar o gráfico agora; as tabelas mostram os mesmos números.</p>');
            });
        });
})();
