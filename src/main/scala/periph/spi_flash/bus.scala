/*
 * File: bus.scala                                                             *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:09:36 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.spi_flash

import chisel3._
import chisel3.util._
import scala.math._

import herd.common.bus._
import herd.common.gen._
import herd.io.periph.spi.{SpiCtrlBus}
import herd.io.periph.spi.{MODE}


// ******************************
//            CONTROL
// ******************************
class SpiFlashStatusBus(p: SpiFlashParams) extends Bundle {
  val idle = Bool()
  val full = Vec(4, Bool())
  val av = Vec(4, Bool())
  val auto = Bool()
  val dma = Bool()
}

class SpiFlashConfigBus(p: SpiFlashParams) extends Bundle {
  val en = Bool()
  val mode = UInt(MODE.NBIT.W)
  val ncycle = UInt(32.W)
  val irq = UInt(IRQ.NBIT.W)
  val auto = Bool()
  val addr = UInt(p.nAddrBit.W)
  val offset = UInt(p.nAddrBit.W)
}
  
class SpiFlashCtrlBus extends Bundle {
  val cmd = UInt(CMD.NBIT.W)
  val mb = Bool()
  val auto = Bool()
  val dma = Bool()
}

// ******************************
//        MEMORY REGISTER
// ******************************
class SpiFlashRegMemIO(p: GenParams, nDataByte: Int) extends Bundle {
  val wen = Input(Vec(5, Bool()))  
  val wdata = Input(UInt(32.W))    

  val status = Output(UInt(32.W))
  val config = Output(UInt(32.W))
  val ncycle = Output(UInt(32.W))
  val addr = Output(UInt(32.W))
  val offset = Output(UInt(32.W))
  
  val creq = Vec(nDataByte, Flipped(new GenRVIO(p, UInt(8.W), UInt(0.W))))
  val dreq = Vec(nDataByte, Flipped(new GenRVIO(p, UInt(0.W), UInt(8.W))))
  val read = Vec(nDataByte, new GenRVIO(p, Bool(), UInt(8.W)))
}

// ******************************
//             PORT
// ******************************
class SpiFlashPortIO(p: GenParams, nDataByte: Int) extends Bundle {  
  val creq = Vec(nDataByte, Flipped(new GenRVIO(p, new SpiFlashCtrlBus(), UInt(0.W))))
  val dreq = Vec(nDataByte, Flipped(new GenRVIO(p, UInt(0.W), UInt(8.W))))
  val read = Vec(nDataByte, new GenRVIO(p, Bool(), UInt(8.W)))
}

// ******************************
//             AUTO
// ******************************
class SpiFlashAutoBus(nAddrBit: Int) extends Bundle {
  val addr = UInt(nAddrBit.W)
  val offset = UInt(nAddrBit.W)
}

class SpiFlashAutoIO(p: GenParams, nAddrBit: Int, nDataByte: Int) extends Bundle {
  val req = Flipped(new GenRVIO(p, new SpiFlashAutoBus(nAddrBit), UInt(0.W)))
  val read = Vec(nDataByte, new GenRVIO(p, Bool(), UInt(8.W)))
}
