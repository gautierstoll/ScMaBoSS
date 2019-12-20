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

Usage
=====

MaBoSS server should have been launched on a given port_number:
```bash
./MaBoSS-server --port port_number --host localhost --verbose
```

## Installation

1. Install scala (2.12) and sbt

2. Download the repository `ScMaBoSS/`

3. Compile the library in `ScMaBoSS/`:
```bash
sbt compile
sbt package
```
4. In your working directory, create a `lib/` sub-directory and copy the library from `ScMaBoSS/target/scala-2.12/`

5. In your working directory, create a `build.sbt`, containing the library dependencies of ScMaBoSS
([saddle](https://github.com/saddle/saddle) and [nspl](https://github.com/pityka/nspl))

6. ScMaBoSS can be used in a scala REPL. For instance, in a sbt console: (like an sbt console) with
```sbt
console
import ScMaBoSS._
```
for MacOSX, sbt console may return an error. In that case, one should launch `TERM=xterm-color` on the terminal before launching `sbt`.
In an sbt console, the memory can be set like in java when launching sbt, eg `sbt -J-Xmx4G -J-Xms4G`.

## Example for using MaBoSS server:
1. Parameters for the server:
```scala
    val hints: Hints = Hints(check = false,hexfloat = false,augment = true,overRide = false,verbose = false)
```
2. Constructing inputs from files:
```scala
    val simulation: CfgMbss = CfgMbss.fromFile("file.cfg", BndMbss.fromFile("file.bnd"))
```
3. Open server socket on port port_number:
```scala
    val mcli : MaBoSSClient = new MaBoSSClient(port=port_number)
```
4. Run MaBoSS:
```scala
    val result : Result = mcli.run(simulation,hints)
```

Methods of class [`Result`](https://gautierstoll.github.io/ScMaBoSS/target/scala-2.12/api/ScMaBoSS/Result.html) can be used for extracting ouput data.

## Example of parallel run of MaBoSS servers, aggregating last line of prob_traj:
1. Parameters for the server:
```scala
    val hints: Hints = Hints(check = false,hexfloat = false,augment = true,overRide = false,verbose = false)
```
2. Constructing inputs from files:
```scala
    val simulation: CfgMbss = CfgMbss.fromFile("file.cfg", BndMbss.fromFile("file.bnd"))
```
3. Define the parallel set of seed,server_name and server_port:
```scala
val parSet =
scala.collection.parallel.immutable.ParSet((seed1,server_name1,server_port1),(seed2,server_name2,server_port2),...)
```
4. Run MaBoSS and collect the aggregated last probability distribution
```
val redLastProb : ParReducibleLastLine = new ParReducibleLastLine(simulation,hints,parSet)
```

## Example for using UPMaBoSS:
1. Create UPMaBoSS object from files, using MaBoSS server on port port_number, not using hexFloat,
with verbose for UPMaBoSS steps:
```scala
    val upTest : UPMaBoSS = new UPMaBoSS("file.upp",CfgMbss.fromFile("file.cfg",BndMbss.fromFile("file.bnd")),port_number,false,true)
```
2. Run UPMaBoSS:
```scala
    upRes : UPMbssOutLight = upTest.runLight(numberOfSteps)
```
Methods of class [`UPMbssOutLight`](https://gautierstoll.github.io/ScMaBoSS/target/scala-2.12/api/ScMaBoSS/UPMbssOutLight.html)
can be used for extracting output data.
Note that [`UPMaBoSS`](https://gautierstoll.github.io/ScMaBoSS/target/scala-2.12/api/ScMaBoSS/UPMaBoSS.html) is a class
that constructs a stream `strRunLight`. Therefore, the simulation can be relaunched with a larger number of steps, with no
new calculation for the first steps.

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
