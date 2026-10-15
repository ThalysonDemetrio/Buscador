// Título, vendedor, garantia e URLs vêm da loja; em item de marketplace o
// vendedor é texto livre de terceiros. Tudo é montado com textContent e
// propriedades, nunca com innerHTML: não dá para esquecer de escapar um campo
// que nunca é interpretado como marcação.

const form = document.getElementById('search');
const termInput = document.getElementById('term');
const statusLine = document.getElementById('status');
const results = document.getElementById('results');

const brl = value => Number(value).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

// "/hardware/placa-de-video-vga" -> "placa de video vga"
const categoryLabel = path => path.split('/').filter(Boolean).pop().replaceAll('-', ' ');

function thumbnail(offer) {
  if (!offer.imageUrl) return el('div', 'thumb empty');
  const img = el('img', 'thumb');
  img.src = offer.imageUrl;
  img.alt = '';
  img.loading = 'lazy';
  return img;
}

function meta(offer) {
  const line = el('div', 'meta');
  line.append(el('span', '', offer.seller));
  if (offer.thirdPartySeller) line.append(el('span', 'warn', 'Vendedor terceiro'));
  if (offer.warranty === 'Sem Garantia') {
    line.append(el('span', 'warn', 'Sem garantia'));
  } else {
    line.append(el('span', '', offer.warranty.slice(0, 40)));
  }
  if (!offer.available) line.append(el('span', 'warn', 'Indisponível'));
  return line;
}

function price(offer) {
  const box = el('div', 'price');
  box.append(el('span', 'cost', brl(offer.effectiveCost)));
  if (offer.discountPercentage > 0) {
    box.append(el('span', 'ref', brl(offer.referencePrice)));
    box.append(el('span', 'discount', `−${offer.discountPercentage}%`));
  }
  const change = offer.changeSinceFirstSeen;
  if (change !== null) {
    const arrow = change < 0 ? '▼' : '▲';
    box.append(el('span', change < 0 ? 'change down' : 'change',
        `${arrow} ${Math.abs(change)}% desde o primeiro preço visto`));
  }
  return box;
}

function offerRow(offer) {
  const row = el('li', 'offer');
  const title = el('a', 'title', offer.title);
  title.href = offer.url;
  title.target = '_blank';
  title.rel = 'noopener';
  row.append(thumbnail(offer), title, price(offer), meta(offer));
  return row;
}

// A cobertura é parcial: dizer isso, em vez de deixar "nenhum resultado"
// parecer "a loja não vende esse produto".
function emptyState(term, categories) {
  const box = el('div', 'empty-state');
  box.append(el('p', '', `Nenhuma oferta encontrada para “${term}”.`));
  box.append(el('p', '', 'A busca cobre apenas estas categorias:'));
  const list = el('ul');
  categories.forEach(path => list.append(el('li', '', categoryLabel(path))));
  box.append(list);
  box.append(el('p', '', 'Se a peça estiver fora delas, ela não é consultada, '
      + 'o que é diferente de a loja não vender.'));
  return box;
}

function render(term, data) {
  results.replaceChildren();
  if (data.failedSources.length) {
    results.append(el('p', 'notice', `Fontes que falharam: ${data.failedSources.join(', ')}. `
        + 'O resultado abaixo está incompleto.'));
  }
  if (!data.offers.length) {
    statusLine.textContent = '';
    results.append(emptyState(term, data.coveredCategories));
    return;
  }
  const sources = [...new Set(data.offers.map(o => o.source))].join(', ');
  const count = data.offers.length;
  statusLine.textContent = `${count} ${count === 1 ? 'oferta' : 'ofertas'} · ${sources}`;
  const list = el('ul', 'offers');
  data.offers.forEach(offer => list.append(offerRow(offer)));
  results.append(list);
}

form.addEventListener('submit', async event => {
  event.preventDefault();
  const term = termInput.value.trim();
  if (!term) return;
  const button = form.querySelector('button');
  button.disabled = true;
  statusLine.textContent = 'Buscando…';
  try {
    const response = await fetch('/api/search?term=' + encodeURIComponent(term));
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    render(term, await response.json());
  } catch {
    results.replaceChildren();
    statusLine.textContent = 'Não foi possível buscar agora. Tente de novo em instantes.';
  } finally {
    button.disabled = false;
  }
});
