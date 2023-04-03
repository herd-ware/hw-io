/*
 * File: bus.scala                                                             *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-04-03 10:09:31 am                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.ps2

import chisel3._
import chisel3.util._
import scala.math._

import herd.common.bus._
import herd.common.gen._


// ******************************
//             PS/2
// ******************************
class Ps2IO extends Bundle {
  val clk = new BiDirectIO(Bool())
  val data = new BiDirectIO(Bool())
}

// ******************************
//            KEYBOARD
// ******************************
// ------------------------------
//            CONTROL
// ------------------------------
class Ps2KeyboardStatusBus extends Bundle {
  val idle = Bool()
  val full = Vec(4, Bool())
  val av = Vec(4, Bool())
}

class Ps2KeyboardConfigBus extends Bundle {
  val en = Bool()
  val irq = UInt(IRQ.NBIT.W)
  val cycle = UInt(32.W)
}

// ------------------------------
//        MEMORY REGISTER
// ------------------------------
class Ps2KeyboardRegMemIO(p: GenParams, nDataByte: Int) extends Bundle {
  val wen = Input(Vec(3, Bool()))  
  val wdata = Input(UInt(32.W))    

  val status = Output(UInt(32.W))
  val config = Output(UInt(32.W))
  val cycle = Output(UInt(32.W))

  val send = Vec(nDataByte, Flipped(new GenRVIO(p, UInt(0.W), UInt(8.W))))
  val rec = Vec(nDataByte, new GenRVIO(p, Bool(), UInt(8.W)))
}

// ******************************
//             PORT
// ******************************
class Ps2PortIO(p: GenParams, nDataByte: Int) extends Bundle {  
  val send = Vec(nDataByte, Flipped(new GenRVIO(p, UInt(0.W), UInt(8.W))))
  val rec = Vec(nDataByte, new GenRVIO(p, Bool(), UInt(8.W)))
}
