ScMaBoSS: Scala for running MaBoSS
==========================

Introduction
============

ScMaBoSS is a library for running MaBoSS through Scala within [MaBoSS server](https://github.com/maboss-bkmc).

Documentation
=============

[scaladoc](https://gautierstoll.github.io/ScMaBoSS/target/scala-2.12/api/ScMaBoSS/)

 Source files:
- `InputMBSS.scala`: inputs for MaBoSS, including bnd and cfg
- `comm.scala`: communication with MaBoSS server
- `Results.scala`: outputs of MaBoSS, including trajectories extraction and simple plotting
- `UPMaBoSS.scala`: UPMaBoSS implementation
- `InputPopMBSS.scala`: inputs for PopMaBoSS, including PopNetState and popStateDist
- `PopResults.scala`: processing PopMaBoSS output files

Usage
=====

A MaBoSS server should have been compiled from the last version of MaBoSS
[MaBoSS Git](https://github.com/maboss-bkmc/MaBoSS-env-2.0). For that, in the `engine/src/` folder of the source,
```bash
make server
```
For compilation, `flex`, `bison`, `gcc` or `g++` are necessary.

MaBoSS server should be running on a given port_number:
```bash
./MaBoSS-server --port port_number --host localhost --verbose
```

For model larger than 64, the maximum number of nodes should be specified, eg
```bash
make server MAXNODES=100
```
and
```bash
./MaBoSS_100n-server --port port_number --host localhost --verbose
```

## Installation

1. Install scala (>2.12) and sbt. Java version should be at least 8.

2. Download the repository `ScMaBoSS/` (eg `git clone https://github.com/gautierstoll/ScMaBoSS`)

3. Compile the library in `ScMaBoSS/`:
    ```bash
    sbt compile
    sbt package
    ```
4. In your working directory, create a `lib/` sub-directory and copy the `.jar` library from `ScMaBoSS/target/scala-2.12/`.
This `.jar` file can be used in any computer that has java 8 installed.

5. In your working directory, create a `build.sbt`, containing the library dependencies of ScMaBoSS
([saddle](https://github.com/saddle/saddle) and [nspl](https://github.com/pityka/nspl)); you can use the file `build.sbt`
of ScMaBoSS, changing only `name` and `version`.

6. ScMaBoSS can be used in a scala REPL. For instance, in a sbt console (after running `sbt`), open an REPL console
    ```sbt
    console
    ```

    ScMaBoSS can be used after
    ```scala
    import ScMaBoSS._
    ```
    For some version of MacOSX and Linux, sbt console may return an error. In that case, one should launch `TERM=xterm-color` 
    on the terminal before launching `sbt`.
    In an sbt console, the memory can be set like in java when launching sbt, eg `sbt -J-Xmx4G -J-Xms4G`.

    For complicated project, it may be useful to develop scripts in an IDE (like [IntelliJ](https://www.jetbrains.com/idea/)).
    For instance, a scala project can be created in the working directory; classes are defined in `.scala` files and scripts in
    `.sc` files. When running `sbt`, classes will be compiled and scripts can be run by the command `:load` in a scala 
    REPL console.

## Example for using MaBoSS server:
1. Parameters for the server:
    ```scala
    val hints: Hints = Hints(check = false,hexfloat = false,augment = true,overRide = false,verbose = false)
    ```
    If `hexfloat = true`, real numbers will be transmitted from MaBoSS server to scala with no loss.

2. Constructing inputs from files:
    ```scala
    val simulation: CfgMbss = CfgMbss.fromFile("file.cfg", BndMbss.fromFile("file.bnd"))
    ```
3. Open server socket on port port_number:
    ```scala
    val optMcli = MaBoSSClient("localhost",port=port_number)
    ```
    Note that for a distant MaBoSS server, ip adress can be used instead of "localhost". 
    If the socket cannot be open, `MaBoSSClient` return a `None`. Otherwise 
4. Run MaBoSS:
    ```scala
   val oResult : Option[Result] = optMcli.head.run(simulation,hints)
    ```
   Note that `optMcli` is an option; in fact, an option is either a list with a single element or an empty 
   list. The socket is now closed. For a new simulation, a new one needs to be created, otherwise an error occurs 
   and the sbt console crashes. Again, `oResult` is an option, because modeling may have crashed; the result needs 
    also to be extracted:
   ```scala
    val simResult = oResult.head
   ```
    Methods of class [`Result`](https://gautierstoll.github.io/ScMaBoSS/target/scala-2.12/api/ScMaBoSS/Result.html) can be
   used for extracting output data. In all methods of this class, if an argument is an object
    [`NetState`](https://gautierstoll.github.io/ScMaBoSS/target/scala-2.12/api/ScMaBoSS/NetState.html), this latter
   can be defined on a subset of the external nodes.
   **Plotting Boolean state trajectories**:
     Suppose you need the plot of two trajectories: a. `Node1` and `Node2` active, b. `Node1` active and `Node2`inactive.
     ```scala
     simResult.plotStateTraj(netStates = List(new NetState(Map("Node1" ->true,"Node2" -> true)),new NetState(Map("Node1" ->true,"Node2" -> false))),filename = "Test.pdf")
     ``` 
     Note that the class [`NetState`](https://gautierstoll.github.io/ScMaBoSS/target/scala-2.12/api/ScMaBoSS/NetState.html) 
     is defined
     by the activity over a list of nodes. Therefore, this list of nodes can be a subset of the external nodes of the
     model.
  * **Plotting node state trajectories**
     Suppose you need the plot of two trajectories: one for `Node1` and another for `Node2`.
     ```scala
     simResult.plotStateTraj(netStates = List(new NetState(Map("DyingTumor" ->true)),new NetState(Map("ATP" -> true))),filename = "Test.pdf")
     ``` 
  * **Exporting MaBoSS output files**
    ```scala
    simResult.writeProbTraj2File("fileTraj.csv")
    simResult.writeFP2File("fileFP.csv")
    simResult.writeStatDist2File("fileST.csv")
    ```
    Note that if `hexfloat = true` in `Hints`, the double are represented in hexfloat in these `.csv` files.

 * **Exporting data for further processing/plotting**
    The result can be exported as a trajectory table, given a set of Boolean state:
    ```scala
    simResult.writeStateTraj(netStates = List(new NetState(Map("Node1" ->true,"Node2" -> true)),new NetState(Map("Node1" ->true,"Node2" -> false))),filename = "Test.csv")
    ``` 
 * **Saving data**
    Data can be saved in order to be handle by the class 
    [`ResultFromFile`](https://gautierstoll.github.io/ScMaBoSS/target/scala-2.12/api/ScMaBoSS/ResultFromFile.html). Two files are necessary:
    ```scala
    simResult.writeLinesWithTime("fileLineTime.csv")
    simResult.writeSizes("fileSize.csv")
    ```
    If argument `hexfloat = true` is added, the data is saved with no loss. The file describing the sizes is trivial for a MaBoSS run. 
    Nevertheless, it is necessary because the class `ResultFromFile` can also be created from data of UPMaBoSS, where the size changes over 
    time.

## Example of parallel run of MaBoSS servers, aggregating last line of prob_traj:
1. Parameters for the server:
    ```scala
    val hints: Hints = Hints(check = false,hexfloat = false,augment = true,overRide = false,verbose = false)
    ```
2. Constructing inputs from files:
    ```scala
    val simulation: CfgMbss = CfgMbss.fromFile("file.cfg", BndMbss.fromFile("file.bnd"))
    ```
3. Define the parallel set of seed,server_name (eg "localhost" or ip address) and server_port:
    ```scala
    val parSet = scala.collection.parallel.immutable.ParSet((seed1,server_name1,server_port1),(seed2,server_name2,server_port2),...)
    ```
4. Run MaBoSS and collect the aggregated last probability distribution
    ```scala
    val redLastProb = ParReducibleLastLine(simulation,hints,parSet)
    ```
    An option is returned.

## Example for using UPMaBoSS:
1. Create UPMaBoSS object from files, using MaBoSS server on port port_number, not using hexFloat,
with verbose for UPMaBoSS steps:
    ```scala
    val upTest : UPMaBoSS = new UPMaBoSS("file.upp",CfgMbss.fromFile("file.cfg",BndMbss.fromFile("file.bnd")),"localhost",port_number,hexUP = false,verbose = true)
    ```
    if `hexUP = true`, real numbers are passed between scala an MaBoSS server (and vice versa) with no loss.
2. Run UPMaBoSS:
    ```scala
    val upRes : UPMbssOutLight = upTest.runLight(numberOfSteps)
    ```
    Methods of class [`UPMbssOutLight`](https://gautierstoll.github.io/ScMaBoSS/target/scala-2.12/api/ScMaBoSS/UPMbssOutLight.html)
    can be used for extracting output data. As `UPMbssOutLight` is a sub-class of `Result`, it can be used as described above, for result
    of MaBoSS server.
    Note that [`UPMaBoSS`](https://gautierstoll.github.io/ScMaBoSS/target/scala-2.12/api/ScMaBoSS/UPMaBoSS.html) is a class
    that constructs a stream `strRunLight`. Therefore, the simulation can be relaunched with a larger number of steps, with no
    new calculation for the first steps.

## Example for data processing of PopMaBoSS:
1. Create a PopMaBoSS results from file:
    ```scala
    val PRes = new PResultFromFile("File_pop_probtraj.csv","File_simple_pop_probtraj.csv",
        listNodes,listLines)
    ```
    The `listNodes` is necessary, because the output files may not display all nodes. The optional listLines restrict 
   the result to a set of lines (start at 1, without the header)

2. Create a probability distribution over population state at a given step:
    ```scala
    val pState30 = new popStateDist(PRes.popStateProb.take(30).head,PRes.listNodes)
    ```
3. Use the different methods of the class
   [`popStateDist`](https://gautierstoll.github.io/ScMaBoSS/target/scala-2.12/api/ScMaBoSS/popStateDist.html)
    for data processing. The most general method is `probDist` that needs as argument a function from `PopNetState` to an
    option onto any type.

   
License
=======

ScMaBoSS is distributed under the Apache License Version 2.0 (see LICENSE file).

Copyright
=========

Copyright (c) 2019 Gautier Stoll

All rights reserved.

ScMaBoSS is subject to a shared copyright. Each contributor retains copyright to
his or her contributions to Saddle, and is free to annotate these contributions
via code repository commit messages. The copyright to the entirety of the code
base is shared among the Saddle Development Team, comprised of the developers
who have made such contributions.

The copyright and license of each file shall read as follows:

> Copyright (c) 2019 Gautier Stoll
>
> Licensed under the Apache License, Version 2.0 (the "License");
> you may not use this file except in compliance with the License.
> You may obtain a copy of the License at
>
> http://www.apache.org/licenses/LICENSE-2.0
>
> Unless required by applicable law or agreed to in writing, software
> distributed under the License is distributed on an "AS IS" BASIS,
> WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
> See the License for the specific language governing permissions and
> limitations under the License.


Individual contributors may, if they so desire, append their names to
the CONTRIBUTORS file.
