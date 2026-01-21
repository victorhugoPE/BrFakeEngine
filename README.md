# BrFakeEngine

## Visão Geral

O **BrFakeEngine** é um motor avançado de **Fake Players (Bots)** para servidores **Lineage 2 Java (L2J)**. Trata-se de um sistema híbrido que roda **dentro da memória do servidor**, simulando jogadores reais com comportamento humano, persistência de dados e integração total com o core do GameServer.

Diferente de bots externos baseados em pacotes de rede, o BrFakeEngine opera com **latência zero**, chamando diretamente os métodos internos do servidor.

---

## Objetivo Principal

> **Trazer realismo absoluto para o mundo do jogo.**

Os Fake Players não são manequins estáticos. Eles **pensam, decidem, erram, fogem, atacam em grupo e morrem** como jogadores reais.

### Comportamentos planejados e implementados

* Bots atacando **o mesmo mob simultaneamente**
* Bots escolhendo **mobs mais distantes** conforme contexto
* Bots que **atacam outros bots (PvP)**
* Bots que **fogem ao perceber risco de morte**
* Bots que **revidam ataques**
* Bots que **morrem de forma legítima**

---

## Sistema de Rancor (Hate / Aggro)

O BrFakeEngine introduz um sistema avançado de **listas de rancor**, permitindo controle fino de comportamento social e de combate.

Funcionalidades previstas:

* Adicionar ou remover players da **lista de rancor** de um bot
* Redirecionar rancor para:

  * **Clans**
  * **Parties**
* Persistência dessas relações para decisões futuras de IA

Esse sistema permite simular rivalidades, guerras e disputas territoriais de forma orgânica.

---

## Arquitetura do Sistema

### 🧠 O Cérebro — Kotlin

Responsável por:

* Lógica de decisão (IA)
* Gerenciamento de estados
* Banco de dados (MariaDB)
* Escalonamento com **Coroutines**

O Kotlin decide **O QUE** o bot deve fazer.

---

### 🧍 O Corpo — Java (L2J)

Responsável por:

* Executar ações reais no mundo do jogo
* Movimentação via GeoEngine
* Combate, skills, status e inventário

O Java decide **COMO** a ação será executada dentro do core do servidor.

---

### 🔌 Ponte de Integração

A comunicação ocorre através da interface `IFakeBridge`, implementada no servidor pelo arquivo:

```
BrServerBridge.java
```

Essa ponte permite que o Kotlin controle o mundo Java sem dependência direta do core.

---

## Banco de Dados e Persistência

O sistema abandonou XML e passou a utilizar **MariaDB**, garantindo persistência real:

* XP
* Inventário
* Skills
* Status do personagem

Tabela principal:

```
fake_player_manager
```

Os Fake Players são restaurados usando:

```
Player.restore(objectId)
```

Ou seja, **são clones reais de jogadores**, não simulações superficiais.

---

## O Grande Desafio Técnico: Bypass de Conexão

O L2J foi projetado para desconectar qualquer player sem um cliente TCP válido.

### Problema

* `Client = null`
* Sem IP
* Desconexão automática

### Solução Implementada

Criamos um **Client Dummy (Mock)**:

1. Instanciamos um `MMOConnection`
2. Criamos um `GameClient` falso
3. Forçamos o estado `IN_GAME`
4. Registramos o player no `World`
5. Disparamos manualmente os pacotes visuais (`CharInfo`, `UserInfo`)

Resultado: o servidor **acredita** que o fake é um player real.

---

## Arquitetura de Execução (Alto Nível)

### Ciclo de Vida do Fake Player

1. **Boot** — Servidor carrega o plugin
2. **Init** — Inicialização do pool de coroutines
3. **Spawn** — Comando `//spawnfake`
4. **Criação** — FakePlayer + FakeGameClient
5. **Loop de IA** — Avaliação contínua:

   * Tenho alvo?
   * Estou em perigo?
   * Devo fugir, atacar ou ignorar?
6. **Execução** — Servidor executa usando física e regras oficiais

---

## Performance e Escalabilidade

* Threads Java tradicionais são evitadas
* Uso intensivo de **Kotlin Coroutines**
* Capaz de rodar **centenas ou milhares de bots** com baixo custo de CPU e memória

---

## Estado Atual do Projeto

* ✅ Spawn estável
* ✅ Persistência via banco
* ✅ Integração Kotlin ↔ Java
* 🟡 IA de movimento (em evolução)
* 🟡 IA de farm
* 🟡 Comportamento social avançado

---

## Filosofia do Projeto

> **O Fake Player não simula o jogo. Ele joga o jogo.**

Tudo passa pelas mesmas regras, cálculos, colisões e limitações de um jogador humano.

---

## Aviso

Este projeto é **experimental, avançado e altamente técnico**.

Não se trata de um bot comum, mas de um **sistema de emulação comportamental injetado no core do servidor**.

---

## Autor

**Victor Hugo**

Projeto desenvolvido com foco em engenharia reversa, performance e realismo extremo dentro do ecossistema L2J.
