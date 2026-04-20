# DOCUMENTO QUE ACOMPANHA A 2ª ENTREGA DO PROJETO DE SD

## 1) Quais dos seguintes requisitos foram corretamente resolvidos?
Abaixo de cada requisito, respondam: "Sim" ou "Não" ou "Sim mas com erros: [enumerar sucintamente os erros]"

### Estender o sistema para permitir nós replicados
R: Sim

### Difusão baseada em blocos (e não transações individuais)
R: Sim

### Variantes não-bloqueantes dos comandos
R: Sim

### Funcionalidade de atrasar, no nó, a execução de cada pedido
R: Sim

### Suporte ao lançamento de novos nós a qualquer ponto no tempo
R: Sim

### Tolerância a falhas silenciosas dos nós
R: Sim


## 2) Descrevam sucintamente as principais alterações que o grupo aplicou a cada componente indicada abaixo (máx. 800 palavras)
Idealmente, componham uma lista de itens (cada um iniciado por um hífen, tal como a listagem dos requisitos apresentada acima).
Refiram ainda se houve alterações aos argumentos de linha de comando dos executáveis, quais foram e qual a sua justificação.

### Aos .proto:
- Em `common.proto`, todos os requests transacionais (`CreateWalletRequest`, `DeleteWalletRequest`, `TransferRequest`) passaram a incluir um campo `requestId` (uint64) que é gerado do lado do cliente.
- Em `node-sequencer.proto`, foram adicionados dois novos RPCs ao serviço `SequencerService` RPCs: `DeliverBlock` (solicita um bloco já fechado por número), e `GetNumberOfClosedBlocks` (permite a um nó recém-lançado saber quantos blocos já existem). Foi removido `DeliverTransaction`.
- A mensagem `Block` foi acrescentada ao `common.proto` com um número de bloco e uma lista de transações ordenadas.

### Ao programa do Cliente:
- O cliente passou a gerar `requestId`s aleatórios (via `ThreadLocalRandom`) para cada pedido.
- Foram adicionados stubs assíncronos (`NodeServiceGrpc.NodeServiceStub`) para as variantes não-bloqueantes dos comandos, com `CompletableFuture` a par dos stubs bloqueantes já existentes.
- A classe `ClientNodeService` passa um cabeçalho gRPC com o valor de atraso (`delay_seconds`) via `MetadataUtils`, ativando a funcionalidade de delay artificial no nó.
- O `NodeOperationDispatcher` trata automaticamente `DEADLINE_EXCEEDED` e `UNAVAILABLE`: ao expirar o timeout (15 s), o cliente re-envia o pedido para outro nó da lista, mantendo o mesmo `requestId` para garantir idempotência.
- Em caso de colisão de `requestId` na primeira tentativa (resposta `ABORTED`), o dispatcher gera um novo id e tenta novamente no mesmo nó.

### Ao programa do Nó:
- Foi criada a classe `BlockFetcher`, uma thread de fundo que chama `DeliverBlock` repetidamente e aplica cada transação ao estado local por ordem canónica.
- Ao arrancar, o nó obtém primeiro o número de blocos já fechados (`getNumberOfClosedBlocks`) e sincroniza o estado aplicando todos os blocos anteriores antes de começar a servir clientes, resolvendo assim o requisito de lançamento tardio.
- O `NodeTransactionalRequestExecutor` orquestra o ciclo de vida de um pedido transacional: verifica duplicados, faz `Broadcast` ao sequenciador, aguarda o outcome canónico registado pelo `BlockFetcher`, e responde ao cliente.
- O `NodeInterceptor` lê o cabeçalho `delay_seconds` e introduz um atraso antes de processar o pedido, quando presente.
- O `NodeState` mantém uma tabela de outcomes indexada por `requestId`; quando o `BlockFetcher` aplica um bloco, acorda todos os threads que aguardam o resultado de algum dos `requestId`s desse bloco.

### Ao programa do Sequenciador:
- O `SequencerState` passa a agrupar transações num `SequencerBlock` corrente. O bloco fecha quando atinge o tamanho máximo ou quando expira o timeout configurado (por omissão `blockSize=4`, `blockTimeout=5 s`).
- Estes dois parâmetros (`blockSize` e `blockTimeout`) são aceites como argumentos de linha de comando na invocação `mvn exec:java -Dexec.args="<port> <blockSize> <blockTimeout>"`.
- O sequenciador mantém um conjunto de `requestId`s já vistos (`seenRequestIds`): transações duplicadas são descartadas silenciosamente antes de entrarem num bloco.
- Os blocos fechados ficam guardados num `ConcurrentHashMap` e são servidos a qualquer nó via `DeliverBlock`; `notifyAll()` é chamado ao fechar cada bloco para desbloquear nós que aguardem.


## 3) Na vossa solução, as transações recebidas pelo sequenciador levam algum identificador?
Se sim, expliquem brevemente o formato e como é gerado o identificador (máx. 100 palavras)

R: Sim. Os ids são gerados do lado do cliente de forma a permitir o reenvio dos requests feitos pelo mesmo, onde é utilizado java.util.concurrent.ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE). Os ids são inteiros de 64-bits (Long).



## 4) Como foi frisado na primeira aula teórica, não é aceitável o uso de GenAI/Code Copilots para gerar código usado diretamente no projeto.
Tendo isto em conta, respondam brevemente às seguintes 3 perguntas:

### i) Os membros do grupo conseguem explicar o código submetido se tal for perguntado na discussão do projeto (feita sem recurso a AI)?
R: Sim.

### ii) Os membros do grupo conseguem defender todas as decisões de desenho e implementação se tal for perguntado na discussão do projeto (feita sem recurso a AI)?
R: Sim.

### iii) Usaram alguma(s) ferramenta(s) de AI? Se sim, para que uso?
R: Claude e Copilot. Foram utilizadas com o objetivo de identificar possíveis erros semânticos e de sincronização, após a criação do código original. Além disso, serviram para debater possíveis implementações, explorar funcionalidades nativas do Java e para a escrita dos comentários, Javadoc e deste relatório.
