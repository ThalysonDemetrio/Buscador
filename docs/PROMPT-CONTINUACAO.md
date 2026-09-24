# Prompt de continuação — Buscador de Promoções de Peças

> Cole este arquivo inteiro como contexto inicial. Ele é autossuficiente:
> assume que você não participou do trabalho anterior.

---

## Quem você é neste projeto

Você vai continuar um projeto Java que já está funcionando. Ele **não** é um
começo do zero: são 38 commits, 61 testes passando, e o sistema já foi
verificado rodando contra a loja real.

Seu trabalho é terminar o que falta **sem quebrar o que existe** e sem
redescobrir o que já foi descoberto. Este documento carrega o conhecimento que
custou caro. Leia até o fim antes de tocar em qualquer arquivo.

---

## 1. O que o sistema faz

Um buscador de preços de peças de informática, para uso interno de uma empresa
que **compra** peças (não vende). Roda local, monousuário, sem login.

O usuário digita "memoria ddr4", o sistema mostra as ofertas ordenadas por
custo, com preço, desconto, vendedor, garantia, e a variação em relação ao que
já foi observado antes.

**O usuário é brasileiro e compra com nota fiscal.** Isso não é detalhe de
contexto: é o que define várias decisões técnicas. Item sem garantia e vendedor
terceiro são avisos vermelhos na tela porque são o que justifica pagar mais
caro numa compra empresarial.

---

## 2. Por que as fontes de dados são essas (não reabra esta discussão)

O escopo original era AliExpress, Amazon e Mercado Livre. Cada fonte foi
investigada empiricamente antes de qualquer código. Resultado:

| Fonte | Achado verificado | Decisão |
|---|---|---|
| **Amazon** | PA-API exige conta de afiliado com 3 vendas qualificadas em 180 dias. O uso é compra interna: essas vendas nunca acontecem | Fora do escopo |
| **AliExpress** | A Open Platform admite só lojistas (exige capital social e registro chinês de software). Via afiliado exige aprovação. E importação não emite nota fiscal brasileira, o que inviabiliza o lançamento contábil | Fora do escopo |
| **GearBest** | Empresa faliu em 2021; o domínio não resolve DNS | Não existe |
| **Pichau / Terabyte** | Cloudflare devolve desafio até no `/robots.txt` | Exigem navegador headless; adiadas |
| **Mercado Livre** | API oficial funciona. Tudo retorna `403` sem token. **Não existe `client_credentials`** — só `authorization_code` e `refresh_token`, ou seja, precisa de uma conta real autorizando | **Fatia 2** |
| **KaBuM** | Sem API pública, mas a página de categoria embute um JSON completo | **Construído** |

**Se o usuário pedir para incluir Amazon ou AliExpress**, explique o gate acima
antes de implementar. Não são "trabalho adiado", são portas trancadas.

---

## 3. Ambiente — leia antes do primeiro comando

**O JDK 25 está instalado mas NÃO está no PATH.** Cada chamada de shell começa
limpa. Todo comando que use `java` ou `./mvnw` precisa ser prefixado:

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"
```

Sem isso você recebe `java: command not found`, que **não é erro de código** e
já custou tempo de diagnóstico nesta sessão.

- Windows, Git Bash disponível.
- Maven e Gradle **não** estão instalados e não devem ser. Use o wrapper
  (`./mvnw`) que está no repositório.
- Node está disponível. **Atenção:** o Node enxerga caminhos do Windows, então
  `/tmp/arquivo.json` do Bash vira `C:\tmp\arquivo.json` no Node e falha. Use
  caminhos absolutos do Windows ao passar arquivos para o Node.
- Diretório: `C:\Users\Micro\Desktop\Nova pasta\Buscador`
- Branch de trabalho: `feat/fatia-1-kabum`

---

## 4. Fatos verificados que você NÃO deve redescobrir

### 4.1 Spring Boot 4.1 reorganizou pacotes — três casos já encontrados

O plano original foi escrito assumindo o layout do Boot 3. Estes três já
morderam:

| O que | Pacote antigo | Pacote atual |
|---|---|---|
| Jackson | `com.fasterxml.jackson.databind` | `tools.jackson.databind` |
| `@WebMvcTest` | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
| Autoconfiguração do `RestClient` | vinha no starter web | módulo próprio: `spring-boot-starter-restclient` |

**O `ObjectMapper` do Jackson 3 é imutável.** Construa com
`JsonMapper.builder()...build()`, nunca `new ObjectMapper().enable(...)`.

**Se encontrar outro import quebrado**, o padrão é: liste as classes dos jars
em `~/.m2/repository/org/springframework/boot/` e ache o endereço novo, em vez
de adivinhar. Mudança de **pacote** é correção mecânica; mudança de
**comportamento** merece parar e perguntar.

### 4.2 Chave de timeout do Boot 4

É `spring.http.clients` no **plural**. A forma singular
(`spring.http.client.*`) está deprecada desde o Boot 4.0.

Isso importa porque **o Spring ignora em silêncio chave que não reconhece**: a
aplicação sobe, os testes passam, e o cliente fica sem timeout nenhum com
aparência de configurado. Já está correto no `application.yml` — não "conserte".

### 4.3 A estrutura do payload da KaBuM

A página de categoria tem `<script id="__NEXT_DATA__">` cujo conteúdo é JSON
com **codificação dupla**: `props.pageProps.data` é uma **string** que contém
outro documento JSON. Só depois de dar parse nessa string é que se chega em
`catalogServer.data`, o array de produtos. `catalogServer.pagination.total` traz
o número de páginas.

**Armadilhas nos campos, todas verificadas na resposta real:**

| Campo | O que você precisa saber |
|---|---|
| `priceWithDiscount` | É o custo efetivo. Quando não há desconto, vem **igual** a `price` — sem condicional |
| `oldPrice` | **Não use.** Veio zerado em 2 de 3 produtos reais |
| `available` | É o sinal de disponibilidade correto |
| `quantity` | **Não use como estoque.** Veio `0` em itens de marketplace que estavam à venda |
| `warranty` | Vem `"Sem Garantia"` em item de marketplace. Dado decisivo para compra empresarial |
| `code` | Identificador estável. É a chave do histórico de preços |

### 4.4 Slugs reais das categorias

Confirmados no sitemap da loja: `coolers`, `disco-rigido-hd`, `fontes`,
`memoria-ram`, `placa-de-video-vga`, `placas-mae`, `processadores`, `ssd-2-5`.

**Atenção:** `placa-de-video` (sem o `-vga`) **não existe** — e a loja devolve
uma página genérica de 50KB com HTTP 200 em vez de 404, o que faz um slug
errado parecer mudança de formato. Já custou um diagnóstico inteiro nesta
sessão.

### 4.5 Restrições do `robots.txt` da KaBuM

A loja proíbe o próprio endpoint de busca. **É por isso que o sistema baixa
páginas de categoria e busca localmente no cache** — não é otimização, é a
forma de ter busca sem violar o que a loja declarou.

O `RobotsGuard` implementa isso e é deliberadamente **mais estrito** que o
arquivo original: bloqueia `sort=` inteiro (o arquivo enumera sete valores) e
`/busca` sem barra. Razão: o risco é assimétrico — deixar passar URL proibida é
falha silenciosa com consequência real (bloqueio de IP), bloquear demais é
barulhento e barato. E o cliente nunca constrói URL com `sort=`, então bloquear
tudo custa zero.

**Nenhuma requisição HTTP pode sair sem passar por `ensureAllowed()` antes.**

---

## 5. Regras do projeto

### 5.1 Política de falha — a regra mais importante

Ela saiu de um defeito real e vale para todo código novo:

| Situação | Resposta correta |
|---|---|
| Dado **errado** (preço que viraria zero) | **Falhar alto.** Dado errado vira decisão de compra errada |
| Dado **faltando** (produto sem preço público) | **Descartar aquele item e logar.** Perder 1 de 60 é melhor que perder 60 |
| **Formato mudou** (estrutura da página) | **Abortar tudo.** Não é um item ruim, é a origem que mudou |

Defaults, quando existirem, **erram para o lado cauteloso**: `available`
ausente vira `false` (esconder é melhor que oferecer o incomprável),
`isMarketplace` ausente vira `true` (mostra o aviso de garantia em vez de
escondê-lo), `warranty` ausente vira `"Não informado"` — nunca `null`, nunca
vazio, porque campo vazio na tela o olho lê como "tem garantia".

### 5.2 Constraints técnicas

- Java 25, Spring Boot 4.1.1.
- **Dinheiro é sempre `BigDecimal`.** Nunca `double`/`float`. No SQLite é
  gravado como `TEXT` — coluna `REAL` guardaria ponto flutuante binário e
  699.99 viraria 699.99000000000001.
- Dependências permitidas além dos starters: `org.jsoup:jsoup:1.23.2` e
  `org.xerial:sqlite-jdbc:3.53.4.0`. Starters de primeira parte do Spring são
  permitidos, com versão gerenciada pelo BOM.
- Nenhum arquivo passa de 600 linhas.
- Testes de unidade **nunca** acessam a rede.
- Package raiz: `br.com.buscador`.
- **Identificadores em inglês; comentários, Javadoc, commits e texto de
  interface em português.**

### 5.3 Sobre testes — três lições que custaram caro

**Teste que passa não é o mesmo que teste que protege.** Três vezes nesta
sessão um teste verde escondia um defeito. A pergunta certa é: *"o que
precisaria estar errado para isso aqui continuar passando?"*

**Nunca ajuste a expectativa do teste para fazê-lo passar.** O caso concreto:
a busca por "memoria" não achava "Memória". O caminho fácil era trocar o termo
do teste para "memória" — a suíte ficaria verde e o produto, inútil, porque
ninguém digita acento numa caixa de busca. A correção certa foi normalizar
acentos.

**Teste anti-troca precisa de valores cruzados.** Um teste criado para detectar
a troca de dois campos booleanos usava o mesmo valor nos dois — passava mesmo
com os campos trocados. Se escrever um teste desses, **prove que ele protege**:
inverta os campos na implementação de propósito, confirme que o teste falha,
desfaça.

### 5.4 Segurança na tela

Conteúdo vindo da loja (`title`, `seller`, `warranty`, `url`) é **texto livre
controlado por vendedores de marketplace**. Já houve um XSS real aqui.

A regra: monte a tela com `createElement`/`textContent`, nunca com `innerHTML`
recebendo dado externo. `href` é atribuído como **propriedade**
(`anchor.href = url`), nunca concatenado em string. Motivo da escolha:
`textContent` não interpreta marcação e é impossível esquecer um caso, enquanto
uma função de escape depende de lembrar de chamá-la em cada interpolação nova.

---

## 6. Estado atual — o que já existe

Branch `feat/fatia-1-kabum`, 38 commits, **61 testes verdes**.

```
src/main/java/br/com/buscador/
  BuscadorApplication.java          só anotações e main — NÃO declare @Bean aqui
  offer/Offer.java                  record de 10 componentes, a fronteira entre fontes
  offer/Source.java                 enum: KABUM, MERCADO_LIVRE
  offer/OfferProvider.java          interface: source() e search(String)
  robots/RobotsGuard.java           recusa URL proibida
  robots/RobotsRules.java           regras da KaBuM
  robots/RobotsConfiguration.java   bean do guarda
  kabum/KabumPayloadParser.java     extrai produtos do __NEXT_DATA__
  kabum/KabumProduct.java           produto cru (9 campos)
  kabum/KabumPage.java              products, totalPages, discardedCount
  kabum/KabumNormalizer.java        KabumProduct -> Offer
  kabum/KabumClient.java            HTTP + guarda de robots
  kabum/KabumProperties.java        config (precisa de @Autowired no construtor!)
  kabum/KabumProvider.java          implementa OfferProvider
  kabum/KabumHttpConfiguration.java bean do RestClient
  catalog/CatalogCache.java         SQLite + busca por palavra-chave
  catalog/CatalogRefresher.java     pagina e enche o cache
  history/PriceHistory.java         grava e calcula variação
  history/PriceChange.java          a variação
  web/SearchController.java         GET /api/search — fan-out com virtual threads
  web/OfferView.java                
  web/SearchResult.java             offers, failedSources, coveredCategories
  resources/schema.sql              3 tabelas
  resources/application.yml         8 categorias, timeouts
  resources/static/index.html       a tela
```

**Duas armadilhas de estrutura já resolvidas — não reintroduza:**

1. **`KabumProvider` tem dois construtores** (um do Spring, um de pacote para
   testes). O do Spring **precisa** de `@Autowired`: sem a anotação o Spring
   não escolhe nenhum, procura o construtor sem argumentos, e a aplicação não
   sobe. E **não** coloque `@Primary` — na Fatia 2 os providers convivem numa
   lista sem precedência.

2. **Nenhum `@Bean` na `BuscadorApplication`.** Bean declarado na classe
   `@SpringBootApplication` não é filtrado pelo slice do `@WebMvcTest` e
   derruba o contexto de teste. Beans moram em classes `@Configuration` junto
   do que configuram.

**Verificado funcionando contra a loja real:** 8 categorias carregam sem falha,
7.132 ofertas em cache, busca "rtx 5060" devolve 79 resultados com preço,
desconto, vendedor e os avisos. Primeira busca do dia ~70s; seguintes ~5s;
cache dura 6h.

---

## 7. O trabalho que falta, em ordem

### Passo 1 — Terminar a Task 10 (teste de contrato)

**Há trabalho não commitado na árvore**: um `pom.xml` modificado e
`src/test/java/br/com/buscador/kabum/KabumContractTest.java` ainda não
versionado. Avalie antes de sobrescrever.

**Para que serve:** a KaBuM não tem contrato com a gente. Um dia ela muda o
formato e o parser quebra. Isso está assumido na spec — a mitigação é
**detecção**, não prevenção. Este teste acessa a loja de verdade e confirma que
a página ainda tem a forma esperada. Quando ficar vermelho, é hora de manutenção
no parser — em vez de o usuário descobrir por uma busca que volta vazia.

Ele **não roda no build padrão**, senão a suíte ficaria vermelha toda vez que a
internet oscilasse e ninguém mais confiaria nela.

**Defeito conhecido do plano, ainda não corrigido:** a configuração proposta
tem `excludedGroups=contract` fixo no surefire, o que **conflita** com
`-Dgroups=contract` — os dois filtros se combinam com AND e o resultado é zero
teste. Ou seja, o comando documentado para rodar o teste de contrato não roda
nada. A correção proposta (não aplicada) é um profile Maven que só exclui o
grupo quando `-Dgroups` não está definido. **Verifique empiricamente as duas
coisas:** que `./mvnw test` não executa o teste de contrato, e que o comando de
rodá-lo de fato o executa.

Use a categoria `/hardware/memoria-ram`. O detalhe do plano está em
`docs/superpowers/plans/2026-09-24-fatia-1-kabum.md`, seção Task 10.

### Passo 2 — Revisão final do branch

Nunca foi feita. Revise o branch inteiro contra `master` e triem os pontos
menores acumulados, que estão registrados em
`.superpowers/sdd/2026-09-24-fatia-1-kabum/progress.md` (procure por
`minor (deferred)`). Os principais:

- O trecho de "nenhuma oferta encontrada" no `index.html` ainda usa
  `insertAdjacentHTML` com escape incompleto. **Não é explorável por terceiro**
  (o termo é digitado pelo próprio usuário; as categorias vêm do
  `application.yml`), mas é padrão inconsistente que alguém pode copiar.
- Testes de `CatalogCacheTest` compartilham o mesmo arquivo SQLite entre
  métodos (o `@DynamicPropertySource` é estático). Não quebra hoje, mas é
  acoplamento implícito.
- Não há teste comportamental de timeout — o que existe prova que a
  configuração chegou ao cliente, não que o timeout dispara.

### Passo 3 — Decidir sobre a relevância da busca

**Este é o problema mais visível no uso real, e é uma decisão de produto — leve
ao usuário antes de implementar.**

A ordenação é puro preço, então buscar "placa de video" traz **suporte de placa
de vídeo a R$ 16,99** nas primeiras posições, antes das placas de verdade. Não
é bug (a spec manda ordenar por custo efetivo), mas atrapalha.

Opções para apresentar, não para escolher sozinho: ordenar por relevância antes
de preço; filtrar acessórios por faixa de preço dentro da categoria; separar
"produtos" de "acessórios" visualmente; ou deixar como está e adicionar filtro
de preço mínimo.

### Passo 4 — Fatia 2: Mercado Livre

Planejada, não iniciada. Entra como um segundo `OfferProvider` na lista — o
`SearchController` **não precisa mudar**, o fan-out já itera sobre a lista
injetada.

**Depende de ação do usuário:** criar a aplicação no devcenter do Mercado Livre
(`https://developers.mercadolivre.com.br/devcenter`). Ele vai precisar de App
ID, Secret Key e um Redirect URI — `http://localhost:8080/auth/callback` serve,
já que roda local.

**O que você precisa saber antes de começar:**

- Autenticação é obrigatória; tudo retorna `403` sem token.
- **Não existe `client_credentials`.** Só `authorization_code` e
  `refresh_token` — uma conta real precisa autorizar o app.
- O `access_token` dura 6 horas.
- **O `refresh_token` é de uso único e rotaciona a cada renovação.** Isso é
  requisito de correção, não detalhe: grave o token novo e confirme a gravação
  **antes** de considerar o antigo gasto. Perder essa gravação obriga o usuário
  a reautorizar na mão.
- Endpoint de busca: `GET https://api.mercadolibre.com/sites/MLB/search`.

**Também será necessário um módulo de `matching`**, para agrupar a mesma peça
entre as duas fontes (tokens de modelo: `RTX 4060`, `DDR4`, `8GB`). Regra de
projeto: **na dúvida, não agrupar.** Dois cards do mesmo produto é um
incômodo; dois produtos diferentes fundidos num card é uma compra errada. O
erro é assimétrico.

**A normalização de acento vale para o ML também** — títulos do Mercado Livre
têm acento pelos mesmos motivos.

---

## 8. Documentos do projeto

| Arquivo | O que é |
|---|---|
| `docs/superpowers/specs/2026-09-24-buscador-promocoes-design.md` | A spec: decisões e por quês |
| `docs/superpowers/plans/2026-09-24-fatia-1-kabum.md` | O plano, **atualizado a cada descoberta** — descreve o que funciona, não o que foi imaginado |
| `docs/superpowers/fixtures/kabum-memoria-ram-2026-09-24.html` | Resposta real da loja, 3 produtos cobrindo casos de borda |
| `.superpowers/sdd/2026-09-24-fatia-1-kabum/progress.md` | Registro de cada decisão tomada e por quê |

O plano foi corrigido ao longo da execução: Jackson 3, slugs certos,
normalização de acento, política de falha, os beans fora da classe principal.
**Confie nele mais que na sua memória de como o Spring Boot costumava ser.**

---

## 9. Como verificar que não quebrou nada

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw test
```

Esperado: **61 testes verdes**, sem acesso à rede.

Para rodar a aplicação:

```bash
./mvnw spring-boot:run
```

Depois abra `http://localhost:8080`. **A primeira busca demora ~70s** (baixa
7.132 produtos de 8 categorias); as seguintes levam ~5s.

**Teste verde com fixture não é a mesma coisa que o sistema funcionando contra
a loja real.** Nesta sessão, três problemas sérios só apareceram rodando de
verdade — inclusive uma categoria inteira que falhava em toda tentativa. Depois
de mudanças relevantes, suba a aplicação e busque de verdade.

---

## 10. Como o usuário prefere trabalhar

- Ele aprova antes de você implementar. Apresente o que pretende fazer e
  espere o "sim".
- Ele quer saber quando você toma uma decisão no lugar dele, e qual o custo se
  estiver errada.
- Relate o que aconteceu de fato: se um teste falhou, diga; se você não rodou,
  diga que não rodou. Não afirme que funciona sem ter verificado.
- **Explícito para commits e push:** cada um precisa de aprovação, e a
  aprovação não vale para o próximo.
