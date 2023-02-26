/*
 * File: bus.scala                                                             *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:10:13 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.uart

import chisel3._
import chisel3.util._
import scala.math._

import herd.common.gen._


// ******************************
//            UART
// ******************************
class UartIO extends Bundle {
  val tx = Output(Bool())
  val rx = Input(Bool())
}

// ******************************
//            CONTROL
// ******************************
class UartStatusBus extends Bundle {
  val idle = Bool()
  val full = Vec(4, Bool())
  val av = Vec(4, Bool())
}

class UartConfigBus extends Bundle {
  val en = Bool()
  val is8bit = Bool()
  val parity = Bool()
  val stop = UInt(2.W)
  val irq = UInt(IRQ.NBIT.W)
  val ncycle = UInt(32.W)
}

// ******************************
//              PORT
// ******************************
class UartPortIO(p: GenParams, nDataByte: Int) extends Bundle {  
  val send = Vec(nDataByte, Flipped(new GenRVIO(p, UInt(0.W), UInt(8.W))))
  val rec = Vec(nDataByte, new GenRVIO(p, Bool(), UInt(8.W)))
}

// ******************************
//        MEMORY REGISTER
// ******************************
class UartRegMemIO(p: GenParams, nDataByte: Int) extends Bundle {
  val wen = Input(Vec(3, Bool()))  
  val wdata = Input(UInt(32.W))    

  val status = Output(UInt(32.W))
  val config = Output(UInt(32.W))
  val ncycle = Output(UInt(32.W))

  val send = Vec(nDataByte, Flipped(new GenRVIO(p, UInt(0.W), UInt(8.W))))
  val rec = Vec(nDataByte, new GenRVIO(p, Bool(), UInt(8.W)))
}
