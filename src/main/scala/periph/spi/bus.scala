/*
 * File: bus.scala                                                             *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-04-03 10:09:58 am                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.spi

import chisel3._
import chisel3.util._
import scala.math._

import herd.common.bus._
import herd.common.gen._


// ******************************
//              SPI
// ******************************
class SpiIO (nSlave: Int) extends Bundle {
  val csn = Output(Vec(nSlave , Bool()))
  val sclk = Output(Bool())
  val data = new BiDirectIO(UInt(4.W))
}

// ******************************
//            CONTROL
// ******************************
class SpiStatusBus extends Bundle {
  val idle = Bool()
  val full = Vec(4, Bool())
  val av = Vec(4, Bool())
}

class SpiConfigBus(p: SpiParams) extends Bundle {
  val en = Bool()
  val cpol = Bool()
  val cpha = Bool()
  val mode = UInt(MODE.NBIT.W)
  val cycle = UInt(32.W)
  val slave = UInt(log2Ceil(p.nSlave).W)
  val big = Bool()
  val irq = UInt(IRQ.NBIT.W)
}

class SpiCtrlBus extends Bundle {
  val cmd = UInt(CMD.NBIT.W)
  val mb = Bool()
}

// ******************************
//        MEMORY REGISTER
// ******************************
class SpiRegMemIO(p: GenParams, nDataByte: Int) extends Bundle {
  val wen = Input(Vec(3, Bool()))  
  val wdata = Input(UInt(32.W))    

  val status = Output(UInt(32.W))
  val config = Output(UInt(32.W))
  val cycle = Output(UInt(32.W))
  
  val creq = Vec(nDataByte, Flipped(new GenRVIO(p, UInt(8.W), UInt(0.W))))
  val dreq = Vec(nDataByte, Flipped(new GenRVIO(p, UInt(0.W), UInt(8.W))))
  val read = Vec(nDataByte, new GenRVIO(p, UInt(0.W), UInt(8.W)))
}

// ******************************
//              PORT
// ******************************
class SpiPortIO(p: GenParams, nDataByte: Int) extends Bundle {  
  val creq = Vec(nDataByte, Flipped(new GenRVIO(p, new SpiCtrlBus(), UInt(0.W))))
  val dreq = Vec(nDataByte, Flipped(new GenRVIO(p, UInt(0.W), UInt(8.W))))
  val read = Vec(nDataByte, new GenRVIO(p, UInt(0.W), UInt(8.W)))
}