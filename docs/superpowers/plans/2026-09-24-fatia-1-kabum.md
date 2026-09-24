# Fatia 1 — KaBuM fim-a-fim: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar o buscador funcionando fim-a-fim com uma única fonte (KaBuM): o usuário digita um termo e vê ofertas com custo efetivo, disponibilidade, garantia, vendedor e variação de preço em relação ao já observado.

**Architecture:** A KaBuM não tem API pública. As páginas de categoria embutem, em `__NEXT_DATA__`, um JSON com codificação dupla contendo a lista completa de produtos. O sistema baixa páginas de categoria permitidas pelo `robots.txt`, guarda os produtos em SQLite, e busca por palavra-chave sobre esse cache. Toda saída HTTP passa por um guarda que recusa URL proibida. A fonte fica atrás da interface `OfferProvider`, para que o Mercado Livre entre na Fatia 2 sem alterar nada acima dela.

**Tech Stack:** Java 25, Spring Boot 4.1.1, Maven (wrapper), jsoup 1.23.2, sqlite-jdbc 3.53.4.0, JUnit 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-24-buscador-promocoes-design.md`

## Global Constraints

- Java 25. Spring Boot 4.1.1.
- Dependências permitidas além dos starters: `org.jsoup:jsoup:1.23.2`, `org.xerial:sqlite-jdbc:3.53.4.0`. Nenhuma outra sem justificativa.
- Todo valor monetário é `BigDecimal`. Nunca `double` ou `float` para dinheiro.
- Nenhum arquivo passa de 600 linhas.
- Nenhuma requisição HTTP sai do sistema sem passar por `RobotsGuard`.
- Testes de unidade nunca acessam a rede. Só o teste de contrato (Task 10) acessa, e ele não roda no build padrão.
- Package raiz: `br.com.buscador`.
- Idioma: **identificadores** (classes, métodos, variáveis, campos) em inglês.
  **Comentários, Javadoc, mensagens de commit e texto de interface** em
  português. Comentário de domínio em português é o padrão do projeto, não
  uma inconsistência.

## Fatos verificados em 2026-09-24

Estes fatos vieram de inspeção da resposta real e estão congelados na fixture
`docs/superpowers/fixtures/kabum-memoria-ram-2026-09-24.html`.

**Caminho até os produtos:**
`<script id="__NEXT_DATA__">` → `props.pageProps.data` (que é uma **string**
contendo JSON) → parse dessa string → `catalogServer.data` (array de produtos).
A paginação fica em `catalogServer.pagination` como
`{prev, current, next, total}`.

**Semântica dos campos, com as armadilhas:**

| Campo | Observação |
|---|---|
| `priceWithDiscount` | É o custo efetivo. Quando não há desconto, vem **igual** a `price`. Sem condicional. |
| `price` | Preço de referência. |
| `oldPrice` | **Não confiável** — veio `0` em 2 de 3 produtos. Não usar. |
| `available` | Sinal de disponibilidade a usar. |
| `quantity` | **Não usar como estoque** — veio `0` em itens de marketplace que estão à venda. |
| `sellerName` | `"KaBuM!"` para venda própria; nome do lojista quando marketplace. |
| `flags.isMarketplace` | Distingue venda própria de terceiro. |
| `warranty` | `"Sem Garantia"` em todos os itens de marketplace observados. Dado decisivo para compra empresarial. |
| `code` | Identificador estável do produto. É a chave do histórico. |
| `friendlyName` | Slug para montar a URL do produto. |

**Restrições do `robots.txt` da KaBuM** (verificadas na origem):
proíbem `/busca/*?`, `*?query=*`, `*sort=most_searched`, `*sort=price`,
`*sort=-price`, `*sort=-offer_products`, `*sort=manufacturer_name`,
`*sort=-date_product_arrived`, `*sort=-number_ratings`, `/precarrinho*`,
`/carrinho*`, `/minha-conta*`, `/login*`, `/manager/`, `/destaques`,
`/lancamentos`.

Consequência: a API interna `/catalog/v2/products-by-category/...?sort=most_searched`
**é proibida** e não pode ser usada. Só páginas de categoria simples.

## Estrutura de arquivos

```
src/main/java/br/com/buscador/
  BuscadorApplication.java          entrypoint
  offer/Offer.java                  tipo normalizado exposto à interface
  offer/Source.java                 enum de fontes
  offer/OfferProvider.java          interface que a Fatia 2 vai implementar
  robots/RobotsGuard.java           recusa URL proibida
  robots/RobotsRules.java           regras por host
  kabum/KabumProduct.java           produto cru da KaBuM
  kabum/KabumPayloadParser.java     extrai produtos do HTML
  kabum/KabumNormalizer.java        KabumProduct -> Offer
  kabum/KabumClient.java            HTTP + guarda
  kabum/KabumCategories.java        categorias configuradas
  kabum/KabumProvider.java          implementa OfferProvider
  catalog/CatalogCache.java         persistência dos produtos
  catalog/CatalogRefresher.java     atualiza cache expirado
  history/PriceObservation.java     registro de preço observado
  history/PriceHistory.java         grava e calcula variação
  history/PriceChange.java          variação calculada
  web/SearchController.java         endpoint JSON
  web/SearchResult.java             resposta da busca
src/main/resources/
  application.yml
  schema.sql
  static/index.html
src/test/java/br/com/buscador/...   espelha a estrutura acima
src/test/resources/fixtures/kabum-memoria-ram-2026-09-24.html
```

---

### Task 0: Toolchain e esqueleto do projeto

**Files:**
- Create: `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/`, `.gitignore`
- Create: `src/main/java/br/com/buscador/BuscadorApplication.java`
- Create: `src/main/resources/application.yml`
- Test: `src/test/java/br/com/buscador/BuscadorApplicationTests.java`

**Interfaces:**
- Consumes: nada.
- Produces: projeto Maven compilável com `./mvnw test` verde.

**Ambiente verificado em 2026-09-24:** o JDK 25 está instalado em
`C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`
(Temurin 25.0.4.1 LTS), mas **não está no PATH**. Maven e Gradle não estão
instalados e não precisam estar: o wrapper vem do Initializr.

**Regra para todos os comandos deste plano:** cada chamada de shell começa
limpa, então o PATH precisa ser exportado **em cada comando** que invoque
`java` ou `./mvnw`. Prefixe sempre:

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"
```

Onde este plano escreve `./mvnw test`, leia "as duas linhas de export acima,
seguidas de `./mvnw test`". Sem isso o comando falha com
`java: command not found`, que parece erro de código e não é.

- [ ] **Step 1: Confirmar o JDK**

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"
java -version && javac -version
```

Expected: `openjdk version "25.0.4.1"` e `javac 25.0.4.1`.

Se o caminho não existir, a versão do Temurin mudou: localize com
`ls "/c/Program Files/Eclipse Adoptium"` e ajuste `JAVA_HOME` em todos os
comandos.

- [ ] **Step 2: Confirmar que o repositório está pronto**

```bash
git branch --show-current
```

Expected: `feat/fatia-1-kabum`. A spec, o plano e a fixture já estão
commitados em `master`.

- [ ] **Step 3: Baixar o esqueleto do Spring Initializr**

Maven não precisa ser instalado: o Initializr já entrega o wrapper (`mvnw`).

```bash
curl -L -o skeleton.zip "https://start.spring.io/starter.zip?type=maven-project&language=java&bootVersion=4.1.1.RELEASE&javaVersion=25&groupId=br.com.buscador&artifactId=buscador&name=buscador&packageName=br.com.buscador&dependencies=web"
unzip -o skeleton.zip && rm skeleton.zip
```

**Atenção — verificado na execução:** `4.1.1.RELEASE` é o identificador
interno do Initializr, mas o artefato no Maven Central é `4.1.1`, sem sufixo.
Depois de descompactar, confirme que o `<parent>` do `pom.xml` diz:

```xml
<version>4.1.1</version>
```

Se vier com `.RELEASE`, corrija — com o sufixo o projeto não resolve a
dependência e nada compila.

**Atenção — o zip traz um `.gitignore` próprio** que sobrescreve o existente.
Garanta que estas entradas sobrevivem: `target/`, `*.db`, `.idea/`, `*.iml`,
`.vscode/`, `.superpowers/`.

**Atenção — `mvnw` perde o bit executável** em checkout Windows. Marque no
índice do git: `git update-index --chmod=+x mvnw`.

- [ ] **Step 4: Adicionar as duas dependências ao `pom.xml`**

Dentro de `<dependencies>`:

```xml
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.23.2</version>
</dependency>
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.53.4.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
```

- [ ] **Step 5: Configurar `src/main/resources/application.yml`**

```yaml
spring:
  datasource:
    url: jdbc:sqlite:buscador.db
    driver-class-name: org.sqlite.JDBC
  sql:
    init:
      mode: always

buscador:
  kabum:
    base-url: https://www.kabum.com.br
    user-agent: buscador-interno/1.0
    cache-ttl: PT6H
    categories:
      - /hardware/memoria-ram
      - /hardware/placa-de-video
      - /hardware/processadores
      - /hardware/ssd-2-5
      - /hardware/placas-mae
```

- [ ] **Step 6: Rodar o teste que veio do esqueleto**

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw test
```

Expected: PASS — o contexto Spring sobe.

`schema.sql` ainda não existe neste ponto, e isso é esperado: a inicialização
SQL do Spring é um no-op quando não há arquivo. **Não remova
`spring.sql.init.mode`** — a Task 6 cria o `schema.sql` que depende dele.

- [ ] **Step 7: Criar `.gitignore`**

```
target/
*.db
.idea/
*.iml
```

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "chore: esqueleto Spring Boot 4.1 com Java 25

Maven wrapper vem do Initializr para não exigir Maven instalado."
```

---

### Task 1: Tipo de domínio `Offer`

**Files:**
- Create: `src/main/java/br/com/buscador/offer/Source.java`
- Create: `src/main/java/br/com/buscador/offer/Offer.java`
- Create: `src/main/java/br/com/buscador/offer/OfferProvider.java`
- Test: `src/test/java/br/com/buscador/offer/OfferTest.java`

**Interfaces:**
- Consumes: nada.
- Produces: `Offer` (record), `Source` (enum), `OfferProvider` (interface com
  `Source source()` e `List<Offer> search(String term)`).

`Offer` é o único tipo que cruza a fronteira dos providers. É ele que permite a
Fatia 2 entrar sem tocar em nada acima.

- [ ] **Step 1: Escrever o teste que falha**

`src/test/java/br/com/buscador/offer/OfferTest.java`:

```java
package br.com.buscador.offer;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfferTest {

    private Offer offer(BigDecimal effective, BigDecimal reference) {
        return new Offer(Source.KABUM, "922165", "Memória RAM Husky 8GB",
                effective, reference, true, "KaBuM!", "3 anos de garantia",
                false, "https://www.kabum.com.br/produto/922165/x");
    }

    @Test
    void reportsDiscountWhenEffectiveCostIsBelowReference() {
        Offer o = offer(new BigDecimal("699.99"), new BigDecimal("823.52"));
        assertThat(o.hasDiscount()).isTrue();
        assertThat(o.discountPercentage()).isEqualTo(15);
    }

    @Test
    void reportsNoDiscountWhenPricesAreEqual() {
        Offer o = offer(new BigDecimal("619"), new BigDecimal("619"));
        assertThat(o.hasDiscount()).isFalse();
        assertThat(o.discountPercentage()).isZero();
    }

    @Test
    void rejectsNullEffectiveCost() {
        assertThatThrownBy(() -> offer(null, new BigDecimal("10")))
                .isInstanceOf(NullPointerException.class);
    }
}
```

- [ ] **Step 2: Rodar o teste e confirmar a falha**

Run: `./mvnw test -Dtest=OfferTest`
Expected: FAIL na compilação — `Offer`, `Source` não existem.

- [ ] **Step 3: Implementar**

`Source.java`:

```java
package br.com.buscador.offer;

public enum Source {
    KABUM("KaBuM!"),
    MERCADO_LIVRE("Mercado Livre");

    private final String label;

    Source(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
```

`Offer.java`:

```java
package br.com.buscador.offer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * effectiveCost é o preço à vista já com desconto, sem frete: frete depende de
 * CEP e não é conhecido no momento da busca.
 */
public record Offer(
        Source source,
        String externalId,
        String title,
        BigDecimal effectiveCost,
        BigDecimal referencePrice,
        boolean available,
        String seller,
        String warranty,
        boolean thirdPartySeller,
        String url
) {
    public Offer {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(effectiveCost, "effectiveCost");
        Objects.requireNonNull(referencePrice, "referencePrice");
    }

    public boolean hasDiscount() {
        return effectiveCost.compareTo(referencePrice) < 0;
    }

    public int discountPercentage() {
        if (!hasDiscount() || referencePrice.signum() == 0) {
            return 0;
        }
        return referencePrice.subtract(effectiveCost)
                .multiply(BigDecimal.valueOf(100))
                .divide(referencePrice, 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
```

`OfferProvider.java`:

```java
package br.com.buscador.offer;

import java.util.List;

public interface OfferProvider {
    Source source();

    List<Offer> search(String term);
}
```

- [ ] **Step 4: Rodar o teste e confirmar que passa**

Run: `./mvnw test -Dtest=OfferTest`
Expected: PASS, 3 testes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/buscador/offer src/test/java/br/com/buscador/offer
git commit -m "feat: tipo Offer e interface OfferProvider

Offer é a fronteira entre fontes: permite somar o Mercado Livre depois
sem alterar cache, histórico ou interface."
```

---

### Task 2: `RobotsGuard`

**Files:**
- Create: `src/main/java/br/com/buscador/robots/RobotsRules.java`
- Create: `src/main/java/br/com/buscador/robots/RobotsGuard.java`
- Test: `src/test/java/br/com/buscador/robots/RobotsGuardTest.java`

**Interfaces:**
- Consumes: nada.
- Produces: `RobotsGuard.ensureAllowed(String url)` — lança
  `DisallowedUrlException` se a URL casar com uma regra de bloqueio.
  `RobotsRules.kabum()` devolve as regras da KaBuM.

O critério de sucesso 4 da spec ("não acessar URL proibida") vira código
executável aqui, em vez de ficar como intenção.

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.buscador.robots;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RobotsGuardTest {

    private final RobotsGuard guard = new RobotsGuard(RobotsRules.kabum());

    @Test
    void allowsPlainCategoryPage() {
        assertThat(guard.isAllowed("https://www.kabum.com.br/hardware/memoria-ram"))
                .isTrue();
    }

    @Test
    void allowsCategoryPageWithPageNumber() {
        assertThat(guard.isAllowed(
                "https://www.kabum.com.br/hardware/memoria-ram?page_number=2"))
                .isTrue();
    }

    @Test
    void blocksInternalCatalogApiBecauseOfSortParameter() {
        assertThat(guard.isAllowed("https://www.kabum.com.br/catalog/v2/"
                + "products-by-category/hardware/memoria-ram"
                + "?sort=most_searched&page_number=1")).isFalse();
    }

    @Test
    void blocksSearchEndpoint() {
        assertThat(guard.isAllowed(
                "https://www.kabum.com.br/busca/memoria-ram?facet=x")).isFalse();
    }

    @Test
    void blocksQueryParameter() {
        assertThat(guard.isAllowed(
                "https://www.kabum.com.br/hardware?query=rtx")).isFalse();
    }

    @Test
    void ensureAllowedThrowsOnDisallowedUrl() {
        assertThatThrownBy(() -> guard.ensureAllowed(
                "https://www.kabum.com.br/hardware?query=rtx"))
                .isInstanceOf(DisallowedUrlException.class)
                .hasMessageContaining("query=");
    }

    @Test
    void ensureAllowedPassesOnAllowedUrl() {
        guard.ensureAllowed("https://www.kabum.com.br/hardware/memoria-ram");
    }
}
```

- [ ] **Step 2: Rodar o teste e confirmar a falha**

Run: `./mvnw test -Dtest=RobotsGuardTest`
Expected: FAIL na compilação.

- [ ] **Step 3: Implementar**

`RobotsRules.java`:

```java
package br.com.buscador.robots;

import java.util.List;

/**
 * Regras transcritas de https://www.kabum.com.br/robots.txt em 2026-09-24.
 * Cada entrada é um trecho que, se presente na URL, a torna proibida.
 */
public record RobotsRules(List<String> disallowedFragments) {

    public static RobotsRules kabum() {
        return new RobotsRules(List.of(
                "/busca/",
                "query=",
                "sort=most_searched",
                "sort=price",
                "sort=-price",
                "sort=-offer_products",
                "sort=manufacturer_name",
                "sort=-date_product_arrived",
                "sort=-number_ratings",
                "/precarrinho",
                "/carrinho",
                "/minha-conta",
                "/login",
                "/manager/",
                "/destaques",
                "/lancamentos"));
    }
}
```

`DisallowedUrlException.java` (no mesmo pacote):

```java
package br.com.buscador.robots;

public class DisallowedUrlException extends RuntimeException {
    public DisallowedUrlException(String message) {
        super(message);
    }
}
```

`RobotsGuard.java`:

```java
package br.com.buscador.robots;

public class RobotsGuard {

    private final RobotsRules rules;

    public RobotsGuard(RobotsRules rules) {
        this.rules = rules;
    }

    public boolean isAllowed(String url) {
        return matchedFragment(url) == null;
    }

    public void ensureAllowed(String url) {
        String matched = matchedFragment(url);
        if (matched != null) {
            throw new DisallowedUrlException(
                    "URL proibida pelo robots.txt (trecho \"" + matched + "\"): " + url);
        }
    }

    private String matchedFragment(String url) {
        for (String fragment : rules.disallowedFragments()) {
            if (url.contains(fragment)) {
                return fragment;
            }
        }
        return null;
    }
}
```

- [ ] **Step 4: Rodar o teste e confirmar que passa**

Run: `./mvnw test -Dtest=RobotsGuardTest`
Expected: PASS, 7 testes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/buscador/robots src/test/java/br/com/buscador/robots
git commit -m "feat: RobotsGuard recusa URLs proibidas pelo robots.txt

Torna executável o critério de não acessar URL bloqueada, em vez de
deixar a regra como convenção. Bloqueia a API interna de catálogo, que
usa sort=most_searched."
```

---

### Task 3: `KabumPayloadParser`

**Files:**
- Create: `src/main/java/br/com/buscador/kabum/KabumProduct.java`
- Create: `src/main/java/br/com/buscador/kabum/KabumPayloadParser.java`
- Test: `src/test/java/br/com/buscador/kabum/KabumPayloadParserTest.java`
- Copiar: `docs/superpowers/fixtures/kabum-memoria-ram-2026-09-24.html`
  para `src/test/resources/fixtures/kabum-memoria-ram-2026-09-24.html`

**Interfaces:**
- Consumes: nada.
- Produces: `KabumProduct` (record) e
  `KabumPayloadParser.parse(String html)` devolvendo `KabumPage`
  (record com `List<KabumProduct> products` e `int totalPages`).

Extrai JSON estruturado, não seletor CSS. É a razão de a fragilidade deste
provider ser menor do que scraping tradicional.

- [ ] **Step 1: Copiar a fixture para os recursos de teste**

```bash
mkdir -p src/test/resources/fixtures
cp docs/superpowers/fixtures/kabum-memoria-ram-2026-09-24.html \
   src/test/resources/fixtures/
```

- [ ] **Step 2: Escrever o teste que falha**

```java
package br.com.buscador.kabum;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KabumPayloadParserTest {

    private final KabumPayloadParser parser = new KabumPayloadParser();
    private String html;

    @BeforeEach
    void loadFixture() throws IOException {
        try (var in = getClass().getResourceAsStream(
                "/fixtures/kabum-memoria-ram-2026-09-24.html")) {
            html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void extractsEveryProductFromDoubleEncodedPayload() {
        List<KabumProduct> products = parser.parse(html).products();
        assertThat(products).hasSize(3);
        assertThat(products).extracting(KabumProduct::code)
                .containsExactly("922165", "313833", "564716");
    }

    @Test
    void readsMoneyAsBigDecimalWithoutBinaryFloatError() {
        KabumProduct husky = parser.parse(html).products().getFirst();
        assertThat(husky.priceWithDiscount()).isEqualByComparingTo(new BigDecimal("699.99"));
        assertThat(husky.price()).isEqualByComparingTo(new BigDecimal("823.52"));
    }

    @Test
    void readsSellerAndWarranty() {
        List<KabumProduct> products = parser.parse(html).products();
        KabumProduct own = products.getFirst();
        assertThat(own.sellerName()).isEqualTo("KaBuM!");
        assertThat(own.marketplace()).isFalse();
        assertThat(own.warranty()).contains("3 anos de garantia");

        KabumProduct thirdParty = products.get(1);
        assertThat(thirdParty.sellerName()).isEqualTo("UP DISTRIBUIDORA");
        assertThat(thirdParty.marketplace()).isTrue();
        assertThat(thirdParty.warranty()).isEqualTo("Sem Garantia");
    }

    @Test
    void readsTotalPagesFromPagination() {
        assertThat(parser.parse(html).totalPages()).isEqualTo(19);
    }

    @Test
    void failsLoudlyWhenPayloadIsMissing() {
        assertThatThrownBy(() -> parser.parse("<html><body>sem script</body></html>"))
                .isInstanceOf(KabumPayloadException.class)
                .hasMessageContaining("__NEXT_DATA__");
    }
}
```

O último teste é o que protege contra o risco da seção 6 da spec: quando a
KaBuM mudar o formato, o sistema falha com mensagem clara em vez de devolver
lista vazia como se não houvesse produto.

- [ ] **Step 3: Rodar o teste e confirmar a falha**

Run: `./mvnw test -Dtest=KabumPayloadParserTest`
Expected: FAIL na compilação.

- [ ] **Step 4: Implementar os tipos**

`KabumProduct.java`:

```java
package br.com.buscador.kabum;

import java.math.BigDecimal;

public record KabumProduct(
        String code,
        String name,
        String friendlyName,
        BigDecimal price,
        BigDecimal priceWithDiscount,
        boolean available,
        String sellerName,
        boolean marketplace,
        String warranty
) {}
```

`KabumPage.java`:

```java
package br.com.buscador.kabum;

import java.util.List;

public record KabumPage(List<KabumProduct> products, int totalPages) {}
```

`KabumPayloadException.java`:

```java
package br.com.buscador.kabum;

public class KabumPayloadException extends RuntimeException {
    public KabumPayloadException(String message) {
        super(message);
    }

    public KabumPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 5: Implementar o parser**

```java
package br.com.buscador.kabum;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * O payload da KaBuM tem codificação dupla: props.pageProps.data é uma string
 * que contém outro documento JSON.
 */
@Component
public class KabumPayloadParser {

    private static final String SCRIPT_ID = "__NEXT_DATA__";

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    public KabumPage parse(String html) {
        Element script = Jsoup.parse(html).getElementById(SCRIPT_ID);
        if (script == null) {
            throw new KabumPayloadException(
                    "script " + SCRIPT_ID + " ausente: o formato da página mudou");
        }
        try {
            JsonNode outer = mapper.readTree(script.data());
            JsonNode dataNode = outer.at("/props/pageProps/data");
            if (dataNode.isMissingNode() || !dataNode.isTextual()) {
                throw new KabumPayloadException(
                        "props.pageProps.data ausente ou não é texto");
            }
            JsonNode inner = mapper.readTree(dataNode.asText());
            return new KabumPage(readProducts(inner), readTotalPages(inner));
        } catch (KabumPayloadException e) {
            throw e;
        } catch (Exception e) {
            throw new KabumPayloadException("payload ilegível", e);
        }
    }

    private List<KabumProduct> readProducts(JsonNode inner) {
        JsonNode array = inner.at("/catalogServer/data");
        if (!array.isArray()) {
            throw new KabumPayloadException("catalogServer.data não é um array");
        }
        List<KabumProduct> products = new ArrayList<>(array.size());
        for (JsonNode node : array) {
            products.add(new KabumProduct(
                    node.path("code").asText(),
                    node.path("name").asText(),
                    node.path("friendlyName").asText(),
                    money(node, "price"),
                    money(node, "priceWithDiscount"),
                    node.path("available").asBoolean(),
                    node.path("sellerName").asText(),
                    node.at("/flags/isMarketplace").asBoolean(),
                    node.path("warranty").asText()));
        }
        return products;
    }

    private int readTotalPages(JsonNode inner) {
        return inner.at("/catalogServer/pagination/total").asInt(1);
    }

    private BigDecimal money(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue() : BigDecimal.ZERO;
    }
}
```

- [ ] **Step 6: Rodar o teste e confirmar que passa**

Run: `./mvnw test -Dtest=KabumPayloadParserTest`
Expected: PASS, 5 testes.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/br/com/buscador/kabum src/test/java/br/com/buscador/kabum \
        src/test/resources/fixtures
git commit -m "feat: parser do payload da KaBuM

Lê JSON estruturado de __NEXT_DATA__ em vez de seletor CSS. Falha alto
quando o formato muda, em vez de devolver lista vazia."
```

---

### Task 4: `KabumNormalizer`

**Files:**
- Create: `src/main/java/br/com/buscador/kabum/KabumNormalizer.java`
- Test: `src/test/java/br/com/buscador/kabum/KabumNormalizerTest.java`

**Interfaces:**
- Consumes: `KabumProduct` (Task 3), `Offer` e `Source` (Task 1).
- Produces: `KabumNormalizer.toOffer(KabumProduct)` devolvendo `Offer`.

Aqui vivem as quatro armadilhas verificadas na origem. Cada uma tem um teste
com o nome da armadilha.

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.buscador.kabum;

import br.com.buscador.offer.Offer;
import br.com.buscador.offer.Source;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class KabumNormalizerTest {

    private final KabumNormalizer normalizer = new KabumNormalizer();

    private KabumProduct product(BigDecimal price, BigDecimal withDiscount,
                                 boolean available, String seller,
                                 boolean marketplace, String warranty) {
        return new KabumProduct("922165", "Memória RAM Husky 8GB",
                "memoria-ram-husky-8gb", price, withDiscount, available,
                seller, marketplace, warranty);
    }

    @Test
    void usesPriceWithDiscountAsEffectiveCost() {
        Offer offer = normalizer.toOffer(product(new BigDecimal("823.52"),
                new BigDecimal("699.99"), true, "KaBuM!", false, "3 anos"));
        assertThat(offer.effectiveCost()).isEqualByComparingTo("699.99");
        assertThat(offer.referencePrice()).isEqualByComparingTo("823.52");
        assertThat(offer.hasDiscount()).isTrue();
    }

    @Test
    void handlesEqualPricesAsNoDiscount() {
        Offer offer = normalizer.toOffer(product(new BigDecimal("619"),
                new BigDecimal("619"), true, "KaBuM!", false, "1 ano"));
        assertThat(offer.effectiveCost()).isEqualByComparingTo("619");
        assertThat(offer.hasDiscount()).isFalse();
    }

    @Test
    void marksMarketplaceSellerAsThirdParty() {
        Offer offer = normalizer.toOffer(product(new BigDecimal("619"),
                new BigDecimal("619"), true, "UP DISTRIBUIDORA", true,
                "Sem Garantia"));
        assertThat(offer.thirdPartySeller()).isTrue();
        assertThat(offer.seller()).isEqualTo("UP DISTRIBUIDORA");
        assertThat(offer.warranty()).isEqualTo("Sem Garantia");
    }

    @Test
    void trustsAvailableFlagEvenWhenQuantityIsZero() {
        Offer offer = normalizer.toOffer(product(new BigDecimal("619"),
                new BigDecimal("619"), true, "GIGANTEC", true, "Sem Garantia"));
        assertThat(offer.available()).isTrue();
    }

    @Test
    void buildsProductUrlFromCodeAndSlug() {
        Offer offer = normalizer.toOffer(product(new BigDecimal("10"),
                new BigDecimal("10"), true, "KaBuM!", false, "1 ano"));
        assertThat(offer.url())
                .isEqualTo("https://www.kabum.com.br/produto/922165/memoria-ram-husky-8gb");
    }

    @Test
    void setsSourceToKabum() {
        Offer offer = normalizer.toOffer(product(new BigDecimal("10"),
                new BigDecimal("10"), true, "KaBuM!", false, "1 ano"));
        assertThat(offer.source()).isEqualTo(Source.KABUM);
        assertThat(offer.externalId()).isEqualTo("922165");
    }
}
```

- [ ] **Step 2: Rodar o teste e confirmar a falha**

Run: `./mvnw test -Dtest=KabumNormalizerTest`
Expected: FAIL na compilação — `KabumNormalizer` não existe.

- [ ] **Step 3: Implementar**

```java
package br.com.buscador.kabum;

import br.com.buscador.offer.Offer;
import br.com.buscador.offer.Source;
import org.springframework.stereotype.Component;

@Component
public class KabumNormalizer {

    private static final String PRODUCT_URL = "https://www.kabum.com.br/produto/%s/%s";

    /**
     * priceWithDiscount vem igual a price quando não há desconto, então serve
     * como custo efetivo sem condicional. oldPrice não é usado: veio zerado em
     * boa parte dos produtos observados.
     */
    public Offer toOffer(KabumProduct product) {
        return new Offer(
                Source.KABUM,
                product.code(),
                product.name(),
                product.priceWithDiscount(),
                product.price(),
                product.available(),
                product.sellerName(),
                product.warranty(),
                product.marketplace(),
                PRODUCT_URL.formatted(product.code(), product.friendlyName()));
    }
}
```

- [ ] **Step 4: Rodar o teste e confirmar que passa**

Run: `./mvnw test -Dtest=KabumNormalizerTest`
Expected: PASS, 6 testes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/buscador/kabum/KabumNormalizer.java \
        src/test/java/br/com/buscador/kabum/KabumNormalizerTest.java
git commit -m "feat: normalizer da KaBuM para Offer

Cada teste cobre uma armadilha verificada na resposta real: oldPrice
zerado, quantity zero em item disponível, e garantia ausente em
marketplace."
```

---

### Task 5: `KabumClient`

**Files:**
- Create: `src/main/java/br/com/buscador/kabum/KabumClient.java`
- Create: `src/main/java/br/com/buscador/kabum/KabumProperties.java`
- Modify: `src/main/java/br/com/buscador/BuscadorApplication.java` (habilitar
  `@ConfigurationPropertiesScan`)
- Test: `src/test/java/br/com/buscador/kabum/KabumClientTest.java`

**Interfaces:**
- Consumes: `RobotsGuard`, `RobotsRules` e `DisallowedUrlException` (Task 2);
  `KabumPayloadParser`, `KabumPage` e `KabumPayloadException` (Task 3); e a
  fixture já copiada para `src/test/resources/fixtures/` na Task 3.
- Produces: `KabumClient.fetchCategoryPage(String categoryPath, int pageNumber)`
  devolvendo `KabumPage`; `KabumProperties` (record de configuração usado
  também pelas Tasks 7, 9 e 10).

- [ ] **Step 1: Escrever o teste que falha**

O teste usa `MockRestServiceServer`, então não acessa a rede.

```java
package br.com.buscador.kabum;

import br.com.buscador.robots.DisallowedUrlException;
import br.com.buscador.robots.RobotsGuard;
import br.com.buscador.robots.RobotsRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KabumClientTest {

    private MockRestServiceServer server;
    private KabumClient client;
    private String fixture;

    @BeforeEach
    void setUp() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        KabumProperties properties = new KabumProperties(
                "https://www.kabum.com.br", "buscador-interno/1.0",
                Duration.ofHours(6), java.util.List.of("/hardware/memoria-ram"));
        client = new KabumClient(builder.build(),
                new RobotsGuard(RobotsRules.kabum()),
                new KabumPayloadParser(), properties);
        try (var in = getClass().getResourceAsStream(
                "/fixtures/kabum-memoria-ram-2026-09-24.html")) {
            fixture = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void fetchesFirstPageWithoutQueryParameters() {
        server.expect(requestTo("https://www.kabum.com.br/hardware/memoria-ram"))
                .andRespond(withSuccess(fixture, MediaType.TEXT_HTML));

        KabumPage page = client.fetchCategoryPage("/hardware/memoria-ram", 1);

        assertThat(page.products()).hasSize(3);
        assertThat(page.totalPages()).isEqualTo(19);
        server.verify();
    }

    @Test
    void addsPageNumberOnlyFromSecondPageOn() {
        server.expect(requestTo(
                        "https://www.kabum.com.br/hardware/memoria-ram?page_number=2"))
                .andRespond(withSuccess(fixture, MediaType.TEXT_HTML));

        client.fetchCategoryPage("/hardware/memoria-ram", 2);

        server.verify();
    }

    @Test
    void refusesDisallowedCategoryPath() {
        assertThatThrownBy(() -> client.fetchCategoryPage("/busca/rtx", 1))
                .isInstanceOf(DisallowedUrlException.class);
    }
}
```

- [ ] **Step 2: Rodar o teste e confirmar a falha**

Run: `./mvnw test -Dtest=KabumClientTest`
Expected: FAIL na compilação.

- [ ] **Step 3: Implementar `KabumProperties`**

```java
package br.com.buscador.kabum;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "buscador.kabum")
public record KabumProperties(
        String baseUrl,
        String userAgent,
        Duration cacheTtl,
        List<String> categories
) {}
```

- [ ] **Step 4: Implementar `KabumClient`**

```java
package br.com.buscador.kabum;

import br.com.buscador.robots.RobotsGuard;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KabumClient {

    private final RestClient restClient;
    private final RobotsGuard guard;
    private final KabumPayloadParser parser;
    private final KabumProperties properties;

    public KabumClient(RestClient restClient, RobotsGuard guard,
                       KabumPayloadParser parser, KabumProperties properties) {
        this.restClient = restClient;
        this.guard = guard;
        this.parser = parser;
        this.properties = properties;
    }

    public KabumPage fetchCategoryPage(String categoryPath, int pageNumber) {
        String url = buildUrl(categoryPath, pageNumber);
        guard.ensureAllowed(url);
        String html = restClient.get()
                .uri(url)
                .header("User-Agent", properties.userAgent())
                .retrieve()
                .body(String.class);
        if (html == null) {
            throw new KabumPayloadException("resposta vazia para " + url);
        }
        return parser.parse(html);
    }

    private String buildUrl(String categoryPath, int pageNumber) {
        String url = properties.baseUrl() + categoryPath;
        return pageNumber <= 1 ? url : url + "?page_number=" + pageNumber;
    }
}
```

- [ ] **Step 5: Registrar os beans**

Em `BuscadorApplication.java`, adicionar a anotação e o bean do guarda:

```java
package br.com.buscador;

import br.com.buscador.robots.RobotsGuard;
import br.com.buscador.robots.RobotsRules;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BuscadorApplication {

    public static void main(String[] args) {
        SpringApplication.run(BuscadorApplication.class, args);
    }

    @Bean
    RobotsGuard robotsGuard() {
        return new RobotsGuard(RobotsRules.kabum());
    }

    @Bean
    RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }
}
```

- [ ] **Step 6: Rodar o teste e confirmar que passa**

Run: `./mvnw test -Dtest=KabumClientTest`
Expected: PASS, 3 testes.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: cliente HTTP da KaBuM com guarda de robots

Toda URL passa pelo RobotsGuard antes da requisição: caminho proibido
falha antes de sair da máquina."
```

---

### Task 6: `CatalogCache` em SQLite

**Files:**
- Create: `src/main/resources/schema.sql`
- Create: `src/main/java/br/com/buscador/catalog/CatalogCache.java`
- Test: `src/test/java/br/com/buscador/catalog/CatalogCacheTest.java`

**Interfaces:**
- Consumes: `Offer` e `Source` (Task 1).
- Produces:
  - `CatalogCache.replaceCategory(String categoryPath, List<Offer> offers)`
  - `CatalogCache.search(String term)` devolvendo `List<Offer>`
  - `CatalogCache.lastRefresh(String categoryPath)` devolvendo
    `Optional<Instant>`

A busca por palavra-chave acontece aqui, sobre dados já baixados. É o que
substitui o endpoint de busca proibido.

- [ ] **Step 1: Criar `schema.sql`**

```sql
CREATE TABLE IF NOT EXISTS cached_offer (
    source           TEXT    NOT NULL,
    external_id      TEXT    NOT NULL,
    category_path    TEXT    NOT NULL,
    title            TEXT    NOT NULL,
    effective_cost   TEXT    NOT NULL,
    reference_price  TEXT    NOT NULL,
    available        INTEGER NOT NULL,
    seller           TEXT,
    warranty         TEXT,
    third_party      INTEGER NOT NULL,
    url              TEXT    NOT NULL,
    PRIMARY KEY (source, external_id)
);

CREATE TABLE IF NOT EXISTS category_refresh (
    category_path TEXT PRIMARY KEY,
    refreshed_at  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS price_observation (
    source         TEXT NOT NULL,
    external_id    TEXT NOT NULL,
    observed_at    TEXT NOT NULL,
    effective_cost TEXT NOT NULL,
    PRIMARY KEY (source, external_id, observed_at)
);
```

Valor monetário é gravado como `TEXT` de propósito: SQLite guardaria `REAL`
como ponto flutuante binário e introduziria erro em centavos.

- [ ] **Step 2: Escrever o teste que falha**

```java
package br.com.buscador.catalog;

import br.com.buscador.offer.Offer;
import br.com.buscador.offer.Source;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CatalogCacheTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        Path db = Path.of(System.getProperty("java.io.tmpdir"),
                "buscador-cache-" + UUID.randomUUID() + ".db");
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
    }

    @Autowired
    private CatalogCache cache;

    private Offer offer(String id, String title, String cost) {
        return new Offer(Source.KABUM, id, title, new BigDecimal(cost),
                new BigDecimal(cost), true, "KaBuM!", "1 ano", false,
                "https://www.kabum.com.br/produto/" + id + "/x");
    }

    @Test
    void findsByCaseInsensitiveKeyword() {
        cache.replaceCategory("/hardware/memoria-ram", List.of(
                offer("1", "Memória RAM Husky Impulse 8GB DDR4", "699.99"),
                offer("2", "Placa de Vídeo RTX 4060", "1899.00")));

        assertThat(cache.search("husky")).extracting(Offer::externalId)
                .containsExactly("1");
        assertThat(cache.search("HUSKY")).hasSize(1);
    }

    @Test
    void requiresEveryTermToMatch() {
        cache.replaceCategory("/hardware/memoria-ram", List.of(
                offer("1", "Memória RAM Husky Impulse 8GB DDR4", "699.99"),
                offer("2", "Memória RAM Kingston 16GB DDR5", "899.00")));

        assertThat(cache.search("memoria ddr4")).extracting(Offer::externalId)
                .containsExactly("1");
    }

    @Test
    void preservesCentsExactly() {
        cache.replaceCategory("/hardware/memoria-ram",
                List.of(offer("1", "Memória RAM Husky", "699.99")));

        assertThat(cache.search("husky").getFirst().effectiveCost())
                .isEqualByComparingTo("699.99");
    }

    @Test
    void replacingCategoryRemovesProductsThatDisappeared() {
        cache.replaceCategory("/hardware/memoria-ram",
                List.of(offer("1", "Memória RAM Husky", "699.99"),
                        offer("2", "Memória RAM Kingston", "899.00")));
        cache.replaceCategory("/hardware/memoria-ram",
                List.of(offer("1", "Memória RAM Husky", "650.00")));

        assertThat(cache.search("memoria")).hasSize(1);
    }

    @Test
    void recordsRefreshInstantPerCategory() {
        assertThat(cache.lastRefresh("/hardware/memoria-ram")).isEmpty();
        cache.replaceCategory("/hardware/memoria-ram",
                List.of(offer("1", "Memória RAM Husky", "699.99")));
        assertThat(cache.lastRefresh("/hardware/memoria-ram")).isPresent();
    }
}
```

- [ ] **Step 3: Rodar o teste e confirmar a falha**

Run: `./mvnw test -Dtest=CatalogCacheTest`
Expected: FAIL na compilação — `CatalogCache` não existe.

- [ ] **Step 4: Implementar**

```java
package br.com.buscador.catalog;

import br.com.buscador.offer.Offer;
import br.com.buscador.offer.Source;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class CatalogCache {

    private final JdbcClient jdbc;

    public CatalogCache(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void replaceCategory(String categoryPath, List<Offer> offers) {
        jdbc.sql("DELETE FROM cached_offer WHERE category_path = ?")
                .param(categoryPath).update();
        for (Offer offer : offers) {
            jdbc.sql("""
                    INSERT INTO cached_offer (source, external_id, category_path,
                        title, effective_cost, reference_price, available, seller,
                        warranty, third_party, url)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (source, external_id) DO UPDATE SET
                        category_path = excluded.category_path,
                        title = excluded.title,
                        effective_cost = excluded.effective_cost,
                        reference_price = excluded.reference_price,
                        available = excluded.available,
                        seller = excluded.seller,
                        warranty = excluded.warranty,
                        third_party = excluded.third_party,
                        url = excluded.url
                    """)
                    .params(offer.source().name(), offer.externalId(), categoryPath,
                            offer.title(), offer.effectiveCost().toPlainString(),
                            offer.referencePrice().toPlainString(),
                            offer.available() ? 1 : 0, offer.seller(),
                            offer.warranty(), offer.thirdPartySeller() ? 1 : 0,
                            offer.url())
                    .update();
        }
        jdbc.sql("""
                INSERT INTO category_refresh (category_path, refreshed_at)
                VALUES (?, ?)
                ON CONFLICT (category_path) DO UPDATE SET
                    refreshed_at = excluded.refreshed_at
                """)
                .params(categoryPath, Instant.now().toString())
                .update();
    }

    public List<Offer> search(String term) {
        List<String> words = Arrays.stream(term.trim().toLowerCase().split("\\s+"))
                .filter(w -> !w.isBlank())
                .toList();
        if (words.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM cached_offer WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        for (String ignored : words) {
            sql.append(" AND lower(title) LIKE ?");
        }
        for (String word : words) {
            params.add("%" + word + "%");
        }
        sql.append(" ORDER BY CAST(effective_cost AS REAL) ASC");
        return jdbc.sql(sql.toString())
                .params(params)
                .query((rs, rowNum) -> new Offer(
                        Source.valueOf(rs.getString("source")),
                        rs.getString("external_id"),
                        rs.getString("title"),
                        new BigDecimal(rs.getString("effective_cost")),
                        new BigDecimal(rs.getString("reference_price")),
                        rs.getInt("available") == 1,
                        rs.getString("seller"),
                        rs.getString("warranty"),
                        rs.getInt("third_party") == 1,
                        rs.getString("url")))
                .list();
    }

    public Optional<Instant> lastRefresh(String categoryPath) {
        return jdbc.sql("SELECT refreshed_at FROM category_refresh WHERE category_path = ?")
                .param(categoryPath)
                .query(String.class)
                .optional()
                .map(Instant::parse);
    }
}
```

A ordenação usa `CAST(... AS REAL)` apenas para ordenar; o valor devolvido
continua vindo do `TEXT`, então nenhum centavo se perde no resultado.

- [ ] **Step 5: Rodar o teste e confirmar que passa**

Run: `./mvnw test -Dtest=CatalogCacheTest`
Expected: PASS, 5 testes.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: cache de catálogo em SQLite com busca por palavra-chave

Busca local sobre categorias já baixadas substitui o endpoint de busca
da KaBuM, proibido pelo robots.txt. Dinheiro em TEXT para não passar
por ponto flutuante binário."
```

---

### Task 7: `CatalogRefresher` e `KabumProvider`

**Files:**
- Create: `src/main/java/br/com/buscador/catalog/CatalogRefresher.java`
- Create: `src/main/java/br/com/buscador/kabum/KabumProvider.java`
- Test: `src/test/java/br/com/buscador/kabum/KabumProviderTest.java`

**Interfaces:**
- Consumes: `KabumClient` (Task 5), `KabumNormalizer` (Task 4),
  `CatalogCache` (Task 6), `KabumProperties` (Task 5).
- Produces: `KabumProvider implements OfferProvider`, e
  `CatalogRefresher.refreshIfStale(String categoryPath)`.

- [ ] **Step 1: Escrever o teste que falha**

Usa dublês simples, sem framework de mock: o teste descreve o comportamento de
atualizar só o que está velho.

```java
package br.com.buscador.kabum;

import br.com.buscador.offer.Offer;
import br.com.buscador.offer.Source;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class KabumProviderTest {

    @Test
    void reportsItsSource() {
        assertThat(new KabumProvider(term -> {}, term -> java.util.List.of())
                .source()).isEqualTo(Source.KABUM);
    }

    @Test
    void refreshesBeforeSearching() {
        var order = new StringBuilder();
        KabumProvider provider = new KabumProvider(
                term -> order.append("refresh;"),
                term -> {
                    order.append("search;");
                    return java.util.List.of();
                });

        provider.search("memoria");

        assertThat(order.toString()).isEqualTo("refresh;search;");
    }

    @Test
    void returnsWhatTheCacheFinds() {
        Offer expected = new Offer(Source.KABUM, "1", "Memória RAM",
                new java.math.BigDecimal("10"), new java.math.BigDecimal("10"),
                true, "KaBuM!", "1 ano", false, "https://x");
        KabumProvider provider = new KabumProvider(
                term -> {}, term -> java.util.List.of(expected));

        assertThat(provider.search("memoria")).containsExactly(expected);
    }
}
```

- [ ] **Step 2: Rodar o teste e confirmar a falha**

Run: `./mvnw test -Dtest=KabumProviderTest`
Expected: FAIL na compilação.

- [ ] **Step 3: Implementar `CatalogRefresher`**

```java
package br.com.buscador.catalog;

import br.com.buscador.kabum.KabumClient;
import br.com.buscador.kabum.KabumNormalizer;
import br.com.buscador.kabum.KabumPage;
import br.com.buscador.kabum.KabumProperties;
import br.com.buscador.offer.Offer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class CatalogRefresher {

    private static final Logger log = LoggerFactory.getLogger(CatalogRefresher.class);

    private final KabumClient client;
    private final KabumNormalizer normalizer;
    private final CatalogCache cache;
    private final KabumProperties properties;

    public CatalogRefresher(KabumClient client, KabumNormalizer normalizer,
                            CatalogCache cache, KabumProperties properties) {
        this.client = client;
        this.normalizer = normalizer;
        this.cache = cache;
        this.properties = properties;
    }

    public void refreshStaleCategories() {
        for (String categoryPath : properties.categories()) {
            if (isStale(categoryPath)) {
                refresh(categoryPath);
            }
        }
    }

    private boolean isStale(String categoryPath) {
        return cache.lastRefresh(categoryPath)
                .map(at -> at.plus(properties.cacheTtl()).isBefore(Instant.now()))
                .orElse(true);
    }

    private void refresh(String categoryPath) {
        try {
            List<Offer> offers = new ArrayList<>();
            KabumPage first = client.fetchCategoryPage(categoryPath, 1);
            first.products().forEach(p -> offers.add(normalizer.toOffer(p)));
            for (int page = 2; page <= first.totalPages(); page++) {
                client.fetchCategoryPage(categoryPath, page).products()
                        .forEach(p -> offers.add(normalizer.toOffer(p)));
            }
            cache.replaceCategory(categoryPath, offers);
            log.info("categoria {} atualizada: {} ofertas", categoryPath, offers.size());
        } catch (RuntimeException e) {
            log.warn("falha ao atualizar {}: {}", categoryPath, e.getMessage());
        }
    }
}
```

A falha de uma categoria é registrada e não interrompe as demais: o cache
antigo daquela categoria continua servindo, o que é melhor que nenhum
resultado.

- [ ] **Step 4: Implementar `KabumProvider`**

```java
package br.com.buscador.kabum;

import br.com.buscador.catalog.CatalogCache;
import br.com.buscador.catalog.CatalogRefresher;
import br.com.buscador.offer.Offer;
import br.com.buscador.offer.OfferProvider;
import br.com.buscador.offer.Source;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Sem @Primary de propósito: na Fatia 2 os providers convivem numa lista, e
 * nenhum tem precedência sobre o outro.
 */
@Component
public class KabumProvider implements OfferProvider {

    private final Consumer<String> refresh;
    private final Function<String, List<Offer>> lookup;

    /**
     * @Autowired é obrigatório aqui: a classe tem dois construtores, e sem a
     * anotação o Spring não escolhe nenhum — procura o construtor sem
     * argumentos, não encontra, e a aplicação não sobe.
     */
    @Autowired
    public KabumProvider(CatalogRefresher refresher, CatalogCache cache) {
        this(term -> refresher.refreshStaleCategories(), cache::search);
    }

    KabumProvider(Consumer<String> refresh, Function<String, List<Offer>> lookup) {
        this.refresh = refresh;
        this.lookup = lookup;
    }

    @Override
    public Source source() {
        return Source.KABUM;
    }

    @Override
    public List<Offer> search(String term) {
        refresh.accept(term);
        return lookup.apply(term);
    }
}
```

- [ ] **Step 5: Rodar o teste e confirmar que passa**

Run: `./mvnw test -Dtest=KabumProviderTest`
Expected: PASS, 3 testes.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: KabumProvider com atualização de cache sob demanda

Falha de uma categoria não derruba as outras: o cache anterior segue
servindo em vez de a busca voltar vazia."
```

---

### Task 8: Histórico de preços

**Files:**
- Create: `src/main/java/br/com/buscador/history/PriceChange.java`
- Create: `src/main/java/br/com/buscador/history/PriceHistory.java`
- Test: `src/test/java/br/com/buscador/history/PriceHistoryTest.java`

**Interfaces:**
- Consumes: `Offer` e `Source` (Task 1), tabela `price_observation` (Task 6).
- Produces:
  - `PriceHistory.record(List<Offer> offers)`
  - `PriceHistory.changeFor(Offer offer)` devolvendo `Optional<PriceChange>`

A chave é `(source, external_id)`, nunca o termo digitado: o conjunto de
resultados muda entre buscas e produziria variação falsa.

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.buscador.history;

import br.com.buscador.offer.Offer;
import br.com.buscador.offer.Source;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PriceHistoryTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        Path db = Path.of(System.getProperty("java.io.tmpdir"),
                "buscador-history-" + UUID.randomUUID() + ".db");
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
    }

    @Autowired
    private PriceHistory history;

    private Offer offer(String id, String cost) {
        return new Offer(Source.KABUM, id, "Memória RAM Husky",
                new BigDecimal(cost), new BigDecimal(cost), true, "KaBuM!",
                "1 ano", false, "https://x");
    }

    @Test
    void reportsNoChangeWhenOfferWasNeverSeen() {
        assertThat(history.changeFor(offer("novo-1", "699.99"))).isEmpty();
    }

    @Test
    void reportsDropAgainstPreviousObservation() {
        history.record(List.of(offer("drop-1", "823.52")));
        Offer cheaper = offer("drop-1", "699.99");

        PriceChange change = history.changeFor(cheaper).orElseThrow();

        assertThat(change.previousCost()).isEqualByComparingTo("823.52");
        assertThat(change.isDrop()).isTrue();
        assertThat(change.percentage()).isEqualTo(-15);
    }

    @Test
    void reportsRiseAgainstPreviousObservation() {
        history.record(List.of(offer("rise-1", "100.00")));

        PriceChange change = history.changeFor(offer("rise-1", "120.00")).orElseThrow();

        assertThat(change.isDrop()).isFalse();
        assertThat(change.percentage()).isEqualTo(20);
    }

    @Test
    void comparesAgainstOldestObservationOfThatOffer() {
        history.record(List.of(offer("multi-1", "100.00")));
        history.record(List.of(offer("multi-1", "90.00")));

        PriceChange change = history.changeFor(offer("multi-1", "80.00")).orElseThrow();

        assertThat(change.previousCost()).isEqualByComparingTo("100.00");
    }

    @Test
    void keepsObservationsSeparatePerOffer() {
        history.record(List.of(offer("sep-1", "100.00")));

        assertThat(history.changeFor(offer("sep-2", "100.00"))).isEmpty();
    }
}
```

- [ ] **Step 2: Rodar o teste e confirmar a falha**

Run: `./mvnw test -Dtest=PriceHistoryTest`
Expected: FAIL na compilação.

- [ ] **Step 3: Implementar `PriceChange`**

```java
package br.com.buscador.history;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record PriceChange(BigDecimal previousCost, BigDecimal currentCost) {

    public boolean isDrop() {
        return currentCost.compareTo(previousCost) < 0;
    }

    /** Negativo quando o preço caiu, positivo quando subiu. */
    public int percentage() {
        if (previousCost.signum() == 0) {
            return 0;
        }
        return currentCost.subtract(previousCost)
                .multiply(BigDecimal.valueOf(100))
                .divide(previousCost, 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
```

- [ ] **Step 4: Implementar `PriceHistory`**

```java
package br.com.buscador.history;

import br.com.buscador.offer.Offer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class PriceHistory {

    private final JdbcClient jdbc;

    public PriceHistory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void record(List<Offer> offers) {
        String now = Instant.now().toString();
        for (Offer offer : offers) {
            jdbc.sql("""
                    INSERT INTO price_observation
                        (source, external_id, observed_at, effective_cost)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (source, external_id, observed_at) DO NOTHING
                    """)
                    .params(offer.source().name(), offer.externalId(), now,
                            offer.effectiveCost().toPlainString())
                    .update();
        }
    }

    public Optional<PriceChange> changeFor(Offer offer) {
        return jdbc.sql("""
                SELECT effective_cost FROM price_observation
                WHERE source = ? AND external_id = ?
                ORDER BY observed_at ASC
                LIMIT 1
                """)
                .params(offer.source().name(), offer.externalId())
                .query(String.class)
                .optional()
                .map(BigDecimal::new)
                .filter(previous -> previous.compareTo(offer.effectiveCost()) != 0)
                .map(previous -> new PriceChange(previous, offer.effectiveCost()));
    }
}
```

Quando não há observação anterior, o retorno é vazio — e a interface mostra a
oferta sem indicação de variação. Ausência de histórico nunca é apresentada
como estabilidade de preço.

- [ ] **Step 5: Rodar o teste e confirmar que passa**

Run: `./mvnw test -Dtest=PriceHistoryTest`
Expected: PASS, 5 testes.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: histórico oportunista de preços

Chave é (fonte, id do produto), não o termo digitado: agrupar por termo
produziria variação falsa, porque o conjunto de resultados muda entre
buscas."
```

---

### Task 9: Endpoint de busca e página web

**Files:**
- Create: `src/main/java/br/com/buscador/web/SearchController.java`
- Create: `src/main/java/br/com/buscador/web/OfferView.java`
- Create: `src/main/java/br/com/buscador/web/SearchResult.java`
- Create: `src/main/resources/static/index.html`
- Test: `src/test/java/br/com/buscador/web/SearchControllerTest.java`

**Interfaces:**
- Consumes: `OfferProvider` e `Offer` (Task 1), `PriceHistory` e `PriceChange`
  (Task 8), `KabumProperties` (Task 5 — para listar as categorias cobertas).
- Produces: `GET /api/search?term=...` devolvendo `SearchResult`
  (`{offers, failedSources, coveredCategories}`).

O fan-out com virtual threads já entra aqui, mesmo com um provider só: é o
ponto de extensão que a Fatia 2 usa sem alteração.

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.buscador.web;

import br.com.buscador.offer.Offer;
import br.com.buscador.offer.OfferProvider;
import br.com.buscador.offer.Source;
import br.com.buscador.history.PriceHistory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest carrega só a camada web. É deliberado: com @SpringBootTest, o
 * KabumProvider real entraria no contexto e o teste acessaria a rede.
 */
@WebMvcTest(SearchController.class)
@Import(SearchControllerTest.Providers.class)
class SearchControllerTest {

    static class Providers {
        @Bean
        OfferProvider workingProvider() {
            return new OfferProvider() {
                public Source source() { return Source.KABUM; }
                public List<Offer> search(String term) {
                    return List.of(new Offer(Source.KABUM, "1",
                            "Memória RAM Husky 8GB", new BigDecimal("699.99"),
                            new BigDecimal("823.52"), true, "KaBuM!", "3 anos",
                            false, "https://x"));
                }
            };
        }

        @Bean
        OfferProvider failingProvider() {
            return new OfferProvider() {
                public Source source() { return Source.MERCADO_LIVRE; }
                public List<Offer> search(String term) {
                    throw new IllegalStateException("fonte fora do ar");
                }
            };
        }

        @Bean
        PriceHistory noHistory() {
            return new PriceHistory(null) {
                public void record(List<Offer> offers) { }
                public java.util.Optional<br.com.buscador.history.PriceChange>
                        changeFor(Offer offer) { return java.util.Optional.empty(); }
            };
        }

        @Bean
        br.com.buscador.kabum.KabumProperties kabumProperties() {
            return new br.com.buscador.kabum.KabumProperties(
                    "https://www.kabum.com.br", "buscador-teste/1.0",
                    java.time.Duration.ofHours(6),
                    List.of("/hardware/memoria-ram", "/hardware/placa-de-video"));
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void returnsOffersFromWorkingProvider() throws Exception {
        mvc.perform(get("/api/search").param("term", "memoria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers[0].title").value("Memória RAM Husky 8GB"))
                .andExpect(jsonPath("$.offers[0].effectiveCost").value("699.99"))
                .andExpect(jsonPath("$.offers[0].discountPercentage").value(15));
    }

    @Test
    void reportsFailedSourcesInsteadOfHidingThem() throws Exception {
        mvc.perform(get("/api/search").param("term", "memoria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedSources[0]").value("Mercado Livre"));
    }

    @Test
    void rejectsBlankTerm() throws Exception {
        mvc.perform(get("/api/search").param("term", "  "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alwaysReportsWhichCategoriesAreCovered() throws Exception {
        mvc.perform(get("/api/search").param("term", "memoria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coveredCategories[0]")
                        .value("/hardware/memoria-ram"))
                .andExpect(jsonPath("$.coveredCategories[1]")
                        .value("/hardware/placa-de-video"));
    }
}
```

A cobertura da KaBuM é parcial por construção (só as categorias configuradas).
Sem esse campo, uma busca vazia pareceria "a loja não tem o produto", que é
diferente de "esse produto está fora das categorias que eu baixo".

- [ ] **Step 2: Rodar o teste e confirmar a falha**

Run: `./mvnw test -Dtest=SearchControllerTest`
Expected: FAIL na compilação.

- [ ] **Step 3: Implementar os tipos de resposta**

`OfferView.java`:

```java
package br.com.buscador.web;

import br.com.buscador.history.PriceChange;
import br.com.buscador.offer.Offer;

public record OfferView(
        String source,
        String title,
        String effectiveCost,
        String referencePrice,
        int discountPercentage,
        boolean available,
        String seller,
        String warranty,
        boolean thirdPartySeller,
        String url,
        Integer changeSinceFirstSeen
) {
    public static OfferView of(Offer offer, PriceChange change) {
        return new OfferView(
                offer.source().label(),
                offer.title(),
                offer.effectiveCost().toPlainString(),
                offer.referencePrice().toPlainString(),
                offer.discountPercentage(),
                offer.available(),
                offer.seller(),
                offer.warranty(),
                offer.thirdPartySeller(),
                offer.url(),
                change == null ? null : change.percentage());
    }
}
```

`SearchResult.java`:

```java
package br.com.buscador.web;

import java.util.List;

/**
 * coveredCategories existe para atender o requisito da spec de não deixar
 * "nenhum resultado" parecer "a loja não tem o produto": a cobertura é
 * parcial por construção, e a interface precisa dizer isso.
 */
public record SearchResult(
        List<OfferView> offers,
        List<String> failedSources,
        List<String> coveredCategories
) {}
```

- [ ] **Step 4: Implementar o controller**

```java
package br.com.buscador.web;

import br.com.buscador.history.PriceHistory;
import br.com.buscador.kabum.KabumProperties;
import br.com.buscador.offer.Offer;
import br.com.buscador.offer.OfferProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@RestController
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final List<OfferProvider> providers;
    private final PriceHistory history;
    private final KabumProperties kabumProperties;

    public SearchController(List<OfferProvider> providers, PriceHistory history,
                            KabumProperties kabumProperties) {
        this.providers = providers;
        this.history = history;
        this.kabumProperties = kabumProperties;
    }

    @GetMapping("/api/search")
    public SearchResult search(@RequestParam String term) {
        if (term == null || term.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "informe um termo de busca");
        }
        List<Offer> offers = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<List<Offer>>> tasks = providers.stream()
                    .map(p -> (Callable<List<Offer>>) () -> p.search(term))
                    .toList();
            List<Future<List<Offer>>> futures = executor.invokeAll(tasks);
            for (int i = 0; i < futures.size(); i++) {
                try {
                    offers.addAll(futures.get(i).get());
                } catch (Exception e) {
                    String label = providers.get(i).source().label();
                    log.warn("fonte {} falhou: {}", label, e.getMessage());
                    failed.add(label);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "busca interrompida");
        }

        List<OfferView> views = offers.stream()
                .sorted(Comparator.comparing(Offer::effectiveCost))
                .map(o -> OfferView.of(o, history.changeFor(o).orElse(null)))
                .toList();
        history.record(offers);
        return new SearchResult(views, failed, kabumProperties.categories());
    }
}
```

A gravação do histórico acontece **depois** de calcular as variações: gravar
antes compararia a oferta com ela mesma e toda variação sairia zero.

- [ ] **Step 5: Criar a página**

`src/main/resources/static/index.html`:

```html
<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Buscador de Peças</title>
<style>
  :root { color-scheme: light dark; }
  body { font: 16px system-ui, sans-serif; margin: 0; padding: 24px;
         max-width: 900px; margin-inline: auto; }
  h1 { font-size: 20px; }
  form { display: flex; gap: 8px; margin-bottom: 24px; }
  input { flex: 1; padding: 10px; font-size: 16px; }
  button { padding: 10px 20px; font-size: 16px; cursor: pointer; }
  .card { border: 1px solid #8884; border-radius: 8px; padding: 14px;
          margin-bottom: 12px; }
  .title { font-weight: 600; margin-bottom: 6px; }
  .cost { font-size: 20px; font-weight: 700; }
  .ref { text-decoration: line-through; opacity: .6; margin-left: 8px;
         font-size: 14px; }
  .tag { display: inline-block; font-size: 12px; padding: 2px 8px;
         border-radius: 999px; border: 1px solid #8886; margin-right: 6px; }
  .warn { border-color: #c0392b; color: #c0392b; }
  .drop { color: #148f4b; font-weight: 600; }
  .failed { border: 1px solid #c0392b; padding: 10px; border-radius: 8px;
            margin-bottom: 16px; }
</style>
</head>
<body>
<h1>Buscador de Peças</h1>
<form id="f">
  <input id="term" placeholder="ex: memoria ddr4 8gb" autofocus>
  <button>Buscar</button>
</form>
<div id="out"></div>
<script>
const brl = v => Number(v).toLocaleString('pt-BR',
    { style: 'currency', currency: 'BRL' });

document.getElementById('f').addEventListener('submit', async e => {
  e.preventDefault();
  const term = document.getElementById('term').value;
  const out = document.getElementById('out');
  out.textContent = 'Buscando...';
  const res = await fetch('/api/search?term=' + encodeURIComponent(term));
  if (!res.ok) { out.textContent = 'Erro na busca.'; return; }
  const data = await res.json();
  out.innerHTML = '';

  if (data.failedSources.length) {
    const d = document.createElement('div');
    d.className = 'failed';
    d.textContent = 'Fontes que falharam: ' + data.failedSources.join(', ')
                  + '. O resultado abaixo está incompleto.';
    out.appendChild(d);
  }
  if (!data.offers.length) {
    // A cobertura é parcial: dizer isso, em vez de deixar "nenhum resultado"
    // parecer "a loja não vende esse produto".
    out.insertAdjacentHTML('beforeend',
      '<p>Nenhuma oferta encontrada para <strong>'
      + term.replace(/</g, '&lt;') + '</strong>.</p>'
      + '<p>A busca cobre apenas estas categorias: <br>'
      + data.coveredCategories.join('<br>')
      + '</p><p>Se a peça estiver fora delas, ela não é consultada — '
      + 'o que é diferente de a loja não vender.</p>');
    return;
  }
  for (const o of data.offers) {
    const card = document.createElement('div');
    card.className = 'card';
    const ref = o.discountPercentage > 0
        ? `<span class="ref">${brl(o.referencePrice)}</span>` : '';
    const change = o.changeSinceFirstSeen === null ? ''
        : `<div class="${o.changeSinceFirstSeen < 0 ? 'drop' : ''}">`
          + `${o.changeSinceFirstSeen}% em relação ao primeiro preço visto</div>`;
    const warranty = o.warranty === 'Sem Garantia'
        ? '<span class="tag warn">Sem garantia</span>'
        : `<span class="tag">${o.warranty.slice(0, 40)}</span>`;
    const third = o.thirdPartySeller
        ? '<span class="tag warn">Vendedor terceiro</span>' : '';
    card.innerHTML = `
      <div class="title"><a href="${o.url}" target="_blank" rel="noopener">${o.title}</a></div>
      <div><span class="cost">${brl(o.effectiveCost)}</span>${ref}</div>
      ${change}
      <div style="margin-top:8px">
        <span class="tag">${o.source}</span>
        <span class="tag">${o.seller}</span>
        ${third}${warranty}
        ${o.available ? '' : '<span class="tag warn">Indisponível</span>'}
      </div>`;
    out.appendChild(card);
  }
});
</script>
</body>
</html>
```

- [ ] **Step 6: Rodar o teste e confirmar que passa**

Run: `./mvnw test -Dtest=SearchControllerTest`
Expected: PASS, 3 testes.

- [ ] **Step 7: Rodar a suíte inteira**

Run: `./mvnw test`
Expected: PASS em tudo.

- [ ] **Step 8: Subir e conferir na mão**

Run: `./mvnw spring-boot:run`

Abrir `http://localhost:8080`, buscar `memoria ddr4`. A primeira busca demora
(baixa as categorias); as seguintes são rápidas. Confirmar: preço, vendedor,
aviso de "Sem garantia" em item de marketplace. Buscar de novo o mesmo termo
não deve mostrar variação (preço não mudou).

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: busca via HTTP e página web

Fan-out com virtual threads já no lugar: a Fatia 2 entra como mais um
OfferProvider sem tocar no controller. Fonte que falha aparece na tela
em vez de sumir silenciosamente."
```

---

### Task 10: Teste de contrato contra a KaBuM real

**Files:**
- Create: `src/test/java/br/com/buscador/kabum/KabumContractTest.java`
- Modify: `pom.xml` (excluir a tag `contract` do build padrão)

**Interfaces:**
- Consumes: `KabumClient` (Task 5).
- Produces: nada consumido por outras tasks. É o alarme do risco da seção 6 da
  spec.

- [ ] **Step 1: Escrever o teste**

```java
package br.com.buscador.kabum;

import br.com.buscador.robots.RobotsGuard;
import br.com.buscador.robots.RobotsRules;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acessa a KaBuM real. Não roda no build padrão.
 * Rodar com: ./mvnw test -Dgroups=contract
 *
 * Quando este teste falhar, o formato da página mudou e o parser precisa
 * de manutenção. É o mecanismo de detecção do risco aceito na spec.
 */
@Tag("contract")
class KabumContractTest {

    @Test
    void liveCategoryPageStillMatchesTheExpectedShape() {
        KabumProperties properties = new KabumProperties(
                "https://www.kabum.com.br", "buscador-interno/1.0",
                Duration.ofHours(6), List.of("/hardware/memoria-ram"));
        KabumClient client = new KabumClient(RestClient.create(),
                new RobotsGuard(RobotsRules.kabum()),
                new KabumPayloadParser(), properties);

        KabumPage page = client.fetchCategoryPage("/hardware/memoria-ram", 1);

        assertThat(page.products()).isNotEmpty();
        assertThat(page.totalPages()).isPositive();
        KabumProduct first = page.products().getFirst();
        assertThat(first.code()).isNotBlank();
        assertThat(first.name()).isNotBlank();
        assertThat(first.priceWithDiscount()).isPositive();
    }
}
```

- [ ] **Step 2: Excluir a tag do build padrão**

Em `pom.xml`, dentro de `<build><plugins>`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <excludedGroups>contract</excludedGroups>
    </configuration>
</plugin>
```

- [ ] **Step 3: Confirmar que o build padrão não acessa a rede**

Run: `./mvnw test`
Expected: PASS, e `KabumContractTest` **não** aparece entre os executados.

- [ ] **Step 4: Rodar o teste de contrato de propósito**

Run: `./mvnw test -Dgroups=contract`
Expected: PASS, acessando a KaBuM real. Se falhar, o formato mudou.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "test: contrato contra a KaBuM real, fora do build padrão

Detecta mudança de formato na origem. O risco não é evitável, mas é
detectável."
```

---

## Definição de pronto da Fatia 1

1. `./mvnw test` verde, sem acesso à rede.
2. `./mvnw spring-boot:run` sobe e `http://localhost:8080` busca e exibe
   ofertas reais da KaBuM.
3. Item de marketplace aparece marcado como vendedor terceiro e sem garantia.
4. Nenhuma URL proibida pelo `robots.txt` é requisitada — garantido por
   `RobotsGuard` e coberto por teste.
5. Repetir uma busca depois de uma mudança de preço mostra a variação.
6. Busca sem resultado informa quais categorias são cobertas, deixando claro
   que a ausência pode ser limite da cobertura e não falta do produto.

## O que vem na Fatia 2

Mercado Livre como segundo `OfferProvider`: OAuth com rotação de refresh token,
`MercadoLivreNormalizer`, e o módulo `matching` para agrupar a mesma peça entre
as duas fontes. Plano próprio, depois que as credenciais do devcenter
existirem.
