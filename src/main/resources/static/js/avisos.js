/* Sininho: conta os avisos novos, abre o painel e consulta /avisos a cada 60 s. */
(function () {
    'use strict';
    var botao = document.getElementById('botao-sino');
    var painel = document.getElementById('painel-avisos');
    if (!botao || !painel) return;
    var selo = document.getElementById('selo-avisos');
    var textoNovos = document.getElementById('texto-avisos-novos');
    var lista = document.getElementById('lista-avisos');
    var vazio = document.getElementById('avisos-vazio');
    var CHAVE = 'lac-avisos-vistos';
    var timer = null;

    function vistos() {
        try {
            var v = JSON.parse(localStorage.getItem(CHAVE) || '[]');
            return Array.isArray(v) ? v : [];
        } catch (e) {
            return [];
        }
    }

    function idsAtuais() {
        return Array.prototype.map.call(painel.querySelectorAll('[data-aviso-id]'), function (li) {
            return li.getAttribute('data-aviso-id');
        });
    }

    function atualizarSelo() {
        var ja = vistos();
        var novos = 0;
        painel.querySelectorAll('[data-aviso-id]').forEach(function (li) {
            var novo = ja.indexOf(li.getAttribute('data-aviso-id')) < 0;
            li.classList.toggle('aviso-novo', novo);
            if (novo) novos++;
        });
        selo.textContent = String(novos);
        selo.classList.toggle('oculto', novos === 0);
        textoNovos.textContent = novos === 0 ? 'nenhum aviso novo' : (novos === 1 ? '1 aviso novo' : novos + ' avisos novos');
        botao.setAttribute('data-novos', String(novos));
    }

    function marcarVistos() {
        try {
            localStorage.setItem(CHAVE, JSON.stringify(idsAtuais()));
        } catch (e) {
        }
    }

    function posicionar() {
        var topo = botao.closest('nav');
        if (topo) document.documentElement.style.setProperty('--topo-avisos', topo.getBoundingClientRect().bottom + 'px');
    }

    function abrir() {
        posicionar();
        painel.classList.remove('oculto');
        botao.setAttribute('aria-expanded', 'true');
        marcarVistos();
        atualizarSelo();
        painel.focus();
    }

    function fechar(devolverFoco) {
        if (painel.classList.contains('oculto')) return;
        painel.classList.add('oculto');
        botao.setAttribute('aria-expanded', 'false');
        if (devolverFoco) botao.focus();
    }

    botao.addEventListener('click', function () {
        if (painel.classList.contains('oculto')) abrir(); else fechar(true);
    });
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && !painel.classList.contains('oculto')) fechar(true);
    });
    document.addEventListener('click', function (e) {
        if (!painel.contains(e.target) && !botao.contains(e.target)) fechar(false);
    });

    function desenhar(dados) {
        lista.textContent = '';
        dados.secoes.forEach(function (s) {
            var secao = document.createElement('section');
            secao.id = s.id;
            secao.className = 'secao-avisos';
            var h3 = document.createElement('h3');
            h3.textContent = s.titulo;
            secao.appendChild(h3);
            var ul = document.createElement('ul');
            s.avisos.forEach(function (a) {
                var li = document.createElement('li');
                li.setAttribute('data-aviso-id', a.id);
                var t = document.createElement('span');
                t.textContent = a.texto;
                var link = document.createElement('a');
                link.href = a.link;
                link.textContent = a.linkTexto;
                li.appendChild(t);
                li.appendChild(document.createTextNode(' '));
                li.appendChild(link);
                ul.appendChild(li);
            });
            secao.appendChild(ul);
            lista.appendChild(secao);
        });
        vazio.classList.toggle('oculto', dados.total > 0);
        if (!painel.classList.contains('oculto')) marcarVistos();
        atualizarSelo();
    }

    function consultar() {
        fetch(botao.getAttribute('data-url'), { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
            .then(function (r) {
                if (r.status === 401) { parar(); return null; }
                return r.ok ? r.json() : null;
            })
            .then(function (d) { if (d) desenhar(d); })
            .catch(function () {});
    }

    function iniciar() {
        if (timer === null) timer = setInterval(consultar, 60000);
    }

    function parar() {
        if (timer !== null) clearInterval(timer);
        timer = null;
    }

    document.addEventListener('visibilitychange', function () {
        if (document.hidden) { parar(); } else { consultar(); iniciar(); }
    });

    atualizarSelo();
    if (!document.hidden) iniciar();
})();
