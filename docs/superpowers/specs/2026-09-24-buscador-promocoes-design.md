# Buscador de Promoções de Peças de Informática — Design

Data: 2026-09-24
Status: aprovado para planejamento de implementação

## 1. Problema

Comprar peças de informática para a empresa exige comparar preço entre lojas
manualmente, abrindo várias abas e sem saber se o preço exibido é realmente
uma promoção ou o valor de sempre.

O sistema resolve isso: uma busca, várias fontes, custo efetivo lado a lado, e
sinalização de queda de preço em relação ao que já foi observado.

## 2. Escopo

### No MVP

- Busca sob demanda por palavra-chave.
- Duas fontes: Mercado Livre e KaBuM.
- Comparação por custo efetivo, com disponibilidade e garantia visíveis.
- Histórico oportunista de preços, alimentado pelas próprias buscas.
- Uso local, monousuário, sem autenticação de aplicação.

### Fora do MVP

Pichau e Terabyte (exigem navegador headless), Amazon, AliExpress, alertas
automáticos, agendamento, multiusuário, monetização por afiliado.

### Decisões de escopo e seus motivos

| Decisão | Motivo |
|---|---|
| Amazon excluída | PA-API 5.0 exige conta Associates com 3 vendas qualificadas em 180 dias. Uso é compra interna, não afiliação: a condição não é alcançável. |
| AliExpress excluído | Open Platform admite apenas lojistas (exige capital social e registro chinês de software). Via afiliado é possível mas depende de aprovação externa. Além disso importação não emite nota fiscal brasileira, o que inviabiliza lançamento contábil. |
| GearBest descartado | Empresa-mãe faliu, site fora do ar desde 2021. O domínio não resolve DNS. |
| Pichau e Terabyte adiadas | Ambas respondem com desafio Cloudflare até em `/robots.txt`. Exigem navegador real, peso que o MVP não justifica. |
| Feeds XML descartados | Nem KaBuM nem Pichau publicam feed de produtos. Os sitemaps contêm apenas URLs e datas, sem preço. |

## 3. Fontes de dados

### 3.1 Mercado Livre

- Endpoint de busca: `GET https://api.mercadolibre.com/sites/MLB/search`
- **Autenticação é obrigatória.** Toda a API responde `403 forbidden` sem
  token, incluindo endpoints historicamente públicos.
- **Não existe `client_credentials`.** Os únicos fluxos são
  `authorization_code` e `refresh_token`: uma conta real precisa autorizar a
  aplicação.
- `access_token` dura 6 horas.
- O `refresh_token` é **de uso único e rotaciona** a cada renovação.

A rotação do refresh token é um requisito de correção, não um detalhe: o token
novo precisa ser persistido com durabilidade garantida antes que o antigo seja
considerado gasto. Perder essa gravação obriga a reautorização manual.

### 3.2 KaBuM

Não há API pública. As páginas de categoria embutem, na carga da página, um
payload JSON com a lista completa de produtos daquela categoria.

Campos disponíveis por produto: `name`, `price`, `priceWithDiscount`,
`oldPrice`, `discountPercentage`, `maxInstallment`, `quantity`, `available`,
`rating`, `averageRating`, `ratingCount`, `warranty`, imagens.

**Restrição de `robots.txt` que o sistema deve respeitar:**

O `robots.txt` da KaBuM proíbe `/busca/*?` e `*?query=*`, e permite páginas de
categoria e de produto.

Consequência de design: o sistema **nunca** acessa o endpoint de busca da
KaBuM. Ele baixa páginas de categoria (permitidas), guarda o payload em cache
local, e executa a busca por palavra-chave sobre esse cache. A capacidade de
busca é preservada sem violar a restrição declarada.

Extrair JSON estruturado, em vez de ler seletores CSS, reduz muito a
fragilidade clássica de scraping — nomes de campo de payload interno mudam com
frequência bem menor que a marcação visual. A fragilidade não é eliminada:
ver a seção de riscos.

## 4. Arquitetura

Stack: Java 25 (LTS) + Spring Boot 4.1. Persistência em SQLite local.

A escolha de Java é deliberada: o núcleo do sistema é um fan-out de chamadas
concorrentes, e virtual threads resolvem isso sem programação reativa.

### 4.1 Módulos

**`provider`**

Interface `OfferProvider` com um único método de busca que devolve ofertas
normalizadas. Implementações: `MercadoLivreProvider`, `KabumProvider`.

Toda diferença entre fontes fica contida aqui. Nenhum módulo acima conhece a
origem de uma oferta além do rótulo.

Execução em paralelo por virtual threads, com timeout por provider. Falha ou
timeout de um provider não impede o retorno dos demais: o resultado carrega a
lista de fontes que falharam, e a interface as exibe explicitamente.

Nunca exibir resultado parcial como se fosse completo.

**`auth`**

Exclusivo do Mercado Livre. Fluxo de autorização executado uma vez; a partir
daí, renovação automática antes do vencimento.

Ordem obrigatória na renovação: gravar o novo par de tokens, confirmar a
gravação, só então usar o novo access token. A inversão dessa ordem, em caso de
falha, deixa o sistema sem credencial recuperável.

**`catalog`**

Cache local das categorias da KaBuM, com marca de validade. Atualiza sob
demanda quando expirado. É o que viabiliza busca por palavra-chave sem tocar
no endpoint proibido pelo `robots.txt`.

**As categorias cobertas são uma lista configurada, não o catálogo inteiro.**
O MVP cobre as categorias de peças relevantes para a compra da empresa
(hardware e seus subníveis, periféricos, computadores). Uma busca por algo fora
dessas categorias não retorna resultado da KaBuM, e a interface diz isso
explicitamente — em vez de deixar parecer que a loja não tem o produto.

A lista de categorias é configuração, não constante no código.

**`normalizer`**

Converte os formatos de cada fonte num tipo `Offer` único, contendo: título,
custo efetivo, disponibilidade, garantia, vendedor, fonte e link.

**Definição de custo efetivo:** preço à vista já com desconto aplicado,
**sem frete**. Frete depende de CEP e não é conhecido no momento da busca; se
fosse estimado, seria um número inventado apresentado como fato. O campo de
parcelamento é exibido como informação, nunca usado para ordenar.

Quando a fonte informa preço cheio e preço com desconto, o custo efetivo é o
valor com desconto, e o preço cheio é exibido ao lado como referência.

No Mercado Livre, distingue vendedor oficial de terceiro. Para compra
empresarial isso muda a decisão: nota fiscal, garantia e prazo dependem de quem
vende, não do preço anunciado.

**`matching`**

Agrupa a mesma peça entre fontes, por normalização de texto e extração de
tokens de modelo e especificação (`DDR4`, `8GB`, `3200MHz`, `RTX 4060`).

Regra de projeto: **na dúvida, não agrupar.** Duas ofertas do mesmo produto
exibidas separadamente é um incômodo; dois produtos diferentes exibidos como um
só é uma decisão de compra errada. O erro é assimétrico e o design assume isso.

**`history`**

Registra silenciosamente os preços observados em cada busca. Nenhum cadastro,
nenhuma configuração pelo usuário.

Na segunda busca pelo mesmo termo, cada oferta pode ser apresentada contra o
valor já observado antes. É o que distingue um comparador de uma lista de
preços: sem histórico, não há resposta para "isso é promoção ou é o preço de
sempre?".

**A comparação é por identidade de oferta, não por termo de busca:** a chave é
o par (fonte, identificador do produto na fonte). Comparar preços agrupados por
termo digitado produziria variações falsas, já que o conjunto de resultados de
uma busca muda entre execuções.

Uma oferta sem observação anterior é exibida sem indicação de variação. Nunca
tratar ausência de histórico como estabilidade de preço.

**`web`**

Uma página: campo de busca, resultados ordenados por custo efetivo, com rótulo
de fonte, disponibilidade, garantia, variação em relação ao histórico, e aviso
visível quando alguma fonte falhou.

### 4.2 Fluxo de uma busca

1. Usuário submete um termo.
2. Fan-out paralelo para os providers habilitados.
3. Mercado Livre consulta a API; KaBuM consulta o cache de categorias,
   atualizando-o se estiver expirado.
4. Cada provider devolve ofertas normalizadas, ou falha isoladamente.
5. `matching` agrupa o que casa com confiança.
6. `history` grava as observações e anota as variações.
7. A interface exibe o resultado, incluindo as fontes que falharam.

## 5. Testes

As respostas reais coletadas durante a investigação servem de fixtures.

- `normalizer` e `matching`: testados contra fixtures, sem rede. É onde o TDD
  tem maior retorno, porque são as regras com mais casos de borda.
- `catalog` e `history`: testados contra SQLite em arquivo temporário.
- Providers: teste de contrato contra a fonte real, marcado para não executar
  no build padrão. Sua função é detectar mudança de formato na origem.

O teste de contrato é o mecanismo de alerta para o risco descrito a seguir.

## 6. Riscos aceitos

**O provider da KaBuM vai quebrar eventualmente.** Depende de um payload
interno sem contrato público. A mitigação é detecção, não prevenção: o teste de
contrato acusa a mudança, e o conserto é manual. Risco assumido
conscientemente, porque a alternativa (rede de afiliados) depende de aprovação
externa que não se pode garantir.

**O matching entre fontes não será perfeito.** Aceito por design, com o viés
deliberado de separar em vez de agrupar errado.

**O MVP tem duas fontes.** Suficiente para comparar, mas a cobertura do mercado
é parcial. Pichau e Terabyte permanecem endereçáveis por navegador headless
quando houver justificativa.

## 7. Critérios de sucesso

1. Buscar um termo retorna ofertas do Mercado Livre e da KaBuM na mesma tela,
   ordenadas por custo efetivo.
2. A falha de uma fonte não impede a exibição da outra, e é sinalizada.
3. Repetir uma busca feita antes mostra a variação de preço observada.
4. O sistema não acessa nenhuma URL proibida pelo `robots.txt` das fontes.
5. A renovação de token do Mercado Livre ocorre sem intervenção manual.
