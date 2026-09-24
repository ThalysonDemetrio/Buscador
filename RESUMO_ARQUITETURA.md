# Buscador de Promoções — Lógica e Arquitetura do Projeto

*Documento gerado automaticamente para consolidar o estado do projeto ao final da Fatia 1 (Integração KaBuM).*

## 1. Visão Geral
O sistema é um buscador local de preços focado em peças de informática, projetado para resolver o problema da comparação manual de ofertas. Ele consolida buscas em uma única interface, padroniza preços (custo efetivo à vista, sem frete) e alerta sobre fatores críticos de compra empresarial: vendas de terceiros (marketplace) e ausência de garantias.

## 2. A Lógica de Funcionamento Core (Fatia 1 - KaBuM)
O maior desafio técnico de extrair dados de lojas brasileiras (como KaBuM, Pichau, Terabyte) são os bloqueios anti-bot agressivos (Cloudflare). A solução desenhada e implementada para a KaBuM é inteligente e "passiva":

1. **Respeito Absoluto ao robots.txt:** A KaBuM proíbe pesquisas automatizadas na sua URL de busca oficial. Por isso, nosso sistema **nunca faz a pesquisa no site deles**.
2. **Atualização em Background (O Cache de Catálogo):**
   - O sistema baixa páginas completas de *categorias permitidas* (ex: Memória RAM, Placas de Vídeo).
   - Ele foge do "web scraping" clássico de HTML, que quebra facilmente. Em vez disso, ele encontra um bloco escondido na página (`__NEXT_DATA__`) que contém um JSON limpo e estruturado com a lista de todos os produtos da tela.
   - O sistema extrai esse JSON, coleta as informações vitais e salva tudo em um banco de dados local (**SQLite**) que vive dentro da sua máquina.
3. **Busca Ultra-Rápida:** Quando o usuário final digita "RTX 4060", a pesquisa bate unicamente no banco de dados SQLite local. Resultado: A busca é instantânea, funciona offline em relação à loja e o IP da empresa nunca é banido.

## 3. O Fluxo de Dados (A Vida de um Produto no Sistema)
A jornada do dado passa por módulos muito bem isolados, cada um com uma responsabilidade única:

1. **KabumClient:** Responsável pelas requisições HTTP na loja (configurado com timeouts rigorosos para a aplicação não travar se a rede cair).
2. **RobotsGuard:** Um "segurança de porta" interno. Ele intercepta as URLs que o Client tenta acessar e bloqueia qualquer link proibido pelas regras da loja, garantindo a ética do projeto em código, não só na teoria.
3. **KabumPayloadParser:** O leitor de JSON. Ele extrai os itens da loja aplicando uma lógica de resistência: se 1 produto entre 60 estiver corrompido, ele descarta aquele e salva os 59 válidos, impedindo a perda da categoria inteira.
4. **KabumNormalizer:** O tradutor. Converte o formato proprietário da KaBuM para o formato unificado do nosso sistema (objeto `Offer`). É ele quem define o **Custo Efetivo** (preço à vista com desconto) e sinaliza perigos como ausência de garantia oficial.
5. **CatalogRefresher & CatalogCache (SQLite):** É o banco de dados. Implementa regras espertas de busca: remove acentos automaticamente (escrever "memoria" e "memória" trazem o mesmo resultado).
6. **PriceHistory:** Historiador silencioso. Cada vez que o preço é processado, ele é guardado. Isso sustenta a funcionalidade de comparar preços ("Caiu 15%").
7. **Controller e Interface (HTML):** A renderização final protegida contra falhas de segurança de Injeção de Código (XSS), evitando que textos de vendedores da loja rodem scripts no seu navegador.

## 4. Regras de Ouro (Decisões de Design)
- **Stack:** Java 25 + Spring Boot 4.1.1.
- **Falha Segura e Degradação:** Erros reais falham alto e abortam a operação. Erros parciais degradam (ex: se o lojista não informa o vendedor, assume-se "Não informado" sem derrubar a tela).
- **Sem Falsos Positivos:** Se a busca cair fora das categorias monitoradas, a interface diz claramente *"Pesquisa fora do escopo coberto"* em vez de mentir dizendo *"Nenhum produto encontrado"*.
- **O Risco Aceito:** O site da KaBuM um dia vai mudar seu layout interno, quebrando o leitor de JSON. Aceitamos isso, e criamos a Task 10 (Teste de Contrato) para que um alerta dispare automatica e antecipadamente quando a loja mudar de cara.

## 5. Limitações Atuais e Próximos Passos
O que o software já faz é maduro, mas antes de avançarmos para outras lojas, ele tem um defeito grave de usabilidade conhecido:
- **A Relevância da Busca (Problema Atual):** Como a listagem final (Web) simplesmente pega os produtos encontrados e ordena do mais barato para o mais caro, uma pesquisa por "Placa de Vídeo" vai trazer Suportes de R$ 15, Adaptadores e Cabos antes da placa real.
- **Passo 2:** Corrigir a filtragem e a relevância de preços (estabelecer um "piso" de valor ou filtro restrito).
- **Passo 3:** Iniciar a construção do provedor do Mercado Livre (exigirá login e persistência rotativa de tokens via API oficial).
