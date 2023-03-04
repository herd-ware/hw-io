/*
 * File: bus.scala
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 07:59:55 pm
 * Modified By: Mathieu Escouteloup
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.core.clint

import chisel3._
import chisel3.util._


// ******************************
//              BUS
// ******************************
class ClintIO(nDataBit: Int) extends Bundle {
  val ip = Output(UInt(nDataBit.W))           // Interrupt pending
  val en = Output(Bool())                     // New interrupt ?
  val ecause = Output(UInt((nDataBit - 1).W)) // Cause

  val ie = Input(UInt(nDataBit.W))            // Interrupt enable
  val ir = Input(UInt(nDataBit.W))            // Interrupt reset
}




