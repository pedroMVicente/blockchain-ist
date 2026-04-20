# BlockchainIST

Distributed Systems Project 2026
 
**Group A48**

**Difficulty level: I am Death incarnate!**

### Team Members

| Name              | User                               | Email                                           |
|-------------------|------------------------------------|-------------------------------------------------|
| Guilherme Silva   | <https://github.com/S1gn3rs>       | <mailto:guilherme.d.silva@tecnico.ulisboa.pt>   |
| Ema Ferrão        | <https://github.com/EmaFerrao>     | <mailto:emassferrao@tecnico.ulisboa.pt>         |
| Pedro Vicente     | <https://github.com/pedroMVicente> | <mailto:pedro.costa.vicente@tecnico.ulisboa.pt> |

## Getting Started

The overall system is made up of several modules.
The definition of messages and services is in _Contract_.

See the [Project Statement](https://github.com/tecnico-distsys/BlockchainIST-2026) for a complete domain and system description.

### Prerequisites

The Project is configured with Java 17 (which is only compatible with Maven >= 3.8), but if you want to use Java 11 you
can too -- just downgrade the version in the POMs.

To confirm that you have them installed and which versions they are, run in the terminal:

```s
javac -version
mvn -version
```

### Installation

To compile and install all modules:

```s
mvn clean install
```

## Built With

* [Maven](https://maven.apache.org/) - Build and dependency management tool;
* [gRPC](https://grpc.io/) - RPC framework.


### Running the System

You must launch the three components beginning with the sequencer, followed by the node(s), and concluding with the client — each running in a separate terminal.



#### 1. Sequencer

Navigate to the `sequencer/` directory:

```
mvn exec:java -Dexec.args="<port> <blockSize> <blockTimeout>"
```

Sample command:

```
mvn exec:java -Dexec.args="8000 4 5"
```

#### 2. Node

Navigate to the `node/` directory:

```
mvn exec:java -Dexec.args="<port> <organization> <sequencerHost>:<sequencerPort>"
```

Example (with a node listening on port `16000`, organisation `eu`, sequencer at `localhost:8000`):

```
mvn exec:java -Dexec.args="16000 eu localhost:8000"
```

#### 3. Client

Navigate to the `client/` directory and supply one or more node addresses in the format `host:port:organization`:

```
mvn exec:java -Dexec.args="<host>:<port>:<organization> [<host>:<port>:<organization> ...]"
```

Sample invocation (to connect to the node executed above):

```
mvn exec:java -Dexec.args="localhost:16000:eu"
```

The client accepts commands via standard input. Enter `X` to quit.

#### Debug Flag

All components (sequencer, node, and client) can receive the `-Ddebug` flag to display debug information on stderr.

#### Default Values
Default values are defined inside of the root `pom.xml` files.
```
  _____     ____         _____     ____         _____     ____
 /      \  |  o |       /      \  |  o |       /      \  |  o | 
|        |/ ___\|      |        |/ ___\|      |        |/ ___\| 
|_________/            |_________/            |_________/ 
|_|_| |_|_|            |_|_| |_|_|            |_|_| |_|_|

```
