/*
 * File: bus.scala
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-21 04:51:23 pm
 * Modified By: Mathieu Escouteloup
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.core

import chisel3._
import chisel3.experimental.IO
import chisel3.util._

import herd.common.core.{HpcBus}


// ******************************
//             DEBUG
// ******************************
class IOCoreDbgBus(p: IOCoreParams)  extends Bundle {
  val hpc = Vec(p.nHart, new HpcBus())
}