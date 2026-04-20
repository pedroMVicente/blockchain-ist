# DOCUMENTO QUE ACOMPANHA A 3ª ENTREGA DO PROJETO DE SD

1) Quais dos seguintes requisitos foram corretamente resolvidos?
Abaixo de cada requisito, respondam: "Sim" ou "Não" ou "Sim mas com erros: [enumerar sucintamente os erros]"

- Suporta a execução/resposta a pedidos de transferência antes da transação correspondente ser entregue pelo sequenciador [C.1].
Sim

- As transações são assinadas digitalmente pelo utilizador que as invocou e as respetivas assinaturas são verificadas antes de cada transação ser executada nas réplicas [C.2].
Sim

- A blockchain gerada pelo sequenciador é assinada digitalmente e as assinaturas correspondentes são verificadas pelos nós que recebem cada bloco [C.2].
Sim


2) Descrevam sucintamente as principais alterações que o grupo aplicou a cada componente indicada abaixo (máx. 800 palavras)
Idealmente, componham uma lista de itens (cada um iniciado por um hífen, tal como a listagem dos requisitos apresentada acima).
Refiram ainda se houve alterações aos argumentos de linha de comando dos executáveis, quais foram e qual a sua justificação.

Aos .proto:
- Adicionámos mensagens `Signed*Request` (para cada tipo de operação) que envolvem o pedido original com a assinatura RSA do utilizador, e `SignedTransaction` que as agrega.
- O `Block` passou a conter `repeated SignedTransaction` para preservar as assinaturas dos utilizadores até à aplicação nos nós.
- Adicionámos `EncryptedRequest` e `EncryptedResponse` para encriptação AES das comunicações. Os RPCs transacionais (cliente-nó e nó-sequenciador) passaram a usar estes tipos.
- O `BlockResponse` foi estendido com `isLatestBlock` e `signature`. Substituímos `GetNumberOfClosedBlocks` por `IsStartingUp`. O `BroadcastRequest` passou a incluir `depends_on` para dependências entre transações.

Ao programa do Cliente:
- Cada pedido transacional é assinado com a chave privada RSA do utilizador e depois cifrado com AES (chave simétrica por utilizador) antes de ser enviado como `EncryptedRequest`.
- As chaves privadas RSA e AES foram adicionadas como recursos no classpath.

Ao programa do Nó:
- Ao processar um bloco o nó apenas notifica as threads específicas que requisitaram uma transação dentro desse mesmo bloco.
- Os nós ao fazerem o fetch inicial da blockchain bloqueiam pedidos de `leSaldo` e `leBlockchain` até que o último bloco que o sequenciador acabou de fechar é enviado ao nó, detetado com o booleano `isLatestBlock` na mensagem `BlockResponse`. Paralelamente permite o envio de transações ao sequenciador. Ao receber um booleano positivo na mensagem `IsStartingUp` o nó não bloqueia `leSaldo` e `leBlockchain` (blockchain global vazia).
- Ao receber um pedido, o nó desencripta com a chave AES do utilizador, verifica a assinatura RSA, e só depois processa a transação.
- O `BlockFetcher` verifica a assinatura RSA do sequenciador em cada bloco e a assinatura de cada transação dentro do bloco antes de a executar, pois não há garantias que o canal entre o nó e o sequenciador seja seguro.
- A comunicação com o sequenciador é cifrada com AES (chave simétrica partilhada).
- O `NodeTransactionalRequestExecutor` foi reestruturado com métodos separados por operação, suportando o caminho otimista para transferências intra-organização e o caminho não-otimista para transferências inter-organização.
- Transferências com saldo insuficiente são rejeitadas imediatamente, sem enviar ao sequenciador.
- Os pedidos de leitura aguardam pela inicialização completa do nó antes de responder.
- A lista de utilizadores autorizados por organização é carregada de `authorized_clients.json`.
- Cada carteira guarda o `latestPendingRequestId`, que é o identificador do último pedido pendente que a afeta. Quando uma nova transação envolve uma carteira, o nó inclui esse identificador no campo `depends_on` da `BroadcastRequest` enviada ao sequenciador, garantindo que este respeita a ordem local de operações sobre a mesma carteira. Uma transação pode depender de no máximo dois pedidos (um por cada carteira envolvida).
- As carteiras possuem um campo `deletePending` que é ativado quando existe um pedido local de eliminação ainda não confirmado pelo sequenciador. Qualquer transferência que envolva uma carteira com `deletePending` ativo é rejeitada de imediato. Quando o bloco com a confirmação chega, a carteira é efetivamente removida.
- As carteiras não são criadas localmente antes da confirmação do sequenciador. O pedido de criação é enviado ao sequenciador e o nó aguarda que o bloco correspondente chegue para adicionar a carteira ao estado local.

Ao programa do Sequenciador:
- O sequenciador assina cada bloco com RSA (SHA256withRSA) antes de o entregar aos nós.
- Toda a comunicação com os nós é cifrada/desencriptada com AES.
- O RPC `GetNumberOfClosedBlocks` foi substituído por `IsStartingUp`, que contem um booleano que serve para o nó saber se o sequenciador ainda não recebeu qualquer tipo de transação (blockchain completamente vazia e sem nenhum bloco a ser criado).
- O RPC `BlockResponse` contém um campo `isLatestBlock` que serve para avisar o nó que o bloco que vai na resposta foi o último bloco que o sequenciador fechou até ao momento do envio da mensagem.
- O sequenciador gere dependências entre transações através da lista `depends_on` do `BroadcastRequest`.

Não houve alterações aos argumentos de linha de comando dos executáveis.

Em relação à encriptação, foi utilizado AES no modo de operação GCM uma vez que é o standard. Todas as chaves simétricas são de 256 bits para ser quantum safe.


3) Na vossa solução para o requisito da C.1, em quais condições uma transferência **NÃO** é executada pelo nó (que recebeu o pedido respetivo) antes da transação ser enviada ao sequenciador? (máx. 100 palavras)
Quando a carteira de destino não pertence à organização do nó (transferência inter-organização), a transação não é executada localmente. É enviada ao sequenciador e o nó aguarda confirmação. Quando ambas as carteiras pertencem ao nó (intra-organização), a transferência é executada imediatamente se a carteira de origem tiver saldo suficiente. Caso o saldo seja insuficiente, a transferência é rejeitada sem ser enviada ao sequenciador.


4) Como foi frisado na primeira aula teórica, não é aceitável o uso de GenAI/Code Copilots para gerar código usado diretamente no projeto.
Tendo isto em conta, respondam brevemente às seguintes 3 perguntas:

i) Os membros do grupo conseguem explicar o código submetido se tal for perguntado na discussão do projeto (feita sem recurso a AI)?
Sim.

ii) Os membros do grupo conseguem defender todas as decisões de desenho e implementação se tal for perguntado na discussão do projeto (feita sem recurso a AI)?
Sim.

iii) Usaram alguma(s) ferramenta(s) de AI? Se sim, para que uso?
Claude e Copilot. Foram utilizadas com o objetivo de identificar possíveis erros semânticos e de sincronização, após a criação do código original. Além disso, serviram para debater possíveis implementações, explorar funcionalidades nativas do Java e para a escrita dos comentários, Javadoc e deste relatório.


