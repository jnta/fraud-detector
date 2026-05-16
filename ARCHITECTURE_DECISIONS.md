# Documento de Decisões Arquiteturais (ADR) - Rinha de Backend 2026

**Projeto:** API de Detecção de Fraudes via Busca Vetorial
**Linguagem:** Java 26
**Objetivo:** Máxima pontuação (p99 <= 1ms, assertividade altíssima) utilizando exatos 1 CPU e 350 MB de RAM.

Este documento consolida todas as decisões tomadas para otimizar o consumo de CPU e RAM no extremo, contornando a sobrecarga tradicional da JVM, e servirá como **Fonte de Verdade Absoluta** para o desenvolvimento.

---

## 1. Runtime e Compilação
- **Stack:** Java 26 + GraalVM Native Image.
- **Motivo:** Aplicações JVM clássicas alocam em média 80-120MB só para subir. Usando AOT (Ahead-of-Time compilation) via GraalVM, o executável reduz o uso basal de RAM da API para a casa dos `15-25MB`. Isso libera a quase totalidade dos 350MB globais para o *Page Cache* do sistema operacional (crucial para o mmap).
- **Concorrência:** Uso estrito de **Virtual Threads** (Project Loom). Para cada requisição recebida, uma Virtual Thread leve será alocada. Como a busca é rápida e síncrona, não precisamos gerenciar pools de threads pesadas do sistema operacional.

## 2. Camada de Rede (HTTP e Load Balancer)
- **API (Código Java):** Nada de Spring Boot, Quarkus ou Micronaut. Utilizaremos o servidor embutido **`com.sun.net.httpserver.HttpServer`** ou uma stack reativa extremamente enxuta como **ActiveJ**. 
- **Parser JSON:** Proibido uso do Jackson (muito reflection e alocação de objetos). Faremos a extração manual via leitura sequencial de bytes ou usaremos `DslJson` configurado para não gerar lixo na memória.
- **Load Balancer:** Utilizaremos o **HAProxy** configurado puramente para roteamento em Round-Robin (preferencialmente Camada 4 - TCP). Ajustaremos o buffer (`tune.bufsize`) para valores mínimos (ex: `16384` ou menos) para evitar que milhares de conexões presas façam o load balancer estourar a RAM de 350MB por *buffering*.

## 3. Algoritmo de Busca Vetorial (A Grande Mudança)
Para resolver o problema do altíssimo consumo de CPU (1 núcleo) da tradicional `KD-Tree` sob 14 dimensões:
- **Estrutura Escolhida:** IVF (Inverted File Index) acoplado com clusters K-Means.
- **Mecânica:** O espaço vetorial será dividido em (exemplo) 10.000 clusters. No *runtime*, a requisição fará a distância euclidiana contra os 10.000 centróides (extremamente rápido), pegará os `Top N` clusters mais próximos, e escaneará linearmente apenas os vetores contidos nestes clusters para encontrar os $K=5$ vizinhos mais próximos.
- **Benefício:** Reduz as varreduras de ramificações imprevisíveis na memória e destrói o custo de CPU mantendo forte corretude na proximidade espacial.

## 4. Otimização de Memória e Quantização (Int8)
Cada vetor float original de 14 dimensões + 1 label gasta 57 bytes. Isso será "encolhido" para **15 bytes** (14 bytes de vetor + 1 byte boolean de label) utilizando **Quantização Int8**.
- O float, pós-normalização, será limitado para 1 byte.
- **Tratamento do Valor Especial `-1` (Ausência de Histórico):** Em vez do mapeamento padrão `0.0 -> 0`, adotaremos um esquema de *escala deslocada* para evitar lógicas de `if/else` caras durante a busca:
  - `-1` no payload mapeia para o byte `0`.
  - `0.0` no payload mapeia para o byte `128`.
  - `1.0` no payload mapeia para o byte `255`.
- **Cálculo de Distância Runtime:** Leitura direta desses bytes e soma dos quadrados das diferenças utilizando a **Vector API (SIMD Hardware Acceleration)** do Java 26.

## 5. Memory Mapped File (mmap)
- O arquivo binário serializado do índice IVF será lido utilizando **`MemorySegment`** (Foreign Function & Memory API / Project Panama - madura no Java 26). 
- O arquivo é deixado 100% off-heap sob os cuidados do kernel Linux.
- Como as duas instâncias da API farão mmap do *mesmo* arquivo no disco, elas compartilharão o espaço na RAM (Page Cache), duplicando a eficiência de memória do contêiner.

## 6. O Ciclo de Vida do Build (Prevenção de OOM)
- **Quando o Índice é Gerado?** Em **tempo de build do Docker (`Dockerfile`)**, utilizando um stage dedicado (Multi-stage build).
- O script fará a descompressão de `references.json.gz`, rodará o treinamento massivo do K-Means e serializará o arquivo `index.bin`.
- O `index.bin` ficará "assado" (*baked*) na imagem Docker final.
- **Vantagem:** O endpoint `GET /ready` sobe instantaneamente. A aplicação inicia, chama o `mmap` do arquivo binário preexistente e responde 200 OK sem disparar picos de consumo de CPU ou Heap de memória no momento de subida dos containers.

## 7. Tratamento Constants e Risco (Eliminando HashMaps)
- Regras estáticas como o `mcc_risk.json` e os limites do `normalization.json` não existirão como JSON ou Maps no *runtime*.
- Eles serão transformados em **Arrays Primitivos no momento de compilação**:
  - Ex: `static final float[] mccRisk = new float[10000];` onde a chave do array é o inteiro do MCC (ex: mcc 5411 acessa o index `[5411]`).
- Com GraalVM, forçaremos a inicialização dessas classes em tempo de build (`--initialize-at-build-time`), inserindo os dados como literais duros no assembly gerado, com tempo de busca $O(1)$ sem cache miss.

## 8. Resiliência: O Fallback Cego (Peso 5)
A regra de penalidade do PRD afirma que Erros 500 valem `-5 pontos`, falsos negativos `-3` e falsos positivos `-1`.
- Haverá um bloco `try-catch(Throwable t)` na base máxima do `HttpServer`.
- Se o servidor lançar uma exceção de formatação JSON nula, OutOfBounds num array, ou problema no cálculo de I/O, o catch interceptará a falha.
- **Ação:** NUNCA deixar vazar HTTP 500. Retornar sempre `HTTP 200 OK` injetando uma aprovação padrão (`approved: true`, `fraud_score: 0.0`), assumindo uma punição menor como prioridade de segurança tática.
