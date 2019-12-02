ScMaBoSS: Scala for running MaBoSS
==========================

Introduction
============

ScMaBoSS is a library for running MaBoSS through Scala within MaBoSS server (https://github.com/maboss-bkmc)

Documentation
=============

 - [scaladoc](https://gautierstoll.github.io/ScMaBoSS/target/scala-2.12/api/ScMaBoSS/)

Usage
=====

MaBoSS server shoudl have been lauched on a given port_number
```bash
./MaBoSS-server --port port_number --hostocalhost --verbose
```

## Import with
```scala
import ScMaBoSS._
```

- InputMBSS.scala: inputs for MaBoSS, including bnd and cfg
- comm.scala: communication with MaBoSS server
- Results.scala: outputs of MaBoSS, including trajectories extraction and simple plotting
- UPMaBoSS.scala: UPMaBoSS implementation

## Example for using MaBoSS server:
- Parameters for the server:
```scala
    val hints: Hints = Hints(check = false,hexfloat = false,augment = true,overRide = false,verbose = false)
```
- Constructing inputs for files:
```scala
    val simulation: CfgMbss = CfgMbss.fromFile("file.cfg", BndMbss.fromFile("file.bnd"))
```
- Open server socket on port port_number:
```scala
    val mcli = new MaBoSSClient(port=port_number)
```
- Run MaBoSS:
```scala
    val result= mcli.run(simulation,hints)
```
- Close socket:
```scala
    mcli.close()
```
Methods of class [Result](https://gautierstoll.github.io/ScMaBoSS/target/scala-2.12/api/ScMaBoSS/Result.html) can be used for extracting ouput data.

## Example for using UPMaBoSS:
- Create UPMaBoSS object from files, using MaBoSS server on port port_number, not using hhoxFloat,
with verbose for UPMaBoSS steps:
```scala
    val upTest = UPMaBoSS.fromFiles("file.upp",CfgMbss.fromFile("file.cfg",BndMbss.fromFile("file.bnd")),port_number,false,true)
```
- Run UPMaBoSS:
```scala
    upRes = upTest.runLight(numberOfSteps)
```
Methods of class [UPMbssOutLight](https://gautierstoll.github.io/ScMaBoSS/target/scala-2.12/api/ScMaBoSS/UPMbssOutLight.html)
can be used for extracting output data. In particular, member lastLinesWithTime can be used within
object [Result](https://gautierstoll.github.io/ScMaBoSS/target/scala-2.12/api/ScMaBoSS/Result$.html).

License
=======

ScMaBoSS is distributed under the Apache License Version 2.0 (see LICENSE file).

Copyright
=========

Copyright (c) 2019-2019 Gautier Stoll

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
